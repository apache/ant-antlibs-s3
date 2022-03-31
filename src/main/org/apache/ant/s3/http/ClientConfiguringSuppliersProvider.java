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
package org.apache.ant.s3.http;

import java.util.Map;

import org.apache.ant.s3.InlineProperties;
import org.apache.ant.s3.ProjectUtils;
import org.apache.ant.s3.build.ConfiguringSupplier;
import org.apache.ant.s3.build.StringConversions;
import org.apache.ant.s3.build.spi.ConfiguringSuppliersProvider;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectComponent;
import org.kohsuke.MetaInfServices;

import software.amazon.awssdk.core.internal.http.loader.DefaultSdkHttpClientBuilder;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;

/**
 * {@link ConfiguringSuppliersProvider} for {@link SdkHttpClient}.
 */
@MetaInfServices
public class ClientConfiguringSuppliersProvider extends ConfiguringSuppliersProvider {

    /**
     * {@link SdkHttpClient} {@link ConfiguringSupplier}.
     */
    public static class HttpClientSupplier extends ProjectComponent
        implements ConfiguringSupplier<SdkHttpClient>, ProjectUtils {
        private final InlineProperties attributes;

        private HttpClientSupplier(Project project) {
            setProject(project);
            attributes = new InlineProperties(project);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object createDynamicElement(String uri, String localName, String qName) throws BuildException {
            return attributes.createDynamicElement(uri, localName, qName);
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public SdkHttpClient get() {
            return new DefaultSdkHttpClientBuilder().buildWithDefaults(
                StringConversions.attributes(SdkHttpConfigurationOption.class, (Map) attributes.getProperties()));
        }
    }

    /**
     * Get an {@link HttpClientSupplier}
     * 
     * @param project
     *            Ant {@link Project}
     * @return {@link HttpClientSupplier}
     */
    public HttpClientSupplier httpClientSupplier(Project project) {
        return new HttpClientSupplier(project);
    }
}
