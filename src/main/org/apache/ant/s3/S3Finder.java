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

import java.io.File;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.PatternSet;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.apache.tools.ant.types.selectors.TokenizedPath;
import org.apache.tools.ant.types.selectors.TokenizedPattern;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.DeleteMarkerEntry;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * {@link AmazonS3} finder.
 */
class S3Finder implements Supplier<Optional<ObjectResource>> {
    interface ObjectFactory<T> {

        ObjectResource resourceFrom(Project project, S3Client s3, String bucket, T source);
    }

    static class Atom<T> {
        static Comparator<Atom<?>> COMPARATOR = Comparator.<Atom<?>, String> comparing(Atom::key)
            .thenComparing(Atom::latest).thenComparing(Atom::lastModified);

        static Atom<S3Object> of(S3Object o) {
            return new Atom<S3Object>(o, o::key, () -> null, () -> true, o::lastModified, ObjectResource::new);
        }

        static Atom<DeleteMarkerEntry> of(DeleteMarkerEntry d) {
            return new Atom<DeleteMarkerEntry>(d, d::key, d::versionId, d::isLatest, d::lastModified,
                ObjectResource::new);
        }

        static Atom<ObjectVersion> of(ObjectVersion v) {
            return new Atom<ObjectVersion>(v, v::key, v::versionId, v::isLatest, v::lastModified, ObjectResource::new);
        }

        final T source;
        final Supplier<String> key;
        final Supplier<String> versionId;
        final BooleanSupplier latest;
        final Supplier<Instant> lastModified;
        final ObjectFactory<T> factory;

        Atom(T source, Supplier<String> key, Supplier<String> versionId, BooleanSupplier latest,
            Supplier<Instant> lastModified, ObjectFactory<T> factory) {
            this.source = source;
            this.key = key;
            this.versionId = versionId;
            this.latest = latest;
            this.lastModified = lastModified;
            this.factory = factory;
        }

        String key() {
            return key.get();
        }

        boolean latest() {
            return latest.getAsBoolean();
        }

        Instant lastModified() {
            return lastModified.get();
        }

        ObjectResource object(Project project, S3Client s3, String bucket) {
            return factory.resourceFrom(project, s3, bucket, source);
        }
    }

    static abstract class BaseFrame<RESPONSE, SELF extends BaseFrame<RESPONSE, SELF>> {
        final S3Finder finder;
        final RESPONSE listing;
        final String prefix;
        final Iterator<CommonPrefix> prefixes;
        final Iterator<Atom<?>> contents;
        final Function<RESPONSE, SELF> factory;

        protected BaseFrame(S3Finder finder, RESPONSE listing, Supplier<String> prefix, List<CommonPrefix> prefixes,
            Stream<Atom<?>> contents, Function<RESPONSE, SELF> factory) {
            this.listing = listing;
            this.prefix = prefix.get();
            this.finder = finder;

            final TokenizedPath path = finder.path(prefix.get());
            final Set<TokenizedPattern> includes = finder.patterns.getLeft();

            if (includes.isEmpty()) {
                this.prefixes = prefixes.iterator();
            } else {
                final boolean canRecurse = includes.stream().anyMatch(include -> {
                    if (!include.matchStartOf(path, finder.caseSensitive)) {
                        return false;
                    }
                    final int remainingDepth = include.containsPattern(SelectorUtils.DEEP_TREE_MATCH)
                        ? Integer.MAX_VALUE : include.depth() - path.depth();

                    return remainingDepth > 1;
                });
                if (canRecurse) {
                    this.prefixes = prefixes.stream().filter(this::allowPrefix).iterator();
                } else {
                    this.prefixes = Collections.emptyIterator();
                }
            }
            this.contents = contents.filter(this::allow).iterator();
            this.factory = factory;
        }

        boolean allowPrefix(CommonPrefix prefix) {
            return finder.patterns.getLeft().stream()
                .anyMatch(p -> p.matchStartOf(finder.path(prefix.prefix()), finder.caseSensitive));
        }

