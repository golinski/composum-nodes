package com.composum.sling.core.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.composum.sling.core.util.LinkUtil.adjustMappedUrl;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * A Sling URL parser / builder class supporting the
 * <a href="https://sling.apache.org/documentation/the-sling-engine/url-decomposition.html">Sling URL decomposition</a>
 * (and composition / modification by using the builder methods) and provides builder methods to change e.g. selectors and extension,
 * but can also represent other URL types.
 * It is meant to represent every user input without failing - if it's not a known URL scheme and thus cannot be parsed
 * it'll just return the input unchanged, and the modification methods fail silently.
 */
@SuppressWarnings({"unused", "ParameterHidesMemberVariable", "UnusedReturnValue"})
public class SlingUrl {

    /**
     * Characterizes the type of the URL.
     */
    public enum UrlType {
        /**
         * Sling URL (or other URL, since these cannot necessarily be distinguished).
         * It can have a scheme ({@link #isExternal()}) or no scheme.
         */
        URL,
        /**
         * A relative URL - has no scheme but represents a (relative) path which possibly has selectors, extension, suffix etc.
         */
        RELATIVE,
        /**
         * Special URL format that doesn't have a path, like mailto: or tel: .
         * Since it cannot be parsed, the get-Methods mostly do not provide a value here, except {@link #getUrl()}.
         */
        SPECIAL,
        /**
         * Anything else that cannot be represented here - including invalid input.
         * Since it cannot be parsed, the get-Methods mostly do not provide a value here, except {@link #getUrl()}.
         */
        OTHER
    }

    public static final LinkCodec CODEC = new LinkCodec();

    protected static final Pattern SCHEME_PATTERN = Pattern.compile("(?i:(?<scheme>[a-zA-Z-]+):)");
    protected static final Pattern USERNAMEPASSWORD = Pattern.compile("((?<username>[^:@/]+)(:(?<password>[^:@/]+))?@)");

    /**
     * Regex matching various variants of Sling-URLs.
     * <p>
     * About the implementation: A suffix can only exist if there is a
     * <p>
     * Debug e.g. with http://www.softlion.com/webtools/regexptest/ .
     */
    protected static final Pattern URL_PATTERN = Pattern.compile("" +
            SCHEME_PATTERN.pattern() + "?" +
            "(?<hostandport>//" + USERNAMEPASSWORD + "?((?<host>[^/:]+)(:(?<port>[0-9]+))?)?)?" +
            "(?<pathnoext>(/([^/.?]+|\\.\\.))*/)" +
            "(" +
            "(?<filenoext>[^/.?]+)" +
            "((?<extensions>(\\.[^./?#]+)+)(?<suffix>/[^?#]*)?)?" +
            ")?" +
            "(?<query>\\?[^?#]*)?" +
            "(?<fragment>#.*)?"
    );

    protected static final Pattern ABSOLUTE_PATH_PATTERN = Pattern.compile("" +
            "(?<pathnoext>(/([^/.?]+|\\.\\.))*/)" +
            "(" +
            "(?<filenoext>[^/.?]+)" +
            "((?<extensions>(\\.[^./?#]+)+)(?<suffix>/[^?#]*)?)?" +
            ")?" +
            "(?<query>\\?[^?#]*)?" +
            "(?<fragment>#.*)?"
    );

    protected static final Pattern RELATIVE_PATH_PATTERN = Pattern.compile("" +
            "(?<pathnoext>([^/.?]+|\\.\\.)*/)?" +
            "(" +
            "(?<filenoext>[^/.?]+)" +
            "((?<extensions>(\\.[^./?#]+)+)(?<suffix>/[^?#]*)?)?" +
            ")?" +
            "(?<query>\\?[^?#]*)?" +
            "(?<fragment>#.*)?"
    );

