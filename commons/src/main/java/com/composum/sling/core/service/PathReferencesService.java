package com.composum.sling.core.service;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * a service to search for resources which are referencing a given path
 */
public interface PathReferencesService {

    /**
     * an options set to specify the parameters of the query to search path references;
     * build the options for the reference search like this:
     * <pre>
     *     final Options options = new Options()
     *             .basePath('/content/mysite')
     *             .propertyName('text')
     *             .includeChildren(true)
     *             .useRelativePath(true)
     *             .findRichText(true);
     * </pre>
     */
    class Options {

        protected String basePath = "";
        protected String primaryType = null;
        protected String resourceName = null;
        protected String resourceType = null;
        protected String propertyName = null;
        protected boolean useAbsolutePath = true;
        protected boolean useRelativePath = false;
        protected boolean includeChildren = false;
        protected boolean childrenOnly = false;
        protected boolean findRichText = false;

        @Nonnull
        public String getBasePath() {
            return basePath;
        }

        @Nullable
        public String getPrimaryType() {
            return primaryType;
        }

        public String getResourceName() {
            return resourceName;
        }

        public String getResourceType() {
            return resourceType;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public boolean isUseAbsolutePath() {
            return useAbsolutePath;
        }

        public boolean isUseRelativePath() {
            return useRelativePath;
        }

        public boolean isIncludeChildren() {
            return includeChildren;
        }

        public boolean isChildrenOnly() {
            return childrenOnly;
        }

        public boolean isFindRichText() {
            return findRichText;
        }

        /**
         * the 'base path' is used to complete a relative path or to determine the relative variant of an absolute path;
         * if in a referer search a relative path is searched this base path is prepended to build the absolute path;
         * if an absolute path is searched the base path is used to build the relative variant of the absolute path if
         * this absolutre path starts with the base path
         */
        public Options basePath(@Nonnull final String path) {
            basePath = path;
            while (basePath.endsWith("/")) {
                basePath = basePath.substring(0, basePath.length() - 1);
            }
            return this;
        }

        /**
         * the primary type of the searched referrers; maybe 'null'
         */
        public Options primaryType(@Nonnull final String name) {
            primaryType = name;
            return this;
        }

        /**
         * the node name of the searched referrers; maybe 'null'
         */
        public Options resourceName(@Nonnull final String name) {
            resourceName = name;
            return this;
        }

        /**
         * the Sling resource type of the searched referrers; maybe 'null'
         */
        public Options resourceType(@Nonnull final String name) {
            resourceType = name;
            return this;
        }

        /**
         * the name of the property which is storing the reference; maybe 'null'
         */
        public Options propertyName(@Nonnull final String name) {
            propertyName = "*".equals(name) ? "" : name;
            if (propertyName.startsWith("@")) {
                propertyName = propertyName.substring(1);
            }
            return this;
        }

        /**
         * if 'true' the absolute path variant of the searched path is checked
         */
        public Options useAbsolutePath(final boolean flag) {
            useAbsolutePath = flag;
            return this;
        }

        /**
         * if 'true' the relative path variant of the searched path is checked
         */
        public Options useRelativePath(final boolean flag) {
            useRelativePath = flag;
            return this;
        }

        /**
         * if 'true' all resources wich are referencing paths starting with the searched path are found
         */
        public Options includeChildren(final boolean flag) {
            includeChildren = flag;
            return this;
        }


        /**
         * if 'true' a search for subpages of the specified path to search is executed
         */
        public Options childrenOnly(final boolean flag) {
            childrenOnly = flag;
            return this;
        }

        /**
         * if 'true' the path is searched also as part of rich text properties (contains a pattern like '="{path}"')
         */
        public Options findRichText(final boolean flag) {
            findRichText = flag;
            return this;
        }
    }

    /**
     * a found resource which is referencing the searched path
     */
    interface Hit {

        /**
         * a property wich is referencing the searched path
         */
        interface Property {

            /**
             * @return the name of the matching property
             */
            @Nonnull
            String getName();

            /**
             * @return the first (the single one) matching value
             */
            @Nullable
            String getValue();

            /**
             * @return the set of matching values, maybe more than one if the property is a multi value property
             */
            @Nonnull
            List<String> getValues();

            /**
             * @return 'true', if one of the matching values seems to be a rich text value
             */
            boolean isRichText();
        }

        /**
         * @return the resource which is referencing the path
         */
        @Nonnull
        Resource getResource();

        /**
         * @return the set of the names of the matching properties
         */
        @Nonnull
        Set<String> getPropertyNames();

        /**
         * @param propertyName the name of the matching property
         * @return the value of the specified property
         */
        @Nullable
        Property getProperty(@Nonnull String propertyName);

        /**
         * @return the first matching property (one of the matching properties)
         */
        @Nullable
        Property getProperty();

        /**
         * @return the value of the first matching property (one of the matching values)
         */
        @Nullable
        String getValue();

        /**
         * @return 'true', if the first matching value seems to be a rich text value
         */
        boolean isRichText();
    }

    /**
     * @param resolver   the resolver (the user/session context)
     * @param options    the options to create the search query
     * @param searchRoot the repository root path of the area to browse
     * @param path       the path for which the referers have to be found; maybe with wildcards ('*'/'%')
     * @return the iterator to traverse the found referrers
     */
    @Nonnull
    Iterator<Hit> findReferrers(@Nonnull ResourceResolver resolver, @Nonnull Options options,
                                @Nonnull String searchRoot, @Nonnull String path);
}
