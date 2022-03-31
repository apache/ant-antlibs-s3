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
package org.apache.ant.s3.build.spi;

import static software.amazon.awssdk.utils.FunctionalUtils.safeFunction;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.ant.s3.build.ConfiguringSupplier;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tools.ant.Project;

/**
 * {@code abstract} SPI class for {@link ConfiguringSupplier}s provision.
 * Methods accepting a {@link Project} and returning a
 * {@link ConfiguringSupplier} are converted to {@link Function}s and mapped to
 * the supplied type.
 */
public abstract class ConfiguringSuppliersProvider
    extends IntrospectingProviderBase<Map.Entry<Class<?>, Function<Project, ConfiguringSupplier<?>>>>
    implements Supplier<Map<Class<?>, Function<Project, ConfiguringSupplier<?>>>> {

    private static final TypeVariable<?> SUPPLIED_TYPE = Supplier.class.getTypeParameters()[0];

    /**
     * Create a new {@link ConfiguringSuppliersProvider}.
     */
    public ConfiguringSuppliersProvider() {
        fieldFilter(filter -> f -> false);
        methodFilter(filter -> filter.and(args(Project.class)).and(returns(ConfiguringSupplier.class)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Class<?>, Function<Project, ConfiguringSupplier<?>>> get() {
        final Map<Class<?>, Function<Project, ConfiguringSupplier<?>>> result = new LinkedHashMap<>();

        introspect(e -> result.put(e.getKey(), e.getValue()));

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Optional<Map.Entry<Class<?>, Function<Project, ConfiguringSupplier<?>>>> map(Method m) {
        final Type supplied = TypeUtils.getTypeArguments(m.getGenericReturnType(), Supplier.class).get(SUPPLIED_TYPE);
        return Optional.of(Pair.of(TypeUtils.getRawType(supplied, null),
            safeFunction(p -> (ConfiguringSupplier<?>) m.invoke(this, p))));
    }
}
