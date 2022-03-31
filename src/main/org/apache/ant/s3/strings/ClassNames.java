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

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.ant.s3.Exceptions;
import org.apache.commons.lang3.StringUtils;

import software.amazon.awssdk.utils.Lazy;

/**
 * (Simple) class names.
 */
@FunctionalInterface
public interface ClassNames extends Strings {

    /**
     * Segment direction.
     */
    public enum Direction {
        FROM_LEFT {
            @Override
            List<String> window(List<String> source, int removeSegments) {
                return source.subList(removeSegments, source.size());
            }
        },
        FROM_RIGHT {
            @Override
            List<String> window(List<String> source, int removeSegments) {
                return source.subList(0, source.size() - removeSegments);
            }
        };

        /**
         * Get a "window" (sublist) into the specified {@code source}
         * {@link List} removing the specified number of segments (elements)
         * from {@code this} direction.
         * 
         * @param source
         * @param removeSegments
         * @return {@link List} of {@link String}
         */
        abstract List<String> window(List<String> source, int removeSegments);
    }

    /**
     * Get an empty {@link ClassNames}.
     * 
     * @return {@link ClassNames}
     */
    public static ClassNames empty() {
        return Collections::emptyIterator;
    }

    /**
     * Get a {@link ClassNames} representing the simple names of the specified
     * classes.
     * 
     * @param clazz
     *            first
     * @param clazzes
     *            additional
     * @return {@link ClassNames}
     */
    public static ClassNames of(Class<?> clazz, Class<?>... clazzes) {
        return of(Stream.concat(Stream.of(clazz), Stream.of(clazzes)).map(Class::getSimpleName)
            .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /**
     * Get {@link ClassNames} of the specified values (not checked).
     * 
     * @param value
     *            first
     * @param values
     *            additional
     * @return {@link ClassNames}
     */
    public static ClassNames of(String value, String... values) {
        return of(Strings.of(value, values));
    }

    /**
     * Get {@link ClassNames} of the specified values.
     *
     * @param names
     *            should be repeatable {@link Iterable}
     * @return {@link ClassNames}
     */
    public static ClassNames of(Iterable<String> names) {
        return names instanceof ClassNames ? (ClassNames) names : names::iterator;
    }

    /**
     * {@inheritDoc}
     * 
     * @return {@link ClassNames}
     */
    default ClassNames andThen(Iterable<String> next) {
        return of(Strings.super.andThen(next));
    }

    /**
     * {@inheritDoc}
     * 
     * @return {@link ClassNames}
     */
    default ClassNames sorted() {
        return of(Strings.super.sorted());
    }

    /**
     * {@inheritDoc}
     * 
     * @return {@link ClassNames}
     */
    default ClassNames sorted(Comparator<? super String> cmp) {
        return of(Strings.super.sorted(cmp));
    }

    /**
     * {@inheritDoc}
     * 
     * @return {@link ClassNames}
     */
    @Override
    default ClassNames reverse() {
        return of(Strings.super.reverse());
    }

    /**
     * {@inheritDoc}
     * 
     * @return {@link ClassNames}
     */
    default ClassNames distinct() {
        return of(Strings.super.distinct());
    }

    /**
     * Get a {@link ClassNames} trimming {@code trimmed} segments from elements
     * of {@code this} in the specified {@code direction}.
     * 
     * @param direction
     *            from which to trim segments
     * @param trimmed
     *            number of segments to remove
     * @return {@link ClassNames}
     */
    default ClassNames segment(Direction direction, int trimmed) {
        return segments(direction, trimmed, trimmed);
    }

    /**
     * Get a {@link ClassNames} trimming segments from elements of {@code this}
     * in the specified {@code direction}.
     * 
     * @param direction
     *            from which to trim segments
     * @return {@link ClassNames}
     */
    default ClassNames segments(Direction direction) {
        final Function<? super String, ? extends Stream<? extends String>> expand = s -> {
            final List<String> segments = Arrays.asList(StringUtils.splitByCharacterTypeCamelCase(s));
            if (segments.isEmpty()) {
                return Stream.empty();
            }
            return IntStream.range(0, segments.size()).mapToObj(n -> direction.window(segments, n))
                .map(w -> StringUtils.join(w, null));
        };
        @SuppressWarnings("resource")
        final Lazy<Iterable<String>> lazy = new Lazy<>(() -> stream().flatMap(expand).collect(Collectors.toList()));

        // use lambda to defer evaluation
        return of(() -> lazy.getValue().iterator());
    }

    /**
     * Get a {@link ClassNames} exposing variants removing successively more
     * camel-case segments of the base content.
     * 
     * @param direction
     *            from which to trim segments
     * @param min
     *            number of segments to remove
     * @param max
     *            number of segments to remove
     * @return {@link ClassNames}
     */
    default ClassNames segments(Direction direction, int min, int max) {
        Exceptions.raiseIf(direction == null || min < 0 || max < 0, IllegalArgumentException::new,
            "Invalid arguments(%s, %d, %d)", direction, min, max);

        if (min == 0 && max == 0) {
            return this;
        }
        final Function<? super String, ? extends Stream<? extends String>> expand = s -> {
            final List<String> segments = Arrays.asList(StringUtils.splitByCharacterTypeCamelCase(s));
            if (min >= segments.size()) {
                return Stream.empty();
            }
            return IntStream.rangeClosed(min, Math.min(max, segments.size() - 1))
                .mapToObj(n -> direction.window(segments, n)).map(w -> StringUtils.join(w, null));
        };
        @SuppressWarnings("resource")
        final Lazy<Iterable<String>> lazy = new Lazy<>(() -> stream().flatMap(expand).collect(Collectors.toList()));

        // use lambda to defer evaluation
        return of(() -> lazy.getValue().iterator());
    }
}
