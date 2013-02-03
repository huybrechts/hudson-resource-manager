package hudson.plugins.resourcemanager;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Environment;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;
import hudson.model.Run.RunnerAbortedException;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

public abstract class ResourceType implements Describable<ResourceType>, Serializable {

    public abstract String getNodeId();

	public boolean setUp(String id, AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
        return true;
    }

    public boolean tearDown(String id, AbstractBuild build, Launcher launcher,
                                  BuildListener listener) throws IOException, InterruptedException {
        return true;
    }

    public boolean isSetupRequired() {
        return true;
    }

    public boolean isTearDownRequired() {
        return true;
    }

    public Descriptor<ResourceType> getDescriptor() {
        return Hudson.getInstance().getDescriptorOrDie(getClass());
    }

	public static List<Descriptor<ResourceType>> all() {
		return Hudson.getInstance().<ResourceType,Descriptor<ResourceType>>getDescriptorList(ResourceType.class);
	}

}
