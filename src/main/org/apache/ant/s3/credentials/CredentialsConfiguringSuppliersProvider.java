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
package org.apache.ant.s3.credentials;

import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.ant.s3.Exceptions;
import org.apache.ant.s3.ProjectUtils;
import org.apache.ant.s3.build.Builder;
import org.apache.ant.s3.build.ClassFinder;
import org.apache.ant.s3.build.ConfiguringSupplier;
import org.apache.ant.s3.build.MetaBuilderByType;
import org.apache.ant.s3.build.spi.ConfiguringSuppliersProvider;
import org.apache.ant.s3.strings.ClassNames;
import org.apache.ant.s3.strings.ClassNames.Direction;
import org.apache.ant.s3.strings.PackageNames;
import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectComponent;
import org.kohsuke.MetaInfServices;

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProcessCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider;
import software.amazon.awssdk.auth.credentials.internal.LazyAwsCredentialsProvider;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleWithSamlCredentialsProvider;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleWithWebIdentityCredentialsProvider;
import software.amazon.awssdk.services.sts.auth.StsGetFederationTokenCredentialsProvider;
import software.amazon.awssdk.services.sts.auth.StsGetSessionTokenCredentialsProvider;
import software.amazon.awssdk.utils.builder.Buildable;

/**
 * {@link ConfiguringSuppliersProvider} for AWS credentials.
 */
@MetaInfServices
public class CredentialsConfiguringSuppliersProvider extends ConfiguringSuppliersProvider {
    /**
     * Base {@link ConfiguringSupplier} implementation for internal stuff.
     *
     * @param <T>
     *            supplied type
     */
    public static abstract class BaseConfiguringSupplier<T> extends ProjectComponent
        implements ConfiguringSupplier<T>, ProjectUtils {
        /**
         * Create a new {@link BaseConfiguringSupplier}.
         * 
         * @param project
         *            Ant {@link Project}
         */
        protected BaseConfiguringSupplier(Project project) {
            setProject(project);
        }
    }

