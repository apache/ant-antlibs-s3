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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.ant.s3.build.spi.ConfiguringSuppliersProvider;
import org.apache.ant.s3.build.spi.Providers;
import org.apache.tools.ant.Project;

/**
 * {@link ConfiguringSupplier}s management.
 */
public class ConfiguringSuppliers {
    private static final Map<Class<?>, Optional<Function<Project, ConfiguringSupplier<?>>>> CONFIGURING_SUPPLIERS =
        Collections.synchronizedMap(new HashMap<>());

    static {
        Providers.stream(ConfiguringSuppliersProvider.class).map(ConfiguringSuppliersProvider::get)
            .forEach(ConfiguringSuppliers::registerConfiguringSuppliers);
    }

    /**
     * Get the {@link ConfiguringSuppliers} instance for the specified
     * {@link Project}.
     * 
     * @param project
     *            Ant {@link Project}
     * @return {@link ConfiguringSuppliers}
     */
    public static ConfiguringSuppliers forProject(Project project) {
        ConfiguringSuppliers result = project.getReference(ConfiguringSuppliers.class.getName());
        if (result == null) {
            result = new ConfiguringSuppliers(project);
            project.addReference(ConfiguringSuppliers.class.getName(), result);
        }
        return result;
    }

    /**
     * Register the {@link ConfiguringSupplier} {@link Supplier}s in the
     * specified {@link Map}.
     * 
     * @param configuringSuppliers
     *            to register
     * @return whether any existing {@link ConfiguringSupplier} {@link Supplier}
     *         was displaced by this process
     */
    static boolean registerConfiguringSuppliers(
        Map<Class<?>, Function<Project, ConfiguringSupplier<?>>> configuringSuppliers) {
        return configuringSuppliers.entrySet().stream()
            .map(e -> CONFIGURING_SUPPLIERS.put(e.getKey(), Optional.of(e.getValue()))).filter(Objects::nonNull)
            .anyMatch(Optional::isPresent);
    }

    private final Project project;

    private ConfiguringSuppliers(Project project) {
        this.project = Objects.requireNonNull(project);
    }

    /**
     * Find a {@link ConfiguringSupplier} for the specified class.
     * 
     * @param <T>
     *            supplied type
     * @param c
     *            type instance
     * @return {@link Optional} {@link ConfiguringSupplier}
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<ConfiguringSupplier<T>> findConfiguringSupplier(Class<T> c) {
        return CONFIGURING_SUPPLIERS.computeIfAbsent(c, k -> Optional.empty())
            .map(fn -> (ConfiguringSupplier<T>) fn.apply(project));
    }
}
