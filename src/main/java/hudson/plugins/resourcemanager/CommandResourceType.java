package hudson.plugins.resourcemanager;

import hudson.model.Descriptor;

public class CommandResourceType extends ResourceType {
	
	

	public static class DescriptorImpl extends Descriptor<ResourceType> {

		@Override
		public String getDisplayName() {
			return "Start/stop using Windows batch command";
		}
		
	}

}
