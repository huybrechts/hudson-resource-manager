package hudson.plugins.resourcemanager;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.ResourceActivity;
import hudson.model.ResourceList;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Messages;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;

import org.kohsuke.stapler.DataBoundConstructor;

public class ResourceBuildWrapper extends BuildWrapper implements Serializable,
		ResourceActivity {

	private final String label;

	@DataBoundConstructor
	public ResourceBuildWrapper(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
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

		final Label label = ResourceManager.getInstance().getLabels().get(
				this.label);
		listener.getLogger().println(
				"Acquiring a resource from " + label + "...");
		final Resource resource = label.acquire(build);

		listener.getLogger().println("Acquired " + resource);

		hudson.model.Environment environment = null;
		try {
			environment = resource.getResourceType().setUp(resource.getId(), build, launcher, listener);

			if (environment != null) {
				build.addAction(new ResourceEnvironmentVariableAction(resource
						.getId()));
			}
		} finally {
			if (environment == null) {
				label.release(resource);
			}
		}

		if (environment == null) {
			return null;
		} else {
			return tearDown(environment, label, resource);
		}
		

	}

	private Environment tearDown(final hudson.model.Environment environment,
			final Label label, final Resource resource) {
		return new Environment() {
			@Override
			public boolean tearDown(AbstractBuild build, BuildListener listener)
					throws IOException, InterruptedException {
				try {

					return environment.tearDown(build, listener);

				} finally {
					listener.getLogger().println("Releasing " + resource);
					label.release(resource);
				}
			}
		};
	}

	private boolean execute(Node node, String command, String resourceId,
			BuildListener listener) throws InterruptedException {
		Launcher launcher = node.createLauncher(listener);
		launcher = launcher.decorateFor(node);
		FilePath ws = node.getRootPath();
		FilePath script = null;
		try {
			try {
				script = ws.createTextTempFile("hudson", ".bat", command
						+ "\r\nexit %ERRORLEVEL%", false);
			} catch (IOException e) {
				Util.displayIOException(e, listener);
				e.printStackTrace(listener.fatalError(Messages
						.CommandInterpreter_UnableToProduceScript()));
				return false;
			}

			String[] cmd = new String[] { "cmd", "/c", "call",
					script.getRemote() };

			int r;
			try {
				EnvVars envVars = EnvVars.getRemote(node.getChannel());
				// on Windows environment variables are converted to all upper
				// case,
				// but no such conversions are done on Unix, so to make this
				// cross-platform,
				// convert variables to all upper cases.
				envVars.put("HUDSON_RESOURCE_ID", resourceId);
				r = launcher.launch().cmds(cmd).envs(envVars).stdout(listener)
						.pwd(ws).join();
			} catch (IOException e) {
				Util.displayIOException(e, listener);
				e.printStackTrace(listener.fatalError(Messages
						.CommandInterpreter_CommandFailed()));
				r = -1;
			}
			return r == 0;
		} finally {
			try {
				if (script != null)
					script.delete();
			} catch (IOException e) {
				Util.displayIOException(e, listener);
				e.printStackTrace(listener.fatalError(Messages
						.CommandInterpreter_UnableToDelete(script)));
			}
		}

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
