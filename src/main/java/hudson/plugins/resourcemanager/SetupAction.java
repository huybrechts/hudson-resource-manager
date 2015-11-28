package hudson.plugins.resourcemanager;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.model.ModelObject;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.ResourceList;
import hudson.model.queue.AbstractQueueTask;
import hudson.model.queue.CauseOfBlockage;
import hudson.security.Permission;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

public class SetupAction extends AbstractQueueTask implements ModelObject, Action, EnvironmentContributingAction {

    private final AbstractBuild<?, ?> build;
    private final String resourceId;

    private transient Stage nextStage;

    private final Map<Stage,SetupRun> stages = new TreeMap<Stage,SetupRun>();

    public SetupAction(AbstractBuild build, String resourceId) {
        this.build = build;
        this.resourceId = resourceId;
    }

    public Map<Stage, SetupRun> getStages() {
        return stages;
    }

    public Stage getNextStage() {
        return nextStage;
    }

    public void setNextStage(Stage nextStage) {
        this.nextStage = nextStage;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "Resource";
    }

    public String getUrlName() {
        return "resource";
    }

    public synchronized Queue.Executable createExecutable() throws IOException {
        SetupRun run =  new SetupRun(this, nextStage);
        stages.put(nextStage, run);
        return run;

    }

    public SetupRun getStage(Stage stage) {
        return stages.get(stage);
    }

    public SetupRun getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        return getStage(Stage.valueOf(token));
    }

    public ResourceBuildWrapper getWrapper() {
        return ((BuildableItemWithBuildWrappers) build.getProject()).getBuildWrappersList().get(ResourceBuildWrapper.class);
    }

    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    public String getSearchUrl() {
        return "resource";
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
        return build.getUrl() + "resource/";
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
        env.put("HUDSON_RESOURCE_ID", resourceId);
        env.put("JENKINS_RESOURCE_ID", resourceId);
        if (ResourceManager.getInstance().getResource(resourceId).getResourceType().isSetupRequired()) {
            env.put("JENKINS_RESOURCE_READY_URL", Hudson.getInstance().getRootUrl() + getUrl() + "/setUp/wait");
        }
    }
}