    /**
     * {@link StaticCredentialsProvider} {@link ConfiguringSupplier}. Supports
     * {@link AwsBasicCredentials} only.
     */
    public static class StaticCredentialsProviderConfiguringSupplier
        extends BaseConfiguringSupplier<StaticCredentialsProvider> {
        private String accessKey;
        private String secretKey;

        /**
         * Create a new {@link StaticCredentialsProviderConfiguringSupplier}.
         * 
         * @param project
         *            Ant {@link Project}
         */
        private StaticCredentialsProviderConfiguringSupplier(Project project) {
            super(project);
        }

        /**
         * Set the accessKey.
         * 
         * @param accessKey
         *            {@link String}
         */
        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        /**
         * Set the secretKey.
         * 
         * @param secretKey
         *            {@link String}
         */
        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setDynamicAttribute(String uri, String localName, String qName, String value)
            throws BuildException {
            switch (StringUtils.lowerCase(localName, Locale.US)) {
            case "accesskey":
                setAccessKey(value);
                break;
            case "secretkey":
                setSecretKey(value);
                break;
            default:
                super.setDynamicAttribute(uri, localName, qName, value);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public StaticCredentialsProvider get() {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
    }

    /**
     * {@link ConfiguringSupplier} implementation for concrete {@code *Builder}
     * types that do not implement {@link Buildable}.
     *
     * @param <B>
     *            {@code *Builder}
     * @param <T>
     *            built/supplied type
     */
    public static class NonBuildableBuilderBuildConfiguringSupplier<B, T> extends BaseConfiguringSupplier<T> {

        private final B sdkBuilder;
        private final Builder<B> builder;
        private final Function<B, T> get;

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private NonBuildableBuilderBuildConfiguringSupplier(Project project, B builder, Function<B, T> get) {
            super(project);
            this.sdkBuilder = builder;
            this.builder = new Builder(builder.getClass(), project);
            this.get = get;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setDynamicAttribute(String uri, String localName, String qName, String value)
            throws BuildException {
            builder.setDynamicAttribute(uri, localName, qName, value);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object createDynamicElement(String uri, String localName, String qName) throws BuildException {
            return builder.createDynamicElement(uri, localName, qName);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public T get() {
            builder.accept(sdkBuilder);
            return get.apply(sdkBuilder);
        }
    }

    /**
     * {@link ConfiguringSupplier} for types that build with no configuration.
     *
     * @param <T>
     *            supplied type
     */
    public static class NoConfigConfiguringSupplier<T> extends BaseConfiguringSupplier<T> {
        private final Supplier<T> get;

        private NoConfigConfiguringSupplier(Project project, Supplier<T> get) {
            super(project);
            this.get = get;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public T get() {
            return get.get();
        }
    }

    /**
     * {@link ConfiguringSupplier} for {@link LazyAwsCredentialsProvider}.
     * Requires a single nested {@code provider} element which is
     * {@link CredentialsConfiguringSuppliersProvider#credentialsProviderConfiguringSupplier(Project)}.
     */
    public class LazyCredentialsProviderConfiguringSupplier
        extends BaseConfiguringSupplier<LazyAwsCredentialsProvider> {
        private ConfiguringSupplier<AwsCredentialsProvider> provider;

        private LazyCredentialsProviderConfiguringSupplier(Project project) {
            super(project);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object createDynamicElement(String uri, String localName, String qName) throws BuildException {
            if ("provider".equals(localName)) {
                Exceptions.raiseUnless(provider == null, buildExceptionTriggered(), new IllegalStateException(),
                    "provider already created");
                provider = credentialsProviderConfiguringSupplier(getProject());
                return provider;
            }
            return super.createDynamicElement(uri, localName, qName);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public LazyAwsCredentialsProvider get() {
            Exceptions.raiseIf(provider == null, buildExceptionTriggered(), new IllegalStateException(),
                "provider not configured");
            return LazyAwsCredentialsProvider.create(provider);
        }
    }

    /**
     * Produce a {@link ConfiguringSupplier} of {@link AwsCredentialsProvider}.
     * This is a {@link MetaBuilderByType} configured to search for
     * {@link AwsCredentialsProvider} types given an {@code @impl} "fragment"
     * that is:
     * <ul>
     * <li>Tested as a FQ classname</li>
     * <li>Plugged into a matrix of: packages
     * <ul>
     * <li>0-2 ancestors of {@link AwsCredentialsProvider}</li>
     * <li>this package</li>
     * </ul>
     * X classname suffixes as segments of {@link AwsCredentialsProvider},
     * successively trimmed from the LHS.</li>
     * <li>If unspecified, defaulted to {@link StaticCredentialsProvider}</li>
     * </ul>
     * 
     * @param project
     *            Ant {@link Project}
     * @return {@link MetaBuilderByType} of {@link AwsCredentialsProvider}
     */
    public MetaBuilderByType<AwsCredentialsProvider> credentialsProviderConfiguringSupplier(Project project) {
        return new MetaBuilderByType<>(project, AwsCredentialsProvider.class,
            new ClassFinder(
                PackageNames.of(AwsCredentialsProvider.class).ancestors(0, 2).andThen(PackageNames.of(getClass())),
                ClassNames.of(AwsCredentialsProvider.class).segments(Direction.FROM_LEFT)),
            StaticCredentialsProvider.class);
    }

    /**
     * Produce a {@link ConfiguringSupplier} for
     * {@link StaticCredentialsProvider}.
     * 
     * @param project
     *            Ant {@link Project}
     * @return {@link StaticCredentialsProviderConfiguringSupplier}
     */
    public StaticCredentialsProviderConfiguringSupplier staticCredentialsProviderConfiguringSupplier(Project project) {
        return new StaticCredentialsProviderConfiguringSupplier(project);
    }

    /**
     * Produce a {@link ConfiguringSupplier} for
     * {@link DefaultCredentialsProvider}.
     * 
     * @param project
     *            Ant {@link Project}
     * @return {@link NonBuildableBuilderBuildConfiguringSupplier}
     */
    public ConfiguringSupplier<DefaultCredentialsProvider> defaultCredentialsProviderConfiguringSupplier(
        Project project) {
        return new NonBuildableBuilderBuildConfiguringSupplier<>(project, DefaultCredentialsProvider.builder(),
            DefaultCredentialsProvider.Builder::build);
    }

    /**
     * Produce a {@link ConfiguringSupplier} for
     * {@link AwsCredentialsProviderChain}.
     * 
     * @param project
     *            Ant {@link Project}
     * @return {@link NonBuildableBuilderBuildConfiguringSupplier}
     */
    public ConfiguringSupplier<AwsCredentialsProviderChain> chain(Project project) {
        return new NonBuildableBuilderBuildConfiguringSupplier<>(project, AwsCredentialsProviderChain.builder(),
            AwsCredentialsProviderChain.Builder::build);
    }

    /**
     * Produce a {@link ConfiguringSupplier} for
     * {@link ProcessCredentialsProvider}.
     * 
     * @param project
     *            Ant {@link Project}
     * @return {@link NonBuildableBuilderBuildConfiguringSupplier}
     */
    public ConfiguringSupplier<ProcessCredentialsProvider> process(Project project) {
        return new NonBuildableBuilderBuildConfiguringSupplier<>(project, ProcessCredentialsProvider.builder(),
            ProcessCredentialsProvider.Builder::build);
    }

    /**
     * Produce a {@link ConfiguringSupplier} for
     * {@link StsAssumeRoleCredentialsProvider}.
     * 
     * @param project
     *            Ant {@link Project}
     * @return {@link NonBuildableBuilderBuildConfiguringSupplier}
     */
    public ConfiguringSupplier<StsAssumeRoleCredentialsProvider> stsAssumeRole(Project project) {
        return new NonBuildableBuilderBuildConfiguringSupplier<>(project, StsAssumeRoleCredentialsProvider.builder(),
            StsAssumeRoleCredentialsProvider.Builder::build);
    }

    /**
     * Produce a {@link ConfiguringSupplier} for
     * {@link StsAssumeRoleWithSamlCredentialsProvider}.
     * 
     * @param project
     *            Ant {@link Project}
     * @return {@link NonBuildableBuilderBuildConfiguringSupplier}
     */
    public ConfiguringSupplier<StsAssumeRoleWithSamlCredentialsProvider> stsAssumeRoleSaml(Project project) {
        return new NonBuildableBuilderBuildConfiguringSupplier<>(project,
            StsAssumeRoleWithSamlCredentialsProvider.builder(),
            StsAssumeRoleWithSamlCredentialsProvider.Builder::build);
    }

    /**
     * Produce a {@link ConfiguringSupplier} for
     * {@link StsAssumeRoleWithWebIdentityCredentialsProvider}.
     * 
     * @param project
     *            Ant {@link Project}
     * @return {@link NonBuildableBuilderBuildConfiguringSupplier}
     */
    public ConfiguringSupplier<StsAssumeRoleWithWebIdentityCredentialsProvider> stsAssumeRoleWebId(Project project) {
        return new NonBuildableBuilderBuildConfiguringSupplier<>(project,
            StsAssumeRoleWithWebIdentityCredentialsProvider.builder(),
            StsAssumeRoleWithWebIdentityCredentialsProvider.Builder::build);
    }

    /**
     * Produce a {@link ConfiguringSupplier} for
     * {@link StsGetFederationTokenCredentialsProvider}.
     * 
     * @param project
     *            Ant {@link Project}
     * @return {@link NonBuildableBuilderBuildConfiguringSupplier}
     */
    public ConfiguringSupplier<StsGetFederationTokenCredentialsProvider> stsFederationToken(Project project) {
        return new NonBuildableBuilderBuildConfiguringSupplier<>(project,
            StsGetFederationTokenCredentialsProvider.builder(),
            StsGetFederationTokenCredentialsProvider.Builder::build);
    }

    /**
     * Produce a {@link ConfiguringSupplier} for
     * {@link StsGetSessionTokenCredentialsProvider}.
     * 
     * @param project
     *            Ant {@link Project}
     * @return {@link NonBuildableBuilderBuildConfiguringSupplier}
     */
    public ConfiguringSupplier<StsGetSessionTokenCredentialsProvider> stsSessionToken(Project project) {
        return new NonBuildableBuilderBuildConfiguringSupplier<>(project,
            StsGetSessionTokenCredentialsProvider.builder(), StsGetSessionTokenCredentialsProvider.Builder::build);
    }

    /**
     * Produce a {@link ConfiguringSupplier} for
     * {@link AnonymousCredentialsProvider}.
     * 
     * @param project
     *            Ant {@link Project}
     * @return {@link NoConfigConfiguringSupplier}
     */
    public ConfiguringSupplier<AnonymousCredentialsProvider> anonymous(Project project) {
        return new NoConfigConfiguringSupplier<>(project, AnonymousCredentialsProvider::create);
    }

    /**
     * Produce a {@link ConfiguringSupplier} for
     * {@link EnvironmentVariableCredentialsProvider}.
     * 
     * @param project
     *            Ant {@link Project}
     * @return {@link NoConfigConfiguringSupplier}
     */
    public ConfiguringSupplier<EnvironmentVariableCredentialsProvider> environmentVariable(Project project) {
        return new NoConfigConfiguringSupplier<>(project, EnvironmentVariableCredentialsProvider::create);
    }

    /**
     * Produce a {@link ConfiguringSupplier} for
     * {@link SystemPropertyCredentialsProvider}.
     * 
     * @param project
     *            Ant {@link Project}
     * @return {@link NoConfigConfiguringSupplier}
     */
    public ConfiguringSupplier<SystemPropertyCredentialsProvider> systemProperty(Project project) {
        return new NoConfigConfiguringSupplier<>(project, SystemPropertyCredentialsProvider::create);
    }

    /**
     * Produce a {@link ConfiguringSupplier} for
     * {@link LazyAwsCredentialsProvider}.
     * 
     * @param project
     *            Ant {@link Project}
     * @return {@link LazyCredentialsProviderConfiguringSupplier}
     */
    public ConfiguringSupplier<LazyAwsCredentialsProvider> lazy(Project project) {
        return new LazyCredentialsProviderConfiguringSupplier(project);
    }
}
