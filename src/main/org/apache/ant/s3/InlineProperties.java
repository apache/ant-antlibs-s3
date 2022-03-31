/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ant.s3;

import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tools.ant.DynamicElementNS;
import org.apache.tools.ant.Project;

/**
 * <p>
 * Structure to allow inline specification of properties.
 * </p>
 * <p>
 * Example: {pre}&lt;foo&gt;foo-value&lt;/foo&gt;
 * &lt;bar&gt;bar-value&lt;/bar&gt; &lt;baz&gt;baz -nextline-value&lt;/baz&gt;
 * {/pre}
 * </p>
 */
public class InlineProperties extends S3DataType implements DynamicElementNS {
    /**
     * Create a new {@link InlineProperties} instance.
     * 
     * @param project Ant {@link Project}
     */
    public InlineProperties(Project project) {
        super(project);
    }

    /**
     * Represents a single inline property.
     */
    public final class InlineProperty {
        private final String name;

        private InlineProperty(String name) {
            this.name = name;
        }

        /**
         * Add text to this property.
         *
         * @param text
         *            to add
         */
        public void addText(String text) {
            final String value;
            if (properties.containsKey(name)) {
                value = Stream.of(properties.getProperty(name), text).filter(Objects::nonNull)
                    .collect(Collectors.joining());
            } else {
                value = text;
            }
            properties.setProperty(name, value);
        }
    }

    /**
     * {@link Properties} object maintained by the {@link InlineProperties}.
     */
    final Properties properties = new Properties();

    /**
     * Handle the specified nested element.
     *
     * @param uri
     *            String URI
     * @param localName
     *            local element name
     * @param qName
     *            qualified name
     * @return InlineProperty
     */
    @Override
    public InlineProperty createDynamicElement(String uri, String localName, String qName) {
        return new InlineProperty(localName);
    }

    /**
     * Get the managed {@link Properties} instance.
     * 
     * @return {@link Properties}
     */
    public Properties getProperties() {
        return properties;
    }
}