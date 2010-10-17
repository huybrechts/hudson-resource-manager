package hudson.plugins.resourcemanager;

import java.io.IOException;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Environment;

import org.kohsuke.stapler.DataBoundConstructor;

public class SimpleResourceType extends ResourceType {

	@DataBoundConstructor
	public SimpleResourceType() {}
	
	@Extension
	public static class DescriptorImpl extends Descriptor<ResourceType> {

		@Override
		public String getDisplayName() {
			return "Simple Resource";
		}
	}
	
}
