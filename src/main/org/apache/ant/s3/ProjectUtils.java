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
 * Interface providing behavior for Ant {@link Project} components.
 */
public interface ProjectUtils {

    /**
     * Attempt to determine a component name for the specified type.
     * 
     * @param type for which component name is desired
     *
     * @return {@link String}
     */
    default String componentName(final Class<?> type) {
        return componentName(getProject(), type);
    }

    /**
     * Attempt to determine a component name for the specified type relative to
     * the specified {@link Project}.
     *
     * @param project Ant project
     * @param type for which component name is desired
     * @return {@link String}
     */
    default String componentName(final Project project, final Class<?> type) {
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
     * @param <T> type
     * @param item required object
     * @param description description in thrown {@link Exception} if absent
     * @return {@code item}
     * @throws IllegalStateException
     *             if {@code t == null}
     */
    default <T> T require(final T item, final String description) {
        Exceptions.raiseIf(item == null, buildException(), "%s is required", description);
        return item;
    }

    /**
     * Require the specified component.
     * 
     * @param component required component
     * @param type of component
     *
     * @param <T> type
     * @return {@code component}
     */
    default <T> T requireComponent(final T component, final Class<?> type) {
        return require(component, componentName(type));
    }

    /**
     * Create a {@link Function} that will create a {@link BuildException} at
     * the specified {@link Location}, given the {@link String} message.
     *
     * @return {@link Function}
     */
    default Function<String, BuildException> buildException() {
        return msg -> new BuildException(msg, getLocation());
    }

    /**
     * Create a {@link BiFunction} that will create a {@link BuildException} at
     * the specified {@link Location}, given the {@link String} message and
     * {@link Throwable} cause.
     *
     * @return {@link BiFunction}
     */
    default BiFunction<String, Throwable, BuildException> buildExceptionTriggered() {
        return (msg, cause) -> new BuildException(msg, cause, getLocation());
    }

    /**
     * Get the {@link Project} of this item.
     * 
     * @return {@link Project}
     */
    Project getProject();

    /**
     * Get the {@link Location} of this item.
     * 
     * @return {@link Location}
     */
    Location getLocation();
}
