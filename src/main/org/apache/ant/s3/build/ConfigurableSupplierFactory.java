/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.ant.s3.build;

import java.util.Optional;

import org.apache.tools.ant.Project;

import software.amazon.awssdk.utils.builder.Buildable;

/**
 * Mixin style implementation of functionality to create
 * {@link ConfigurableSupplier}s, permitting creation of subordinate objects
 * bound to a single Ant {@link Project}.
 */
public interface ConfigurableSupplierFactory {
    /**
     * Primary functionality provided by this interface. Get a
     * {@link ConfigurableSupplier} for the specified {@link Class}.
     * 
     * @param <T> type param
     * @param type supplied
     * @return {@link Optional} {@link ConfigurableSupplier} of {@code T}
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    default <T> Optional<ConfigurableSupplier<T>> configurableSupplier(Class<T> type) {
        final Optional<ConfiguringSupplier<T>> configuringSupplier =
            ConfiguringSuppliers.forProject(getProject()).findConfiguringSupplier(type);

        if (configuringSupplier.isPresent()) {
            return (Optional) configuringSupplier;
        }
        final Optional<BuildableSupplier<?, T>> buildableSupplier = Buildables.findBuildableSupplier(type);
        if (buildableSupplier.isPresent()) {
            final Buildable buildable = buildableSupplier.get().get();
            final Builder builder = new Builder(buildable.getClass(), getProject());

            return Optional.of(new ConfigurableSupplier<T>() {

                @Override
                public T get() {
                    builder.accept(buildable);
                    return (T) buildable.build();
                }

                @Override
                public DynamicConfiguration getConfiguration() {
                    return builder;
                }
            });
        }
        return Optional.empty();
    }

    /**
     * Get the {@link Project} associated with this
     * {@link ConfigurableSupplierFactory}.
     * 
     * @return {@link Project}
     */
    Project getProject();
}
