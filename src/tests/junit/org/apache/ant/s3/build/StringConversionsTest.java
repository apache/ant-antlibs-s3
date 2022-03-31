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
package org.apache.ant.s3.build;

import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static org.assertj.core.api.Assertions.assertThat;
import static software.amazon.awssdk.utils.FunctionalUtils.safeFunction;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.reflect.TypeLiteral;
import org.junit.Test;

import software.amazon.awssdk.awscore.defaultsmode.DefaultsMode;
import software.amazon.awssdk.regions.Region;

public class StringConversionsTest {
    enum MetaSyntacticVariable {
        FOO, BAR, BAZ;
    }

    @Test
    public void testWrapperConversions() {
        assertThat(StringConversions.<Byte> as(Byte.class, String.valueOf(Byte.MAX_VALUE)))
            .isEqualTo(Byte.valueOf(Byte.MAX_VALUE));

        assertThat(StringConversions.<Short> as(Short.class, String.valueOf(Short.MAX_VALUE)))
            .isEqualTo(Short.MAX_VALUE);

        assertThat(StringConversions.<Character> as(Character.class, String.valueOf(Character.MAX_VALUE)))
            .isEqualTo(Character.MAX_VALUE);

        assertThat(StringConversions.<Integer> as(Integer.class, String.valueOf(Integer.MAX_VALUE)))
            .isEqualTo(Integer.MAX_VALUE);

        assertThat(StringConversions.<Long> as(Long.class, String.valueOf(Long.MAX_VALUE))).isEqualTo(Long.MAX_VALUE);
        assertThat(StringConversions.<Float> as(Float.class, String.valueOf(Float.MAX_VALUE)))
            .isEqualTo(Float.MAX_VALUE);
        assertThat(StringConversions.<Double> as(Double.class, String.valueOf(Double.MAX_VALUE)))
            .isEqualTo(Double.MAX_VALUE);
        assertThat(StringConversions.<Boolean> as(Boolean.class, "true")).isTrue();
    }

    @Test
    public void testPrimitiveConversions() {
        assertThat(StringConversions.<Byte> as(Byte.TYPE, String.valueOf(Byte.MAX_VALUE)))
            .isEqualTo(Byte.valueOf(Byte.MAX_VALUE));

        assertThat(StringConversions.<Short> as(Short.TYPE, String.valueOf(Short.MAX_VALUE)))
            .isEqualTo(Short.MAX_VALUE);

        assertThat(StringConversions.<Character> as(Character.TYPE, String.valueOf(Character.MAX_VALUE)))
            .isEqualTo(Character.MAX_VALUE);

        assertThat(StringConversions.<Integer> as(Integer.TYPE, String.valueOf(Integer.MAX_VALUE)))
            .isEqualTo(Integer.MAX_VALUE);

        assertThat(StringConversions.<Long> as(Long.TYPE, String.valueOf(Long.MAX_VALUE))).isEqualTo(Long.MAX_VALUE);
        assertThat(StringConversions.<Float> as(Float.TYPE, String.valueOf(Float.MAX_VALUE)))
            .isEqualTo(Float.MAX_VALUE);
        assertThat(StringConversions.<Double> as(Double.TYPE, String.valueOf(Double.MAX_VALUE)))
            .isEqualTo(Double.MAX_VALUE);
        assertThat(StringConversions.<Boolean> as(Boolean.TYPE, "true")).isTrue();
    }

    @Test
    public void testOtherDefaultConversions() {
        assertThat(StringConversions.<String> as(String.class, "foo")).isEqualTo("foo");
        assertThat(StringConversions.<Duration> as(Duration.class, "PT66H")).isEqualTo(Duration.ofHours(66));
        assertThat(StringConversions.<URI> as(URI.class, "https://ant.apache.org")).hasScheme("https")
            .hasHost("ant.apache.org").hasNoPort().hasPath("").hasNoFragment().hasNoParameters();

        assertThat(StringConversions.<Path> as(Path.class, System.getProperty("user.dir")))
            .isEqualTo(new File(System.getProperty("user.dir")).toPath());

        assertThat(StringConversions.<BigDecimal> as(BigDecimal.class, "999.999")).isEqualTo("999.999");

        assertThat(StringConversions.<BigInteger> as(BigInteger.class, "999")).isEqualTo("999");
        assertThat(StringConversions.<byte[]> as(byte[].class, "foo")).containsExactly('f', 'o', 'o');
        assertThat(StringConversions.<char[]> as(char[].class, "foo")).containsExactly('f', 'o', 'o');
    }

    @Test
    public void testEnumConversions() {
        assertThat(StringConversions.<MetaSyntacticVariable> as(MetaSyntacticVariable.class, "foo"))
            .isSameAs(MetaSyntacticVariable.FOO);
        assertThat(StringConversions.<MetaSyntacticVariable> as(MetaSyntacticVariable.class, "BAR"))
            .isSameAs(MetaSyntacticVariable.BAR);
    }

    @Test
    public void testDefaultAwsConversions() {
        for (DefaultsMode dm : DefaultsMode.values()) {
            assertThat(StringConversions.<DefaultsMode> as(DefaultsMode.class, dm.toString())).isSameAs(dm);
        }

        final int psf = PUBLIC | STATIC | FINAL;

        Stream.of(Region.class.getDeclaredFields()).filter(f -> (f.getModifiers() & psf) == psf)
            .filter(f -> Region.class.equals(f.getType())).map(safeFunction(f -> f.get(null)))
            .map(Region.class::cast)
            .forEach(region -> assertThat(StringConversions.<Region> as(Region.class, region.id())).isSameAs(region));
    }

    @Test
    public void testCommaDelimitedArrayConversion() {
        assertThat(StringConversions.<MetaSyntacticVariable[]> as(MetaSyntacticVariable[].class, "foo,baz"))
            .containsExactly(MetaSyntacticVariable.FOO, MetaSyntacticVariable.BAZ);

        assertThat(StringConversions.<int[]> as(int[].class, "5,7,9")).containsExactly(5, 7, 9);
    }

    @Test
    public void testCommaDelimitedCollectionConversion() {
        assertThat(StringConversions.as(new TypeLiteral<Set<MetaSyntacticVariable>>() {}, "bar,foo"))
            .containsExactly(MetaSyntacticVariable.FOO, MetaSyntacticVariable.BAR).isInstanceOf(EnumSet.class);

        assertThat(StringConversions.as(new TypeLiteral<List<Integer>>() {}, "2,4,6,8,4")).containsExactly(2, 4, 6, 8,
            4);

        assertThat(StringConversions.as(new TypeLiteral<Collection<Integer>>() {}, "2,4,6,8,4")).containsExactly(2, 4,
            6, 8, 4);

        assertThat(StringConversions.as(new TypeLiteral<Set<Integer>>() {}, "2,4,6,8,4")).containsExactly(2, 4, 6, 8);

        assertThat(StringConversions.as(new TypeLiteral<ArrayDeque<String>>() {}, "moe,larry,curly"))
            .containsExactly("moe", "larry", "curly");
    }
}
