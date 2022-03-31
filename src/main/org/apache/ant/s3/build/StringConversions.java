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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.ant.s3.Exceptions;
import org.apache.ant.s3.build.spi.Providers;
import org.apache.ant.s3.build.spi.StringConversionsProvider;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.commons.lang3.reflect.Typed;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

import software.amazon.awssdk.utils.AttributeMap;

/**
 * Static utility class for conversions from {@link String} to needed types.
 */
public class StringConversions {
    private static final TypeVariable<?> ATTRIBUTE_KEY_TYPE = AttributeMap.Key.class.getTypeParameters()[0];

    private static final TypeVariable<?> COLLECTION_ELEMENT = Collection.class.getTypeParameters()[0];

    private static final Map<Class<?>, Function<String, ?>> CONVERTERS = new HashMap<>();

    static {
        Providers.stream(StringConversionsProvider.class).map(StringConversionsProvider::get)
            .forEach(StringConversions::register);
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
     * Convert {@code value} to the type represented by {@code type}.
     *
     * @param <T>
     *            target
     * @param type
     *            {@link Typed} of {@code T}
     * @param value
     *            source
     * @return {@code T}
     */
    public static <T> T as(Typed<T> type, String value) {
        return as(type.getType(), value);
    }

    /**
     * Convert {@code value} to {@code type}.
     *
     * @param <T>
     *            target
     * @param type
     *            target type instance
     * @param value
     *            source
     * @return {@code T}
     */
    @SuppressWarnings("unchecked")
    public static <T> T as(Type type, String value) {
        final Class<?> clazz = ClassUtils.primitiveToWrapper(TypeUtils.getRawType(type, null));

        final Optional<Object> converted = Optional.of(clazz).map(CONVERTERS::get).map(fn -> fn.apply(value));

        if (converted.isPresent()) {
            return (T) converted.get();
        }
        final Optional<Type> componentType = getComponentType(type);
        if (componentType.isPresent()) {
            final List<Object> list = Stream.of(StringUtils.split(value, ',')).map(String::trim)
                .map(v -> as(componentType.get(), v)).collect(Collectors.toList());

            return (T) toCollectionish(type, list);
        }
        if (clazz.isEnum()) {
            try {
                @SuppressWarnings("rawtypes")
                final T result = (T) Enum.valueOf((Class) clazz, value);
                return result;
            } catch (IllegalArgumentException e) {
            }
            @SuppressWarnings("rawtypes")
            final T result = (T) Enum.valueOf((Class) clazz, value.toUpperCase(Locale.US));
            return result;
        }
        // Ant conventions
        Constructor<T> ctor;
        try {
            final Constructor<T> _ctor = (Constructor<T>) clazz.getDeclaredConstructor(Project.class, String.class);
            ctor = _ctor;
        } catch (NoSuchMethodException | SecurityException e) {
            try {
                final Constructor<T> _ctor = (Constructor<T>) clazz.getDeclaredConstructor(String.class);
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
     *            key type
     * @param keyType
     *            as {@link Class}
     * @param m
     *            source
     * @return {@link AttributeMap}
     */
    public static <K extends AttributeMap.Key<?>> AttributeMap attributes(Class<K> keyType, Map<String, String> m) {
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

            final Object value = as(valueType, v);

            b.<Object> put(key, value);
        });

        return b.build();
    }

    /**
     * Register the converters in the specified {@link Map}.
     * 
     * @param converters
     *            to register
     * @return whether any existing converter was displaced by this process
     */
    static boolean register(Map<Class<?>, Function<String, ?>> converters) {
        return converters.entrySet().stream().map(e -> CONVERTERS.put(e.getKey(), e.getValue()))
            .anyMatch(Objects::nonNull);
    }

    private static Optional<Type> getComponentType(Type t) {
        final Class<?> clazz = TypeUtils.getRawType(t, null);
        if (clazz.isArray()) {
            return Optional.of(clazz.getComponentType());
        }
        if (Collection.class.isAssignableFrom(clazz)) {
            return Optional.ofNullable(TypeUtils.getTypeArguments(t, Collection.class))
                .map(m -> m.get(COLLECTION_ELEMENT));
        }
        return Optional.empty();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <T> T toCollectionish(Type t, List<Object> l) {
        final Class<?> c = TypeUtils.getRawType(t, null);
        if (Arrays.asList(Collection.class, List.class).contains(c)) {
            return (T) l;
        }
        if (Set.class.equals(c)) {
            if (Optional.of(COLLECTION_ELEMENT).map(TypeUtils.getTypeArguments(t, Collection.class)::get)
                .filter(Class.class::isInstance).map(Class.class::cast).filter(Class::isEnum).isPresent()) {
                return (T) EnumSet.copyOf((List) l);
            }
            return (T) new LinkedHashSet<>(l);
        }
        if (Collection.class.isAssignableFrom(c)) {
            try {
                final Constructor<?> ctor = c.getDeclaredConstructor(Collection.class);
                return (T) ctor.newInstance(l);
            } catch (Exception e) {
            }
        }
        if (c.isArray()) {
            Class<?> componentType = c.getComponentType();
            final boolean primitive = (componentType.isPrimitive());
            if (primitive) {
                componentType = ClassUtils.primitiveToWrapper(componentType);
            }
            final Object result = l.toArray((Object[]) Array.newInstance(componentType, l.size()));
            return (T) (primitive ? ArrayUtils.toPrimitive(result) : result);
        }
        throw new IllegalArgumentException();
    }
}
