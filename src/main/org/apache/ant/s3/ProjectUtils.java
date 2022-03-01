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
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.tools.ant.AntTypeDefinition;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.ComponentHelper;
import org.apache.tools.ant.Location;
import org.apache.tools.ant.Project;

/**
 * Project utils.
 */
class ProjectUtils {

    /**
     * Attempt to determine a component name for the specified type relative to
     * the specified {@link Project}.
     *
     * @param project
     * @param type
     * @return {@link String}
     */
    static String componentName(final Project project, final Class<?> type) {
        Objects.requireNonNull(type, "type");

        if (project != null) {
            for (final Map.Entry<String, AntTypeDefinition> e : ComponentHelper.getComponentHelper(project)
                .getAntTypeTable().entrySet()) {
                if (Objects.equals(e.getValue().getTypeClass(project), type)) {
                    return e.getKey();
                }
            }
        }
        return type.getSimpleName();
    }

    /**
     * Require the specified item.
     *
     * @param <T>
     * @param item
     * @param description
     * @return {@code item}
     * @throws IllegalStateException
     *             if {@code t == null}
     */
    static <T> T require(final T item, final String description) {
        Exceptions.raiseIf(item == null, BuildException::new, "%s is required", description);
        return item;
    }

    /**
     * Require the specified component.
     *
     * @param <T>
     * @param project
     * @param component
     * @param type
     * @return {@code component}
     */
    static <T> T requireComponent(final Project project, final T component, final Class<?> type) {
        return require(component, componentName(project, type));
    }

    /**
     * Create a {@link Function} that will create a {@link BuildException} at
     * the specified {@link Location}, given the {@link String} message.
     *
     * @param location
     * @return {@link Function}
     */
    static Function<String, BuildException> buildExceptionAt(final Location location) {
        return msg -> new BuildException(msg, location);
    }

    /**
     * Create a {@link BiFunction} that will create a {@link BuildException} at
     * the specified {@link Location}, given the {@link String} message and
     * {@link Throwable} cause.
     *
     * @param location
     * @return {@link BiFunction}
     */
    static BiFunction<String, Throwable, BuildException> buildExceptionTriggeredAt(final Location location) {
        return (msg, cause) -> new BuildException(msg, cause, location);
    }

    private ProjectUtils() {
    }
}
