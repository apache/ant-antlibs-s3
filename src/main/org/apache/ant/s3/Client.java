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

import org.apache.ant.s3.build.RootConfiguringSupplier;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.DataType;

import software.amazon.awssdk.services.s3.S3Client;

/**
 * {@link DataType} providing access to an {@link S3Client} instance.
 */
public class Client extends RootConfiguringSupplier<S3Client> {

    /**
     * Create a new {@link Client}.
     *
     * @param project Ant {@link Project}
     */
    public Client(Project project) {
        super(project);
    }
}
