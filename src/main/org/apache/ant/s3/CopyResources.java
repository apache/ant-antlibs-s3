/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.ant.s3;

import java.io.IOException;
import java.util.Optional;
import java.util.Vector;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FilterChain;
import org.apache.tools.ant.types.FilterSet;
import org.apache.tools.ant.types.FilterSetCollection;
import org.apache.tools.ant.types.Mapper;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.ResourceFactory;
import org.apache.tools.ant.types.resources.Union;
import org.apache.tools.ant.util.FileNameMapper;
import org.apache.tools.ant.util.IdentityMapper;
import org.apache.tools.ant.util.ResourceUtils;

/**
 * Abstract task to copy {@link Resource}s.
 */
public abstract class CopyResources extends LoggingTask {

    private final Union sources = new Union();
    private final Vector<FilterSet> filterSets = new Vector<>();
    private final Vector<FilterChain> filterChains = new Vector<>();
    private Mapper mapperElement;
    private String inputEncoding;
    private String outputEncoding;
    private boolean filtering;
    private boolean overwrite;
    private boolean enableMultipleMappings;
    private boolean append;
    private boolean preserveLastModified;

    /**
     * Create a new {@link CopyResources} instance.
     *
     * @param project
     *            Ant {@link Project}
     */
    protected CopyResources(Project project) {
        super(project);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void execute() throws BuildException {
        final ResourceFactory resourceFactory = resourceFactory();

        log("About to %s %d resources to %s", getTaskName(), sources.size(), resourceFactory);

        final FilterSetCollection executionFilters = new FilterSetCollection();
        if (isFiltering()) {
            executionFilters.addFilterSet(getProject().getGlobalFilterSet());
        }
        final FileNameMapper m =
            Optional.ofNullable(mapperElement).map(Mapper::getImplementation).orElseGet(IdentityMapper::new);

        final Function<Resource, Stream<Resource>> toTargets = src -> {
            final String[] targets = m.mapFileName(src.getName());
            final Stream<String> s;
            if (isEnableMultipleMappings()) {
                s = Stream.of(targets);
            } else {
                s = Stream.of(targets[0]);
            }
            return s.map(resourceFactory::getResource).filter(t -> {
                if (t.equals(src)) {
                    log(Project.MSG_INFO, "Skipping (self) %s of %s to %s", getTaskName(), src, t);
                    return false;
                }
                return true;
            });
        };
        sources.forEach(src -> {
            toTargets.apply(src).forEach(target -> {
                if (!src.isExists()) {
                    log("Skipping %s of nonexistent resource %s", getTaskName(), src);
                    return;
                }
                log("%s %s to %s", getTaskName(), src, target);
                try {
                    copyResource(src, target, executionFilters, filterChains);
                } catch (final IOException e) {
                    log(e, "Unable to %s %s to %s due to exception", getTaskName(), src, target);
                }
            });
        });
    }

    /**
     * Add a nested source {@link ResourceCollection}.
     *
     * @param sources
     *            to add
     */
    public void add(ResourceCollection sources) {
        this.sources.add(sources);
    }

    /**
     * Define the nested {@code mapper} to map source to target
     * {@link Resource}s.
     *
     * @return a {@link Mapper} to be configured
     * @throws BuildException
     *             if more than one mapper is defined
     */
    public Mapper createMapper() {
        if (mapperElement != null) {
            throw new BuildException("Cannot define more than one mapper", getLocation());
        }
        return mapperElement = new Mapper(getProject());
    }

    /**
     * Add a nested {@link FileNameMapper}.
     *
     * @param fileNameMapper
     *            the {@link FileNameMapper} to add
     */
    public void add(FileNameMapper fileNameMapper) {
        createMapper().add(fileNameMapper);
    }

    /**
     * Get the input encoding.
     *
     * @return {@link String}
     */
    public String getInputEncoding() {
        return inputEncoding;
    }

    /**
     * Set the input encoding.
     *
     * @param inputEncoding
     *            to set
     */
    public void setInputEncoding(String inputEncoding) {
        this.inputEncoding = inputEncoding;
    }

    /**
     * Get the output encoding.
     *
     * @return {@link String}
     */
    public String getOutputEncoding() {
        return outputEncoding;
    }

    /**
     * Set the output encoding.
     *
     * @param outputEncoding
     *            to set
     */
    public void setOutputEncoding(String outputEncoding) {
        this.outputEncoding = outputEncoding;
    }

    /**
     * Learn whether global Ant filters will be applied to copy operations.
     *
     * @return {@code boolean}
     */
    public boolean isFiltering() {
        return filtering;
    }

    /**
     * Set whether global Ant filters should be applied to copy operations.
     *
     * @param filtering
     *            flag
     */
    public void setFiltering(boolean filtering) {
        this.filtering = filtering;
    }

    /**
     * Add a nested {@code filterchain}.
     *
     * @return {@link FilterChain}
     */
    public FilterChain createFilterChain() {
        final FilterChain filterChain = new FilterChain();
        filterChains.addElement(filterChain);
        return filterChain;
    }

    /**
     * Add a nested {@code filterset}.
     *
     * @return {@link FilterSet}
     */
    public FilterSet createFilterSet() {
        final FilterSet filterSet = new FilterSet();
        filterSets.addElement(filterSet);
        return filterSet;
    }

    /**
     * Learn whether up-to-date target {@link Resource}s will be overwritten.
     *
     * @return {@code boolean}
     */
    public boolean isOverwrite() {
        return overwrite;
    }

    /**
     * Set whether up-to-date target {@link Resource}s should be overwritten.
     *
     * @param overwrite
     *            flag
     */
    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    /**
     * Learn whether, when a given source {@link Resource} applied to the
     * configured {@link Mapper}/{@link FileNameMapper} results in multiple
     * target {@link Resource}s, all targets, or just the first one, will be
     * copied to.
     *
     * @return {@code boolean}
     */
    public boolean isEnableMultipleMappings() {
        return enableMultipleMappings;
    }

    /**
     * When a given source {@link Resource} applied to the configured
     * {@link Mapper}/{@link FileNameMapper} results in multiple target
     * {@link Resource}s, set whether all targets, or just the first one, will
     * be copied to.
     *
     * @param enableMultipleMappings
     *            flag
     */
    public void setEnableMultipleMappings(boolean enableMultipleMappings) {
        this.enableMultipleMappings = enableMultipleMappings;
    }

    /**
     * Learn whether target {@link Resource} content should be appended.
     *
     * @return {@code boolean}
     */
    public boolean isAppend() {
        return append;
    }

    /**
     * Set whether target {@link Resource} content should be appended.
     *
     * @param append
     *            flag
     */
    public void setAppend(boolean append) {
        this.append = append;
    }

    /**
     * Learn whether last modified time is being preserved on target
     * {@link Resource}s.
     *
     * @return {@code boolean}
     */
    public boolean isPreserveLastModified() {
        return preserveLastModified;
    }

    /**
     * Set whether to preserve last modified time on target {@link Resource}s.
     *
     * @param preserveLastModified
     *            flag
     */
    public void setPreserveLastModified(boolean preserveLastModified) {
        this.preserveLastModified = preserveLastModified;
    }

    /**
     * Template method to return the {@link ResourceFactory} destination.
     *
     * @return {@link ResourceFactory}
     */
    protected abstract ResourceFactory resourceFactory();

    /**
     * Copy {@code source} to {@code target} with the given
     * {@link FilterSetCollection} and {@code filterChains}.
     *
     * @param source
     *            {@link Resource}
     * @param dest
     *            {@link Resource}
     * @param filters
     *            {@link FilterSetCollection}
     * @param filterChains
     *            {@link Vector} of {@link FilterChain}
     * @throws IOException on error
     */
    protected void copyResource(Resource source, Resource dest, FilterSetCollection filters,
        Vector<FilterChain> filterChains) throws IOException {
        ResourceUtils.copyResource(source, dest, filters, filterChains, isOverwrite(), isPreserveLastModified(),
            isAppend(), getInputEncoding(), getOutputEncoding(), getProject());
    }
}
