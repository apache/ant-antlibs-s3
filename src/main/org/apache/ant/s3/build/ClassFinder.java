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

import static software.amazon.awssdk.utils.FunctionalUtils.safeFunction;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.ant.s3.Exceptions;
import org.apache.ant.s3.strings.ClassNames;
import org.apache.ant.s3.strings.PackageNames;
import org.apache.ant.s3.strings.Strings;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Entity to find Java classes by naming convention. A "fragment" is "plugged
 * into" a matrix of packagename prefixes and classname suffixes to yield FQ
 * classnames. The fragment can be prefixed by a full or partial package name,
 * and is also interpretable as a FQ classname itself.
 */
public class ClassFinder {

    /**
     * {@link Pattern} to identify the point at which the final dot in a
     * {@link String} is followed by a lowercase character.
     */
    private static final Pattern PACKAGE_TO_CLASS_TRANSITION = Pattern.compile("((?:^|\\.)[a-z])(?=[^\\.]*$)");

    private static String toClassConvention(String fragment) {
        final String result;
        final Matcher m = PACKAGE_TO_CLASS_TRANSITION.matcher(fragment);
        if (m.find()) {
            final StringBuffer b = new StringBuffer();
            m.appendReplacement(b, m.group(1).toUpperCase());
            m.appendTail(b);
            result = b.toString();
        } else {
            result = fragment;
        }
        return StringUtils.stripStart(result, ".");
    }

    private static String toString(Strings strings) {
        return strings.stream().collect(Collectors.joining(", ", "[", "]"));
    }

    private final PackageNames packageNames;
    private final ClassNames suffixes;

    /**
     * Create a new {@link ClassFinder}.
     * 
     * @param packageNames
     *            relative to which to search
     * @param suffixes
     *            to append
     */
    public ClassFinder(Iterable<String> packageNames, Iterable<String> suffixes) {
        this.packageNames = PackageNames.of("").andThen(packageNames).distinct();
        this.suffixes = ClassNames.of("").andThen(suffixes).distinct();
    }

    /**
     * Find a {@link Class} instance plugging the specified shorthand
     * {@link String} into the specified matrix of package names and classname
     * suffixes.
     * 
     * @param s
     *            fragment whose first alpha character after any final dot
     *            ({@code .}) will be converted to uppercase
     * @return {@link Class}
     */
    public Class<?> find(String s) {
        return find(s, null);
    }

    /**
     * Find a {@link Class} instance plugging the specified shorthand
     * {@link String} into the specified matrix of package names and classname
     * suffixes.
     * 
     * @param <T>
     *            supertype
     * @param s
     *            fragment whose first alpha character after any final dot
     *            ({@code .}) will be converted to uppercase
     * @param requiredSupertype
     *            may be {@code null}
     * @return {@link Class} extending {@code T}
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> Class<? extends T> find(String s, Class<T> requiredSupertype) {
        final String fragment = toClassConvention(s);

        final Predicate<Class<?>> filter;
        final ClassLoader loader;
        if (requiredSupertype == null) {
            filter = c -> true;
            loader = Thread.currentThread().getContextClassLoader();
        } else {
            filter = requiredSupertype::isAssignableFrom;
            loader = requiredSupertype.getClassLoader();
        }
        return (Class) packageNames.stream().map(p -> p.isEmpty() ? fragment : p + '.' + fragment)
            .flatMap(pf -> suffixes.stream().map(sf -> pf + sf)).map(name -> probeFor(loader, name))
            .filter(Objects::nonNull).filter(filter).findFirst()
            .orElseThrow(() -> Exceptions.create(IllegalArgumentException::new,
                () -> String.format("Cannot find class for '%s' among packages %s X suffixes %s", s,
                    toString(packageNames), toString(suffixes))));
    }

    private Class<?> probeFor(ClassLoader loader, String className) {
        try {
            return ClassUtils.getClass(loader, className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}