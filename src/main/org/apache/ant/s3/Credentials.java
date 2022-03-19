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

import java.util.function.Consumer;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * AWS credentials configuration. {@pcode profile} is preferred to
 * {@code accessKey}/{@code secretKey}.
 */
public class Credentials extends S3DataType implements Consumer<S3ClientBuilder> {
    private String accessKey;
    private String secretKey;
    private String profile;

    /**
     * Create a new {@link Credentials} instance.
     *
     * @param project
     */
    public Credentials(final Project project) {
        super(project);
    }

    /**
     * Get the access key.
     *
     * @return {@link String}
     */
    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Set the access key.
     *
     * @param accessKey
     */
    public void setAccessKey(final String accessKey) {
        this.accessKey = StringUtils.trimToNull(accessKey);
    }

    /**
     * Get the secret key.
     *
     * @return {@link String}
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Set the secret key.
     *
     * @param secretKey
     */
    public void setSecretKey(final String secretKey) {
        this.secretKey = StringUtils.trimToNull(secretKey);
    }

    /**
     * Get the desired profile.
     *
     * @return {@link String}
     */
    public String getProfile() {
        return profile;
    }

    /**
     * Set the desired profile.
     *
     * @param profile
     */
    public void setProfile(final String profile) {
        this.profile = StringUtils.trimToNull(profile);
    }

    /**
     * Apply settings to {@code builder}.
     *
     * @param builder
     */
    @Override
    public void accept(S3ClientBuilder builder) {
        final AwsCredentialsProvider credentialsProvider;

        if (getProfile() == null) {
            Exceptions.raiseIf(getAccessKey() == null || getSecretKey() == null, buildException(),
                "%s requires both @accessKey and @secretKey in the absence of @profile", getDataTypeName());

            credentialsProvider =
                StaticCredentialsProvider.create(AwsBasicCredentials.create(getAccessKey(), getSecretKey()));
        } else {
            credentialsProvider = ProfileCredentialsProvider.create(getProfile());
        }
        builder.credentialsProvider(credentialsProvider);
    }
}