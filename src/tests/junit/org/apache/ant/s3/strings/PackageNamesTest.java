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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Unit test {@link PackageNames}.
 */
public class PackageNamesTest {
    @Test
    public void testEmpty() {
        assertThat(PackageNames.empty()).isEmpty();
    }

    @Test
    public void testExplicitString() {
        assertThat(PackageNames.of("foo"))

            .containsExactly("foo")

            .containsExactly("foo");
    }

    @Test
    public void testExplicitStrings() {
        assertThat(PackageNames.of("foo", "bar", "baz"))

            .containsExactly("foo", "bar", "baz")

            .containsExactly("foo", "bar", "baz");
    }

    @Test
    public void testComposite() {
        assertThat(

            PackageNames.of("foo", "bar").andThen(

                PackageNames.of("baz", "blah")

            )

        )

            .isInstanceOf(PackageNames.class)

            .containsExactly("foo", "bar", "baz", "blah")

            .containsExactly("foo", "bar", "baz", "blah");
    }

    @Test
    public void testChainedComposite() {
        assertThat(

            PackageNames.of("foo").andThen(

                PackageNames.of("bar").andThen(

                    PackageNames.of("baz").andThen(

                        PackageNames.of("blah")

                    )

                )

            )

        )

            .isInstanceOf(PackageNames.class)

            .containsExactly("foo", "bar", "baz", "blah")

            .containsExactly("foo", "bar", "baz", "blah");
    }

    @Test
    public void testSorted() {
        final PackageNames msv = PackageNames.of("foo", "bar", "baz");

        assertThat(msv.sorted())

            .isInstanceOf(PackageNames.class)

            .containsExactly("bar", "baz", "foo")

            .containsExactly("bar", "baz", "foo");

        assertThat(msv.sorted(Comparator.reverseOrder()))

            .isInstanceOf(PackageNames.class)

            .containsExactly("foo", "baz", "bar")

            .containsExactly("foo", "baz", "bar");
    }

    @Test
    public void testReverse() {
        assertThat(PackageNames.of("foo", "bar", "baz").reverse())

            .isInstanceOf(PackageNames.class)

            .containsExactly("baz", "bar", "foo")

            .containsExactly("baz", "bar", "foo");
    }

    @Test
    public void testDistinct() {
        assertThat(PackageNames.of("foo", "bar", "baz", "foo", "bar", "baz").distinct())

            .isInstanceOf(PackageNames.class)

            .containsExactly("foo", "bar", "baz");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAncestorInvalidMinGen() {
        PackageNames.of("foo").ancestors(-1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAncestorInvalidMaxGen() {
        PackageNames.of("foo").ancestors(0, -1);
    }

    @Test
    public void testAncestors() {
        final PackageNames foo_bar_baz = PackageNames.of("foo.bar.baz");

        assertThat(foo_bar_baz.ancestor(0))

            .isSameAs(foo_bar_baz)

            .containsExactly("foo.bar.baz")

            .containsExactly("foo.bar.baz");

        assertThat(foo_bar_baz.ancestor(1))

            .containsExactly("foo.bar")

            .containsExactly("foo.bar");

        assertThat(foo_bar_baz.ancestor(2))

            .containsExactly("foo")

            .containsExactly("foo");

        assertThat(foo_bar_baz.ancestor(3)).isEmpty();

        assertThat(foo_bar_baz.ancestors(1, 2))

            .containsExactly("foo.bar", "foo")

            .containsExactly("foo.bar", "foo");

        for (int max = 2; max < 5; max++) {
            assertThat(foo_bar_baz.ancestors(0, max))

                .containsExactly("foo.bar.baz", "foo.bar", "foo")

                .containsExactly("foo.bar.baz", "foo.bar", "foo");
        }

        assertThat(foo_bar_baz.ancestors())

            .containsExactly("foo.bar.baz", "foo.bar", "foo")

            .containsExactly("foo.bar.baz", "foo.bar", "foo");
    }

    @Test
    public void testAncestorsMultipleRoots() {
        final PackageNames base = PackageNames.of("foo.bar.baz", "moe.larry.curly.shemp");

        assertThat(base.ancestor(1))

            .containsExactly("foo.bar", "moe.larry.curly")

            .containsExactly("foo.bar", "moe.larry.curly");

        assertThat(base.ancestor(2))

            .containsExactly("foo", "moe.larry")

            .containsExactly("foo", "moe.larry");

        assertThat(base.ancestor(3))

            .containsExactly("moe")

            .containsExactly("moe");

        assertThat(base.ancestor(4)).isEmpty();

        assertThat(base.ancestors(1, 2))

            .containsExactly("foo.bar", "foo", "moe.larry.curly", "moe.larry")

            .containsExactly("foo.bar", "foo", "moe.larry.curly", "moe.larry");

        assertThat(base.ancestors(1, 3))

            .containsExactly("foo.bar", "foo", "moe.larry.curly", "moe.larry", "moe")

            .containsExactly("foo.bar", "foo", "moe.larry.curly", "moe.larry", "moe");

        assertThat(base.ancestors(2, 3))

            .containsExactly("foo", "moe.larry", "moe")

            .containsExactly("foo", "moe.larry", "moe");

        for (int max = 3; max < 6; max++) {
            assertThat(base.ancestors(0, max))

                .containsExactly("foo.bar.baz", "foo.bar", "foo", "moe.larry.curly.shemp", "moe.larry.curly",
                    "moe.larry", "moe")

                .containsExactly("foo.bar.baz", "foo.bar", "foo", "moe.larry.curly.shemp", "moe.larry.curly",
                    "moe.larry", "moe");
        }

        assertThat(base.ancestors())

            .containsExactly("foo.bar.baz", "foo.bar", "foo", "moe.larry.curly.shemp", "moe.larry.curly", "moe.larry",
                "moe")

            .containsExactly("foo.bar.baz", "foo.bar", "foo", "moe.larry.curly.shemp", "moe.larry.curly", "moe.larry",
                "moe");
    }

    @Test
    public void testExplicitRootClass() {
        final Iterable<String> expected = packageNames(String.class);

        assertThat(PackageNames.of(String.class))

            .containsExactlyElementsOf(expected)

            .containsExactlyElementsOf(expected);
    }

    @Test
    public void testExplicitRootClasses() {
        final Iterable<String> expected = packageNames(String.class, List.class, Pattern.class);

        assertThat(PackageNames.of(String.class, List.class, Pattern.class))

            .containsExactlyElementsOf(expected)

            .containsExactlyElementsOf(expected);
    }

    private Iterable<String> packageNames(Class<?>... clazzes) {
        return Stream.of(clazzes).map(c -> c.getPackage().getName()).collect(Collectors.toList());
    }
}
