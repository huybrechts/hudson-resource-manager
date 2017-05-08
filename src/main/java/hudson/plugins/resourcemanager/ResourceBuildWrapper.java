package hudson.plugins.resourcemanager;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildWrapper;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import hudson.util.QuotedStringTokenizer;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildWrapper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class ResourceBuildWrapper extends SimpleBuildWrapper implements Serializable, ResourceActivity {

    private final String label;

    @DataBoundConstructor
    public ResourceBuildWrapper(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public List<Label> getLabels() {
        List<Label> result = new ArrayList<Label>();
        for (String l : new QuotedStringTokenizer(label).toArray()) {
            Label label = ResourceManager.getInstance().getLabels().get(l);
            if (label != null) {
                result.add(label);
            }
        }
        return result;
    }

    public ResourceList getResourceList() {
        ResourceList result = new ResourceList();
        for (Label l: getLabels()) {
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
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {

        final PrintStream logger = listener.getLogger();

        Result result = Result.SUCCESS;

        for (Label label: getLabels()) {
            logger.println("[resource-manager] Acquiring a resource from " + label + "...");
            final Resource resource = label.acquire(build);

            logger.println("[resource-manager] Acquired " + resource);

            final SetupAction setupAction = new SetupAction(build, resource.getId(), label.getName());
            build.addAction(setupAction);

            if (resource.getResourceType().isSetupRequired()) {
                logger.println("[resource-manager] Scheduling setup");
                final Future<Queue.Executable> f = Hudson.getInstance().getQueue().schedule(setupAction, 0).getFuture();

                logger.println("[resource-manager] Waiting for setup complete");
                try {
                    result = result.combine(((SetupRun) f.get()).getResult());
                    logger.println("[resource-manager] Setup complete");
                } catch (ExecutionException e) {
                    e.getCause().printStackTrace(logger);
                }
                if (result != Result.SUCCESS) {
                    logger.println("[resource-manager] Setup failed");
                    break;
                }
            }
        }
        if (result != Result.SUCCESS) {
            for (SetupAction action: build.getActions(SetupAction.class)) {
                Label label = ResourceManager.getInstance().getLabels().get(action.getLabel());
                Resource resource = ResourceManager.getInstance().getResource(action.getResourceId());
                label.release(resource);
                logger.println("[resource-manager] Released " + resource);
            }
            return;
        }

        context.setDisposer(new ResourceDisposer());


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

        public AutoCompletionCandidates doAutoCompleteLabel(@QueryParameter String value) {
            AutoCompletionCandidates c = new AutoCompletionCandidates();
            Set<String> labels = ResourceManager.getInstance().getLabels().keySet();
            for (String label: ResourceManager.getInstance().getLabels().keySet()) {
                if (label.startsWith(value)) c.add(label);
            }
            return c;
        }
    }

    private static class ResourceDisposer extends Disposer {
        @Override
        public void tearDown(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            PrintStream logger = listener.getLogger();
            Result result = Result.SUCCESS;
            for (SetupAction setupAction: build.getActions(SetupAction.class)) {
                Resource resource = ResourceManager.getInstance().getResource(setupAction.getResourceId());
                try {
                    if (resource.getResourceType().isTearDownRequired()) {
                        logger.println("[resource-manager] Scheduling tear down");
                        final Future<Queue.Executable> f = Hudson.getInstance().getQueue().schedule(setupAction, 0).getFuture();

                        logger.println("[resource-manager] Waiting for tear down complete");

                        try {
                            result = result.combine(((SetupRun) f.get()).getResult());
                        } catch (ExecutionException e) {
                            e.getCause().printStackTrace(logger);
                        }
                    }
                } finally {
                    ResourceManager.getInstance().getLabels().get(setupAction.getLabel()).release(resource);
                    logger.println("[resource-manager] Released " + resource);
                }
            }
            if (result == Result.SUCCESS) {
                logger.println("[resource-manager] Tear down complete");
            } else {
                logger.println("[resource-manager] Tear down failed");
            }
        }
    }
}