    public static final Pattern HTTP_SCHEME = Pattern.compile("^https?$", Pattern.CASE_INSENSITIVE);
    public static final Pattern SPECIAL_SCHEME = Pattern.compile("^(mailto|tel|fax)$", Pattern.CASE_INSENSITIVE);

    protected UrlType type;
    protected String scheme;
    protected String username;
    protected String password;
    protected String host;
    protected Integer port;
    protected String contextPath;
    /**
     * Contains the path inclusive leading and trailing / , not the file/resource itself: for /a/b/c it's /a/b/ .
     * Emptly for relative paths / unparseable stuff.
     */
    protected String path;
    /**
     * The filename; if the url could not be parsed ({@link UrlType#SPECIAL} or {@link UrlType#OTHER}), this contains the url without the scheme.
     */
    protected String name;
    protected final List<String> selectors = new ArrayList<>();
    protected String extension;
    protected String suffix;
    protected final LinkedHashMap<String, List<String>> parameters = new LinkedHashMap<>();
    protected String fragment;

    private transient String url;

    protected transient String resourcePath;
    protected transient Resource resource;

    protected final SlingHttpServletRequest request;
    protected final LinkMapper linkMapper;

    public SlingUrl(@Nonnull final SlingHttpServletRequest request,
                    @Nonnull final Resource resource) {
        this(request, resource, null, null);
    }

    public SlingUrl(@Nonnull final SlingHttpServletRequest request,
                    @Nonnull final Resource resource, @Nullable final String extension) {
        this(request, resource, null, extension,
                null, null, true, getLinkMapper(request, null));
    }

    public SlingUrl(@Nonnull final SlingHttpServletRequest request,
                    @Nonnull final Resource resource,
                    @Nullable final String selectors, @Nullable final String extension) {
        this(request, resource, selectors, extension,
                null, null, true, getLinkMapper(request, null));
    }

    public SlingUrl(@Nonnull final SlingHttpServletRequest request,
                    @Nonnull final Resource resource,
                    @Nullable final String selectors, @Nullable final String extension,
                    @Nullable final String suffix) {
        this(request, resource, selectors, extension, suffix,
                null, true, getLinkMapper(request, null));
    }

    public SlingUrl(@Nonnull final SlingHttpServletRequest request,
                    @Nonnull final Resource resource,
                    @Nullable final String selectors, @Nullable final String extension,
                    @Nullable final String suffix,
                    @Nullable final String parameterString) {
        this(request, resource, selectors, extension, suffix, parameterString,
                true, getLinkMapper(request, null));
    }

    public SlingUrl(@Nonnull final SlingHttpServletRequest request,
                    @Nonnull final Resource resource,
                    @Nullable final String selectors, @Nullable final String extension,
                    @Nullable final String suffix,
                    @Nullable final String parameterString, boolean decodeParameters,
                    @Nullable LinkMapper linkMapper) {
        this.request = Objects.requireNonNull(request, "request required");
        this.linkMapper = linkMapper;
        this.resource = resource;
        this.resourcePath = resource.getPath();
        this.name = StringUtils.substringAfterLast(resourcePath, "/");
        this.path = resourcePath.substring(0, resourcePath.length() - name.length());
        this.type = UrlType.URL;
        setSelectors(selectors);
        setExtension(extension);
        setSuffix(suffix);
        setParameters(parameterString, decodeParameters);
    }

    /**
     * Parses the url without URL-decoding it.
     */
    public SlingUrl(@Nonnull final SlingHttpServletRequest request, @Nonnull final String url) {
        this(request, url, false);
    }

    /**
     * Parses the url, possibly URL-decoding it when decode = true.
     */
    public SlingUrl(@Nonnull final SlingHttpServletRequest request, @Nonnull final String url,
                    boolean decode) {
        this(request, url, decode, getLinkMapper(request, null));
    }

