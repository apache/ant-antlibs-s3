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

import java.util.Map;
import java.util.function.Consumer;

import org.apache.tools.ant.Project;

import software.amazon.awssdk.core.internal.http.loader.DefaultSdkHttpClientBuilder;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.utils.AttributeMap;

/**
 * S3 client Http configuration.
 */
public class HttpConfiguration extends InlineProperties implements Consumer<S3ClientBuilder> {

    /**
     * Create a new {@link HttpConfiguration} element.
     *
     * @param project
     */
    public HttpConfiguration(Project project) {
        super(project);
    }

    /**
     * Apply this {@link HttpConfiguration} to the specified
     * {@link S3ClientBuilder}.
     *
     * @param b
     */
    @Override
    public void accept(S3ClientBuilder b) {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        final AttributeMap attributeMap =
            StringConversions.attributes(SdkHttpConfigurationOption.class, (Map) properties);

        b.httpClient(new DefaultSdkHttpClientBuilder().buildWithDefaults(attributeMap));
    }
}
