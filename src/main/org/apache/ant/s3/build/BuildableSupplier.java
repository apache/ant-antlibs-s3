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

import static software.amazon.awssdk.utils.FunctionalUtils.safeSupplier;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.function.Supplier;

import org.apache.ant.s3.Exceptions;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

import software.amazon.awssdk.utils.builder.Buildable;

/**
 * Buildable supplier.
 */
public class BuildableSupplier<B extends Buildable, T> implements Supplier<B> {

    @SuppressWarnings("rawtypes")
    static final TypeVariable<Class<BuildableSupplier>> BUILDER = BuildableSupplier.class.getTypeParameters()[0];

    @SuppressWarnings("rawtypes")
    static final TypeVariable<Class<BuildableSupplier>> BUILT = BuildableSupplier.class.getTypeParameters()[1];

    private static Type resolveAgainst(Class<?> t, TypeVariable<?> v) {
        return TypeUtils.getTypeArguments(t, (Class<?>) v.getGenericDeclaration()).get(v);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static BuildableSupplier of(Method m) {
        Exceptions.raiseUnless(Modifier.isStatic(m.getModifiers()), IllegalArgumentException::new,
            "%s is not a static method", m);

        Class<?> buildableType = m.getReturnType();

        Supplier<Object> impl = safeSupplier(() -> m.invoke(null));

        if (!Buildable.class.isAssignableFrom(buildableType)) {
            Exceptions.raiseUnless(buildableType.isInterface(), IllegalArgumentException::new,
                "%s returns type that is neither %s nor an interface", m, Buildable.class.getSimpleName());

            Exceptions.raiseUnless(Buildables.quacksLikeABuildable(buildableType), IllegalArgumentException::new,
                "%s returns type that is not duck-type %s", m, Buildable.class.getSimpleName());

            final Method buildMethod = MethodUtils.getAccessibleMethod(buildableType, Buildables.BUILD_NAME);

            final Class[] interfaces = new Class[] { buildableType, Buildable.class };
            final ClassLoader ccl = Thread.currentThread().getContextClassLoader();

            final Supplier wrapped = impl;
            impl = () -> {
                final Object target = wrapped.get();
                return Proxy.newProxyInstance(ccl, interfaces, (proxy, method, args) -> {
                    if (Buildables.BUILD_METHOD.test(method)) {
                        method = buildMethod;
                    }
                    return method.invoke(target, args);
                });
            };
            buildableType = Proxy.getProxyClass(ccl, interfaces);
        }
        final Class<?> builtType =
            MethodUtils.getMatchingAccessibleMethod(buildableType, Buildables.BUILD_NAME).getReturnType();

        return new BuildableSupplier(impl, buildableType, builtType);
    }

    final Supplier<B> impl;
    final Class<B> builderType;
    final Class<T> returnType;

    @SuppressWarnings("unchecked")
    protected BuildableSupplier(Supplier<B> impl) {
        this.impl = impl;

        this.builderType = (Class<B>) TypeUtils.getRawType(resolveAgainst(getClass(), Buildables.BUILDER), null)
            .asSubclass(Buildable.class);

        this.returnType = (Class<T>) TypeUtils.getRawType(resolveAgainst(getClass(), Buildables.BUILT), null);
    }

    BuildableSupplier(Supplier<B> impl, Class<B> builderType, Class<T> returnType) {
        this.impl = impl;
        this.builderType = builderType;
        this.returnType = returnType;
    }

    /**
     * Learn the {@link Buildable} type itself.
     * 
     * @return {@link Class} of {@code B}
     */
    public Class<B> getBuilderType() {
        return builderType;
    }

    /**
     * Learn the return type.
     * 
     * @return {@link Class}
     */
    public Class<T> getReturnType() {
        return returnType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public B get() {
        return impl.get();
    }
}