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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.ant.s3.strings.ClassNames.Direction;
import org.junit.Test;

/**
 * Unit test {@link ClassNames}.
 */
public class ClassNamesTest {
    @Test
    public void testEmpty() {
        assertThat(ClassNames.empty()).isEmpty();
    }

    @Test
    public void testExplicitString() {
        assertThat(ClassNames.of("foo"))

            .containsExactly("foo")

            .containsExactly("foo");
    }

    @Test
    public void testExplicitStrings() {
        assertThat(ClassNames.of("foo", "bar", "baz"))

            .containsExactly("foo", "bar", "baz")

            .containsExactly("foo", "bar", "baz");
    }

    @Test
    public void testComposite() {
        assertThat(

            ClassNames.of("foo", "bar").andThen(

                ClassNames.of("baz", "blah")

            )

        )

            .isInstanceOf(ClassNames.class)

            .containsExactly("foo", "bar", "baz", "blah")

            .containsExactly("foo", "bar", "baz", "blah");
    }

    @Test
    public void testChainedComposite() {
        assertThat(

            ClassNames.of("foo").andThen(

                ClassNames.of("bar").andThen(

                    ClassNames.of("baz").andThen(

                        ClassNames.of("blah")

                    )

                )

            )

        )

            .isInstanceOf(ClassNames.class)

            .containsExactly("foo", "bar", "baz", "blah")

            .containsExactly("foo", "bar", "baz", "blah");
    }

    @Test
    public void testSorted() {
        final ClassNames msv = ClassNames.of("foo", "bar", "baz");

        assertThat(msv.sorted())

            .isInstanceOf(ClassNames.class)

            .containsExactly("bar", "baz", "foo")

            .containsExactly("bar", "baz", "foo");

        assertThat(msv.sorted(Comparator.reverseOrder()))

            .isInstanceOf(ClassNames.class)

            .containsExactly("foo", "baz", "bar")

            .containsExactly("foo", "baz", "bar");
    }

    @Test
    public void testReverse() {
        assertThat(ClassNames.of("foo", "bar", "baz").reverse())

            .isInstanceOf(ClassNames.class)

            .containsExactly("baz", "bar", "foo")

            .containsExactly("baz", "bar", "foo");
    }

    @Test
    public void testDistinct() {
        assertThat(ClassNames.of("foo", "bar", "baz", "foo", "bar", "baz").distinct())

            .isInstanceOf(ClassNames.class)

            .containsExactly("foo", "bar", "baz");
    }

    @Test
    public void testExplicitClassname() {
        assertThat(ClassNames.of(String.class)).containsExactlyElementsOf(simpleNames(String.class));
    }

    @Test
    public void testExplicitClassnames() {
        assertThat(ClassNames.of(String.class, Object.class, List.class))
            .containsExactlyElementsOf(simpleNames(String.class, Object.class, List.class));
    }

    @Test
    public void testSegmentsFromLeft() {
        final ClassNames base = ClassNames.of("FooBarBaz");

        assertThat(base.segment(Direction.FROM_LEFT, 0)).isSameAs(base);

        assertThat(base.segment(Direction.FROM_LEFT, 1))

            .containsExactly("BarBaz")

            .containsExactly("BarBaz");

        assertThat(base.segment(Direction.FROM_LEFT, 2))

            .containsExactly("Baz")

            .containsExactly("Baz");

        assertThat(base.segment(Direction.FROM_LEFT, 3)).isEmpty();

        assertThat(base.segments(Direction.FROM_LEFT, 0, 1))

            .containsExactly("FooBarBaz", "BarBaz")

            .containsExactly("FooBarBaz", "BarBaz");

        assertThat(base.segments(Direction.FROM_LEFT, 1, 2))

            .containsExactly("BarBaz", "Baz")

            .containsExactly("BarBaz", "Baz");

        for (int max = 2; max < 6; max++) {
            assertThat(base.segments(Direction.FROM_LEFT, 0, max))

                .containsExactly("FooBarBaz", "BarBaz", "Baz")

                .containsExactly("FooBarBaz", "BarBaz", "Baz");
        }

        assertThat(base.segments(Direction.FROM_LEFT))

            .containsExactly("FooBarBaz", "BarBaz", "Baz")

            .containsExactly("FooBarBaz", "BarBaz", "Baz");
    }

