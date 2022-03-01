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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Reference;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.Union;
import org.apache.tools.ant.util.StringUtils;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Error;

/**
 * Delete S3 object resources.
 */
public class Delete extends LoggingTask {
    private static final int DEFAULT_BLOCK_SIZE = 1000;

    private Supplier<S3Client> s3;
    private ResourceCollection resources;
    private Precision as = Precision.object;
    private int blockSize = DEFAULT_BLOCK_SIZE;

    /**
     * Create a new {@link Delete} task instance.
     */
    public Delete() {
        super();
    }

    /**
     * Create a new {@link Delete} task instance bound to the specified
     * {@link Project}.
     * 
     * @param project
     */
    public Delete(Project project) {
        super(project);
    }

    /**
     * Get the delete mode, semantically expressed "as".
     * 
     * @return {@link Precision}
     */
    public Precision getAs() {
        return as;
    }

    /**
     * Set the delete precision, semantically expressed "as":
     * <ul>
     * <li>{@code object}: in a versioned bucket this will leave a delete marker
     * as the current version of the object.</li>
     * <li>{@code version}: in a versioned bucket this will leave no delete
     * marker, but will leave behind the previous version if it exists.</li>
     * </ul>
     * 
     * @param as
     *            {@link Precision}
     */
    public void setAs(Precision as) {
        this.as = as;
    }

    /**
     * Add a configured {@link Client}.
     *
     * @param s3
     */
    public void addConfigured(final Client s3) {
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
    public void setClientRefid(final String refid) {
        Objects.requireNonNull(StringUtils.trimToNull(refid), "@clientrefid must not be null/empty/blank");

        addConfigured(getProject().<Client> getReference(refid));
    }

    /**
     * Add a nested {@link ResourceCollection}.
     * 
     * @param coll
     */
    public synchronized void addConfigured(ResourceCollection coll) {
        Exceptions.raiseIf(coll == null, IllegalArgumentException::new, "null %s",
            ResourceCollection.class.getSimpleName());

        if (resources == null) {
            resources = coll;
            return;
        }
        if (!(resources instanceof Union)) {
            resources = new Union(getProject(), resources);
        }
        ((Union) resources).add(coll);
    }

    /**
     * Add by reference a {@link ResourceCollection} to delete.
     * 
     * @param refid
     */
    public void setRefid(Reference refid) {
        addConfigured(refid.<ResourceCollection> getReferencedObject(getProject()));
    }

    /**
     * Get the blockSize.
     * 
     * @return {@code int}
     */
    public int getBlockSize() {
        return blockSize;
    }

    /**
     * Set the blockSize.
     * 
     * @param blockSize
     *            {@code int}
     */
    public void setBlockSize(int blockSize) {
        this.blockSize = blockSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws BuildException {
        if (s3 == null) {
            objects().forEach(o -> {
                log("Deleting %s", o);
                o.delete();
            });
        } else {
            deleteObjects(s3.get());
        }
    }

    private void deleteObjects(S3Client s3) {
        Exceptions.raiseIf(blockSize < 1, BuildException::new, "Illegal block size %d", blockSize);

        final Map<String, List<ObjectResource>> buckets =
            objects().collect(Collectors.groupingBy(ObjectResource::getBucket));

        final Set<S3Error> errors = new LinkedHashSet<>();

        buckets.forEach((bucket, objects) -> subdivide(objects).forEach(so -> {
            final DeleteObjectsResponse response = s3.deleteObjects(
                b -> b.bucket(bucket).delete(db -> db.quiet(Boolean.valueOf(!isVerbose())).objects(so.stream()
                    .peek(o -> log("Adding %s to deletion batch", o)).map(this::id).collect(Collectors.toSet()))));

            log("Deleted %d objects with %d errors", response.deleted().size(), response.errors().size());
            errors.addAll(response.errors());
        }));
        if (!errors.isEmpty()) {
            errors.forEach(e -> log(Project.MSG_ERR, "%s", e.toString()));
        }
    }

    private Stream<ObjectResource> objects() {
        return resources.stream().map(r -> r.asOptional(ObjectResource.class)).filter(r -> {
            if (!r.isPresent()) {
                log(Project.MSG_WARN, "Will not attempt to delete %s as it is no S3 %s", r,
                    ObjectResource.class.getSimpleName());
                return false;
            }
            return true;
        }).map(Optional::get).filter(o -> {
            if (getAs() == Precision.object && o.isDeleteMarker()) {
                log(Project.MSG_WARN, "Will not attempt to delete %s as it is a delete marker and @as = %s", o,
                    getAs());
                return false;
            }
            return true;
        });
    }

    private Collection<Collection<ObjectResource>> subdivide(List<ObjectResource> coll) {
        if (coll.size() > getBlockSize()) {
            final Collection<Collection<ObjectResource>> subdivided = new ArrayList<>();
            final int sz = coll.size();
            int start = 0;
            do {
                final int end = Math.min(start + getBlockSize(), sz);
                subdivided.add(coll.subList(start, end));
                start = end;
            } while (start < sz);
            return subdivided;
        }
        return Collections.singleton(coll);
    }

    private ObjectIdentifier id(ObjectResource o) {
        final ObjectIdentifier.Builder b = ObjectIdentifier.builder().key(o.getKey());
        if (getAs() == Precision.version) {
            b.versionId(o.getVersionId());
        }
        return b.build();
    }
}
