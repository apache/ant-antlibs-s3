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
package org.apache.ant.s3.build;

import java.util.function.Function;

import org.apache.ant.s3.build.spi.DefaultProvider;
import org.apache.ant.s3.build.spi.StringConversionsProvider;
import org.kohsuke.MetaInfServices;

import software.amazon.awssdk.awscore.defaultsmode.DefaultsMode;
import software.amazon.awssdk.regions.Region;

/**
 * AWS {@link StringConversionsProvider}.
 */
@DefaultProvider
@MetaInfServices
public class AwsStringConversionsProvider extends StringConversionsProvider {
    /**
     * {@link Region} from {@link String}.
     */
    public static final Function<String, Region> regionOf = Region::of;

    /**
     * {@link DefaultsMode} from {@link String}.
     */
    public static final Function<String, DefaultsMode> defaultsModeFromValue = DefaultsMode::fromValue;
}
