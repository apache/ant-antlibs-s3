/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ant.s3;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.resources.comparators.ResourceComparator;
import org.apache.tools.ant.types.resources.selectors.ResourceSelector;
import org.apache.tools.ant.types.selectors.SelectorUtils;
import org.apache.tools.ant.util.StringUtils;

/**
 * S3 {@link ResourceComparator}/{@link ResourceSelector} base/class
 * organization.
 */
public abstract class CompareSelect extends ResourceComparator implements ResourceSelector, ProjectUtils {

    /**
     * {@link CompareSelect} {@link ForStringAttribute}.
     */
    public static abstract class ForStringAttribute extends CompareSelect {

        /**
         * "Match as" strategy.
         */
        public enum MatchAs {
            /**
             * Ant extended glob matching.
             */
            glob {

                @Override
                Predicate<String> matcher(final CompareSelect.ForStringAttribute selector) {
                    return s -> SelectorUtils.matchPath(selector.getSpecification(), s, selector.isCaseSensitive());
                }
            },
            /**
             * Literal matching.
             */
            literal {

                @Override
                Predicate<String> matcher(final CompareSelect.ForStringAttribute selector) {
                    final BiPredicate<String, String> impl =
                        selector.isCaseSensitive() ? String::equals : String::equalsIgnoreCase;
                    return s -> impl.test(selector.getSpecification(), s);
                }
            },
            /**
             * Regex matching.
             */
            regex {

                @Override
                Predicate<String> matcher(final CompareSelect.ForStringAttribute selector) {
                    return Pattern
                        .compile(selector.getSpecification(), selector.isCaseSensitive() ? 0 : Pattern.CASE_INSENSITIVE)
                        .asPredicate();
                }
            };

            /**
             * Render a {@link Predicate} from {@code selector}'s settings.
             *
             * @param selector
             * @return {@link Predicate}
             */
            abstract Predicate<String> matcher(CompareSelect.ForStringAttribute selector);
        }

        private final Comparator<Resource> cmp;
        private final StringBuffer specification = new StringBuffer();
        private MatchAs matchAs = MatchAs.literal;
        private boolean caseSensitive = true;

        private volatile Predicate<String> predicate;

        /**
         * Create a new {@link CompareSelect.ForStringAttribute}.
         *
         * @param project
         *            Ant {@link Project}
         */
        protected ForStringAttribute(final Project project) {
            super(project);
            cmp = comparingS3(this::extractValueFrom);
        }

        /**
         * Add nested text by which the value to compare is set.
         *
         * @param text
         *            to add
         */
        public void addText(final String text) {
            Optional.ofNullable(StringUtils.trimToNull(text)).map(getProject()::replaceProperties).map(String::trim)
                .ifPresent(t -> {
                    specification.append(t);
                    predicate = null;
                });
        }

        /**
         * Get "match as" strategy (default {@link MatchAs#literal}.
         *
         * @return {@link MatchAs}
         */
        public MatchAs getMatchAs() {
            return matchAs;
        }

        /**
         * Set "match as" strategy.
         *
         * @param matchAs
         *            strategy
         */
        public void setMatchAs(final MatchAs matchAs) {
            Exceptions.raiseIf(matchAs == null, buildException(), "@matchas may not be null");

            if (this.matchAs != matchAs) {
                this.matchAs = matchAs;
                predicate = null;
            }
        }

        /**
         * Learn whether matching should be performed in a case-sensitive manner
         * (default {@code true}).
         *
         * @return {@code boolean}
         */
        public final boolean isCaseSensitive() {
            return caseSensitive;
        }

