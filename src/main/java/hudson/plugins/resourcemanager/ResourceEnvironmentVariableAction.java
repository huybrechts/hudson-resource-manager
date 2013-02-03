package hudson.plugins.resourcemanager;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;
import hudson.model.InvisibleAction;

@Deprecated
class ResourceEnvironmentVariableAction extends InvisibleAction implements EnvironmentContributingAction {
	
	private final String id;

	public ResourceEnvironmentVariableAction(String id) {
		super();
		this.id = id;
	}

	public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
		env.put("HUDSON_RESOURCE_ID", id);
	}

}