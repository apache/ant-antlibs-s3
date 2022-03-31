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

import java.lang.reflect.Modifier;
import java.lang.reflect.TypeVariable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.lang3.reflect.MethodUtils;

import software.amazon.awssdk.utils.builder.Buildable;

/**
 * Work with AWS SDK {@link Buildable}s.
 */
public class Buildables {
    private static final String BUILDABLE_SUPPLIER_NAME = "builder";

    static final String BUILD_NAME = "build";

    @SuppressWarnings("rawtypes")
    static final TypeVariable<Class<BuildableSupplier>> BUILDER = BuildableSupplier.class.getTypeParameters()[0];

    @SuppressWarnings("rawtypes")
    static final TypeVariable<Class<BuildableSupplier>> BUILT = BuildableSupplier.class.getTypeParameters()[1];

    static final MethodSignature BUILD_METHOD;

    private static final Map<Class<?>, Optional<BuildableSupplier<?, ?>>> BUILDABLE_SUPPLIERS =
        Collections.synchronizedMap(new HashMap<>());

    static {
        try {
            BUILD_METHOD = MethodSignature.of(Buildable.class.getDeclaredMethod(BUILD_NAME));
        } catch (NoSuchMethodException | SecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Find a {@link Supplier} of {@link Buildable} for the specified class.
     * 
     * @param <T>
     *            supplied type
     * @param c
     *            {@link Class} instance for {@code T}
     * @return {@link Optional} {@link Buildable} {@link Supplier}
     */
    public static <T> Optional<BuildableSupplier<?, T>> findBuildableSupplier(Class<T> c) {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        final Optional<BuildableSupplier<?, T>> result = (Optional) BUILDABLE_SUPPLIERS.computeIfAbsent(c, k -> {
            try {
                return Optional.of(c.getDeclaredMethod(BUILDABLE_SUPPLIER_NAME))
                    .filter(m -> quacksLikeABuildable(m.getReturnType()) && Modifier.isStatic(m.getModifiers()))
                    .map(BuildableSupplier::of);
            } catch (NoSuchMethodException | SecurityException e) {
                return Optional.empty();
            }
        });
        return result;
    }

    /**
     * Learn whether {@code type} implements {@link Buildable#build()} by "duck
     * typing."
     * 
     * @param type
     *            to test
     * @return {@code boolean}
     */
    static boolean quacksLikeABuildable(Class<?> type) {
        return Buildable.class.isAssignableFrom(type)
            || Optional.ofNullable(MethodUtils.getMatchingAccessibleMethod(type, BUILD_NAME))
                .filter(m -> !Modifier.isStatic(m.getModifiers()) && !Void.TYPE.equals(m.getReturnType())).isPresent();
    }
}
