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
import static org.apache.ant.s3.ProjectUtils.componentName;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Resource;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteMarkerEntry;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.S3Request;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;

/**
 * Amazon S3 object {@link Resource} implementation.
 */
public class ObjectResource extends Resource {
    private static class VersionInfo {
        final boolean deleteMarker;
        final boolean latest;

        VersionInfo(boolean deleteMarker, boolean latest) {
            this.deleteMarker = deleteMarker;
            this.latest = latest;
        }
    }

    @FunctionalInterface
    private interface Finalizer {
        static final Finalizer NOP = () -> {
        };

        void run() throws Exception;

        default Finalizer then(Finalizer next) {
            return () -> {
                run();
                next.run();
            };
        }
    }

    private static Finalizer closeWeak(Closeable closeable) {
        final Reference<Closeable> ref = new WeakReference<>(closeable);
        return () -> Optional.of(ref).map(Reference::get).ifPresent(t -> {
            try {
                t.close();
            } catch (IOException e) {
            }
        });
    }

    private S3Client s3;
    private String bucket;
    private String key;
    private volatile LongSupplier size;
    private volatile Supplier<Instant> lastModified;
    private volatile Supplier<String> versionId;
    private String contentType;
    private volatile Map<String, String> metadata;
    private volatile Map<String, String> tagging;
    private volatile Boolean exists;
    private volatile Finalizer finalizer = Finalizer.NOP;
    private volatile HeadObjectResponse head;
    private Optional<VersionInfo> versionInfo = Optional.empty();

    /**
     * Create a new {@link ObjectResource}.
     *
     * @param project
     */
    public ObjectResource(Project project) {
        setProject(project);
    }

    /**
     * Create a new {@link ObjectResource} from a listing.
     *
     * @param project
     * @param s3
     * @param bucket
     * @param summary
     * @param precision
     */
    ObjectResource(Project project, S3Client s3, String bucket, S3Object summary) {
        this(project, s3, bucket, summary.key(), summary::size, summary::lastModified, null, Precision.object);
    }

    ObjectResource(Project project, S3Client s3, String bucket, DeleteMarkerEntry deleteMarker) {
        this(project, s3, bucket, deleteMarker.key(), () -> UNKNOWN_SIZE, deleteMarker::lastModified,
            deleteMarker::versionId, Precision.version);
        versionInfo = Optional.ofNullable(new VersionInfo(true, deleteMarker.isLatest().booleanValue()));
    }

    ObjectResource(Project project, S3Client s3, String bucket, ObjectVersion version) {
        this(project, s3, bucket, version.key(), version::size, version::lastModified, version::versionId,
            Precision.version);
        versionInfo = Optional.of(new VersionInfo(false, version.isLatest().booleanValue()));
    }

    ObjectResource(Project project, S3Client s3, String bucket, String key, LongSupplier size,
        Supplier<Instant> lastModified, Supplier<String> versionId, Precision precision) {

        this(project, s3, bucket, key);
        this.size = size;
        this.lastModified = lastModified;
        this.versionId = versionId;
    }

    /**
     * Create a new {@link ObjectResource} fully-formed.
     *
     * @param project
     * @param s3
     * @param bucket
     * @param key
     */
    ObjectResource(Project project, S3Client s3, String bucket, String key) {
        setProject(project);
        setBucket(bucket);
        setKey(key);
        this.s3 = s3;
    }

    /**
     * Get the bucket of the S3 object.
     *
     * @return {@link String}
     */
    public String getBucket() {
        return isReference() ? getRef().getBucket() : bucket;
    }

    /**
     * Set the bucket of the S3 object.
     *
     * @param bucket
     */
    public void setBucket(String bucket) {
        checkAttributesAllowed();
        this.bucket = bucket;
    }

    /**
     * Get the S3 object key within its bucket.
     *
     * @return {@link String}
     */
    public String getKey() {
        if (isReference()) {
            return getRef().getKey();
        }
        return key;
    }