        /**
         * Set whether matching should be performed in a case-sensitive manner.
         *
         * @param caseSensitive
         *            flag
         */
        public void setCaseSensitive(final boolean caseSensitive) {
            if (caseSensitive != this.caseSensitive) {
                this.caseSensitive = caseSensitive;
                predicate = null;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isSelected(final Resource r) {
            return r.asOptional(ObjectResource.class).map(this::extractValueFrom).filter(predicate()).isPresent();
        }

        /**
         * Extract the value to be matched from the specified
         * {@link ObjectResource}.
         *
         * @param obj
         *            owning value
         * @return {@link String}
         */
        protected abstract String extractValueFrom(ObjectResource obj);

        /**
         * {@inheritDoc}
         */
        @Override
        protected int resourceCompare(final Resource foo, final Resource bar) {
            return cmp.compare(foo, bar);
        }

        /**
         * Get the specification against which values will be matched.
         *
         * @return {@link String}
         */
        String getSpecification() {
            return getProject().replaceProperties(specification.toString()).trim();
        }

        private Predicate<String> predicate() {
            if (predicate == null) {
                predicate = matchAs.matcher(this);
            }
            return predicate;
        }
    }

    /**
     * Select {@link ObjectResource} by bucket.
     */
    public static class Bucket extends CompareSelect.ForStringAttribute {

        /**
         * Create a new {@link Bucket} selector.
         *
         * @param project
         *            Ant {@link Project}
         */
        public Bucket(final Project project) {
            super(project);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected String extractValueFrom(final ObjectResource obj) {
            return obj.getBucket();
        }
    }

    /**
     * Select {@link ObjectResource} by key.
     */
    public static class Key extends CompareSelect.ForStringAttribute {

        /**
         * Create a new {@link Key} selector.
         *
         * @param project
         *            Ant {@link Project}
         */
        public Key(final Project project) {
            super(project);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected String extractValueFrom(final ObjectResource obj) {
            return obj.getKey();
        }
    }

    /**
     * Select {@link ObjectResource} by content type.
     */
    public static class ContentType extends CompareSelect.ForStringAttribute {

        /**
         * Create a new {@link ContentType} selector.
         *
         * @param project
         *            Ant {@link Project}
         */
        public ContentType(final Project project) {
            super(project);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected String extractValueFrom(final ObjectResource obj) {
            return obj.getContentType();
        }
    }

    /**
     * Select {@link ObjectResource} by metadata.
     */
    public static class Meta extends CompareSelect.ForStringAttribute {
        private String key;

        /**
         * Create a new {@link Meta} selector.
         *
         * @param project
         *            Ant {@link Project}
         */
        public Meta(final Project project) {
            super(project);
        }

        /**
         * Get the user metadata key to match on.
         *
         * @return {@link String}
         */
        public String getKey() {
            return key;
        }

        /**
         * Set the user metadata key to match on.
         *
         * @param key
         *            to match
         */
        public void setKey(final String key) {
            this.key = key;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected String extractValueFrom(final ObjectResource obj) {
            return obj.getMetadata().get(getKey());
        }
    }

    /**
     * Select {@link ObjectResource} by tag.
     */
    public static class Tag extends CompareSelect.ForStringAttribute {
        private String key;

        /**
         * Create a new {@link Tag} selector.
         *
         * @param project
         *            Ant {@link Project}
         */
        public Tag(final Project project) {
            super(project);
        }

        /**
         * Get the tag key to match on.
         *
         * @return {@link String}
         */
        public String getKey() {
            return key;
        }

        /**
         * Set the tag key to match on.
         *
         * @param key
         *            to match
         */
        public void setKey(final String key) {
            this.key = key;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected String extractValueFrom(final ObjectResource obj) {
            return obj.getTagging().get(getKey());
        }
    }

    /**
     * Select {@link ObjectResource} by version ID.
     */
    public static class VersionId extends CompareSelect.ForStringAttribute {

        /**
         * Create a new {@link VersionId} selector.
         * 
         * @param project
         *            Ant {@link Project}
         */
        public VersionId(Project project) {
            super(project);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected String extractValueFrom(ObjectResource obj) {
            return obj.getVersionId();
        }
    }

    /**
     * {@link CompareSelect} {@link ForBooleanAttribute}.
     */
    public static abstract class ForBooleanAttribute extends CompareSelect {

        private final Predicate<ObjectResource> test;

        /**
         * Create a new {@link CompareSelect.ForBooleanAttribute}.
         * 
         * @param project
         *            Ant {@link Project}
         * @param test
         *            to determine truth
         */
        protected ForBooleanAttribute(Project project, Predicate<ObjectResource> test) {
            super(project);
            this.test = test;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected int resourceCompare(Resource foo, Resource bar) {
            return Boolean.compare(isSelected(foo), isSelected(bar));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isSelected(Resource r) {
            return r.asOptional(ObjectResource.class).filter(test).isPresent();
        }
    }

    /**
     * Select {@link ObjectResource} on the basis of whether it is a delete
     * marker.
     */
    public static class DeleteMarker extends CompareSelect.ForBooleanAttribute {

        /**
         * Create a new {@link DeleteMarker}.
         * 
         * @param project
         *            Ant {@link Project}
         */
        public DeleteMarker(Project project) {
            super(project, ObjectResource::isDeleteMarker);
        }
    }

    /**
     * Select {@link ObjectResource} on the basis of being the latest version.
     */
    public static class Latest extends CompareSelect.ForBooleanAttribute {

        /**
         * Create a new {@link Latest}.
         * 
         * @param project
         *            Ant {@link Project}
         */
        public Latest(Project project) {
            super(project, ObjectResource::isLatest);
        }
    }

    /**
     * Select by object {@link Precision}.
     */
    public static class ByPrecision extends CompareSelect {
        private static final Comparator<Resource> COMPARATOR = comparingS3(ObjectResource::getPrecision);

        private Precision precision;

        /**
         * Create a new {@link ByPrecision}.
         * 
         * @param project
         *            Ant {@link Project}
         */
        public ByPrecision(Project project) {
            super(project);
        }

        /**
         * Get the precision.
         * 
         * @return Precision
         */
        public Precision getPrecision() {
            return precision;
        }

        /**
         * Set the precision.
         * 
         * @param precision
         *            {@link Precision}
         */
        public void setPrecision(Precision precision) {
            this.precision = precision;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isSelected(Resource r) {
            Exceptions.raiseIf(getPrecision() == null, IllegalStateException::new, "@precision not specified");
            return r.asOptional(ObjectResource.class).map(ObjectResource::getPrecision).filter(p -> p == getPrecision())
                .isPresent();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected int resourceCompare(Resource foo, Resource bar) {
            return COMPARATOR.compare(foo, bar);
        }
    }

    private static final Function<Resource, ObjectResource> S3O = r -> r.as(ObjectResource.class);

    private static final <T extends Comparable<T>> Comparator<Resource> comparingS3(
        Function<ObjectResource, ? extends T> xform) {
        return Comparator.nullsFirst(Comparator.comparing(S3O.andThen(r -> r == null ? null : xform.apply(r))));
    }

    /**
     * Create a {@link CompareSelect} instance.
     * 
     * @param project
     *            Ant {@link Project}
     */
    protected CompareSelect(Project project) {
        setProject(project);
    }
}