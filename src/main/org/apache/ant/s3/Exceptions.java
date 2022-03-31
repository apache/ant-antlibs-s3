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
package org.apache.ant.s3;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Utility class for the creation and throwing of {@link Exception}s.
 */
public class Exceptions {

    public static <E extends Exception> E create(final Function<? super String, ? extends E> fn, final String format,
        final Object... args) {
        return create(fn, () -> String.format(format, args));
    }

    public static <E extends Exception, C extends Throwable> E create(
        final BiFunction<? super String, ? super C, ? extends E> fn, final C cause, final String format,
        final Object... args) {
        return create(fn, cause, () -> String.format(format, args));
    }

    public static <E extends Exception> E create(final Function<? super String, ? extends E> fn,
        final Supplier<String> message) {
        return elideStackTrace(fn.apply(message.get()));
    }

    public static <E extends Exception, C extends Throwable> E create(
        final BiFunction<? super String, ? super C, ? extends E> fn, final C cause, final Supplier<String> message) {
        return elideStackTrace(fn.apply(message.get(), cause));
    }

    public static <E extends Exception, R> R raise(final Function<? super String, ? extends E> fn, final String format,
        final Object... args) throws E {
        throw create(fn, format, args);
    }

    public static <E extends Exception> void raiseIf(final boolean condition,
        final Function<? super String, ? extends E> fn, final String format, final Object... args) throws E {
        if (condition) {
            raise(fn, format, args);
        }
    }

    public static <E extends Exception> void raiseUnless(final boolean condition,
        final Function<? super String, ? extends E> fn, final String format, final Object... args) throws E {
        raiseIf(!condition, fn, format, args);
    }

    public static <E extends Exception, R> R raise(final Function<? super String, ? extends E> fn,
        final Supplier<String> message) throws E {
        throw create(fn, message);
    }

    public static <E extends Exception> void raiseIf(final boolean condition,
        final Function<? super String, ? extends E> fn, final Supplier<String> message) throws E {
        if (condition) {
            raise(fn, message);
        }
    }

    public static <E extends Exception> void raiseUnless(final boolean condition,
        final Function<? super String, ? extends E> fn, final Supplier<String> message) throws E {
        raiseIf(!condition, fn, message);
    }

    public static <E extends Exception, C extends Throwable, R> R raise(
        final BiFunction<? super String, ? super C, ? extends E> fn, final C cause, final String format,
        final Object... args) throws E {
        throw create(fn, cause, format, args);
    }

    public static <E extends Exception, C extends Throwable> void raiseIf(final boolean condition,
        final BiFunction<? super String, ? super C, ? extends E> fn, final C cause, final String format,
        final Object... args) throws E {
        if (condition) {
            raise(fn, cause, format, args);
        }
    }

    public static <E extends Exception, C extends Throwable> void raiseUnless(final boolean condition,
        final BiFunction<? super String, ? super C, ? extends E> fn, final C cause, final String format,
        final Object... args) throws E {
        raiseIf(!condition, fn, cause, format, args);
    }

    public static <E extends Exception, C extends Throwable, R> R raise(
        final BiFunction<? super String, ? super C, ? extends E> fn, final C cause, final Supplier<String> message)
        throws E {
        throw create(fn, cause, message);
    }

    public static <E extends Exception, C extends Throwable> void raiseIf(final boolean condition,
        final BiFunction<? super String, ? super C, ? extends E> fn, final C cause, final Supplier<String> message)
        throws E {
        if (condition) {
            raise(fn, cause, message);
        }
    }

    public static <E extends Exception, C extends Throwable> void raiseUnless(final boolean condition,
        final BiFunction<? super String, ? super C, ? extends E> fn, final C cause, final Supplier<String> message)
        throws E {
        raiseIf(!condition, fn, cause, message);
    }

    private static <T extends Throwable> T elideStackTrace(final T t) {
        final StackTraceElement[] stackTrace = t.fillInStackTrace().getStackTrace();
        t.setStackTrace(Stream.of(stackTrace).filter(e -> !Exceptions.class.getName().equals(e.getClassName()))
            .toArray(StackTraceElement[]::new));
        return t;
    }

    private Exceptions() {
    }
}
