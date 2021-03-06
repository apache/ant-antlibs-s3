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
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiFunction;
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

        static Atom<String> asDir(S3Object o) {
            return new Atom<String>(o.key(), o::key, () -> null, () -> true, o::lastModified, ObjectResource::ofPrefix);
        }

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
        final TokenizedPath path;
        final Set<TokenizedPattern> includes;
        final Set<TokenizedPattern> excludes;
        final int maxDepth;
        final Iterator<CommonPrefix> prefixes;
        final Iterator<Atom<?>> contents;
        final BiFunction<S3Finder, RESPONSE, SELF> factory;

        protected BaseFrame(S3Finder finder, RESPONSE listing, Supplier<String> prefix, List<CommonPrefix> prefixes,
            Stream<Atom<?>> contents, BiFunction<S3Finder, RESPONSE, SELF> factory) {
            this.listing = listing;
            this.prefix = prefix.get();
            this.finder = finder;

            path = finder.path(prefix.get());
            includes = finder.patterns.getLeft();
            excludes = finder.patterns.getRight();
            maxDepth = includes.stream().mapToInt(
                include -> include.containsPattern(SelectorUtils.DEEP_TREE_MATCH) ? Integer.MAX_VALUE : include.depth())
                .max().orElse(Integer.MAX_VALUE);

            if (includes.isEmpty() && excludes.isEmpty()) {
                this.prefixes = prefixes.iterator();
            } else {
                final int recurseDepth = path.depth() + (finder.includePrefixes ? 0 : 1);
                this.prefixes = maxDepth > recurseDepth ? prefixes.stream().filter(this::allowPrefix).iterator()
                    : Collections.emptyIterator();
            }
            final boolean canMatch = includes.isEmpty() || includes.stream().anyMatch(include -> {
                if (include.containsPattern(SelectorUtils.DEEP_TREE_MATCH)) {
                    return path.depth() > include.rtrimWildcardTokens().depth();
                }
                if (path.depth() == include.depth() && finder.includePrefixes) {
                    return true;
                }
                return include.depth() - path.depth() == 1;
            });

            this.contents = canMatch ? contents.filter(this::allow).iterator() : Collections.emptyIterator();
            this.factory = factory;
        }

        final boolean allowPrefix(CommonPrefix prefix) {
            final TokenizedPath asPath = finder.path(prefix.prefix());
            if (maxDepth == asPath.depth()
                && excludes.stream().anyMatch(p -> p.matchPath(asPath, finder.caseSensitive))) {
                return false;
            }
            return includes.stream().anyMatch(p -> p.matchStartOf(asPath, finder.caseSensitive));
        }

        final boolean allow(Atom<?> atom) {
            final TokenizedPath path = finder.path(atom.key());
            final boolean included = includes.isEmpty() || finder.matchesAny(includes, path);
            return included && !finder.matchesAny(finder.patterns.getRight(), path);
        }

        final SELF push() {
            final String nextPrefix = prefixes.next().prefix();

            OptionalInt maxKeys = OptionalInt.empty();

            if (maxDepth - path.depth() == 1 && finder.includePrefixes) {
                final TokenizedPath nextPath = finder.path(nextPrefix);
                if (includes.stream()
                    .allMatch(include -> include.depth() > 0
                        && !SelectorUtils.hasWildcards(SelectorUtils.tokenizePath(include.getPattern()).lastElement())
                        && include.matchPath(nextPath, finder.caseSensitive))) {
                    // looks like we're targeting the prefix; limit search appropriately:
                    maxKeys = OptionalInt.of(1);
                }
            }
            return factory.apply(finder, push(nextPrefix, maxKeys));
        }

        final Optional<SELF> next() {
            if (maxDepth == path.depth()) {
                // only possible match was prefix, which we should have found in the first listing
                return Optional.empty();
            }
            return nextResponse().map(r -> factory.apply(finder, r));
        }

        abstract Optional<RESPONSE> nextResponse();

        abstract RESPONSE push(String prefix, OptionalInt maxKeys);

        @Override
        public final String toString() {
            return String.format("%s[%s]", getClass().getSimpleName(), prefix);
        }
    }

    private static Stream<Atom<?>> atoms(ListObjectsV2Response objects, boolean includePrefixes) {
        return objects.contents().stream().<Atom<?>> map(o -> {
            if (o.key().equals(objects.prefix())) {
                return includePrefixes ? Atom.asDir(o) : null;
            }
            return Atom.of(o);
        }).filter(Objects::nonNull);
    }

    static class ObjectsFrame extends BaseFrame<ListObjectsV2Response, ObjectsFrame> {

        ObjectsFrame(S3Finder finder, ListObjectsV2Response objects) {
            super(finder, objects, objects::prefix, objects.commonPrefixes(), atoms(objects, finder.includePrefixes),
                ObjectsFrame::new);
        }

        @Override
        Optional<ListObjectsV2Response> nextResponse() {
            if (listing.isTruncated()) {
                return Optional.of(finder.listObjects(prefix, listing.nextContinuationToken(), OptionalInt.empty()));
            }
            return Optional.empty();
        }

        @Override
        ListObjectsV2Response push(String prefix, OptionalInt maxKeys) {
            return finder.listObjects(prefix, listing.nextContinuationToken(), maxKeys);
        }
    }

    private static boolean breaksKey(ListObjectVersionsResponse versions) {
        return versions.isTruncated() && versions.nextKeyMarker().equals(versions.keyMarker());
    }

    private static Stream<Atom<?>> atoms(ListObjectVersionsResponse versions, boolean includePrefixes) {
        Stream<Atom<?>> result =
            Stream.concat(versions.deleteMarkers().stream().map(Atom::of), versions.versions().stream().map(Atom::of));

        if (!includePrefixes) {
            result = result.filter(a -> !a.key().equals(versions.prefix()));
        }
        if (versions.isTruncated()) {
            // attempt to facilitate version ordering by omitting last/next key:
            if (!breaksKey(versions)) {
                final String nextKey = versions.nextKeyMarker();
                result = result.filter(atom -> !nextKey.equals(atom.key()));
            }
        }
        // we think plain objects listing is already sorted by key, so just sort versions atoms here:
        result = result.sorted(Atom.COMPARATOR);

        return result;
    }

    static class VersionsFrame extends BaseFrame<ListObjectVersionsResponse, VersionsFrame> {

        VersionsFrame(S3Finder finder, ListObjectVersionsResponse versions) {
            super(finder, versions, versions::prefix, versions.commonPrefixes(),
                atoms(versions, finder.includePrefixes), VersionsFrame::new);
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
                return Optional.of(finder.listVersions(prefix, listing.nextKeyMarker(), vMarker, OptionalInt.empty()));
            }
            return Optional.empty();
        }

        @Override
        ListObjectVersionsResponse push(String prefix, OptionalInt maxKeys) {
            return finder.listVersions(prefix, null, null, maxKeys);
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
    private final boolean includePrefixes;

    /**
     * Create a new {@link S3Finder} instance.
     *
     * @param project
     * @param s3
     * @param bucket
     * @param precision
     * @param delimiter
     * @param patterns
     * @param includePrefixes
     */
    S3Finder(Project project, S3Client s3, String bucket, Precision precision, String delimiter, PatternSet patterns,
        boolean caseSensitive, boolean includePrefixes) {
        this.project = project;
        this.s3 = s3;
        this.bucket = bucket;
        this.delimiter = delimiter;
        this.patterns = tokenize(patterns);
        this.caseSensitive = caseSensitive;
        this.includePrefixes = includePrefixes;

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
            return new ObjectsFrame(this, listObjects(prefix, null, OptionalInt.empty()));
        case version:
            return new VersionsFrame(this, listVersions(prefix, null, null, OptionalInt.empty()));
        default:
            throw Exceptions.create(IllegalStateException::new, "Unknown %s %s", Precision.class.getSimpleName(),
                precision);
        }
    }

    ListObjectsV2Response listObjects(String prefix, String continuationToken, OptionalInt maxKeys) {
        project.log(String.format("listing %s objects '%s' '%s'", bucket, prefix, continuationToken),
            Project.MSG_DEBUG);

        return s3.listObjectsV2(
            req -> {
                req.bucket(bucket).delimiter(delimiter).prefix(prefix).continuationToken(continuationToken);
                maxKeys.ifPresent(req::maxKeys);
            });
    }

    ListObjectVersionsResponse listVersions(String prefix, String keyMarker, String versionMarker, OptionalInt maxKeys) {
        project.log(String.format("listing %s versions '%s' '%s' '%s'", bucket, prefix, keyMarker, versionMarker),
            Project.MSG_DEBUG);

        return s3.listObjectVersions(b -> {
            b.bucket(bucket).delimiter(delimiter).prefix(prefix).keyMarker(keyMarker).versionIdMarker(versionMarker);
            maxKeys.ifPresent(b::maxKeys);
        });
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
