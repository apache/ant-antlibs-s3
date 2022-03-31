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

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.ant.s3.Exceptions;
import org.apache.ant.s3.S3DataType;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

import software.amazon.awssdk.utils.Lazy;

/**
 * Root {@link ConfiguringSupplier}.
 *
 * @param <T>
 *            supplied type
 */
public class RootConfiguringSupplier<T> extends S3DataType
    implements ConfiguringSupplier<T>, ConfigurableSupplierFactory {

    private static final TypeVariable<?> SUPPLIED_TYPE = Supplier.class.getTypeParameters()[0];

    private final ConfigurableSupplier<T> configurableSupplier;
    private final Lazy<T> payload;

    /**
     * Create a new {@link RootConfiguringSupplier}.
     * 
     * @param project
     *            Ant {@link Project}
     * @param t
     *            type supplied
     */
    @SuppressWarnings("unchecked")
    public RootConfiguringSupplier(Project project, Class<T> t) {
        super(project);

        if (t == null) {
            final Type boundType = TypeUtils.getTypeArguments(getClass(), Supplier.class).get(SUPPLIED_TYPE);

            Exceptions.raiseIf(boundType == null, IllegalStateException::new, "%s does not bind %s", getClass(),
                SUPPLIED_TYPE);

            t = (Class<T>) TypeUtils.getRawType(boundType, null);
        }
        configurableSupplier = configurableSupplier(t).get();
        payload = new Lazy<>(configurableSupplier::get);
    }

    /**
     * Create a new {@link RootConfiguringSupplier} for a bound subtype.
     * 
     * @param project
     *            Ant {@link Project}
     */
    protected RootConfiguringSupplier(Project project) {
        this(project, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T get() {
        return payload.getValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDynamicAttribute(String uri, String localName, String qName, String value) throws BuildException {
        configurableSupplier.getConfiguration().setDynamicAttribute(uri, localName, qName, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object createDynamicElement(String uri, String localName, String qName) throws BuildException {
        return configurableSupplier.getConfiguration().createDynamicElement(uri, localName, qName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <TT> Optional<ConfigurableSupplier<TT>> configurableSupplier(Class<TT> type) {
        final Optional<ConfigurableSupplier<TT>> result = ConfigurableSupplierFactory.super.configurableSupplier(type);

        Exceptions.raiseUnless(result.isPresent(), buildExceptionTriggered(), new IllegalArgumentException(),
            "Could not find %s for %s", ConfigurableSupplier.class.getSimpleName(), type);

        return result;
    }
}
