package com.composum.sling.nodes.servlet;

import com.composum.sling.core.BeanContext;
import com.composum.sling.core.ResourceHandle;
import com.composum.sling.core.servlet.AbstractServiceServlet;
import com.composum.sling.core.servlet.ServletOperation;
import com.composum.sling.core.servlet.ServletOperationSet;
import com.composum.sling.core.servlet.Status;
import com.composum.sling.core.util.RequestUtil;
import com.composum.sling.nodes.NodesConfiguration;
import com.composum.sling.nodes.scene.Scene;
import com.composum.sling.nodes.scene.SceneConfigurations;
import com.composum.sling.nodes.scene.SceneConfigurations.Config;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.RepositoryException;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;

@Component(service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Nodes Source Servlet",
                ServletResolverConstants.SLING_SERVLET_PATHS + "=" + SceneServlet.SERVLET_PATH,
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET,
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_POST,
                "sling.auth.requirements=" + SceneServlet.SERVLET_PATH
        }
)
public class SceneServlet extends AbstractServiceServlet {

    public static final String SERVLET_PATH = "/bin/cpm/nodes/scene";

    public static final String PARAM_SCENE = "scene";

    @Reference
    protected NodesConfiguration nodesConfig;

    protected BundleContext bundleContext;

    //
    // Servlet operations
    //

    public enum Extension {json}

    public enum Operation {data, prepare, remove}

    protected ServletOperationSet<SceneServlet.Extension, SceneServlet.Operation> operations = new ServletOperationSet<>(SceneServlet.Extension.json);

    protected ServletOperationSet<SceneServlet.Extension, SceneServlet.Operation> getOperations() {
        return operations;
    }

    @Activate
    private void activate(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    protected boolean isEnabled() {
        return nodesConfig.isEnabled(this);
    }

    /**
     * setup of the servlet operation set for this servlet instance
     */
    @Override
    public void init() throws ServletException {
        super.init();

        // GET
        operations.setOperation(ServletOperationSet.Method.GET, SceneServlet.Extension.json,
                Operation.data, new SceneDataOperation());

        // POST
        operations.setOperation(ServletOperationSet.Method.POST, SceneServlet.Extension.json,
                Operation.prepare, new PrepareSceneOperation());
        operations.setOperation(ServletOperationSet.Method.POST, SceneServlet.Extension.json,
                Operation.remove, new RemoveSceneOperation());
    }

    protected abstract class SceneOperation implements ServletOperation {

        @Override
        public void doIt(@Nonnull final SlingHttpServletRequest request,
                         @Nonnull final SlingHttpServletResponse response,
                         @Nullable final ResourceHandle resource)
                throws RepositoryException, IOException, ServletException {
            final Status status = new Status(request, response);
            final String sceneId = request.getParameter(PARAM_SCENE);
            if (resource != null && StringUtils.isNotBlank(sceneId)) {
                try {
                    String sceneKey = StringUtils.substringBefore(sceneId, "/");
                    String toolId = StringUtils.substringAfter(sceneId, "/");
                    final Config sceneConfig =
                            SceneConfigurations.instance(request).getSceneConfig(sceneKey);
                    if (sceneConfig != null) {
                        final BeanContext context = new BeanContext.Servlet(
                                getServletContext(), bundleContext, request, response);
                        final Scene scene = new Scene(context, sceneConfig, resource.getPath());
                        applyScene(status, context, scene, toolId);
                    } else {
                        status.error("scene not available ({})", sceneId);
                    }
                } catch (IOException ex) {
                    status.error("an error has been occured", ex);
                }
            } else {
                status.error("values missed (path={},scene={})",
                        request.getRequestPathInfo().getSuffix(), sceneId);
            }
            status.sendJson();
        }

        protected abstract void applyScene(@Nonnull Status status, @Nonnull BeanContext context,
                                           @Nonnull Scene scene, @Nonnull String toolId)
                throws IOException;

        protected void answer(@Nonnull final Status status,
                              @Nonnull final Scene scene, @Nonnull final String toolId) {
            Config config = scene.getConfig();
            Config.Tool tool = config.getTool(toolId);
            if (scene.isContentPrepared()) {
                status.data("scene").put("contentPath", scene.getContentPath());
            }
            if (tool != null) {
                status.data("tool").put("frameUrl", scene.getFrameUrl(toolId));
            }
            status.data("config").put("key", config.getKey());
            status.data("config").put("path", config.getPath());
        }
    }

    protected class SceneDataOperation extends SceneOperation {

        @Override
        protected void applyScene(@Nonnull final Status status, @Nonnull final BeanContext context,
                                  @Nonnull final Scene scene, @Nonnull final String toolId) {
            answer(status, scene, toolId);
        }
    }

    protected class PrepareSceneOperation extends SceneOperation {

        @Override
        protected void applyScene(@Nonnull final Status status, @Nonnull final BeanContext context,
                                  @Nonnull final Scene scene, @Nonnull final String toolId)
                throws IOException {
            final SlingHttpServletRequest request = context.getRequest();
            final boolean reset = RequestUtil.getParameter(request, "reset", Boolean.FALSE);
            final Resource sceneContent = scene.prepareContent(reset);
            if (sceneContent != null) {
                request.getResourceResolver().commit();
                answer(status, scene, toolId);
            } else {
                status.error("no content available ({})", scene.getContentPath());
            }
        }
    }

    protected class RemoveSceneOperation extends SceneOperation {

        @Override
        protected void applyScene(@Nonnull final Status status, @Nonnull final BeanContext context,
                                  @Nonnull final Scene scene, @Nonnull final String toolId)
                throws IOException {
            final Resource sceneResource = scene.getContentResource();
            if (!ResourceUtil.isNonExistingResource(sceneResource)) {
                final SlingHttpServletRequest request = context.getRequest();
                ResourceResolver resolver = request.getResourceResolver();
                resolver.delete(sceneResource);
                resolver.commit();
            }
            answer(status, scene, toolId);
        }
    }
}
