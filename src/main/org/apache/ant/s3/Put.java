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

import static org.apache.ant.s3.ProjectUtils.buildExceptionAt;
import static org.apache.ant.s3.ProjectUtils.require;
import static org.apache.ant.s3.ProjectUtils.requireComponent;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Vector;
import java.util.function.Supplier;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.FilterChain;
import org.apache.tools.ant.types.FilterSetCollection;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceFactory;
import org.apache.tools.ant.types.resources.FileProvider;
import org.apache.tools.ant.util.StringUtils;

import software.amazon.awssdk.services.s3.S3Client;

/**
 * Put {@link Resource}s into an S3 bucket.
 */
public class Put extends CopyResources {

    private Supplier<S3Client> s3;
    private String bucket;

    /**
     * Add a configured {@link Client}.
     *
     * @param s3
     */
    public void addConfigured(Client s3) {
        if (this.s3 != null) {
            throw new BuildException("S3 client already specified");
        }
        this.s3 = Objects.requireNonNull(s3);
    }

    /**
     * Set the {@link Client} by reference.
     *
     * @param refid
     */
    public void setClientRefid(String refid) {
        Objects.requireNonNull(StringUtils.trimToNull(refid), "@clientrefid must not be null/empty/blank");

        addConfigured(getProject().<Client> getReference(refid));
    }

    /**
     * Get the bucket to which target objects should be written.
     *
     * @return {@link String}
     */
    public String getBucket() {
        return bucket;
    }

    /**
     * Set the bucket to which target objects should be written.
     *
     * @param bucket
     */
    public void setBucket(String bucket) {
        this.bucket = StringUtils.trimToNull(bucket);
    }

    /**
     * Disable {@code append}.
     *
     * @param append
     * @throws BuildException
     *             if {@code append == true}
     */
    @Override
    public void setAppend(boolean append) {
        Exceptions.raiseIf(append, buildExceptionAt(getLocation()), "@append not supported by %s", getTaskName());
    }

    /**
     * Disable {@code preserveLastModified}.
     *
     * @param preserveLastModified
     * @throws BuildException
     *             if {@code preserveLastModified == true}
     */
    @Override
    public void setPreserveLastModified(boolean preserveLastModified) {
        Exceptions.raiseIf(preserveLastModified, buildExceptionAt(getLocation()),
            "@preserveLastModified not supported by %s", getTaskName());
    }

    /**
     * Enforce "always overwrite."
     * @return {@code true}
     */
    @Override
    public boolean isOverwrite() {
        return true;
    }

    /**
     * Disable {@code overwrite}.
     * 
     * @param overwrite
     * @throws BuildException
     *             if {@code overwrite == false}
     */
    @Override
    public void setOverwrite(boolean overwrite) {
        Exceptions.raiseUnless(overwrite, buildExceptionAt(getLocation()), "%s only operates in overwrite mode",
            getTaskName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ResourceFactory resourceFactory() {
        final S3Client client = s3();
        final String _bucket = require(getBucket(), "@bucket");
        final String s = String.format("bucket %s", _bucket);

        return new ResourceFactory() {
            @Override
            public Resource getResource(String key) {
                return new ObjectResource(getProject(), client, _bucket, key);
            }

            @Override
            public String toString() {
                return s;
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void copyResource(Resource source, Resource dest, FilterSetCollection filters,
        final Vector<FilterChain> filterChains) throws IOException {

        while (filterChains.isEmpty() && !filters.hasFilters()) {
            final ObjectResource target = dest.as(ObjectResource.class);

            final String whyNot;

            if (source == dest) {
                whyNot = "Source and target resources are same runtime object %1$s";
            } else if (target.fullyEquals(source)) {
                whyNot = "Source and target resources are same fully-equal s3 object %1$s";
            } else {
                final Optional<FileProvider> fp = source.asOptional(FileProvider.class);
                if (fp.isPresent()) {
                    // use dedicated File-based put for simple File copy:
                    target.put(s3(), fp.get().getFile());
                    return;
                }
                break;
            }
            log(whyNot + " and no filters specified; nothing to do", source, target);
        }
        super.copyResource(source, dest, filters, filterChains);
    }

    private S3Client s3() {
        return requireComponent(getProject(), s3, Client.class).get();
    }
}