        boolean allow(Atom<?> atom) {
            final TokenizedPath path = finder.path(atom.key());

            final Set<TokenizedPattern> includes = finder.patterns.getLeft();
            final boolean included = includes.isEmpty() || finder.matchesAny(includes, path);
            return included && !finder.matchesAny(finder.patterns.getRight(), path);
        }

        SELF push() {
            return factory.apply(push(prefixes.next().prefix()));
        }

        Optional<SELF> next() {
            return nextResponse().map(factory);
        }

        abstract Optional<RESPONSE> nextResponse();

        abstract RESPONSE push(String prefix);

        @Override
        public String toString() {
            return String.format("%s[%s]", getClass().getSimpleName(), prefix);
        }
    }

    static class ObjectsFrame extends BaseFrame<ListObjectsV2Response, ObjectsFrame> {

        ObjectsFrame(S3Finder finder, ListObjectsV2Response objects) {
            super(finder, objects, objects::prefix, objects.commonPrefixes(), objects.contents().stream().map(Atom::of),
                r -> new ObjectsFrame(finder, r));
        }

        @Override
        Optional<ListObjectsV2Response> nextResponse() {
            if (listing.isTruncated()) {
                return Optional.of(finder.listObjects(prefix, listing.nextContinuationToken()));
            }
            return Optional.empty();
        }

        @Override
        ListObjectsV2Response push(String prefix) {
            return finder.listObjects(prefix, listing.nextContinuationToken());
        }
    }

    private static boolean breaksKey(ListObjectVersionsResponse versions) {
        return versions.isTruncated() && versions.nextKeyMarker().equals(versions.keyMarker());
    }

    private static Stream<Atom<?>> atoms(ListObjectVersionsResponse versions) {
        Stream<Atom<?>> result =
            Stream.concat(versions.deleteMarkers().stream().map(Atom::of), versions.versions().stream().map(Atom::of));

        if (versions.isTruncated()) {
            // attempt to to facilitate version ordering by omitting last/next
            // key:

            if (!breaksKey(versions)) {
                final String nextKey = versions.nextKeyMarker();
                result = result.filter(atom -> !nextKey.equals(atom.key()));
            }
        }
        // we think plain objects listing is already sorted by key, so just sort
        // versions atoms here:
        result = result.sorted(Atom.COMPARATOR);

        return result;
    }

    static class VersionsFrame extends BaseFrame<ListObjectVersionsResponse, VersionsFrame> {

        VersionsFrame(S3Finder finder, ListObjectVersionsResponse versions) {
            super(finder, versions, versions::prefix, versions.commonPrefixes(), atoms(versions),
                r -> new VersionsFrame(finder, r));
        }

        @Override
        Optional<ListObjectVersionsResponse> nextResponse() {
            if (listing.isTruncated()) {
                final String vMarker;

                if (breaksKey(listing)) {
                    vMarker = listing.nextVersionIdMarker();
                } else {
                    vMarker = null;
                }
                return Optional.of(finder.listVersions(prefix, listing.nextKeyMarker(), vMarker));
            }
            return Optional.empty();
        }

        @Override
        ListObjectVersionsResponse push(String prefix) {
            return finder.listVersions(prefix, null, null);
        }
    }

    private static Optional<String> determinePrefix(Set<TokenizedPattern> includes) {
        Set<TokenizedPattern> patterns = includes.stream().map(TokenizedPattern::rtrimWildcardTokens)
            .map(path -> path.depth() == 0 ? path.toPattern()
                : new TokenizedPattern(StringUtils.appendIfMissing(path.toString(), File.separator)))
            .collect(Collectors.toSet());

        for (int depth = patterns.stream().mapToInt(TokenizedPattern::depth).min().orElse(0); patterns
            .size() > 1; depth--) {

            final int d = depth;

            patterns = patterns.stream().map(p -> {
                while (p.depth() > d) {
                    p = p.withoutLastToken();
                }
                return p;
            }).collect(Collectors.toSet());
        }
        return patterns.stream().findFirst().map(TokenizedPattern::getPattern);
    }

