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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.ClassUtils.Interfaces;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DynamicAttributeNS;
import org.apache.tools.ant.DynamicElementNS;
import org.apache.tools.ant.Project;

import software.amazon.awssdk.core.internal.http.loader.DefaultSdkHttpClientBuilder;
import software.amazon.awssdk.http.SdkHttpClient;

/**
 * Support AWS SDK v2 fluent builder conventions.
 *
 * @param <T>
 */
public class Builder<T> extends S3DataType implements Consumer<T>, DynamicAttributeNS, DynamicElementNS {
    private static class Parameter {
        final Method mutator;
        final Object value;

        Parameter(Method mutator, Object value) {
            this.mutator = mutator;
            this.value = value;
        }
    }

    private static final Map<Class<?>, Supplier<?>> SUPPLIERS;
    private static final Set<BiPredicate<String, String>> NAME_COMPARISONS =
        Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(String::equals, String::equalsIgnoreCase)));

    static {
        final Map<Class<?>, Supplier<?>> suppliers = new LinkedHashMap<>();
        suppliers.put(SdkHttpClient.Builder.class, DefaultSdkHttpClientBuilder::new);
        SUPPLIERS = Collections.unmodifiableMap(suppliers);
    }

    private static boolean isEquivalentTo(Class<?> c, Type t) {
        if (ParameterizedType.class.isInstance(t)) {
            t = ((ParameterizedType) t).getRawType();
        }
        return c.equals(t);
    }

    private static boolean isFluent(Method m) {
        final Class<?> declaringClass = m.getDeclaringClass();

        final Type genericReturnType = m.getGenericReturnType();

        if (isEquivalentTo(declaringClass, genericReturnType)) {
            return true;
        }
        if (TypeVariable.class.isInstance(genericReturnType)) {
            final TypeVariable<?> var = (TypeVariable<?>) genericReturnType;
            if (var.getGenericDeclaration().equals(declaringClass)) {
                ;
            }
            for (final Type bound : var.getBounds()) {
                if (isEquivalentTo(declaringClass, bound)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isFluentSdkMutator(Method m) {
        return isFluent(m) && !Modifier.isStatic(m.getModifiers()) && m.getParameterTypes().length == 1;
    }

    private static Parameter parameter(Class<?> c, String name, String value) {
        return searchMethods(c, name, m -> {
            try {
                return Optional.of(new Parameter(m, StringConversions.as(m.getParameterTypes()[0], value)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });
    }

    private static Method element(Class<?> c, String name) {
        return searchMethods(c, name, m -> {
            return Optional.of(m).filter(o -> {
                final Class<?> pt = m.getParameterTypes()[0];
                return Consumer.class.equals(pt) || SUPPLIERS.containsKey(pt);
            });
        });
    }

    private static <R> R searchMethods(Class<?> c, String name, Function<Method, Optional<R>> fn) {
        for (BiPredicate<String, String> nameComparison : NAME_COMPARISONS) {

            for (Class<?> type : ClassUtils.hierarchy(c, Interfaces.INCLUDE)) {
                for (Method m : type.getDeclaredMethods()) {
                    if (nameComparison.test(name, m.getName()) && isFluentSdkMutator(m)) {
                        final Optional<R> result = fn.apply(m);
                        if (result.isPresent()) {
                            return result.get();
                        }
                    }
                }
            }
        }
        throw new IllegalArgumentException(name);
    }

    private final Class<T> target;
    private final Set<Parameter> parameters = new LinkedHashSet<>();
    private final Map<Method, Builder<?>> elements = new LinkedHashMap<>();

    /**
     * Create a new {@link Builder} instance.
     *
     * @param target
     *            type
     */
    public Builder(Class<T> target, Project project) {
        super(project);
        this.target = Objects.requireNonNull(target, "target");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void accept(T t) {
        parameters.forEach(p -> {
            try {
                p.mutator.invoke(t, p.value);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        });
        elements.forEach((m, b) -> {
            final Class<?> pt = m.getParameterTypes()[0];
            final Object arg;
            if (Consumer.class.equals(pt)) {
                arg = b;
            } else {
                arg = SUPPLIERS.get(pt).get();

                @SuppressWarnings("unchecked")
                final Consumer<Object> cmer = (Consumer<Object>) b;
                cmer.accept(arg);
            }
            try {
                m.invoke(t, arg);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDynamicAttribute(String uri, String localName, String qName, String value) throws BuildException {
        parameters.add(parameter(target, localName, value));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object createDynamicElement(String uri, String localName, String qName) throws BuildException {
        final Method m = element(target, localName);
        final Builder<?> result = new Builder<>(m.getParameterTypes()[0], getProject());
        elements.put(m, result);
        return result;
    }
}
