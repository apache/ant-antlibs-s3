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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Modeled method signature.
 */
public final class MethodSignature implements Predicate<Method> {

    private static final Set<MethodSignature> INTERNED = new HashSet<>();

    /**
     * Factory method.
     * 
     * @param m
     *            {@link Method}
     * @return {@link MethodSignature}
     */
    public static MethodSignature of(Method m) {
        final MethodSignature result = new MethodSignature(m.getName(), m.getParameterTypes());

        final Optional<MethodSignature> interned = INTERNED.stream().filter(Predicate.isEqual(result)).findFirst();

        if (interned.isPresent()) {
            return interned.get();
        }
        INTERNED.add(result);
        return result;
    }

    private final String name;
    private final List<Class<?>> parameterTypes;

    private MethodSignature(String name, Class<?>[] parameterTypes) {
        this.name = name;
        this.parameterTypes = Stream.of(parameterTypes)
            .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    /**
     * Get the {@code name}.
     * 
     * @return {@link String}
     */
    public String getName() {
        return name;
    }

    /**
     * Get the parameter types.
     * 
     * @return {@link List} of {@link Class}
     */
    public List<Class<?>> getParameterTypes() {
        return parameterTypes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, parameterTypes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MethodSignature)) {
            return false;
        }
        final MethodSignature other = (MethodSignature) obj;
        return Objects.equals(name, other.name) && Objects.equals(parameterTypes, other.parameterTypes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return parameterTypes.stream().map(Class::getName).collect(Collectors.joining(", ", name + "(", ")"));
    }

    /**
     * {@inheritDoc}
     * 
     * @param t
     *            {@link Method}
     * @return {@code boolean}
     */
    @Override
    public boolean test(Method t) {
        return name.equals(t.getName()) && Arrays.asList(t.getParameterTypes()).equals(parameterTypes);
    }
}