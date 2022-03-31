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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.ant.s3.Exceptions;
import org.apache.ant.s3.S3DataType;
import org.apache.ant.s3.build.ConfigurableSupplier.DynamicConfiguration;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.ClassUtils.Interfaces;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.DataType;
import org.apache.tools.ant.types.Reference;

/**
 * Support AWS SDK v2 fluent builder conventions.
 *
 * @param <T>
 *            type to configure/introspect
 */
public class Builder<T> extends S3DataType implements DynamicConfiguration, Consumer<T>, ConfigurableSupplierFactory {

    private abstract class Mutation implements Consumer<T> {
        final Method mutator;

        Mutation(Method mutator) {
            this.mutator = mutator;
        }

        abstract Object getArg();

        @Override
        public void accept(T t) {
            try {
                mutator.invoke(t, getArg());
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private class AttributeMutation extends Mutation {
        final Object arg;

        AttributeMutation(Method mutator, Object arg) {
            super(mutator);
            this.arg = arg;
        }

        @Override
        Object getArg() {
            return arg;
        }
    }

    private class ElementMutation extends Mutation {
        final ConfigurableSupplier<?> configurableSupplier;

        ElementMutation(Method mutator, ConfigurableSupplier<?> configurableSupplier) {
            super(mutator);
            this.configurableSupplier = configurableSupplier;
        }

        @Override
        Object getArg() {
            return Optional.of(getConfig()).filter(DataType.class::isInstance).map(DataType.class::cast)
                .filter(DataType::isReference).map(DataType::getRefid).map(Reference::getReferencedObject)
                .orElseGet(configurableSupplier);
        }

        DynamicConfiguration getConfig() {
            return configurableSupplier.getConfiguration();
        }
    }

    private static final TypeVariable<?> CONSUMER_ARG = Consumer.class.getTypeParameters()[0];

    private static final Set<BiPredicate<String, String>> NAME_COMPARISONS =
        Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(String::equals, String::equalsIgnoreCase)));

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

    private static <T> Builder<T>.ElementMutation elementMutation(Builder<T> b, String name) {
        return Stream.<Function<Method, Optional<Builder<T>
            .ElementMutation>>> of(b::cmer, b::configurableSupplier, b::fallback).map(fn -> b.searchMethods(name, fn))
            .filter(Optional::isPresent).findFirst()
            .orElseThrow(() -> Exceptions.create(b.buildException(), "Unknown element %s", name)).get();
    }

    protected final Class<T> target;
    private final Set<Mutation> mutations = new LinkedHashSet<>();

    /**
     * Create a new {@link Builder} instance.
     *
     * @param target
     *            type
     * @param project
     *            Ant {@link Project}
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
        validate();
        mutations.forEach(cmer -> cmer.accept(t));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDynamicAttribute(String uri, String localName, String qName, String value) throws BuildException {
        final AttributeMutation mutation = searchMethods(localName, m -> {
            try {
                final Object convertedValue = StringConversions.as(m.getGenericParameterTypes()[0], value);
                return Optional.of(new AttributeMutation(m, convertedValue));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }).orElseThrow(() -> Exceptions.create(buildException(), "Unknown attribute %s", localName));

        mutations.add(mutation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object createDynamicElement(String uri, String localName, String qName) throws BuildException {
        final ElementMutation mutation = elementMutation(this, localName);
        mutations.add(mutation);
        return mutation.getConfig();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Optional<ElementMutation> cmer(Method method) {
        return Optional.of(method).filter(m -> Consumer.class.equals(m.getParameterTypes()[0])).map(m -> {
            final Builder<?> builder = new Builder(TypeUtils.getRawType(
                TypeUtils.getTypeArguments(method.getGenericParameterTypes()[0], Consumer.class).get(CONSUMER_ARG),
                null), getProject());

            return new ElementMutation(method, new ConfigurableSupplier() {

                @Override
                public Object get() {
                    return builder;
                }

                @Override
                public DynamicConfiguration getConfiguration() {
                    return builder;
                }
            });
        });
    }

    private Optional<ElementMutation> configurableSupplier(Method method) {
        return configurableSupplier(method.getParameterTypes()[0]).map(cs -> new ElementMutation(method, cs));
    }

    private Optional<ElementMutation> fallback(Method m) {
        return Optional.of(new ElementMutation(m, new ConfigurableSupplier<Object>() {
            final Class<?> pt = m.getParameterTypes()[0];
            final Builder<?> builder = new Builder<>(pt, getProject());

            @Override
            public Object get() {
                throw Exceptions.create(buildExceptionTriggered(), new UnsupportedOperationException(),
                    "Don't know how to handle argument of type %s; consider @refid for this argument", pt.getName());
            }

            @Override
            public DynamicConfiguration getConfiguration() {
                return builder;
            }
        }));
    }

    private <R> Optional<R> searchMethods(String name, Function<Method, Optional<R>> fn) {
        for (BiPredicate<String, String> nameComparison : NAME_COMPARISONS) {
            for (Class<?> type : ClassUtils.hierarchy(target, Interfaces.INCLUDE)) {
                for (Method m : type.getDeclaredMethods()) {
                    if (nameComparison.test(name, m.getName()) && isFluentSdkMutator(m)) {
                        final Optional<R> result = fn.apply(m);
                        if (result.isPresent()) {
                            return result;
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private void validate() {
        Exceptions.raiseIf(isReference() && !mutations.isEmpty(), buildException(),
            "Cannot specify @refid in conjunction with configured builder mutations");
    }
}
