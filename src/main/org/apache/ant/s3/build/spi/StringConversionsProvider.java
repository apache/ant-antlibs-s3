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

import static software.amazon.awssdk.utils.FunctionalUtils.safeSupplier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.commons.lang3.tuple.Pair;

import software.amazon.awssdk.utils.FunctionalUtils.UnsafeSupplier;

/**
 * {@code abstract} SPI class for {@link String} conversions.
 */
public abstract class StringConversionsProvider
    extends IntrospectingProviderBase<Map.Entry<Class<?>, Function<String, ?>>>
    implements Supplier<Map<Class<?>, Function<String, ?>>> {

    private static final TypeVariable<?> FUNCTION_RESULT = Function.class.getTypeParameters()[1];

    /**
     * Create a new {@link StringConversionsProvider}.
     */
    protected StringConversionsProvider() {
        final Type t = TypeUtils.parameterize(Function.class, String.class, TypeUtils.wildcardType().build());

        fieldFilter(filter -> filter.and(type(t)));

        methodFilter(filter -> filter.and(args()).and(returns(t)));
    }

    /**
     * Default implementation reflectively locates all {@code public} no-arg
     * methods and {@code public final} fields returning {@link Function} of
     * {@link String} to some other type.
     * 
     * @return {@link Map}
     */
    @Override
    public final Map<Class<?>, Function<String, ?>> get() {
        final Map<Class<?>, Function<String, ?>> result = new LinkedHashMap<>();

        introspect(e -> result.put(e.getKey(), e.getValue()));

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Optional<Map.Entry<Class<?>, Function<String, ?>>> map(Field f) {
        return map(f.getGenericType(), () -> f.get(this));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Optional<Map.Entry<Class<?>, Function<String, ?>>> map(Method m) {
        return map(m.getGenericReturnType(), () -> m.invoke(this));
    }

    @SuppressWarnings("unchecked")
    private Optional<Map.Entry<Class<?>, Function<String, ?>>> map(Type t, UnsafeSupplier<?> fnSupplier) {
        return Optional
            .of(Pair.of(TypeUtils.getRawType(TypeUtils.getTypeArguments(t, Function.class).get(FUNCTION_RESULT), null),
                (Function<String, ?>) safeSupplier(fnSupplier).get()));
    }
}
