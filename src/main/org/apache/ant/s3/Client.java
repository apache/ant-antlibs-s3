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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.DataType;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * {@link DataType} providing access to an {@link S3Client} instance.
 */
public class Client extends S3DataType implements Supplier<S3Client> {

    private Builder<S3ClientBuilder> builder;
    private Credentials credentials;
    private HttpConfiguration httpConfiguration;

    /**
     * Create a new {@link Client}.
     *
     * @param project
     */
    public Client(Project project) {
        super(project);
    }

    /**
     * Create a nested {@code builder} element to allow customization.
     *
     * @return {@link AmazonS3ClientBuilder}
     */
    public Builder<S3ClientBuilder> createBuilder() {
        checkChildrenAllowed();

        if (builder != null) {
            singleElementAllowed("builder");
        }
        return builder = new Builder<>(S3ClientBuilder.class, getProject());
    }

    /**
     * Create a nested {@link Credentials} element.
     *
     * @return {@link Credentials}
     */
    public Credentials createCredentials() {
        checkChildrenAllowed();

        if (credentials != null) {
            singleElementAllowed("credentials");
        }
        return credentials = new Credentials(getProject());
    }

    /**
     * Create a nested {@link HttpConfiguration} element.
     *
     * @return {@link HttpConfiguration}
     */
    public HttpConfiguration createHttp() {
        checkChildrenAllowed();

        if (httpConfiguration != null) {
            singleElementAllowed("http");
        }
        return httpConfiguration = new HttpConfiguration(getProject());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public S3Client get() {
        if (isReference()) {
            return getRefid().<Client> getReferencedObject().get();
        }
        final S3ClientBuilder scb = S3Client.builder();
        Optional.ofNullable(builder).ifPresent(bb -> bb.accept(scb));

        Stream.of(credentials, httpConfiguration).filter(Objects::nonNull).forEach(c -> c.accept(scb));

        return scb.build();
    }

    private void singleElementAllowed(final String name) {
        throw new BuildException(String.format("%s permits a single nested %s element", getDataTypeName(), name),
            getLocation());
    }
}
