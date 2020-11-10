package com.composum.sling.core.util;

import com.composum.sling.core.ResourceHandle;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.resourcebuilder.api.ResourceBuilder;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.xss.XSSAPI;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.ThrowsException;

import static com.composum.sling.core.util.MimeTypeUtil.*;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

/**
 * Tests for LinkUtil using a real resolver.
 */
public class LinkUtilResolverTest {

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @Rule
    public final ErrorCollector ec = new ErrorCollector();

    protected ResourceResolver resolver;

    protected MockSlingHttpServletRequest request;

    protected ResourceHandle plainResource;
    protected ResourceHandle resourceWithExtension;
    protected ResourceHandle imgNoExtension;
    protected ResourceHandle imgWithExtension;
    protected ResourceHandle imgOtherExtension;

    @Before
    public void setup() throws IllegalAccessException {
        resolver = context.resourceResolver();
        request = context.request();
        plainResource = ResourceHandle.use(context.build().resource("/content/plain",
                ResourceUtil.PROP_PRIMARY_TYPE, ResourceUtil.TYPE_UNSTRUCTURED,
                ResourceUtil.PROP_RESOURCE_TYPE, "some/thing").getCurrentParent());
        resourceWithExtension = ResourceHandle.use(context.build().resource("/content/with.ext", ResourceUtil.PROP_PRIMARY_TYPE, ResourceUtil.TYPE_UNSTRUCTURED).getCurrentParent());
        imgNoExtension = makeImage("/content/imgNoExt");
        imgWithExtension = makeImage("/content/img.jpg");
        imgOtherExtension = makeImage("/content/img.jpeg");

        XSSAPI xssapi = mock(XSSAPI.class, new ThrowsException(new IllegalStateException("Not mocked")));
        Mockito.doAnswer(invocation -> invocation.getArgument(0)).when(xssapi).getValidHref(anyString());
        ServiceHandle xssapihandle = (ServiceHandle) FieldUtils.readStaticField(com.composum.sling.core.util.XSS.class, "XSSAPI_HANDLE", true);
        FieldUtils.writeField(xssapihandle, "service", xssapi, true);
    }

    @Test
    public void getUrl() {
        ec.checkThat(LinkUtil.getUrl(request, "?bla=blu"), is("?bla=blu")); // unclear.
        
        ec.checkThat(LinkUtil.getUrl(request, "/content/noresource"), is("/noresource"));
        ec.checkThat(LinkUtil.getUrl(request, "/content/noresource?bla=blu"), is("/noresource?bla=blu"));
        ec.checkThat(LinkUtil.getUrl(request, plainResource.getPath()), is("/plain.html")); // adds .html since it has sling:resourceType
        ec.checkThat(LinkUtil.getUrl(request, plainResource.getPath() + "?bla=blu"), is("/plain?bla=blu")); // adds .html since it has sling:resourceType
        ec.checkThat(LinkUtil.getUrl(request, resourceWithExtension.getPath()), is("/with.ext"));
        ec.checkThat(LinkUtil.getUrl(request, imgNoExtension.getPath()), is("/imgNoExt"));
        ec.checkThat(LinkUtil.getUrl(request, imgWithExtension.getPath()), is("/img.jpg"));
        ec.checkThat(LinkUtil.getUrl(request, imgOtherExtension.getPath()), is("/img.jpeg"));
    }

    ResourceHandle makeImage(String path) {
        ResourceBuilder parent = context.build().resource(ResourceUtil.getParent(path));
        ClassLoader loader = getClass().getClassLoader();
        ResourceBuilder file = parent.file(ResourceUtil.getName(path), loader.getResourceAsStream("someimage.jpg"), "image/jpeg", System.currentTimeMillis());
        return ResourceHandle.use(file.commit().getCurrentParent());
    }

    @Test
    public void getExtension() {
        ec.checkThat(LinkUtil.getExtension(plainResource, null), is(".html"));
        ec.checkThat(LinkUtil.getExtension(resourceWithExtension, null), is("")); // strange.
        ec.checkThat(LinkUtil.getExtension(imgNoExtension, null), is(""));
        ec.checkThat(LinkUtil.getExtension(imgWithExtension, null), is(""));
        ec.checkThat(LinkUtil.getExtension(imgOtherExtension, null), is(""));

        ec.checkThat(LinkUtil.getExtension(plainResource, ""), is(""));
        ec.checkThat(LinkUtil.getExtension(resourceWithExtension, ""), is(""));
        ec.checkThat(LinkUtil.getExtension(imgNoExtension, ""), is(""));
        ec.checkThat(LinkUtil.getExtension(imgWithExtension, ""), is(""));
        ec.checkThat(LinkUtil.getExtension(imgOtherExtension, ""), is(""));

        ec.checkThat(LinkUtil.getExtension(plainResource, "json"), is(".json"));
        ec.checkThat(LinkUtil.getExtension(resourceWithExtension, "json"), is(".json"));
        ec.checkThat(LinkUtil.getExtension(imgNoExtension, "json"), is(".json"));
        ec.checkThat(LinkUtil.getExtension(imgWithExtension, "json"), is(".json"));
        ec.checkThat(LinkUtil.getExtension(imgOtherExtension, "json"), is(".json"));

        ec.checkThat(LinkUtil.getExtension(plainResource, "jpg"), is(".jpg"));
        ec.checkThat(LinkUtil.getExtension(resourceWithExtension, "jpg"), is(".jpg"));
        ec.checkThat(LinkUtil.getExtension(imgNoExtension, "jpg"), is(".jpg"));
        ec.checkThat(LinkUtil.getExtension(imgWithExtension, "jpg"), is("")); // has it already. strange.
        ec.checkThat(LinkUtil.getExtension(imgOtherExtension, "jpg"), is(".jpg"));
    }

}
