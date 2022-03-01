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

import java.util.function.IntSupplier;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

/**
 * Quick(er) counter of S3 objects.
 */
class Counter implements IntSupplier {
    private final S3Client s3;
    private final String bucket;

    /**
     * Create a new {@link Counter} instance.
     *
     * @param s3
     * @param bucket
     */
    Counter(final S3Client s3, final String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized int getAsInt() {
        return s3.listObjectsV2Paginator(req -> req.bucket(bucket)).stream().mapToInt(ListObjectsV2Response::keyCount)
            .sum();
    }
}
