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

import static org.apache.ant.s3.ProjectUtils.componentName;
import static org.apache.ant.s3.ProjectUtils.require;
import static org.apache.ant.s3.ProjectUtils.requireComponent;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.PatternSet;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.Union;
import org.apache.tools.ant.types.resources.selectors.ResourceSelector;
import org.apache.tools.ant.util.StringUtils;

import software.amazon.awssdk.services.s3.S3Client;

/**
 * {@link ResourceCollection} of {@link ObjectResource}s.
 */
public class ObjectResources extends S3DataType implements ResourceCollection {
    /**
     * Default delimiter.
     */
    public static final String DEFAULT_DELIMITER = "/";

    private static <T> Stream<T> sequentialStream(Iterator<T> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false);
    }

    private Supplier<S3Client> s3;
    private String bucket;
    private String delimiter = DEFAULT_DELIMITER;
    private Precision as = Precision.object;

    private final PatternSet patterns = new PatternSet();
    private final List<ResourceSelector> selectors = new ArrayList<>();

    private volatile boolean caseSensitive = true;

    private volatile Optional<ResourceCollection> resourceCache;
    private volatile boolean cache = true;

    /**
     * Create a new {@link ObjectResources} instance.
     *
     * @param project
     */
    public ObjectResources(final Project project) {
        super(project);
        patterns.setProject(project);
        resetResourceCache();
    }

    /**
     * Add the nested {@link Client}.
     *
     * @param s3
     */
    public void addConfigured(final Client s3) {
        checkChildrenAllowed();

        Exceptions.raiseUnless(this.s3 == null, buildException(),
            () -> String.format("%s already specified", componentName(getProject(), Client.class)));

        this.s3 = Objects.requireNonNull(s3);
        resetResourceCache();
    }

    /**
     * Set {@link Client} by reference.
     *
     * @param refid
     */
    public void setClientRefid(final String refid) {
        checkAttributesAllowed();

        Exceptions.raiseIf(StringUtils.trimToNull(refid) == null, buildException(),
            "@clientrefid must not be null/empty/blank");

        addConfigured(getProject().<Client> getReference(refid));
    }

    /**
     * Add a configured {@link ResourceSelector}.
     *
     * @param selector
     */
    public void addConfigured(ResourceSelector selector) {
        checkChildrenAllowed();

        if (selector == null) {
            return;
        }
        selectors.add(selector);
        resetResourceCache();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<Resource> iterator() {
        if (isReference()) {
            getCheckedRef(ObjectResources.class).iterator();
        }
        if (resourceCache.isPresent()) {
            return resourceCache.get().iterator();
        }
        final S3Finder finder = new S3Finder(getProject(), s3(), require(getBucket(), "@bucket"), as,
            require(getDelimiter(), "@delimiter"), patterns, isCaseSensitive());

        final Optional<Set<ObjectResource>> cacheSet;
        if (isCache()) {
            cacheSet = Optional.of(new LinkedHashSet<>());
        } else {
            cacheSet = Optional.empty();
        }
        final Iterator<Resource> result = new Iterator<Resource>() {
            Optional<ObjectResource> next = finder.get();

            @Override
            public Resource next() {
                try {
                    final ObjectResource e = next.orElseThrow(NoSuchElementException::new);
                    cacheSet.ifPresent(c -> c.add(e));
                    return e;
                } finally {
                    next = finder.get();
                    if (!next.isPresent()) {
                        cacheSet.ifPresent(c -> {
                            final Union u = new Union(getProject());
                            u.addAll(c);
                            resourceCache = Optional.of(u);
                        });
                    }
                }
            }

            @Override
            public boolean hasNext() {
                return next.isPresent();
            }
        };

        return selectors.isEmpty() ? result : sequentialStream(result).filter(this::isSelected).iterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        if (isReference()) {
            return getCheckedRef(ObjectResources.class).size();
        }
        if (resourceCache.isPresent()) {
            return resourceCache.get().size();
        }
        if (patterns.hasPatterns(getProject()) || !selectors.isEmpty()) {
            final AtomicInteger result = new AtomicInteger();
            iterator().forEachRemaining(junk -> result.incrementAndGet());
            return result.intValue();
        }

        return new Counter(s3(), require(getBucket(), "@bucket")).getAsInt();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFilesystemOnly() {
        return false;
    }

    /**
     * Get the bucket of this {@link ObjectResources}.
     *
     * @return {@link String}
     */
    public String getBucket() {
        return bucket;
    }

    /**
     * Set the bucket of this {@link ObjectResources}.
     *
     * @param bucket
     */
    public void setBucket(String bucket) {
        checkAttributesAllowed();
        bucket = StringUtils.trimToNull(bucket);

        if (!Objects.equals(bucket, this.bucket)) {
            this.bucket = bucket;
            resetResourceCache();
        }
    }

    /**
     * Get the precision, semantically expressed "as".
     * 
     * @return {@link Precision}
     */
    public Precision getAs() {
        return as;
    }

    /**
     * Set the precision, semantically expressed "as". This affects only the
     * name property and {@link Object#toString()} value of generated
     * {@link ObjectResource}s.
     * 
     * @param precision
     *            {@link Precision}
     */
    public void setAs(Precision precision) {
        this.as = Objects.requireNonNull(precision);
    }

    /**
     * Learn whether the include/exclude patterns are to be applied with case
     * sensitivity.
     *
     * @return {@code boolean}
     */
    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    /**
     * Set whether to apply include/exclude patterns with case sensitivity
     * (default {@code true}).
     *
     * @param caseSensitive
     *            {@code boolean}
     */
    public void setCaseSensitive(boolean caseSensitive) {
        checkAttributesAllowed();
        if (caseSensitive != this.caseSensitive) {
            this.caseSensitive = caseSensitive;
            resetResourceCache();
        }
    }

    /**
     * Get the delimiter of this {@link ObjectResources}.
     *
     * @return {@link String}
     */
    public String getDelimiter() {
        return delimiter;
    }

    /**
     * Set the delimiter of this {@link ObjectResources}.
     *
     * @param delimiter
     *            empty to use no delimiter
     */
    public void setDelimiter(String delimiter) {
        checkAttributesAllowed();
        delimiter = StringUtils.trimToNull(delimiter);
        if (!Objects.equals(delimiter, this.delimiter)) {
            this.delimiter = delimiter;
            resetResourceCache();
        }
    }

    /**
     * Learn whether the discovered {@link ObjectResource}s will be cached.
     *
     * @return {@code boolean}
     */
    public boolean isCache() {
        return cache;
    }

    /**
     * Set whether the discovered {@link ObjectResource}s should be cached,
     * default {@code true}.
     *
     * @param cache
     */
    public void setCache(boolean cache) {
        this.cache = cache;
        if (!cache) {
            resetResourceCache();
        }
    }

    /**
     * Add a nested {@link PatternSet}.
     *
     * @param patternSet
     */
    public void addConfigured(PatternSet patternSet) {
        checkChildrenAllowed();
        patterns.addConfiguredPatternset(patternSet);
        resetResourceCache();
    }

    /**
     * Add a nested include.
     *
     * @return {@link PatternSet.NameEntry}
     */
    public PatternSet.NameEntry createInclude() {
        checkChildrenAllowed();
        resetResourceCache();
        return patterns.createInclude();
    }

    /**
     * Add a nested includesfile.
     *
     * @return {@link PatternSet.PatternFileNameEntry}
     */
    public PatternSet.NameEntry createIncludesFile() {
        checkChildrenAllowed();
        resetResourceCache();
        return patterns.createIncludesFile();
    }

    /**
     * Add a nested exclude.
     *
     * @return {@link PatternSet.NameEntry}
     */
    public PatternSet.NameEntry createExclude() {
        checkChildrenAllowed();
        resetResourceCache();
        return patterns.createExclude();
    }

    /**
     * Add a nested excludesfile.
     *
     * @return {@link PatternSet.PatternFileNameEntry}
     */
    public PatternSet.NameEntry createExcludesFile() {
        checkChildrenAllowed();
        resetResourceCache();
        return patterns.createExcludesFile();
    }

    /**
     * Set includes patterns via attribute.
     *
     * @param includes
     */
    public void setIncludes(String includes) {
        checkAttributesAllowed();
        resetResourceCache();
        patterns.setIncludes(includes);
    }

    /**
     * Set includes file via attribute.
     *
     * @param includesFile
     */
    public void setIncludesFile(File includesFile) {
        checkAttributesAllowed();
        resetResourceCache();
        patterns.setIncludesfile(includesFile);
    }

    /**
     * Set excludes pattersn via attribute.
     *
     * @param excludes
     */
    public void setExcludes(String excludes) {
        checkAttributesAllowed();
        resetResourceCache();
        patterns.setExcludes(excludes);
    }

    /**
     * Set excludes file via attribute.
     *
     * @param excludesFile
     */
    public void setExcludesFile(File excludesFile) {
        checkAttributesAllowed();
        resetResourceCache();
        patterns.setExcludesfile(excludesFile);
    }

    private S3Client s3() {
        return requireComponent(getProject(), s3, Client.class).get();
    }

    private boolean isSelected(Resource resource) {
        return selectors.stream().anyMatch(sel -> sel.isSelected(resource));
    }

    private void resetResourceCache() {
        resourceCache = Optional.empty();
    }
}
