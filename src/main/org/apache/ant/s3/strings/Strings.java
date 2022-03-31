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
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Fun with {@link String}s.
 */
@FunctionalInterface
public interface Strings extends Iterable<String> {

    /**
     * Get an empty {@link Strings}.
     * 
     * @return {@link Strings}
     */
    public static Strings empty() {
        return Collections::emptyIterator;
    }

    /**
     * Get {@link Strings} of the specified values.
     * 
     * @param value
     *            first
     * @param values
     *            additional
     * @return {@link Strings}
     */
    public static Strings of(String value, String... values) {
        return of(
            Stream.concat(Stream.of(value), Stream.of(values)).collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /**
     * Get {@link Strings} of the specified values.
     *
     * @param names
     *            should be repeatable {@link Iterable}
     * @return {@link Strings}
     */
    public static Strings of(Iterable<String> names) {
        return names instanceof Strings ? (Strings) names : names::iterator;
    }

    /**
     * Get a {@link Strings} combining {@code this} with {@code next}.
     * 
     * @param next
     *            subsequent {@link String}s
     * @return {@link Strings}
     */
    default Strings andThen(Iterable<String> next) {
        return () -> Stream.of(this, of(next)).flatMap(Strings::stream).iterator();
    }

    /**
     * Get a {@link Strings} sorting {@code this} by natural order.
     * 
     * @return {@link Strings}
     */
    default Strings sorted() {
        return sorted(Comparator.naturalOrder());
    }

    /**
     * Get a {@link Strings} sorting {@code this} by the specified
     * {@link Comparator}.
     * 
     * @param cmp
     *            {@link Comparator} to sort by
     * @return {@link Strings}
     */
    default Strings sorted(Comparator<? super String> cmp) {
        return () -> stream().sorted(cmp).iterator();
    }

    /**
     * Get a {@link Strings} reversing {@code this}.
     * 
     * @return {@link Strings}
     */
    default Strings reverse() {
        final Deque<String> contents = new LinkedList<>();
        this.forEach(contents::push);
        return of(contents);
    }

    /**
     * Get distinct {@link Strings} from {@code this}.
     * 
     * @return {@link Strings}
     */
    default Strings distinct() {
        return () -> stream().distinct().iterator();
    }

    /**
     * Get a {@link Stream} of our contents.
     * 
     * @return {@link Stream} of {@link String}
     */
    default Stream<String> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
