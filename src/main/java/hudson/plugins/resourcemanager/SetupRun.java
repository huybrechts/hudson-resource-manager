package hudson.plugins.resourcemanager;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BallColor;
import hudson.model.BuildListener;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.framework.io.LargeText;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SetupRun implements Queue.Executable {

    private static final Logger LOGGER = Logger.getLogger(SetupRun.class.getName());

    private SetupAction parent;
    private Stage stage;
    private Result result;
    private long timestamp = System.currentTimeMillis();

    public SetupRun(SetupAction parent, Stage stage) {
        this.parent = parent;
        this.stage = stage;
    }

    public SetupAction getParent() {
        return parent;
    }

    public Stage getStage() {
        return stage;
    }


    public void run() {
        StreamBuildListener listener = null;
        try {
            listener = new StreamBuildListener(new FileOutputStream(getLogFile()));

            if (run(listener)) {
                result = Result.SUCCESS;
            } else {
                result = Result.UNSTABLE;
            }

        } catch (FileNotFoundException e) {
            result = Result.FAILURE;
            LOGGER.log(Level.SEVERE, "Failed to write " + getLogFile(), e);
        } catch (InterruptedException e) {
            e.printStackTrace(listener.getLogger());
            result = Result.FAILURE;
        } catch (Exception e) {
            result = Result.FAILURE;
            e.printStackTrace(listener.getLogger());
        } finally {
            if (listener != null)
                listener.getLogger().close();
            if (result == null)
                result = Result.FAILURE;

            latch.countDown();
        }
    }

    public File getLogFile() {
        return new File(getBuild().getRootDir(), "resourceManager-" + stage.name() + ".log");
    }

    private AbstractBuild<?, ?> getBuild() {
        return getParent().getBuild();
    }

    public void doProgressiveLog(StaplerRequest req, StaplerResponse rsp) throws IOException {
        new LargeText(getLogFile(), isRunning()).doProgressText(req, rsp);
    }

    public Result getResult() {
        return result;
    }

    public boolean isRunning() {
        return result == null;
    }

    public long getEstimatedDuration() {
        return -1;
    }

    /**
     * Gets the icon color for display.
     */
    public BallColor getIconColor() {
        if (isRunning()) {
            return BallColor.BLUE_ANIME;
        } else {
            return getResult().color;
        }
    }

    public String getBuildStatusUrl() {
        return getIconColor() + ".gif";
    }

    public Calendar getTimestamp() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        return cal;
    }

    public String getNumber() {
        return stage.name();
    }

    public String getUrl() {
        return getParent().getUrl() + getNumber() + "/";
    }

    public HttpResponse doConsole() {
        return HttpResponses.redirectViaContextPath(getUrl());
    }


    private boolean run(BuildListener listener) throws InterruptedException, IOException {

        Resource resource = ResourceManager.getInstance().getResource(parent.getResourceId());

        Node node = Executor.currentExecutor().getOwner().getNode();
        final Launcher launcher = node.createLauncher(listener);

        listener.getLogger().println("[resource-manager] starting " + stage.toString());

        boolean success = false;

        switch (stage) {
            case setUp:
                success = resource.getResourceType().setUp(resource.getId(), parent.getBuild(), launcher, listener);
                break;
            case tearDown:
                success = resource.getResourceType().tearDown(resource.getId(), parent.getBuild(), launcher, listener);
        }

        if (!success) {
            listener.getLogger().println("[resource-manager] " + stage + " failed!");
            return false;
        } else {
            return true;
        }

    }

    private transient CountDownLatch latch = new CountDownLatch(1);

    public void waitForCompletion() throws InterruptedException {
        latch.await();
    }

    public boolean waitForCompletion(int seconds) throws InterruptedException {
        return latch.await(seconds, TimeUnit.SECONDS);
    }

    public void doWait(HttpServletResponse rsp, @QueryParameter int seconds) throws InterruptedException, IOException {
        if (seconds == 0) {
            seconds = 300;
        }
        if (waitForCompletion(seconds)) {
            if (result == Result.SUCCESS) {
                rsp.getOutputStream().println("ready");
            } else {
                rsp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                rsp.getOutputStream().println("errror");
            }
        } else {
            rsp.getOutputStream().println("not ready");
        }
    }


}