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
package org.apache.ant.s3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.utils.AttributeMap;

/**
 * Static utility class for conversions from {@link String} to needed types.
 */
class StringConversions {
    private static final TypeVariable<?> ATTRIBUTE_KEY_TYPE = AttributeMap.Key.class.getTypeParameters()[0];

    private static final Map<Class<?>, Function<String, ?>> CONVERTERS;

    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER =
        Stream
            .of(Byte.class, Short.class, Integer.class, Character.class, Long.class, Float.class, Double.class,
                Boolean.class)
            .collect(Collectors.collectingAndThen(Collectors.<Class<?>, Class<?>, Class<?>> toMap(t -> {
                try {
                    return (Class<?>) t.getDeclaredField("TYPE").get(null);
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    throw new IllegalStateException(e);
                }
            }, Function.identity()), Collections::unmodifiableMap));

    static {
        final Map<Class<?>, Function<String, ?>> cnv = new LinkedHashMap<>();
        cnv.put(Byte.class, Byte::valueOf);
        cnv.put(Short.class, Short::valueOf);
        cnv.put(Integer.class, Integer::valueOf);
        cnv.put(Character.class, s -> s.charAt(0));
        cnv.put(Long.class, Long::valueOf);
        cnv.put(Float.class, Float::valueOf);
        cnv.put(Double.class, Double::valueOf);
        cnv.put(Boolean.class, Boolean::valueOf);
        cnv.put(String.class, Function.identity());
        cnv.put(Region.class, Region::of);
        CONVERTERS = Collections.unmodifiableMap(cnv);
    }

    private static Field keyField(Class<? extends AttributeMap.Key<?>> keyType, String name) {
        try {
            final Field result = keyType.getDeclaredField(name.toUpperCase(Locale.US));
            Exceptions.raiseUnless(Modifier.isStatic(result.getModifiers()) && result.getType().equals(keyType),
                IllegalArgumentException::new,
                () -> String.format("Illegal %s key: %s", keyType.getSimpleName(), name));
            return result;
        } catch (NoSuchFieldException | SecurityException e) {
            throw new BuildException(e);
        }
    }

    /**
     * Convert {@code value} to {@code type}.
     *
     * @param type
     * @param value
     * @return T
     */
    static <T> T as(Class<?> type, String value) {
        if (type.isPrimitive()) {
            type = PRIMITIVE_TO_WRAPPER.get(type);
        }
        final Optional<Object> converted = Optional.of(type).map(CONVERTERS::get).map(fn -> fn.apply(value));

        if (converted.isPresent()) {
            @SuppressWarnings("unchecked")
            final T result = (T) converted.get();
            return result;
        }
        if (type.isEnum()) {
            try {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                final T result = (T) Enum.valueOf((Class) type, value);
                return result;
            } catch (IllegalArgumentException e) {
            }
            @SuppressWarnings({ "unchecked", "rawtypes" })
            final T result = (T) Enum.valueOf((Class) type, value.toUpperCase(Locale.US));
            return result;
        }
        // Ant conventions
        Constructor<T> ctor;
        try {
            @SuppressWarnings("unchecked")
            final Constructor<T> _ctor = (Constructor<T>) type.getDeclaredConstructor(Project.class, String.class);
            ctor = _ctor;
        } catch (NoSuchMethodException | SecurityException e) {
            try {
                @SuppressWarnings("unchecked")
                final Constructor<T> _ctor = (Constructor<T>) type.getDeclaredConstructor(String.class);
                ctor = _ctor;
            } catch (NoSuchMethodException | SecurityException e2) {
                ctor = null;
            }
        }
        if (ctor != null) {
            try {
                return ctor.newInstance(value);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException();
    }

    /**
     * Generate an AWS SDK {@link AttributeMap} from the specified
     * {@link String} {@link Map} for the specified key type.
     *
     * @param <K>
     * @param keyType
     * @param m
     * @return {@link AttributeMap}
     */
    static <K extends AttributeMap.Key<?>> AttributeMap attributes(Class<K> keyType, Map<String, String> m) {
        final AttributeMap.Builder b = AttributeMap.builder();

        m.forEach((k, v) -> {
            final Field keyField = keyField(keyType, k);
            final AttributeMap.Key<Object> key;
            try {
                @SuppressWarnings("unchecked")
                final AttributeMap.Key<Object> _key = (AttributeMap.Key<Object>) keyField.get(null);
                key = _key;
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new BuildException(e);
            }
            final Type valueType =
                TypeUtils.getTypeArguments(keyField.getGenericType(), AttributeMap.Key.class).get(ATTRIBUTE_KEY_TYPE);

            final Object value = as(TypeUtils.getRawType(valueType, null), v);

            b.<Object> put(key, value);
        });

        return b.build();
    }
}
