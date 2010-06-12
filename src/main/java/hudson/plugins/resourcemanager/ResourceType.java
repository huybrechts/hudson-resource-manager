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

public abstract class ResourceType implements Describable<ResourceType> {

	private String id;

	private boolean enabled;
	private boolean inUse;
	
	public ResourceType(String id) {
		this.id = id;
	}
	
	public Environment setUp(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		return null;
	}

    public Launcher decorateLauncher(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, RunnerAbortedException {
        return launcher;
    }
    
    public Descriptor<ResourceType> getDescriptor() {
        return Hudson.getInstance().getDescriptorOrDie(getClass());
    }

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isInUse() {
		return inUse;
	}

	public void setInUse(boolean inUse) {
		this.inUse = inUse;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}
    
    
}
