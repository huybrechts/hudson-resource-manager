package hudson.plugins.resourcemanager;

import hudson.EnvVars;
import hudson.model.*;
import hudson.model.Label;
import hudson.model.queue.AbstractQueueTask;
import hudson.model.queue.CauseOfBlockage;
import hudson.security.Permission;

import java.io.IOException;

public class SetupAction extends AbstractQueueTask implements ModelObject, Action, EnvironmentContributingAction {

    private final Run<?, ?> build;
    private final String resourceId;
    private final String label;

    private SetupRun setUp;
    private SetupRun.TeardownRun tearDown;

    public SetupAction(Run build, String resourceId, String label) {
        this.build = build;
        this.resourceId = resourceId;
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "Resource";
    }

    public String getUrlName() {
        return "resource-" + resourceId;
    }

    public synchronized Queue.Executable createExecutable() throws IOException {
        if (setUp == null) {
            return setUp = new SetupRun(this);
        } else {
            return tearDown = new SetupRun.TeardownRun(this);
        }
    }

    public SetupRun getSetUp() {
        return setUp;
    }

    public SetupRun.TeardownRun getTearDown() {
        return tearDown;
    }

    public Run<?, ?> getBuild() {
        return build;
    }

    public String getSearchUrl() {
        return getUrlName();
    }

    /////////////////////////////////

    public Label getAssignedLabel() {
        return Hudson.getInstance().getLabel(ResourceManager.getInstance().getResource(resourceId).getResourceType().getNodeId());
    }

    public Node getLastBuiltOn() {
        return null;
    }

    public boolean isBuildBlocked() {
        return false;
    }

    public String getWhyBlocked() {
        return null;
    }

    public CauseOfBlockage getCauseOfBlockage() {
        return null;
    }

    public String getName() {
        return "Resource";
    }

    public String getFullDisplayName() {
        return getBuild().getFullDisplayName() + " \u00BB " + getName();
    }

    public long getEstimatedDuration() {
        return -1;
    }

    public void checkAbortPermission() {
        Hudson.getInstance().checkPermission(Permission.CONFIGURE);
    }

    public boolean hasAbortPermission() {
        return Hudson.getInstance().hasPermission(Permission.CONFIGURE);
    }

    public String getUrl() {
        return build.getUrl() + getUrlName();
    }

    public boolean isConcurrentBuild() {
        return false;
    }

    public ResourceList getResourceList() {
        return new ResourceList();
    }

    public Object getSameNodeConstraint() {
        return null;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
        int index = build.getActions(SetupAction.class).indexOf(this);
        env.put("JENKINS_RESOURCE_ID_" + label, resourceId);

        if (index == 0) {
            env.put("HUDSON_RESOURCE_ID", resourceId);
            env.put("JENKINS_RESOURCE_ID", resourceId);
        }
    }

}