    /**
     * Parses the url, possibly URL-decoding it when decode = true. A linkmapper can be given if the url is a path.
     */
    public SlingUrl(@Nonnull final SlingHttpServletRequest request, @Nonnull final String url,
                    boolean decode, @Nullable LinkMapper linkMapper) {
        this.request = Objects.requireNonNull(request, "request required");
        this.linkMapper = linkMapper;
        parseUrl(url, decode);
    }

    @Nonnull
    public SlingHttpServletRequest getRequest() {
        return request;
    }

    /**
     * An external URL: we assume it's external when a scheme is set.
     */
    public boolean isExternal() {
        return isNotBlank(scheme);
    }

    /**
     * For internal urls the path to the rendered resource. The path to {@link #getResource()} if that exists.
     */
    @Nullable
    public String getResourcePath() {
        if (resourcePath == null && !isExternal() && type == UrlType.URL) { // FIXME(hps,05.06.20) only for http, too!
            ResourceResolver resolver = request.getResourceResolver();
            resourcePath = defaultString(path) + name;
            if (isNotBlank(extension)) {
                resourcePath += '.' + extension;
            }
            resource = resolver.getResource(resourcePath);
            if (resource == null) {
                resourcePath = defaultString(path) + name;
                resource = resolver.getResource(resourcePath);
            }
        }
        return resourcePath;
    }

    /**
     * For internal urls: if this is a path to a resource, this returns it (suffix, selectors and parameters ignored, if present).
     */
    @Nullable
    public Resource getResource() {
        getResourcePath(); // possibly initialize
        return resource;
    }

    /**
     * If an internal path starts with the {@link HttpServletRequest#getContextPath()}, this contains the context path,
     * and the context path is removed from {@link #getPath()}.
     */
    @Nullable
    public String getContextPath() { // FIXME(hps,05.06.20) setter for contextPath?
        return isNotBlank(contextPath) ? contextPath : request.getContextPath();
    }

    /**
     * The path to the file, including the filename. Does not include the extension, selectors etc.
     */
    public String getPath() {
        return defaultString(path) + name;
    }

    @Nonnull
    List<String> getSelectors() {
        return selectors;
    }

    public SlingUrl selector(@Nullable String... value) {
        return addSelector(value);
    }

    public SlingUrl addSelector(@Nullable String... value) {
        clearTransients();
        if (value != null) {
            selectors.addAll(Arrays.asList(value));
        }
        return this;
    }

    public SlingUrl setSelector(String... value) {
        clearSelectors();
        addSelector(value);
        return this;
    }

    public SlingUrl selectors(@Nullable final String selectors) {
        return setSelectors(selectors);
    }

    /**
     * Sets the selectors to the given string - can contain multiple selectors separated with period.
     */
    public SlingUrl setSelectors(@Nullable final String selectors) {
        clearSelectors();
        if (selectors != null) {
            for (String sel : StringUtils.split(selectors, '.')) {
                if (isNotBlank(sel)) {
                    this.selectors.add(sel);
                }
            }
        }
        return this;
    }

    public SlingUrl removeSelector(String... value) {
        clearTransients();
        if (value != null) {
            for (String val : value) {
                selectors.remove(val);
            }
        }
        return this;
    }

    public SlingUrl clearSelectors() {
        clearTransients();
        selectors.clear();
        return this;
    }

    @Nullable
    public String getExtension() {
        return extension;
    }

    public SlingUrl extension(@Nullable final String extension) {
        return setExtension(extension);
    }

    public SlingUrl setExtension(@Nullable String extension) {
        if (extension != null) {
            int dot = extension.lastIndexOf(".");
            if (dot >= 0) {
                extension = extension.substring(dot + 1);
            }
        }
        this.extension = extension;
        clearTransients();
        return this;
    }

    @Nullable
    public String getSuffix() {
        return suffix;
    }

    public SlingUrl suffix(@Nullable final Resource resource) {
        return setSuffix(resource != null ? resource.getPath() : null);
    }

