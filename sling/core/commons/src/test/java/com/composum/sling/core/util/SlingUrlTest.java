package com.composum.sling.core.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SlingUrlTest {

    @Rule
    public final ErrorCollector ec = new ErrorCollector();

    protected SlingHttpServletRequest request;
    protected ResourceResolver resolver;
    protected SlingUrl url;
    protected StringBuilder linkexamples = new StringBuilder();

    @Before
    public void setup() {
        request = mock(SlingHttpServletRequest.class);
        resolver = mock(ResourceResolver.class);
        when(resolver.getResource(anyString())).thenReturn(null);
        when(resolver.map(any(SlingHttpServletRequest.class), anyString())).thenAnswer(invocation -> {
            SlingHttpServletRequest req = invocation.getArgument(0, SlingHttpServletRequest.class);
            String uri = invocation.getArgument(1, String.class);
            Pattern hostMapping = Pattern.compile("^/x(/.*)?$");
            Matcher matcher = hostMapping.matcher(uri);
            return matcher.matches() ? "http://host.xxx" + matcher.group(1) : req.getContextPath() + uri;
        });
        when(request.getContextPath()).thenReturn("/ctx");
        when(request.getResourceResolver()).thenReturn(resolver);
        when(request.getAttribute(LinkMapper.LINK_MAPPER_REQUEST_ATTRIBUTE)).thenReturn(null);

        System.out.println(SlingUrl.URL_PATTERN);
    }

    @Test
    public void slingUrlTest() {
        url = new SlingUrl(request, newResource(resolver, "/a/bb/ccc"), "html");
        printChecks(url);
        ec.checkThat(url.getContextPath(), is("/ctx"));
        ec.checkThat(url.getExtension(), is("html"));
        ec.checkThat(url.isExternal(), is(false));
        ec.checkThat(url.getFragment(), nullValue());
        ec.checkThat(url.getPath(), is("/a/bb/ccc"));
        ec.checkThat(url.getResourcePath(), is("/a/bb/ccc"));
        ec.checkThat(url.getSuffix(), nullValue());
        ec.checkThat(url.getUrl(), is("/ctx/a/bb/ccc.html"));
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=URL,path=/a/bb/,name=ccc,extension=html,resourcePath=/a/bb/ccc]"));

        url = new SlingUrl(request, newResource(resolver, "/a/bb/ddd"))
                .selectors("m.n").extension("json").suffix("/ddd/eee/xxx.json").parameters("d&c=x%20y");
        printChecks(url);
        ec.checkThat(url.getContextPath(), is("/ctx"));
        ec.checkThat(url.getExtension(), is("json"));
        ec.checkThat(url.isExternal(), is(false));
        ec.checkThat(url.getFragment(), nullValue());
        ec.checkThat(url.getPath(), is("/a/bb/ddd"));
        ec.checkThat(url.getResourcePath(), is("/a/bb/ddd"));
        ec.checkThat(url.getSuffix(), is("/ddd/eee/xxx.json"));
        ec.checkThat(url.getUrl(), is("/ctx/a/bb/ddd.m.n.json/ddd/eee/xxx.json?d&c=x%20y"));
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=URL,path=/a/bb/,name=ddd,selectors=[m, n],extension=json,suffix=/ddd/eee/xxx.json,parameters={d=[], c=[x y]},resourcePath=/a/bb/ddd]"));

        url = new SlingUrl(request, "/ctx/x/bb/ccc/öä ü.s.x.html/x/x/z.html?a=b&c", false);
        printChecks(url);
        ec.checkThat(url.getContextPath(), is("/ctx"));
        ec.checkThat(url.getExtension(), is("html"));
        ec.checkThat(url.isExternal(), is(false));
        ec.checkThat(url.getFragment(), nullValue());
        ec.checkThat(url.getPath(), is("/x/bb/ccc/öä ü"));
        ec.checkThat(url.getResourcePath(), is("/x/bb/ccc/öä ü"));
        ec.checkThat(url.getSuffix(), is("/x/x/z.html"));
        ec.checkThat(url.getUrl(), is("http://host.xxx/bb/ccc/%C3%B6%C3%A4%20%C3%BC.s.x.html/x/x/z.html?a=b&c"));
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=URL,contextPath=/ctx,path=/x/bb/ccc/,name=öä ü,selectors=[s, x],extension=html,suffix=/x/x/z.html,parameters={a=[b], c=[]},resourcePath=/x/bb/ccc/öä ü]"));

        url.selector("sel").removeSelector("x")
                .suffix(newResource(resolver, "/c/dd/e#e"))
                .removeParameter("a")
                .parameter("x", "aöü")
                .parameter("Öß", "& 12")
                .parameter("$")
                .fragment("%%$&");
        printChecks(url);
        ec.checkThat(url.getContextPath(), is("/ctx"));
        ec.checkThat(url.getExtension(), is("html"));
        ec.checkThat(url.isExternal(), is(false));
        ec.checkThat(url.getFragment(), is("%%$&"));
        ec.checkThat(url.getPath(), is("/x/bb/ccc/öä ü"));
        ec.checkThat(url.getResourcePath(), is("/x/bb/ccc/öä ü"));
        ec.checkThat(url.getSuffix(), is("/c/dd/e#e"));
        ec.checkThat(url.getUrl(), is("http://host.xxx/bb/ccc/%C3%B6%C3%A4%20%C3%BC.s.sel.html/c/dd/e%23e?c&x=a%C3%B6%C3%BC&%C3%96%C3%9F=%26%2012&%24#%25%25%24%26"));
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=URL,contextPath=/ctx,path=/x/bb/ccc/,name=öä ü,selectors=[s, sel],extension=html,suffix=/c/dd/e#e,parameters={c=[], x=[aöü], Öß=[& 12], $=[]},fragment=%%$&,resourcePath=/x/bb/ccc/öä ü]"));

        url = new SlingUrl(request, "https://www.google.com/");
        printChecks(url);
        ec.checkThat(url.getContextPath(), is("/ctx"));
        ec.checkThat(url.getExtension(), nullValue());
        ec.checkThat(url.isExternal(), is(true));
        ec.checkThat(url.getFragment(), nullValue());
        ec.checkThat(url.getPath(), is("/"));
        ec.checkThat(url.getResourcePath(), nullValue());
        ec.checkThat(url.getSuffix(), nullValue());
        ec.checkThat(url.getUrl(), is("https://www.google.com/"));
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=URL,scheme=https,host=www.google.com,path=/,name=,external=true]"));


        url = new SlingUrl(request, "https://www.google.com/ä-@ß$?x=yßz&a#aa");
        printChecks(url);
        ec.checkThat(url.getContextPath(), is("/ctx"));
        ec.checkThat(url.getExtension(), nullValue());
        ec.checkThat(url.isExternal(), is(true));
        ec.checkThat(url.getFragment(), is("aa"));
        ec.checkThat(url.getPath(), is("/ä-@ß$"));
        ec.checkThat(url.getResourcePath(), nullValue());
        ec.checkThat(url.getSuffix(), nullValue());
        ec.checkThat(url.getUrl(), is("https://www.google.com/%C3%A4-%40%C3%9F%24?x=y%C3%9Fz&a#aa"));
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=URL,scheme=https,host=www.google.com,path=/,name=ä-@ß$,parameters={x=[yßz], a=[]},fragment=aa,external=true]"));

        url = new SlingUrl(request, "mailto:%C3%A4.user%40%C3%B6.domain.x", true); // "mailto:ä.user@ö.domain.x" in UTF-8
        printChecks(url);
        ec.checkThat(url.getContextPath(), is("/ctx"));
        ec.checkThat(url.getExtension(), nullValue());
        ec.checkThat(url.isExternal(), is(true));
        ec.checkThat(url.getFragment(), nullValue());
        ec.checkThat(url.getPath(), is("ä.user@ö.domain.x"));
        ec.checkThat(url.getResourcePath(), nullValue());
        ec.checkThat(url.getSuffix(), nullValue());
        ec.checkThat(url.getUrl(), is("mailto:%C3%A4.user%40%C3%B6.domain.x"));
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=SPECIAL,scheme=mailto,name=ä.user@ö.domain.x,external=true]"));

        url = new SlingUrl(request, "mailto:ä.user@ö.domain.x");
        printChecks(url);
        ec.checkThat(url.getUrl(), is("mailto:%C3%A4.user%40%C3%B6.domain.x"));
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=SPECIAL,scheme=mailto,name=ä.user@ö.domain.x,external=true]"));

        url = new SlingUrl(request, "tel:%2B01%20123%20/%203456-78%20999", true); // +01 123 / 3456-78 999
        printChecks(url);
        ec.checkThat(url.getContextPath(), is("/ctx"));
        ec.checkThat(url.getExtension(), nullValue());
        ec.checkThat(url.isExternal(), is(true));
        ec.checkThat(url.getFragment(), nullValue());
        ec.checkThat(url.getPath(), is("+01 123 / 3456-78 999"));
        ec.checkThat(url.getResourcePath(), nullValue());
        ec.checkThat(url.getSuffix(), nullValue());
        ec.checkThat(url.getUrl(), is("tel:%2B01%20123%20/%203456-78%20999"));
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=SPECIAL,scheme=tel,name=+01 123 / 3456-78 999,external=true]"));

        url = new SlingUrl(request, "tel:+01 123 / 3456-78 999");
        printChecks(url);
        ec.checkThat(url.getUrl(), is("tel:%2B01%20123%20/%203456-78%20999"));
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=SPECIAL,scheme=tel,name=+01 123 / 3456-78 999,external=true]"));

        url = new SlingUrl(request, "some/path.ext");
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=RELATIVE,path=some/,name=path,extension=ext]"));
        ec.checkThat(url.getContextPath(), is("/ctx"));
        ec.checkThat(url.getExtension(), is("ext"));
        ec.checkThat(url.isExternal(), is(false));
        ec.checkThat(url.getFragment(), nullValue());
        ec.checkThat(url.getPath(), is("some/path"));
        ec.checkThat(url.getResourcePath(), nullValue());
        ec.checkThat(url.getSuffix(), nullValue());
        ec.checkThat(url.getUrl(), is("some/path.ext"));

        // without context path
        url = new SlingUrl(request, "/x/bb", false);
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=URL,path=/x/,name=bb,resourcePath=/x/bb]"));
        ec.checkThat(url.getContextPath(), is("/ctx"));
        ec.checkThat(url.getUrl(), is("http://host.xxx/bb")); // linkmapper removes /x

        url = new SlingUrl(request, "../img/loading.gif");
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=OTHER,name=../img/loading.gif]"));
        ec.checkThat(url.getUrl(), equalTo("../img/loading.gif"));
    }

    @Test
    public void additionalTests() {
        url = new SlingUrl(request, "http://ends.with/slash/");
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=URL,scheme=http,host=ends.with,path=/slash/,name=,external=true]"));
        ec.checkThat(url.getUrl(), is("http://ends.with/slash/"));

        // there are exotic things like "ftp://myname@host.dom/%2Fetc/motd.txt" but that's a weird special case we ignore.

        url = new SlingUrl(request, "ftp://myname@host.dom/etc/motd.txt");
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=URL,scheme=ftp,username=myname,host=host.dom,path=/etc/,name=motd,extension=txt,external=true]"));
        ec.checkThat(url.getUrl(), is("ftp://myname@host.dom/etc/motd.txt"));

        url = new SlingUrl(request, "ftp://ftp.cs.brown.edu/pub/Effective_C%2B%2B_errata.txt", true);
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=URL,scheme=ftp,host=ftp.cs.brown.edu,path=/pub/,name=Effective_C++_errata,extension=txt,external=true]"));
        ec.checkThat(url.getUrl(), is("ftp://ftp.cs.brown.edu/pub/Effective_C%2B%2B_errata.txt"));

        url = new SlingUrl(request, "ftp://myname:pass@host.dom/etc/");
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=URL,scheme=ftp,username=myname,password=pass,host=host.dom,path=/etc/,name=,external=true]"));
        ec.checkThat(url.getUrl(), is("ftp://myname:pass@host.dom/etc/"));

        url = new SlingUrl(request, "file://localhost/etc/fstab");
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=URL,scheme=file,host=localhost,path=/etc/,name=fstab,external=true]"));
        ec.checkThat(url.getUrl(), is("file://localhost/etc/fstab"));


        url = new SlingUrl(request, "file:///etc/fstab");
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=URL,scheme=file,path=/etc/,name=fstab,external=true]"));
        ec.checkThat(url.getUrl(), is("file:///etc/fstab"));


        url = new SlingUrl(request, "file:///c:/WINDOWS/clock.avi");
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=URL,scheme=file,path=/c:/WINDOWS/,name=clock,extension=avi,external=true]"));
        ec.checkThat(url.getUrl(), is("file:///c%3A/WINDOWS/clock.avi"));


        url = new SlingUrl(request, "file:///path/");
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=URL,scheme=file,path=/path/,name=,external=true]"));
        ec.checkThat(url.getUrl(), is("file:///path/"));


        url = new SlingUrl(request, "mailto:ä.user@ö.domain.x", false);
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=SPECIAL,scheme=mailto,name=ä.user@ö.domain.x,external=true]"));
        ec.checkThat(url.getUrl(), is("mailto:%C3%A4.user%40%C3%B6.domain.x"));

        url = new SlingUrl(request, "//host/path", false); // "protocol relative URL"
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=OTHER,name=//host/path]"));
        ec.checkThat(url.getUrl(), is("//host/path"));

    }

    @Test
    public void emailFormats() {
        url = new SlingUrl(request, "mailto:ä.user@ö.domain.x");
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=SPECIAL,scheme=mailto,name=ä.user@ö.domain.x,external=true]"));
        ec.checkThat(url.getUrl(), is("mailto:%C3%A4.user%40%C3%B6.domain.x"));

        url = new SlingUrl(request, "mailto:John Smith <john.smith@example.org>");
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=SPECIAL,scheme=mailto,name=John Smith <john.smith@example.org>,external=true]"));
        ec.checkThat(url.getUrl(), is("mailto:John%20Smith%20%3Cjohn.smith%40example.org%3E"));

        url = new SlingUrl(request, "mailto:\"john..doe\"@example.org");
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=SPECIAL,scheme=mailto,name=\"john..doe\"@example.org,external=true]"));
        ec.checkThat(url.getUrl(), is("mailto:%22john..doe%22%40example.org"));

        url = new SlingUrl(request, "mailto:with+symbol@example.com");
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=SPECIAL,scheme=mailto,name=with+symbol@example.com,external=true]"));
        ec.checkThat(url.getUrl(), is("mailto:with%2Bsymbol%40example.com"));

        url = new SlingUrl(request, "mailto:us'%20%%r%example.com@example.org");
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=SPECIAL,scheme=mailto,name=us'%20%%r%example.com@example.org,external=true]"));
        ec.checkThat(url.getUrl(), is("mailto:us%27%2520%25%25r%25example.com%40example.org"));

        url = new SlingUrl(request, "mailto:=?utf-8?B?w5xtbMOkdXR0ZcOfdA==?= <umlauttest@example.com>");
        printChecks(url);
        ec.checkThat(url.toDebugString(), is("SlingUrl[type=SPECIAL,scheme=mailto,name==?utf-8?B?w5xtbMOkdXR0ZcOfdA==?= <umlauttest@example.com>,external=true]"));
        ec.checkThat(url.getUrl(), is("mailto:%3D%3Futf-8%3FB%3Fw5xtbMOkdXR0ZcOfdA%3D%3D%3F%3D%20%3Cumlauttest%40example.com%3E"));
    }

    /**
     * Generates code to easily create many examples.
     */
    protected void printChecks(SlingUrl url) {
        linkexamples.append("<p><a href=\"").append(url.getUrl()).append("\">").append(url.getUrl()).append("</a></p>\n");
        StringBuilder builder = new StringBuilder();
        builder.append("\n\n        url = new SlingUrl(request, \"").append(url.getUrl()).append("\");")
                .append("\n        printChecks(url);")
                .append("\n        ec.checkThat(url.toDebugString(), is(\"").append(url.toDebugString()).append("\"));")
                .append("\n        ec.checkThat(url.getUrl(), is(\"").append(url.getUrl()).append("\"));");
        System.out.println(builder);

        // sanity check: the url decoded again and encoded again should get us the same things
        SlingUrl url2 = new SlingUrl(request, url.getUrl(), true);
        ec.checkThat(url2.toDebugString(), url2.getUrl(), is(url.getUrl()));
    }

    /**
     * Prints examples for manual check in browser, e.g. with https://www.w3schools.com/html/tryit.asp?filename=tryhtml_links_w3schools .
     */
    @After
    public void printLinks() {
        System.out.println();
        System.out.println(linkexamples);
        linkexamples.setLength(0);
    }

    @Test
    public void printPatterns() {
        new SlingUrl(request, "") {
            {
                System.out.println(SlingUrl.URL_PATTERN);
                System.out.println(SlingUrl.ABSOLUTE_PATH_PATTERN);
                System.out.println(SlingUrl.RELATIVE_PATH_PATTERN);
            }
        };
    }

    protected Resource newResource(ResourceResolver resolver, String path) {
        String name = StringUtils.substringAfterLast(path, "/");
        Resource resource = mock(Resource.class);
        when(resource.getPath()).thenReturn(path);
        when(resource.getName()).thenReturn(name);
        when(resource.getResourceResolver()).thenReturn(resolver);
        when(resolver.getResource(path)).thenReturn(resource);
        return resource;
    }
}