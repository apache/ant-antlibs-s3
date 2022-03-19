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

import java.util.Formatter;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

/**
 * Define task logging convenience configuration.
 */
abstract class LoggingTask extends Task implements ProjectUtils {

    private int verbosity = Project.MSG_VERBOSE;

    /**
     * Create a new {@link LoggingTask} instance.
     */
    protected LoggingTask() {
        super();
    }

    /**
     * Create a new {@link LoggingTask} instance bound to the specified
     * {@link Project}.
     * 
     * @param project
     */
    protected LoggingTask(Project project) {
        this();
        setProject(project);
    }

    /**
     * Learn whether this {@link Task} is operating with increased log output.
     *
     * @return {@code boolean}
     */
    public boolean isVerbose() {
        return verbosity < Project.MSG_VERBOSE;
    }

    /**
     * Set whether this {@link Task} should operate with increased log output.
     *
     * @param verbose
     */
    public void setVerbose(final boolean verbose) {
        verbosity = verbose ? Project.MSG_INFO : Project.MSG_VERBOSE;
    }

    /**
     * Log a formatted message at the default level dictated by
     * {@link #isVerbose()}.
     *
     * @param format
     * @param args
     * @see Formatter
     */
    protected void log(final String format, final Object... args) {
        log(verbosity, format, args);
    }

    /**
     * Log a formatted message at the specified level.
     *
     * @param level
     * @param format
     * @param args
     * @see Formatter
     */
    protected void log(final int level, final String format, final Object... args) {
        log(String.format(format, args), level);
    }

    /**
     * Log a formatted message with accompanying/triggering {@link Throwable}.
     *
     * @param t
     * @param format
     * @param args
     * @see Formatter
     */
    protected void log(final Throwable t, final String format, final Object... args) {
        log(Project.MSG_ERR, t, format, args);
    }

    /**
     * Log a formatted message with accompanying/triggering {@link Throwable} at
     * a specific level.
     *
     * @param level
     * @param t
     * @param format
     * @param args
     * @see Formatter
     */
    protected void log(final int level, final Throwable t, final String format,
        final Object... args) {
        log(String.format(format, args), t, level);
    }

}