    public SlingUrl suffix(@Nullable final Resource resource, @Nullable final String extension) {
        return setSuffix(resource != null ? resource.getPath()
                + (isNotBlank(extension) ? ("." + extension) : "") : null);
    }

    public SlingUrl suffix(@Nullable final String suffix) {
        return setSuffix(suffix);
    }

    public SlingUrl setSuffix(@Nullable final String suffix) {
        this.suffix = suffix;
        clearTransients();
        return this;
    }

    /**
     * Unmodifiable map of the contained parameters.
     * <p>
     * This is unmodifiable since otherwise we would have to trust the user to call {@link #clearTransients()} on every change.
     */
    @Nonnull
    Map<String, List<String>> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }

    public SlingUrl parameter(String name, String... value) {
        return addParameter(name, value);
    }

    public SlingUrl addParameter(String name, String... value) {
        clearTransients();
        List<String> values = parameters.computeIfAbsent(name, k -> new ArrayList<>());
        if (value != null) {
            for (String val : value) {
                if (val != null) {
                    values.add(val);
                }
            }
        }
        return this;
    }

    /**
     * Adds parameters parsed from an HTTP query string.
     */
    public SlingUrl addParameters(String parameterString, boolean decode) {
        if (parameterString != null) {
            parseParameters(parameterString, decode);
        }
        return this;
    }

    public SlingUrl setParameter(String name, String... value) {
        removeParameter(name);
        addParameter(name, value);
        return this;
    }

    public SlingUrl parameters(String parameterString) {
        return setParameters(parameterString, true);
    }

    public SlingUrl setParameters(String parameterString, boolean decode) {
        clearParameters();
        addParameters(parameterString, decode);
        return this;
    }

    public SlingUrl removeParameter(String name) {
        clearTransients();
        parameters.remove(name);
        return this;
    }

    public SlingUrl clearParameters() {
        clearTransients();
        parameters.clear();
        return this;
    }

    /**
     * The fragment part of the URL. Caution: this isn't available on all types.
     */
    @Nullable
    public String getFragment() {
        return fragment;
    }

    /**
     * Sets the fragment. Caution: this isn't available on all types.
     * Same as {@link #fragment(String)}.
     *
     * @return this for builder style chaining
     */
    @Nonnull
    public SlingUrl setFragment(@Nullable String fragment) {
        return fragment(fragment);
    }

    /**
     * Sets the fragment part of the URL. Caution: this isn't available on all types.
     * Same as {@link #setFragment(String)}.
     *
     * @return this for builder style chaining
     */
    @Nonnull
    public SlingUrl fragment(@Nullable String fragment) {
        this.fragment = fragment;
        clearTransients();
        return this;
    }


    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    /**
     * Just compares {@link #toString()} - that is, the url.
     */
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object other) {
        return toString().equals(other.toString());
    }

    /**
     * Same as {@link #getUrl()}.
     */
    @Override
    public String toString() {
        return getUrl();
    }

    /**
     * Builds the URL from its parts saved in the builder.
     */
    public String getUrl() {
        if (url == null) {
            url = buildUrl();
        }
        return url;
    }

    /**
     * Internal reset method for the calculated transient values like the URL, called when anything changes.
     */
    protected void clearTransients() {
        this.url = null;
        this.resource = null;
        this.resourcePath = null;
    }

    /**
     * The scheme part of an URL. Caution: this isn't available on all types.
     */
    @Nullable
    public String getScheme() {
        return scheme;
    }

    /**
     * Sets the scheme. Caution: this isn't available on all types.
     * Same as {@link #scheme(String)}.
     *
     * @return this for builder style chaining
     */
    @Nonnull
    public SlingUrl setScheme(@Nullable String scheme) {
        return scheme(scheme);
    }

    /**
     * Sets the scheme part of an URL. Caution: this isn't available on all types.
     * Same as {@link #setScheme(String)}.
     *
     * @return this for builder style chaining
     */
    @Nonnull
    public SlingUrl scheme(@Nullable String scheme) {
        this.scheme = scheme;
        clearTransients();
        return this;
    }


    /**
     * The username part of an URL. Caution: this isn't available on all types.
     */
    @Nullable
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username. Caution: this isn't available on all types.
     * Same as {@link #username(String)}.
     *
     * @return this for builder style chaining
     */
    @Nonnull
    public SlingUrl setUsername(@Nullable String username) {
        return username(username);
    }

    /**
     * Sets the username part of an URL. Caution: this isn't available on all types.
     * Same as {@link #setUsername(String)}.
     *
     * @return this for builder style chaining
     */
    @Nonnull
    public SlingUrl username(@Nullable String username) {
        this.username = username;
        clearTransients();
        return this;
    }


    /**
     * The password part of an URL. Caution: this isn't available on all types.
     */
    @Nullable
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password. Caution: this isn't available on all types.
     * Same as {@link #password(String)}.
     *
     * @return this for builder style chaining
     */
    @Nonnull
    public SlingUrl setPassword(@Nullable String password) {
        return password(password);
    }

    /**
     * Sets the password part of an URL. Caution: this isn't available on all types.
     * Same as {@link #setPassword(String)}.
     *
     * @return this for builder style chaining
     */
    @Nonnull
    public SlingUrl password(@Nullable String password) {
        this.password = password;
        clearTransients();
        return this;
    }

    /**
     * The host part of the URL. Caution: this isn't available on all types.
     */
    @Nullable
    public String getHost() {
        return host;
    }

    /**
     * Sets the host part of the URL. Caution: this isn't available on all types.
     * Same as {@link #host(String)}.
     *
     * @return this for builder style chaining
     */
    @Nonnull
    public SlingUrl setHost(@Nullable String host) {
        return host(host);
    }

    /**
     * Sets the host part of the URL. Caution: this isn't available on all types.
     * Same as {@link #setHost(String)}.
     *
     * @return this for builder style chaining
     */
    @Nonnull
    public SlingUrl host(@Nullable String host) {
        this.host = host;
        clearTransients();
        return this;
    }

    /**
     * The port part of the URL. Caution: this isn't available on all types.
     */
    @Nullable
    public Integer getPort() {
        return port;
    }

    /**
     * Sets the port. Caution: this isn't available on all types.
     * Same as {@link #port(Integer)}.
     *
     * @return this for builder style chaining
     */
    @Nonnull
    public SlingUrl setPort(@Nullable Integer port) {
        return port(port);
    }

    /**
     * Sets the port part of the URL. Caution: this isn't available on all types.
     * Same as {@link #setPort(Integer)}.
     *
     * @return this for builder style chaining
     */
    @Nonnull
    public SlingUrl port(@Nullable Integer port) {
        this.port = port;
        clearTransients();
        return this;
    }

    /**
     * The type of the URL. Caution: this isn't available on all types.
     */
    @Nonnull
    public UrlType getType() {
        return type;
    }

    /**
     * Sets the type. Caution: this isn't available on all types.
     * Same as {@link #type(UrlType)}.
     *
     * @return this for builder style chaining
     */
    @Nonnull
    public SlingUrl setType(@Nonnull UrlType type) {
        return type(type);
    }

    /**
     * Sets the type of the URL. Caution: this isn't available on all types.
     * Same as {@link #setType(UrlType)}.
     *
     * @return this for builder style chaining
     */
    @Nonnull
    public SlingUrl type(@Nonnull UrlType type) {
        this.type = Objects.requireNonNull(type);
        clearTransients();
        return this;
    }

    public void reset() {
        type = null;
        scheme = null;
        host = null;
        port = null;
        contextPath = null;
        path = null;
        name = null;
        selectors.clear();
        extension = null;
        suffix = null;
        parameters.clear();
        fragment = null;
        clearTransients();
        resourcePath = null;
        resource = null;
    }

    protected String buildUrl() {
        StringBuilder builder = new StringBuilder();
        if (isNotBlank(scheme)) {
            builder.append(scheme).append(':');
        }
        if (type == UrlType.SPECIAL || type == UrlType.OTHER) {
            builder.append(CODEC.encode(name));
        } else {
            if (isNotBlank(scheme)) {
                builder.append("//");
            }
            if (isNotBlank(host)) {
                if (isNotBlank(username)) {
                    builder.append(CODEC.encode(username));
                    if (isNotBlank(password)) {
                        builder.append(":").append(CODEC.encode(password));
                    }
                    builder.append("@");
                }
                builder.append(CODEC.encode(host));
                if (port != null) {
                    builder.append(":").append(port);
                }
            }

            String pathAndName = isExternal() ? CODEC.encode(path + name) : LinkUtil.encodePath(path + name);
            if (!isExternal() && linkMapper != null && type != UrlType.RELATIVE) {
                pathAndName = linkMapper.mapUri(request, pathAndName);
                pathAndName = adjustMappedUrl(request, pathAndName);
            }
            builder.append(pathAndName);

            for (String value : selectors) {
                builder.append('.').append(CODEC.encode(value));
            }
            if (isNotBlank(extension)) {
                builder.append('.').append(CODEC.encode(extension));
            }
            if (isNotBlank(suffix)) {
                builder.append(isExternal() ? CODEC.encode(suffix) : LinkUtil.encodePath(suffix));
            }

            if (parameters.size() > 0) {
                int index = 0;
                for (Map.Entry<String, List<String>> param : parameters.entrySet()) {
                    String paramName = param.getKey();
                    List<String> values = param.getValue();
                    if (values.size() > 0) {
                        for (String val : values) {
                            builder.append(index == 0 ? '?' : '&');
                            builder.append(CODEC.encode(paramName));
                            if (val != null) {
                                builder.append("=").append(CODEC.encode(val));
                            }
                            index++;
                        }
                    } else {
                        builder.append(index == 0 ? '?' : '&');
                        builder.append(CODEC.encode(paramName));
                        index++;
                    }
                }
            }

            if (isNotBlank(fragment)) {
                builder.append('#').append(CODEC.encode(fragment));
            }
        }
        return builder.toString();
    }

    protected void parseUrl(@Nonnull final String url, final boolean decode) throws IllegalArgumentException {
        reset();
        Matcher schemeMatcher = SCHEME_PATTERN.matcher(url);
        if (schemeMatcher.lookingAt()) {
            scheme = schemeMatcher.group("scheme");
        }

        if (isNotBlank(scheme)) {
            if (SPECIAL_SCHEME.matcher(scheme).matches()) { // mailto, tel, ... - unprocessed
                type = UrlType.SPECIAL;
                name = decode(url.substring(schemeMatcher.end()), decode);
            } else { // non-special scheme
                Matcher matcher = URL_PATTERN.matcher(url);
                if (matcher.matches()) { // normal URL
                    type = UrlType.URL;
                    assignFromGroups(matcher, decode, true);
                } else { // doesn't match URL_PATTERN, can't parse -> other
                    type = UrlType.OTHER;
                    name = decode(url.substring(schemeMatcher.end()), decode);
                }
            }

        } else { // no scheme : path or other
            Matcher matcher;
            if ((matcher = ABSOLUTE_PATH_PATTERN.matcher(url)).matches()) {
                type = UrlType.URL;
                assignFromGroups(matcher, decode, false);
            } else if ((matcher = RELATIVE_PATH_PATTERN.matcher(url)).matches()) {
                type = UrlType.RELATIVE;
                assignFromGroups(matcher, decode, false);
            } else { // doesn't match URL_PATTERN, can't parse -> other
                type = UrlType.OTHER;
                name = url;
            }

        }
    }

    protected void assignFromGroups(Matcher matcher, boolean decode, boolean hostAndPort) {
        String value;
        if (hostAndPort) {
            if (isNotBlank(value = matcher.group("host"))) {
                host = value;
            }
            if (isNotBlank(value = matcher.group("port"))) {
                port = Integer.parseInt(value);
            }
            if (isNotBlank(value = matcher.group("username"))) {
                username = value;
            }
            if (isNotBlank(value = matcher.group("password"))) {
                password = value;
            }
        }
        if (isNotBlank(value = matcher.group("pathnoext"))) {
            path = decode(value, decode);
            String contextPath = request.getContextPath();
            if (isNotBlank(contextPath) && path.startsWith(contextPath + "/")) {
                this.contextPath = contextPath;
                path = path.substring(contextPath.length());
            }
        }
        name = isNotBlank(value = matcher.group("filenoext")) ? decode(value, decode) : "";
        if (isNotBlank(value = matcher.group("extensions"))) {
            String[] selExt = StringUtils.split(value.substring(1), '.');
            for (int i = 0; i < selExt.length - 1; i++) {
                selectors.add(decode(selExt[i], decode));
            }
            extension = decode(selExt[selExt.length - 1], decode);
        }
        if (isNotBlank(value = matcher.group("suffix"))) {
            suffix = decode(value, decode);
        }
        if (isNotBlank(value = matcher.group("query"))) {
            parseParameters(value, decode);
        }
        if (isNotBlank(value = matcher.group("fragment"))) {
            fragment = decode(value, decode).substring(1);
        }
    }

    protected String decode(String value, boolean decode) {
        return decode ? CODEC.decode(value) : value;
    }

    protected void parseParameters(@Nonnull String parameterString, boolean decode) {
        parameterString = parameterString.trim();
        while (parameterString.startsWith("?")) {
            parameterString = parameterString.substring(1);
        }
        for (String param : StringUtils.split(parameterString, '&')) {
            String[] nameVal = StringUtils.split(param, "=", 2);
            addParameter(decode(nameVal[0], decode),
                    nameVal.length > 1 ? decode(nameVal[1], decode) : null);
        }
    }

    protected static LinkMapper getLinkMapper(@Nonnull final SlingHttpServletRequest request,
                                              @Nullable LinkMapper linkMapper) {
        if (linkMapper == null) {
            linkMapper = (LinkMapper) request.getAttribute(LinkMapper.LINK_MAPPER_REQUEST_ATTRIBUTE);
            if (linkMapper == null) {
                linkMapper = LinkMapper.RESOLVER;
            }
        }
        return linkMapper;
    }

    /**
     * Lists the internal parse results - mainly for debugging purposes.
     */
    public String toDebugString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        if (type != null) {
            builder.append("type", type);
        }
        if (scheme != null) {
            builder.append("scheme", scheme);
        }
        if (username != null) {
            builder.append("username", username);
        }
        if (password != null) {
            builder.append("password", password);
        }
        if (host != null) {
            builder.append("host", host);
        }
        if (port != null) {
            builder.append("port", port);
        }
        if (contextPath != null) {
            builder.append("contextPath", contextPath);
        }
        if (path != null) {
            builder.append("path", path);
        }
        if (name != null) {
            builder.append("name", name);
        }
        if (!selectors.isEmpty()) {
            builder.append("selectors", selectors);
        }
        if (extension != null) {
            builder.append("extension", extension);
        }
        if (suffix != null) {
            builder.append("suffix", suffix);
        }
        if (!parameters.isEmpty()) {
            builder.append("parameters", parameters);
        }
        if (fragment != null) {
            builder.append("fragment", fragment);
        }
        if (isExternal()) {
            builder.append("external", true);
        }
        if (getResourcePath() != null) {
            builder.append("resourcePath", resourcePath);
        }
        return builder.toString();
    }

}