    private final Deque<BaseFrame<?, ?>> staque = new ArrayDeque<>();
    private final Project project;
    private final S3Client s3;
    private final String bucket;
    private final String delimiter;
    private final boolean caseSensitive;
    private final Pair<Set<TokenizedPattern>, Set<TokenizedPattern>> patterns;

    /**
     * Create a new {@link S3Finder} instance.
     *
     * @param project
     * @param s3
     * @param bucket
     * @param precision
     * @param delimiter
     * @param patterns
     */
    S3Finder(Project project, S3Client s3, String bucket, Precision precision, String delimiter, PatternSet patterns,
        boolean caseSensitive) {
        this.project = project;
        this.s3 = s3;
        this.bucket = bucket;
        this.delimiter = delimiter;
        this.patterns = tokenize(patterns);
        this.caseSensitive = caseSensitive;

        final String prefix;
        if (caseSensitive) {
            prefix =
                determinePrefix(this.patterns.getLeft()).map(p -> p.replace(File.separator, delimiter)).orElse(null);
        } else {
            prefix = null;
        }
        staque.push(root(precision, prefix));
    }

    private Pair<Set<TokenizedPattern>, Set<TokenizedPattern>> tokenize(PatternSet patterns) {
        final Function<String[], Set<TokenizedPattern>> tokenizer = p -> p == null ? Collections.emptySet()
            : Stream.of(p).map(this::path).map(TokenizedPath::toPattern).collect(Collectors.toSet());

        return Pair.of(tokenizer.apply(patterns.getIncludePatterns(project)),
            tokenizer.apply(patterns.getExcludePatterns(project)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Optional<ObjectResource> get() {
        while (!staque.isEmpty()) {
            BaseFrame<?, ?> top = staque.peek();
            while (top.prefixes.hasNext()) {
                top = top.push();
                staque.push(top);
            }
            if (top.contents.hasNext()) {
                return Optional.of(top.contents.next().object(project, s3, bucket));
            }
            staque.pop();
            final Optional<? extends BaseFrame<?, ?>> next = top.next();
            if (next.isPresent()) {
                top = next.get();
                staque.push(top);
            }
        }
        return Optional.empty();
    }

    private BaseFrame<?, ?> root(Precision precision, String prefix) {
        switch (precision) {
        case object:
            return new ObjectsFrame(this, listObjects(prefix, null));
        case version:
            return new VersionsFrame(this, listVersions(prefix, null, null));
        default:
            throw Exceptions.create(IllegalStateException::new, "Unknown %s %s", Precision.class.getSimpleName(),
                precision);
        }
    }

    ListObjectsV2Response listObjects(String prefix, String continuationToken) {
        project.log(String.format("listing %s objects '%s' '%s'", bucket, prefix, continuationToken),
            Project.MSG_DEBUG);

        return s3.listObjectsV2(
            req -> req.bucket(bucket).delimiter(delimiter).prefix(prefix).continuationToken(continuationToken));
    }

    ListObjectVersionsResponse listVersions(String prefix, String keyMarker, String versionMarker) {
        project.log(String.format("listing %s versions '%s' '%s' '%s'", bucket, prefix, keyMarker, versionMarker),
            Project.MSG_DEBUG);

        return s3.listObjectVersions(b -> b.bucket(bucket).delimiter(delimiter).prefix(prefix).keyMarker(keyMarker)
            .versionIdMarker(versionMarker));
    }

    TokenizedPath path(String s) {
        if (s == null) {
            return TokenizedPath.EMPTY_PATH;
        }
        return new TokenizedPath(StringUtils.removeStart(s, delimiter).replace(delimiter, File.separator));
    }

    boolean matchesAny(Collection<TokenizedPattern> tokenizedPatterns, TokenizedPath path) {
        return tokenizedPatterns.stream().anyMatch(pattern -> pattern.matchPath(path, caseSensitive));
    }
}
