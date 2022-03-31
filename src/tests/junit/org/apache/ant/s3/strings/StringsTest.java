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

import org.junit.Test;

/**
 * Unit test {@link Strings}.
 */
public class StringsTest {
    @Test
    public void testEmpty() {
        assertThat(Strings.empty()).isEmpty();
    }

    @Test
    public void testExplicitString() {
        assertThat(Strings.of("foo"))

            .containsExactly("foo")

            .containsExactly("foo");
    }

    @Test
    public void testExplicitStrings() {
        assertThat(Strings.of("foo", "bar", "baz"))

            .containsExactly("foo", "bar", "baz")

            .containsExactly("foo", "bar", "baz");
    }

    @Test
    public void testComposite() {
        assertThat(Strings.of("foo", "bar").andThen(Strings.of("baz", "blah")))

            .containsExactly("foo", "bar", "baz", "blah")

            .containsExactly("foo", "bar", "baz", "blah");
    }

    @Test
    public void testChainedComposite() {
        assertThat(

            Strings.of("foo").andThen(

                Strings.of("bar").andThen(

                    Strings.of("baz").andThen(

                        Strings.of("blah")

                    )

                )

            )

        )

            .containsExactly("foo", "bar", "baz", "blah")

            .containsExactly("foo", "bar", "baz", "blah");
    }

    @Test
    public void testSorted() {
        final Strings msv = Strings.of("foo", "bar", "baz");

        assertThat(msv.sorted())

            .containsExactly("bar", "baz", "foo")

            .containsExactly("bar", "baz", "foo");

        assertThat(msv.sorted(Comparator.reverseOrder()))

            .containsExactly("foo", "baz", "bar")

            .containsExactly("foo", "baz", "bar");
    }

    @Test
    public void testReverse() {
        assertThat(Strings.of("foo", "bar", "baz").reverse()).containsExactly("baz", "bar", "foo");
    }

    @Test
    public void testDistinct() {
        assertThat(Strings.of("foo", "bar", "baz", "foo", "bar", "baz").distinct())

            .containsExactly("foo", "bar", "baz");
    }
}
