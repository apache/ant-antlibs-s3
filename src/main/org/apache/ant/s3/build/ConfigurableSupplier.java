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

import java.util.function.Supplier;

import org.apache.ant.s3.Exceptions;
import org.apache.ant.s3.ProjectUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DynamicAttributeNS;
import org.apache.tools.ant.DynamicElementNS;

/**
 * A configurable {@link Supplier}.
 *
 * @param <T>
 *            supplied type
 */
public interface ConfigurableSupplier<T> extends Supplier<T> {

    /**
     * Dynamic configuration superset interface.
     */
    interface DynamicConfiguration extends DynamicAttributeNS, DynamicElementNS, ProjectUtils {

        /**
         * {@inheritDoc}
         * 
         * Default implementation.
         * 
         * @throws BuildException
         *             always
         */
        @Override
        default void setDynamicAttribute(String uri, String localName, String qName, String value)
            throws BuildException {
            Exceptions.raise(buildExceptionTriggered(), new UnsupportedOperationException(), "@%s not supported",
                qName);
        }

        /**
         * {@inheritDoc}
         * 
         * Default implementation.
         * 
         * @throws BuildException
         *             always
         */
        @Override
        default Object createDynamicElement(String uri, String localName, String qName) throws BuildException {
            throw Exceptions.create(buildExceptionTriggered(), new UnsupportedOperationException(),
                "nested %s element not supported", qName);
        }
    }

    /**
     * Get the {@link DynamicConfiguration} for this
     * {@link ConfigurableSupplier}.
     * 
     * @return {@link DynamicConfiguration}
     */
    DynamicConfiguration getConfiguration();
}