    @Test
    public void testSegmentsFromRight() {
        final ClassNames base = ClassNames.of("FooBarBaz");

        assertThat(base.segment(Direction.FROM_RIGHT, 0)).isSameAs(base);

        assertThat(base.segment(Direction.FROM_RIGHT, 1))

            .containsExactly("FooBar")

            .containsExactly("FooBar");

        assertThat(base.segment(Direction.FROM_RIGHT, 2))

            .containsExactly("Foo")

            .containsExactly("Foo");

        assertThat(base.segment(Direction.FROM_RIGHT, 3)).isEmpty();

        assertThat(base.segments(Direction.FROM_RIGHT, 0, 1))

            .containsExactly("FooBarBaz", "FooBar")

            .containsExactly("FooBarBaz", "FooBar");

        assertThat(base.segments(Direction.FROM_RIGHT, 1, 2))

            .containsExactly("FooBar", "Foo")

            .containsExactly("FooBar", "Foo");

        for (int max = 2; max < 6; max++) {
            assertThat(base.segments(Direction.FROM_RIGHT, 0, max))

                .containsExactly("FooBarBaz", "FooBar", "Foo")

                .containsExactly("FooBarBaz", "FooBar", "Foo");
        }

        assertThat(base.segments(Direction.FROM_RIGHT))

            .containsExactly("FooBarBaz", "FooBar", "Foo")

            .containsExactly("FooBarBaz", "FooBar", "Foo");
    }

    @Test
    public void testSegmentsFromLeftWithMultipleRoots() {
        final ClassNames base = ClassNames.of("FooBarBaz", "DoReMi");

        assertThat(base.segment(Direction.FROM_LEFT, 0)).isSameAs(base);

        assertThat(base.segment(Direction.FROM_LEFT, 1))

            .containsExactly("BarBaz", "ReMi")

            .containsExactly("BarBaz", "ReMi");

        assertThat(base.segment(Direction.FROM_LEFT, 2))

            .containsExactly("Baz", "Mi")

            .containsExactly("Baz", "Mi");

        assertThat(base.segment(Direction.FROM_LEFT, 3)).isEmpty();

        assertThat(base.segments(Direction.FROM_LEFT, 0, 1))

            .containsExactly("FooBarBaz", "BarBaz", "DoReMi", "ReMi")

            .containsExactly("FooBarBaz", "BarBaz", "DoReMi", "ReMi");

        assertThat(base.segments(Direction.FROM_LEFT, 1, 2))

            .containsExactly("BarBaz", "Baz", "ReMi", "Mi")

            .containsExactly("BarBaz", "Baz", "ReMi", "Mi");

        for (int max = 2; max < 6; max++) {
            assertThat(base.segments(Direction.FROM_LEFT, 0, max))

                .containsExactly("FooBarBaz", "BarBaz", "Baz", "DoReMi", "ReMi", "Mi")

                .containsExactly("FooBarBaz", "BarBaz", "Baz", "DoReMi", "ReMi", "Mi");
        }

        assertThat(base.segments(Direction.FROM_LEFT))

            .containsExactly("FooBarBaz", "BarBaz", "Baz", "DoReMi", "ReMi", "Mi")

            .containsExactly("FooBarBaz", "BarBaz", "Baz", "DoReMi", "ReMi", "Mi");
    }

    @Test
    public void testSegmentsFromRightWithMultipleRoots() {
        final ClassNames base = ClassNames.of("FooBarBaz", "DoReMi");

        assertThat(base.segment(Direction.FROM_RIGHT, 0)).isSameAs(base);

        assertThat(base.segment(Direction.FROM_RIGHT, 1))

            .containsExactly("FooBar", "DoRe")

            .containsExactly("FooBar", "DoRe");

        assertThat(base.segment(Direction.FROM_RIGHT, 2))

            .containsExactly("Foo", "Do")

            .containsExactly("Foo", "Do");

        assertThat(base.segment(Direction.FROM_RIGHT, 3)).isEmpty();

        assertThat(base.segments(Direction.FROM_RIGHT, 0, 1))

            .containsExactly("FooBarBaz", "FooBar", "DoReMi", "DoRe")

            .containsExactly("FooBarBaz", "FooBar", "DoReMi", "DoRe");

        assertThat(base.segments(Direction.FROM_RIGHT, 1, 2))

            .containsExactly("FooBar", "Foo", "DoRe", "Do")

            .containsExactly("FooBar", "Foo", "DoRe", "Do");

        for (int max = 2; max < 6; max++) {
            assertThat(base.segments(Direction.FROM_RIGHT, 0, max))

                .containsExactly("FooBarBaz", "FooBar", "Foo", "DoReMi", "DoRe", "Do")

                .containsExactly("FooBarBaz", "FooBar", "Foo", "DoReMi", "DoRe", "Do");
        }

        assertThat(base.segments(Direction.FROM_RIGHT))

            .containsExactly("FooBarBaz", "FooBar", "Foo", "DoReMi", "DoRe", "Do")

            .containsExactly("FooBarBaz", "FooBar", "Foo", "DoReMi", "DoRe", "Do");
    }

    private Iterable<String> simpleNames(Class<?>... clazzes) {
        return Stream.of(clazzes).map(Class::getSimpleName).collect(Collectors.toList());
    }
}
