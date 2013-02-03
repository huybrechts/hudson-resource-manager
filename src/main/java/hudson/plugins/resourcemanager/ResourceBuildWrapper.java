package hudson.plugins.resourcemanager;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Queue;
import hudson.model.ResourceActivity;
import hudson.model.ResourceList;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.tasks.BuildWrapper;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.kohsuke.stapler.DataBoundConstructor;

public class ResourceBuildWrapper extends BuildWrapper implements Serializable,
        ResourceActivity {

    private final String label;
    private final boolean async;

    @DataBoundConstructor
    public ResourceBuildWrapper(String label, boolean async) {
        this.label = label;
        this.async = async;
    }

    public String getLabel() {
        return label;
    }

    public boolean isAsync() {
        return async;
    }

    public ResourceList getResourceList() {
        Label l = ResourceManager.getInstance().getLabels().get(this.label);
        ResourceList result = new ResourceList();
        if (l != null) {
            hudson.model.Resource r = l.getResource();
            if (r != null) {
                result = result.w(r);
            }
        }
        return result;
    }

    public String getDisplayName() {
        return "Require resource";
    }

    @Override
    public Environment setUp(AbstractBuild build, Launcher launcher,
                             BuildListener listener) throws IOException, InterruptedException {

        final PrintStream logger = listener.getLogger();

        final Label label = ResourceManager.getInstance().getLabels().get(this.label);
        logger.println("[resource-manager] Acquiring a resource from " + label + "...");
        final Resource resource = label.acquire(build);

        logger.println("[resource-manager] Acquired " + resource);

        final SetupAction setupAction = new SetupAction(build, resource.getId());
        build.addAction(setupAction);

        if (resource.getResourceType().isSetupRequired()) {
            logger.println("[resource-manager] Scheduling setup");
            setupAction.setNextStage(Stage.setUp);
            final Future<Queue.Executable> f = Hudson.getInstance().getQueue().schedule(setupAction, 0).getFuture();

            if (!async) {
                logger.println("[resource-manager] Waiting for setup complete");
                Result result = null;
                try {
                    result = ((SetupRun) f.get()).getResult();
                    logger.println("[resource-manager] Setup complete");
                } catch (ExecutionException e) {
                    e.getCause().printStackTrace(logger);
                }
                if (result != Result.SUCCESS) {
                    logger.println("[resource-manager] Setup failed");
                    label.release(resource);
                    logger.println("[resource-manager] Released " + resource);
                    return null;
                }
            }
        }

        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {

                try {
                    if (resource.getResourceType().isTearDownRequired()) {
                        setupAction.setNextStage(Stage.tearDown);
                        logger.println("[resource-manager] Scheduling tear down");
                        final Future<Queue.Executable> f = Hudson.getInstance().getQueue().schedule(setupAction, 0).getFuture();

                        logger.println("[resource-manager] Waiting for tear down complete");

                        Result result = null;
                        try {
                            result = ((SetupRun) f.get()).getResult();
                        } catch (ExecutionException e) {
                            e.getCause().printStackTrace(logger);
                        }
                        if (result == Result.SUCCESS) {
                            logger.println("[resource-manager] Tear down complete");
                        } else {
                            logger.println("[resource-manager] Tear down failed");
                        }
                        return result == Result.SUCCESS;
                    } else {
                        return true;
                    }
                } finally {
                    label.release(resource);
                    logger.println("[resource-manager] Released " + resource);
                }
            }

        };

    }

    @Extension
    public static class DescriptorImpl extends Descriptor<BuildWrapper> {

        public DescriptorImpl() {
        }

        public String getDisplayName() {
            return "Reserve a resource";
        }

        public Collection<Label> getLabels() {
            return ResourceManager.getInstance().getLabels().values();
        }

    }
}
