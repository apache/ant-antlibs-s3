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
package org.apache.ant.s3;

import java.util.Formatter;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.DataType;

/**
 * S3 {@link DataType}.
 */
public abstract class S3DataType extends DataType implements ProjectUtils {
    /**
     * Create a new {@link S3DataType} instance.
     *
     * @param project Ant {@link Project}
     */
    protected S3DataType(final Project project) {
        setProject(project);
    }

    /**
     * Log a formatted message at {@link Project#MSG_INFO} level.
     *
     * @param format {@link String}
     * @param args to format
     * @see Formatter
     */
    protected void log(final String format, final Object... args) {
        log(Project.MSG_INFO, format, args);
    }

    /**
     * Log a formatted message at the specified level.
     *
     * @param level log level
     * @param format {@link String}
     * @param args to format
     * @see Formatter
     */
    protected void log(final int level, final String format, final Object... args) {
        log(String.format(format, args), level);
    }
}
