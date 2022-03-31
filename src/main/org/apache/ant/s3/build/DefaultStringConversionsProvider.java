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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.function.Function;

import org.apache.ant.s3.build.spi.DefaultProvider;
import org.apache.ant.s3.build.spi.StringConversionsProvider;
import org.kohsuke.MetaInfServices;

/**
 * Default/baseline {@link StringConversionsProvider}.
 */
@DefaultProvider
@MetaInfServices
public class DefaultStringConversionsProvider extends StringConversionsProvider {
    /**
     * {@link Byte} from {@link String}.
     */
    public final Function<String, Byte> toByte = Byte::valueOf;

    /**
     * {@link Short} from {@link String}.
     */
    public final Function<String, Short> toShort = Short::valueOf;

    /**
     * {@link Integer} from {@link String}.
     */
    public final Function<String, Integer> toInt = Integer::valueOf;

    /**
     * {@link Character} from {@link String}.
     */
    public final Function<String, Character> toChar = s -> s.charAt(0);

    /**
     * {@link Long} from {@link String}.
     */
    public final Function<String, Long> toLong = Long::valueOf;

    /**
     * {@link Float} from {@link String}.
     */
    public final Function<String, Float> toFloat = Float::valueOf;

    /**
     * {@link Double} from {@link String}.
     */
    public final Function<String, Double> toDouble = Double::valueOf;

    /**
     * {@link Boolean} from {@link String}.
     */
    public final Function<String, Boolean> toBoolean = Boolean::valueOf;

    /**
     * Identity.
     */
    public final Function<String, String> toString = Function.identity();

    /**
     * {@link Duration} from {@link String}.
     */
    public final Function<String, Duration> toDuration = Duration::parse;

    /**
     * {@link URI} from {@link String}.
     */
    public final Function<String, URI> toUri = URI::create;

    /**
     * {@link Path} from {@link String}.
     */
    public final Function<String, Path> toPath = Paths::get;

    /**
     * {@link BigDecimal} from {@link String}.
     */
    public final Function<String, BigDecimal> toBigDecimal = BigDecimal::new;

    /**
     * {@link BigInteger} from {@link String}.
     */
    public final Function<String, BigInteger> toBigInteger = BigInteger::new;

    /**
     * {@code byte[]} from {@link String}.
     */
    public final Function<String, byte[]> toByteArray = String::getBytes;

    /**
     * {@code char[]} from {@link String}.
     */
    public final Function<String, char[]> toCharArray = String::toCharArray;
}
