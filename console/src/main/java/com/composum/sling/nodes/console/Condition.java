package com.composum.sling.nodes.console;

import com.composum.sling.core.BeanContext;
import com.composum.sling.core.util.ResourceUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.version.VersionManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface Condition {

    String KEY_RESOURCE_TYPE = "resourceType";
    String KEY_PRIMARY_TYPE = "primaryType";
    String KEY_VERSIONABLE = "versionable";
    String KEY_ACL = "acl";
    String KEY_JCR = "jcr";
    String KEY_CLASS = "class";

    /**
     * check the configured condition for the given resource
     */
    boolean accept(@Nonnull BeanContext context, @Nonnull Resource resource);

    abstract class Set implements Condition {

        protected final List<Condition> conditions = new ArrayList<>();

        protected Set(Condition... conditions) {
            this.conditions.addAll(Arrays.asList(conditions));
        }

        protected static Condition fromProperties(@Nonnull final Set set, @Nonnull final Map<String, Object> properties) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                Condition condition = DEFAULT.getCondition(entry.getKey(), entry.getValue());
                if (condition != null) {
                    set.conditions.add(condition);
                }
            }
            return set;
        }
    }

    class And extends Set {

        public And(Condition... conditions) {
            super(conditions);
        }

        @Nullable
        public static Condition fromResource(@Nullable final Resource resource) {
            return resource != null ? fromProperties(resource.getValueMap()) : null;
        }

        @Nonnull
        public static Condition fromProperties(@Nonnull final Map<String, Object> properties) {
            return fromProperties(new And(), properties);
        }

        @Override
        public boolean accept(@Nonnull BeanContext context, @Nonnull Resource resource) {
            for (Condition condition : conditions) {
                if (!condition.accept(context, resource)) {
                    return false;
                }
            }
            return true;
        }
    }

    class Or extends Set {

        public Or(Condition... conditions) {
            super(conditions);
        }

        @Nullable
        public static Condition fromResource(@Nullable final Resource resource) {
            return resource != null ? fromProperties(resource.getValueMap()) : null;
        }

        public static Condition fromProperties(@Nonnull final Map<String, Object> properties) {
            return fromProperties(new Or(), properties);
        }

        @Override
        public boolean accept(@Nonnull BeanContext context, @Nonnull Resource resource) {
            for (Condition condition : conditions) {
                if (condition.accept(context, resource)) {
                    return true;
                }
            }
            return conditions.isEmpty();
        }
    }

    // implementations

    /**
     * check the availability of a class as a precondition for a console module
     */
    class ClassAvailability implements Condition {

        public static final Logger LOG = LoggerFactory.getLogger(ClassAvailability.class);

        protected final String pattern;

        public ClassAvailability(@Nonnull final String pattern) {
            this.pattern = pattern;
        }

        @Override
        public boolean accept(@Nonnull final BeanContext context, @Nonnull final Resource resource) {
            boolean classAvailable = false;
            try {
                context.getType(pattern);
                classAvailable = true;
            } catch (Exception ex) {
                LOG.warn("precondition check failed: " + ex.getMessage());
            }
            return classAvailable;
        }
    }

    /**
     * checks that the resource is a JCR resource
     */
    class JcrResource implements Condition {

        @Override
        public boolean accept(@Nonnull final BeanContext context, @Nonnull final Resource resource) {
            return !ResourceUtil.isSyntheticResource(resource) && resource.adaptTo(Node.class) != null;
        }
    }

    /**
     * checks the ability to manage ACLs at the resource
     */
    class CanHaveAcl extends JcrResource {
    }

    /**
     * checks the ability to manage versions at the resource
     */
    class Versionable extends JcrResource {

        public static final Logger LOG = LoggerFactory.getLogger(Versionable.class);

        @Override
        public boolean accept(@Nonnull final BeanContext context, @Nonnull final Resource resource) {
            if (super.accept(context, resource)) {
                final ResourceResolver resolver = resource.getResourceResolver();
                final Session session = resolver.adaptTo(Session.class);
                if (session != null) {
                    try {
                        final VersionManager versionManager = session.getWorkspace().getVersionManager();
                        versionManager.getBaseVersion(resource.getPath());
                        return true;
                    } catch (UnsupportedRepositoryOperationException ignore) {
                        // OK - node is simply not versionable.
                    } catch (RepositoryException ex) {
                        LOG.error("unexpected exception checking '" + resource.getPath() + "'", ex);
                    }
                }
            }
            return false;
        }
    }

    /**
     * checks that the resources primary type matches the pattern
     */
    class PrimaryType implements Condition {

        protected final String pattern;

        public PrimaryType(@Nonnull final String pattern) {
            this.pattern = pattern;
        }

        @Override
        public boolean accept(@Nonnull final BeanContext context, @Nonnull final Resource resource) {
            return StringUtils.isBlank(pattern)
                    || pattern.equals(resource.getValueMap().get(JcrConstants.JCR_PRIMARYTYPE, String.class));
        }
    }

    /**
     * checks that the resources primary type matches the pattern
     */
    class ResourceType implements Condition {

        protected final String pattern;

        public ResourceType(@Nonnull final String pattern) {
            this.pattern = pattern;
        }

        @Override
        public boolean accept(@Nonnull final BeanContext context, @Nonnull final Resource resource) {
            return StringUtils.isBlank(pattern) || resource.isResourceType(pattern);
        }
    }

    Condition VERSIONABLE = new Versionable();
    Condition CAN_HAVE_ACL = new CanHaveAcl();
    Condition JCR_RESOURCE = new JcrResource();

    // factory

    interface Factory {

        @Nullable
        Condition getCondition(@Nonnull String key, @Nullable Object pattern);
    }

    class Options implements Factory {

        private final Map<String, Factory> factorySet = new HashMap<>();

        @Override
        @Nullable
        public Condition getCondition(@Nonnull final String key, @Nullable final Object pattern) {
            final Factory factory = factorySet.get(key);
            return factory != null ? factory.getCondition(key.toLowerCase(), pattern) : null;
        }

        @Nullable
        public Condition getCondition(@Nullable final String rule) {
            if (StringUtils.isNotBlank(rule)) {
                final String[] parts = StringUtils.split(rule, ":", 2);
                return getCondition(parts[0], parts.length > 1 ? parts[1] : null);
            }
            return null;
        }

        @Nonnull
        public Options addFactory(@Nonnull final String key, @Nonnull final Factory factory) {
            factorySet.put(key, factory);
            return this;
        }
    }

    Options DEFAULT = new Options()
            .addFactory(KEY_RESOURCE_TYPE, (key, pattern) ->
                    pattern instanceof String ? new ResourceType((String) pattern) : null)
            .addFactory(KEY_PRIMARY_TYPE, (key, pattern) ->
                    pattern instanceof String ? new PrimaryType((String) pattern) : null)
            .addFactory(KEY_VERSIONABLE, (key, pattern) -> VERSIONABLE)
            .addFactory(KEY_ACL, (key, pattern) -> CAN_HAVE_ACL)
            .addFactory(KEY_JCR, (key, pattern) -> JCR_RESOURCE)
            .addFactory(KEY_CLASS, (key, pattern) ->
                    pattern instanceof String ? new ClassAvailability((String) pattern) : null);
}