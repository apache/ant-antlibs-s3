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

/**
 * Describes the precision to which S3 object operations are desired.
 */
public enum Precision {
    /**
     * Treat objects as simple files.
     * Omit object version; in a versioned bucket this will leave a delete
     * marker as the current version of the object.
     */
    object,

    /**
     * Treat the object's version as part of its identity.
     * Delete the current object version, leaving behind previous version if
     * one exists.
     */
    version;
}