    /**
     * Set the key of the S3 object within its bucket.
     *
     * @param key
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Set the name of the S3 object, which for our purposes is equivalent to
     * calling {@link #setKey(String)}.
     */
    @Override
    public void setName(String name) {
        setKey(name);
    }

    /**
     * Get the name of the S3 object, which may be suffixed with the object
     * version if {@link #getPrecision()} returns {@link Precision#version} and
     * this object exists.
     */
    @Override
    public String getName() {
        final String result = getKey();

        if (getPrecision() == Precision.version) {
            final String version = getVersionId();
            if (StringUtils.isNotBlank(version)) {
                return String.format("%s@%s", result, version);
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     * @throws UnsupportedOperationException
     */
    @Override
    public void setDirectory(boolean directory) {
        throw new UnsupportedOperationException();
    }

    /**
     * Get the precision of this object, which impacts only its name.
     * 
     * @return {@link Precision}
     */
    public Precision getPrecision() {
        return versionInfo.isPresent() ? Precision.version : Precision.object;
    }

    /**
     * Learn whether this object represents a delete marker.
     * 
     * @return {@code boolean}
     */
    public boolean isDeleteMarker() {
        return versionInfo.filter(i -> i.deleteMarker).isPresent();
    }

    /**
     * Learn whether this object is the latest revision per bucket + key (always
     * {@code true} for objects with {@link Precision#object}).
     * 
     * @return {@code boolean}
     */
    public boolean isLatest() {
        return !versionInfo.isPresent() || versionInfo.get().latest;
    }

    /**
     * Get the content type of the S3 object.
     *
     * @return {@link String}
     */
    public String getContentType() {
        if (contentType == null) {
            return head().map(HeadObjectResponse::contentType).orElse(null);
        }
        return contentType;
    }

    /**
     * Set the content type of the S3 object.
     *
     * @param contentType
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * Create the nested {@code metadata} element.
     *
     * @return {@link InlineProperties}
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public InlineProperties createMetadata() {
        checkChildrenAllowed();
        Exceptions.raiseUnless(metadata == null, buildExceptionAt(getLocation()), "metadata already specified");

        final InlineProperties result = new InlineProperties(getProject());
        metadata = (Map) result.properties;
        return result;
    }

    /**
     * Get the {@link Map} of metadata.
     *
     * @return {@link Map}
     */
    public Map<String, String> getMetadata() {
        if (isReference()) {
            return getRef().getMetadata();
        }
        if (metadata == null) {
            synchronized (this) {
                if (metadata == null) {
                    metadata = new LinkedHashMap<>();
                    head().map(HeadObjectResponse::metadata).ifPresent(metadata::putAll);
                }
            }
        }
        return metadata;
    }

    /**
     * Set the metadata.
     *
     * @param metadata
     */
    public void setMetadata(Map<String, String> metadata) {
        Exceptions.raiseIf(isReference(), UnsupportedOperationException::new, "setMetadata");
        this.metadata = metadata;
    }

    /**
     * Create the nested {@code tagging} element.
     *
     * @return {@link InlineProperties}
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public InlineProperties createTagging() {
        checkChildrenAllowed();
        Exceptions.raiseUnless(tagging == null, buildExceptionAt(getLocation()), "tagging already specified");

        final InlineProperties result = new InlineProperties(getProject());
        tagging = (Map) result.properties;
        return result;
    }

    /**
     * Get the tagging of the S3 object.
     *
     * @return {@link Map}
     */
    public Map<String, String> getTagging() {
        if (isReference()) {
            return getRef().getTagging();
        }
        if (tagging == null) {
            synchronized (this) {
                if (tagging == null) {
                    tagging = new LinkedHashMap<>();
                    readTagging().ifPresent(r -> {
                        r.tagSet().stream().forEach(t -> tagging.put(t.key(), t.value()));
                    });
                }
            }
        }
        return tagging;
    }

    /**
     * Set the tagging for the S3 object.
     *
     * @param tagging
     */
    public void setTagging(Map<String, String> tagging) {
        if (isReference()) {
            throw new UnsupportedOperationException();
        }
        this.tagging = tagging;
    }

    /**
     * Add the configured {@link Client} element.
     *
     * @param s3
     */
    public void addConfigured(Client s3) {
        checkChildrenAllowed();

        Exceptions.raiseUnless(this.s3 == null, buildExceptionAt(getLocation()),
            () -> String.format("%s already specified", componentName(getProject(), Client.class)));

        this.s3 = Objects.requireNonNull(s3).get();
    }

    /**
     * Set the {@link Client} by reference.
     *
     * @param refid
     */
    public void setClientRefid(String refid) {
        checkAttributesAllowed();
        Exceptions.raiseIf(StringUtils.isBlank(refid), buildExceptionAt(getLocation()),
            "@clientrefid must not be null/empty/blank");

        addConfigured(getProject().<Client> getReference(refid));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isExists() {
        if (isReference()) {
            return getRef().isExists();
        }
        if (exists == null) {
            synchronized (this) {
                if (exists == null) {
                    exists = Boolean.valueOf(head().isPresent());
                }
            }
        }
        return exists.booleanValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSize() {
        if (isReference()) {
            return getRef().getSize();
        }
        if (size == null) {
            return head().map(HeadObjectResponse::contentLength).orElse(UNKNOWN_SIZE);
        }
        return size.getAsLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLastModified() {
        if (isReference()) {
            return getRef().getLastModified();
        }
        if (lastModified == null) {
            return head().map(r -> r.lastModified().toEpochMilli()).orElse(UNKNOWN_DATETIME);
        }
        return lastModified.get().toEpochMilli();
    }

    /**
     * Get the version ID, if available.
     * 
     * @return {@link String}
     */
    public String getVersionId() {
        if (isReference()) {
            return getRef().getVersionId();
        }
        if (versionId == null) {
            return head().map(HeadObjectResponse::versionId).orElse(null);
        }
        return versionId.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream getInputStream() throws IOException {
        if (isReference()) {
            return getRef().getInputStream();
        }
        final ResponseInputStream<GetObjectResponse> result = object();
        finalizer = finalizer.then(closeWeak(result));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OutputStream getOutputStream() throws IOException {
        if (isReference()) {
            return getRef().getOutputStream();
        }
        final Consumer<PutObjectRequest.Builder> put = put();

        return new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    s3().putObject(put, RequestBody.fromBytes(toByteArray()));
                }
            }
        };
    }

    /**
     * Put a {@link File} as the content of this S3 object.
     *
     * @param file
     */
    public void put(File file) {
        if (isReference()) {
            getRef().put(file);
        } else {
            put(s3(), file);
        }
    }

    /**
     * Put a {@link File} as the content of thie S3 object, specifying the
     * {@link S3Client} client to use.
     *
     * @param s3
     * @param file
     */
    public void put(S3Client s3, File file) {
        if (isReference()) {
            getRef().put(s3, file);
        }
        s3.putObject(put(), Objects.requireNonNull(file, "file").toPath());
    }

    /**
     * Delete this object from its bucket using its configured client.
     */
    public void delete() {
        if (isReference()) {
            getRef().delete();
        }
        delete(s3());
    }

    /**
     * Delete this object from its bucket using the specified {@link S3Client}.
     * 
     * @param s3
     */
    public void delete(S3Client s3) {
        if (isReference()) {
            getRef().delete(s3);
        }
        s3.deleteObject(request(DeleteObjectRequest.Builder::bucket, DeleteObjectRequest.Builder::key,
            DeleteObjectRequest.Builder::versionId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return isReference() ? getRef().toString() : String.format("s3://%s/%s", getBucket(), getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return isReference() ? getRef().hashCode() : Objects.hash(ObjectResource.class, getBucket(), getKey());
    }

    /**
     * Learn whether this {@link ObjectResource} is "fully equal" to the
     * specified {@link Object}. This means that locally-configurable S3 object
     * data are equal, in addition to {@link Object#equals(Object)} equality.
     *
     * @param other
     * @return {@code boolean}
     */
    public boolean fullyEquals(Object other) {
        return super.equals(other) && ((Resource) other).asOptional(ObjectResource.class)
            .filter(s3o -> getContentType().equals(s3o.getContentType()) && getMetadata().equals(s3o.getMetadata())
                && getTagging().equals(s3o.getTagging()) && StringUtils.equals(getVersionId(), s3o.getVersionId()))
            .isPresent();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void finalize() throws Throwable {
        finalizer.run();
        super.finalize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ObjectResource getRef() {
        return getCheckedRef(ObjectResource.class);
    }

    private ResponseInputStream<GetObjectResponse> object() {
        return s3().getObject(request(GetObjectRequest.Builder::bucket, GetObjectRequest.Builder::key,
            GetObjectRequest.Builder::versionId));
    }

    private Consumer<PutObjectRequest.Builder> put() {
        final Precision precision = getPrecision();
        Exceptions.raiseIf(precision == Precision.version, UnsupportedOperationException::new,
            "Put not supported for objects with %s %s", precision, Precision.class.getSimpleName());

        final Optional<String> _contentType = Optional.ofNullable(getContentType());
        final Optional<Map<String, String>> _metadata = Optional.of(getMetadata()).filter(m -> !m.isEmpty());

        final Optional<Tagging> _tagging = Optional
            .of(getTagging()).filter(m -> !m.isEmpty()).map(m -> m.entrySet().stream()
                .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build()).collect(Collectors.toList()))
            .map(Tagging.builder()::tagSet).map(Tagging.Builder::build);

        return request(PutObjectRequest.Builder::bucket, PutObjectRequest.Builder::key).andThen(b -> {
            _contentType.ifPresent(b::contentType);
            _metadata.ifPresent(b::metadata);
            _tagging.ifPresent(b::tagging);
        });
    }

    private <B extends S3Request.Builder> Consumer<B> request(BiConsumer<B, String> setBucket,
        BiConsumer<B, String> setKey) {
        return b -> {
            setBucket.accept(b, ProjectUtils.require(getBucket(), "@bucket"));
            setKey.accept(b, ProjectUtils.require(getKey(), "@key"));
        };
    }

    private <B extends S3Request.Builder> Consumer<B> request(BiConsumer<B, String> setBucket,
        BiConsumer<B, String> setKey, BiConsumer<B, String> setVersionId) {
        Consumer<B> result = request(setBucket, setKey);

        if (getPrecision() == Precision.version) {
            result = result.andThen(b -> {
                final String versionId = getVersionId();
                if (versionId != null) {
                    setVersionId.accept(b, versionId);
                }
            });
        }
        return result;
    }

    private Optional<GetObjectTaggingResponse> readTagging() {
        try {
            return Optional.of(s3().getObjectTagging(request(GetObjectTaggingRequest.Builder::bucket,
                GetObjectTaggingRequest.Builder::key, GetObjectTaggingRequest.Builder::versionId)));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    private Optional<HeadObjectResponse> head() {
        if (head == null) {
            synchronized (this) {
                if (head == null) {
                    try {
                        Consumer<HeadObjectRequest.Builder> request =
                            request(HeadObjectRequest.Builder::bucket, HeadObjectRequest.Builder::key);

                        if (getPrecision() == Precision.version && versionId != null) {
                            request = request.andThen(b -> b.versionId(versionId.get()));
                        }
                        head = s3().headObject(request);
                    } catch (NoSuchKeyException e) {
                        return Optional.empty();
                    }
                }
            }
        }
        return Optional.ofNullable(head);
    }

    private S3Client s3() {
        return ProjectUtils.requireComponent(getProject(), s3, Client.class);
    }
}
