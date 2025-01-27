<%@page session="false" pageEncoding="utf-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<sling:defineObjects/>
<cpn:component var="model" type="com.composum.sling.core.usermanagement.view.View" scope="request">
    <%
        if (!model.isReadAllowed()) {
            slingResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
        } else {
    %>
    <html data-context-path="${slingRequest.contextPath}">
    <sling:call script="head.jsp"/>
    <body id="usermanagement" class="console left-open top-open">
    <div id="ui">
        <sling:call script="dialogs.jsp"/>
        <sling:include resourceType="composum/nodes/console/components/navbar"/>
        <div id="content-wrapper">
            <c:set var="writeAllowed" value="${model.permissible['nodes/users/manager']['write']}"/>
            <c:set var="writeDisabled" value="${writeAllowed?'':' disabled'}"/>
            <div id="split-view-horizontal-split" class="split-pane horizontal-split fixed-left">
                <div class="split-pane-component left-pane">
                    <div>
                        <div class="tree-panel">
                            <div id="usermanagement-tree" data-selected="${model.path}">
                            </div>
                        </div>
                        <div class="tree-actions action-bar btn-toolbar" role="toolbar">
                            <div class="align-left">
                                <div class="btn-group btn-group-sm" role="group">
                                    <button type="button" class="refresh glyphicon-refresh glyphicon btn btn-default"
                                            title="Refresh tree view"><span class="label">Refresh</span></button>
                                </div>
                                <div class="btn-group btn-group-sm" role="group">
                                    <button type="button" class="addsystemuser fa fa-stack btn btn-default"
                                            title="Add System User"${writeDisabled}>
                                        <i class="fa fa-user-o fa-stack-1x"></i>
                                        <i class="fa fa-plus-circle fa-stack-1x"></i>
                                        <span class="label">Add System User</span>
                                    </button>
                                    <button type="button" class="adduser fa fa-stack btn btn-default"
                                            title="Add User"${writeDisabled}>
                                        <i class="fa fa-user fa-stack-1x"></i>
                                        <i class="fa fa-plus-circle fa-stack-1x"></i>
                                        <span class="label">Add User</span>
                                    </button>
                                    <button type="button" class="addgroup fa fa-stack btn btn-default"
                                            title="Add Group"${writeDisabled}>
                                        <i class="fa fa-users fa-stack-1x"></i>
                                        <i class="fa fa-plus-circle fa-stack-1x"></i>
                                        <span class="label">Add Group</span>
                                    </button>
                                </div>
                                <div class="btn-group btn-group-sm" role="group">
                                    <button type="button" class="deleteauthorizable fa fa-minus btn btn-default"
                                            title="Delete Authorizable"${writeDisabled}><span
                                            class="label">Delete Authorizable</span>
                                    </button>
                                </div>
                            </div>
                            <div class="align-right">
                            </div>
                        </div>
                    </div>
                </div>
                <div class="split-pane-divider split-pane-divider-main-horizontal"></div>
                <div class="split-pane-component right-pane">
                    <div id="split-view-vertical-split" class="split-pane vertical-split fixed-top">
                        <div class="split-pane-component top-pane query-split-pane">
                            <div id="usermanagement-query">
                                <sling:call script="query.jsp"/>
                            </div>
                        </div>
                        <div class="split-pane-divider split-pane-divider-main-vertical"></div>
                        <div class="split-pane-component bottom-pane">
                            <div id="usermanagement-view">
                                <sling:call script="view.jsp"/>
                            </div>
                            <div class="close-top"><a href="#" class="fa fa-angle-double-up"
                                                      title="Collapse top panel"></a></div>
                        </div>
                        <div class="open-top"><a href="#" class="fa fa-angle-double-down" title="Restore top panel"></a>
                        </div>
                    </div>
                    <div class="close-left"><a href="#" class="fa fa-angle-double-left" title="Collapse left panel"></a>
                    </div>
                </div>
                <div class="open-left"><a href="#" class="fa fa-angle-double-right" title="Restore left panel"></a>
                </div>
            </div>
            <c:remove var="writeDisabled"/>
            <c:remove var="writeAllowed"/>
        </div>
    </div>
    <sling:call script="script.jsp"/>
    <sling:include resourceType="composum/nodes/console/components/tryLogin"/>
    </body>
    </html>
    <%}%>
</cpn:component>
