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
package org.apache.ant.s3.build;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.apache.ant.s3.Exceptions;
import org.apache.ant.s3.ProjectUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectComponent;

import software.amazon.awssdk.utils.builder.Buildable;

/**
 * {@link ConfiguringSupplier} by named Java type.
 */
public class MetaBuilderByType<T> extends ProjectComponent
    implements ConfiguringSupplier<T>, ConfigurableSupplierFactory, ProjectUtils {

    private final Class<T> api;
    private final ClassFinder classFinder;
    private final Class<? extends T> defaultImpl;
    private Map<String, String> attributeCache = new LinkedHashMap<>();
    private BiConsumer<String, String> attributeCmer = attributeCache::put;
    private Optional<ConfigurableSupplier<? extends T>> configurableSupplier = Optional.empty();

    /**
     * Create a new {@link MetaBuilderByType}.
     * 
     * @param project
     *            Ant {@link Project}
     * @param api
     *            supertype whose child to find
     * @param classFinder
     *            to use
     */
    public MetaBuilderByType(Project project, Class<T> api, ClassFinder classFinder) {
        this(project, api, classFinder, null);
    }

    /**
     * Create a new {@link MetaBuilderByType}.
     * 
     * @param project
     *            Ant {@link Project}
     * @param api
     *            supertype whose child to find
     * @param classFinder
     *            to use
     * @param defaultImpl
     *            of {@code api}
     */
    public MetaBuilderByType(Project project, Class<T> api, ClassFinder classFinder, Class<? extends T> defaultImpl) {
        this.api = api;
        this.classFinder = classFinder;
        this.defaultImpl = defaultImpl;
        setProject(project);
    }

    /**
     * Set the implementation type by name.
     * 
     * @param impl
     *            fragment
     * @see ClassFinder#find(String, Class)
     */
    public void setImpl(String impl) {
        useImpl(classFinder.find(impl, api));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T get() {
        return configurableSupplier().get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void setDynamicAttribute(String uri, String localName, String qName, String value)
        throws BuildException {
        attributeCmer.accept(localName, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object createDynamicElement(String uri, String localName, String qName) throws BuildException {
        return configurableSupplier().getConfiguration().createDynamicElement(uri, localName, qName);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void useImpl(Class<? extends T> type) {
        Exceptions.raiseIf(configurableSupplier.isPresent(), buildExceptionTriggered(), new IllegalStateException(),
            "Already set %s [for type %s]", ConfigurableSupplier.class.getSimpleName());

        configurableSupplier = (Optional) configurableSupplier(type);

        Exceptions.raiseUnless(configurableSupplier.isPresent(), buildExceptionTriggered(),
            new IllegalArgumentException(), "Could not find %s for %s", Buildable.class.getSimpleName(), type);

        synchronized (this) {
            attributeCmer = (k, v) -> configurableSupplier().getConfiguration().setDynamicAttribute(null, k, null, v);
            attributeCache.forEach(attributeCmer);
            attributeCache = null;
        }
    }

    private ConfigurableSupplier<? extends T> configurableSupplier() {
        if (!configurableSupplier.isPresent()) {
            Exceptions.raiseIf(defaultImpl == null, buildExceptionTriggered(), new IllegalStateException(),
                "subtype has not been set/found and no default impl was configured");

            useImpl(defaultImpl);
        }
        return configurableSupplier.get();
    }
}
