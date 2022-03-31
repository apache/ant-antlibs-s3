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
package org.apache.ant.s3.strings;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.ant.s3.Exceptions;
import org.apache.commons.lang3.Range;

import software.amazon.awssdk.utils.Lazy;

/**
 * Package names.
 */
@FunctionalInterface
public interface PackageNames extends Strings {

    /**
     * Get an empty {@link PackageNames}.
     * 
     * @return {@link PackageNames}
     */
    public static PackageNames empty() {
        return Collections::emptyIterator;
    }

    /**
     * Get {@link PackageNames} of the specified root classes.
     * 
     * @param rootClass
     *            first
     * @param rootClasses
     *            additional
     * @return {@link PackageNames}
     */
    public static PackageNames of(Class<?> rootClass, Class<?>... rootClasses) {
        return of(Stream.concat(Stream.of(rootClass), Stream.of(rootClasses)).map(c -> c.getPackage().getName())
            .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /**
     * Get {@link PackageNames} of the specified values (not checked).
     * 
     * @param name
     *            first
     * @param names
     *            additional
     * @return {@link PackageNames}
     */
    public static PackageNames of(String name, String... names) {
        return of(Strings.of(name, names));
    }

    /**
     * Get {@link PackageNames} of the specified values (not checked).
     * 
     * @param names
     *            should be repeatable {@link Iterable}
     * @return {@link PackageNames}
     */
    public static PackageNames of(Iterable<String> names) {
        return names instanceof PackageNames ? (PackageNames) names : names::iterator;
    }

    /**
     * {@inheritDoc}
     * 
     * @return {@link PackageNames}
     */
    @Override
    default PackageNames andThen(Iterable<String> next) {
        return of(Strings.super.andThen(next));
    }

    /**
     * {@inheritDoc}
     * 
     * @return {@link PackageNames}
     */
    @Override
    default PackageNames sorted() {
        return of(Strings.super.sorted());
    }

    /**
     * {@inheritDoc}
     * 
     * @return {@link PackageNames}
     */
    @Override
    default PackageNames sorted(Comparator<? super String> cmp) {
        return of(Strings.super.sorted(cmp));
    }

    /**
     * {@inheritDoc}
     * 
     * @return {@link PackageNames}
     */
    @Override
    default PackageNames reverse() {
        return of(Strings.super.reverse());
    }

    /**
     * {@inheritDoc}
     * 
     * @return {@link PackageNames}
     */
    default PackageNames distinct() {
        return of(Strings.super.distinct());
    }

    /**
     * Get a {@link PackageNames} exposing the {@code Nth} ancestor package of
     * {@code this}, where {@code N} is {@code displacement}. Syntactic sugar
     * for {@link #ancestors(int, int)} with {@code displacement} as both
     * {@code min} and {@code max}.
     * 
     * @param displacement
     *            distance
     * @return {@link PackageNames}
     */
    default PackageNames ancestor(int displacement) {
        return ancestors(displacement, displacement);
    }

    /**
     * Get a {@link PackageNames} exposing all ancestor packages of
     * {@code this}.
     * 
     * @return {@link PackageNames}
     */
    default PackageNames ancestors() {
        final PackageNames wrapped = this;

        @SuppressWarnings("resource")
        final Lazy<Iterable<String>> lazy = new Lazy<>(() -> wrapped.stream().flatMap(s -> {
            final StringBuilder b = new StringBuilder(s);
            final Stream.Builder<String> result = Stream.builder();

            while (true) {
                result.accept(b.toString());
                final int lastDot = b.lastIndexOf(".");
                if (lastDot < 0) {
                    break;
                }
                b.setLength(lastDot);
            }
            return result.build();
        }).collect(Collectors.toList()));

        // use lambda to defer evaluation
        return of(() -> lazy.getValue().iterator());
    }

    /**
     * Get a {@link PackageNames} exposing ancestor packages of {@code this}.
     * 
     * @param min
     *            levels
     * @param max
     *            levels
     * @return {@link PackageNames}
     */
    default PackageNames ancestors(int min, int max) {
        Exceptions.raiseIf(min < 0 || max < 0, IllegalArgumentException::new, "Invalid arguments(%d, %d)", min, max);

        if (min == 0 && max == 0) {
            return this;
        }
        final PackageNames wrapped = this;
        final Range<Integer> generations = Range.between(min, max);

        @SuppressWarnings("resource")
        final Lazy<Iterable<String>> lazy = new Lazy<>(() -> wrapped.stream().flatMap(s -> {
            final StringBuilder b = new StringBuilder(s);
            final Stream.Builder<String> result = Stream.builder();

            for (int n = 0; !generations.isBefore(n); n++) {
                if (generations.contains(n)) {
                    result.accept(b.toString());
                }
                final int lastDot = b.lastIndexOf(".");
                if (lastDot < 0) {
                    break;
                }
                b.setLength(lastDot);
            }
            return result.build();
        }).collect(Collectors.toList()));

        // use lambda to defer evaluation
        return of(() -> lazy.getValue().iterator());
    }
}
