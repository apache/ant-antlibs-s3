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

import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PUBLIC;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.ant.s3.build.MethodSignature;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

/**
 * Base class for introspecting SPI providers.
 */
public abstract class IntrospectingProviderBase<E> {

    private Predicate<Field> fieldFilter = mods(PUBLIC | FINAL);
    private Predicate<Method> methodFilter = uniqueSignatures().and(mods(PUBLIC));

    /**
     * Override or augment the field filter {@link Predicate}.
     * 
     * @param mod
     *            accepts existing filter
     */
    protected void fieldFilter(UnaryOperator<Predicate<Field>> mod) {
        this.fieldFilter = mod.apply(fieldFilter);
    }

    /**
     * Override or augment the method filter {@link Predicate}.
     * 
     * @param mod
     *            accepts existing filter
     */
    protected void methodFilter(UnaryOperator<Predicate<Method>> mod) {
        this.methodFilter = mod.apply(methodFilter);
    }

    /**
     * Introspect this object's fields and methods, mapping and sending to the
     * specified {@link Consumer}.
     * 
     * @param cmer
     *            accepts mapped objects
     * @see #map(Field)
     * @see #map(Method)
     */
    protected void introspect(Consumer<E> cmer) {
        final Stream<Optional<E>> fromFields =
            Stream.of(FieldUtils.getAllFields(getClass())).filter(fieldFilter).map(this::map);

        final Stream<Optional<E>> fromMethods =
            StreamSupport.stream(ClassUtils.hierarchy(getClass()).spliterator(), false).map(Class::getDeclaredMethods)
                .flatMap(Stream::of).filter(methodFilter).map(this::map);

        Stream.concat(fromFields, fromMethods).filter(Optional::isPresent).map(Optional::get).forEach(cmer);
    }

    /**
     * Map a {@link Field} that passed the {@link #fieldFilter} to {@code E}.
     * 
     * @param f
     *            {@link Field} to map
     * @return {@link Optional} of {@code E}
     */
    protected Optional<E> map(Field f) {
        return Optional.empty();
    }

    /**
     * Map a {@link Method} that passed the {@link #methodFilter} to {@code E}.
     * 
     * @param m
     *            {@link Method} to map
     * @return {@link Optional} of {@code E}
     */
    protected Optional<E> map(Method m) {
        return Optional.empty();
    }

    /**
     * Create a {@link Predicate} to select {@link Member}s by present Java
     * modifiers.
     * 
     * @param <M>
     *            {@link Member} type
     * @param mods
     *            mask
     * @return {@link Predicate} of {@code <M>}
     */
    protected <M extends Member> Predicate<M> mods(int mods) {
        return m -> (m.getModifiers() & mods) == mods;
    }

    /**
     * Create a {@link Predicate} to select {@link Member}s by name.
     * 
     * @param <M>
     *            {@link Member} type
     * @param test
     *            name {@link Predicate}
     * @return {@link Predicate} of {@code <M>}
     */
    protected <M extends Member> Predicate<M> named(Predicate<String> test) {
        return m -> test.test(m.getName());
    }

    /**
     * Create a {@link Predicate} to select {@link Field}s by declared type
     * assignability.
     * 
     * @param t
     *            compare to {@link Field#getGenericType()}
     * @return {@link Predicate} of {@link Field}
     */
    protected Predicate<Field> type(Type t) {
        return f -> TypeUtils.isAssignable(f.getGenericType(), t);
    }

    /**
     * Return a {@link Predicate} that discards a {@link Method} if its unique
     * signature has already been processed.
     * 
     * @return {@link Predicate} of {@link Method}
     */
    protected Predicate<Method> uniqueSignatures() {
        final Set<MethodSignature> signaturesEncountered = new LinkedHashSet<>();
        return m -> signaturesEncountered.add(MethodSignature.of(m));
    }

    /**
     * Return a {@link Predicate} to select {@link Method}s by argument type
     * assignability.
     * 
     * @param args
     *            compare to {@link Method#getParameterTypes()}
     * @return {@link Predicate} of {@link Method}
     */
    protected Predicate<Method> args(Class<?>... args) {
        return m -> ClassUtils.isAssignable(m.getParameterTypes(), args);
    }

    /**
     * Return a {@link Predicate} to select {@link Method}s by return type
     * assignability.
     * 
     * @param t
     *            compare to {@link Method#getGenericReturnType()}
     * @return {@link Predicate} of {@link Method}
     */
    protected Predicate<Method> returns(Type t) {
        return m -> TypeUtils.isAssignable(m.getGenericReturnType(), t);
    }
}
