package hudson.plugins.resourcemanager;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.EnvironmentContributingAction;
import hudson.model.InvisibleAction;
import hudson.model.ResourceActivity;
import hudson.model.ResourceList;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Messages;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;

import org.kohsuke.stapler.DataBoundConstructor;

public class ResourceBuildWrapper extends BuildWrapper implements Serializable, ResourceActivity {
	
	private final String label;

	@DataBoundConstructor
	public ResourceBuildWrapper(String label) {
		super();
		this.label = label;
	}
	
	public String getLabel() {
		return label;
	}

	public ResourceList getResourceList() {
		Label l = ResourceManager.getInstance().getLabels().get(this.label);
		if (l != null) {
			return new ResourceList().w(l.getResource());
		} else {
			return new ResourceList();
		}
	}

	public String getDisplayName() {
		return "Require resource";
	}
	
	@Override
	public Environment setUp(AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		
		final Label label = ResourceManager.getInstance().getLabels().get(this.label);
		listener.getLogger().println("Acquiring a resource from " + label + "...");
		final Resource resource = label.acquire();

		listener.getLogger().println("Acquired " + resource);
		
		boolean success = false;
		try {
			
			// startup
			if (resource.getStartCommand() != null) {
				String nodeId = resource.getNode();
				Node node = nodeId == null ? Hudson.getInstance() :  Hudson.getInstance().getNode(nodeId);
				
				if (node.toComputer() == null) {
					throw new IOException("Cannot start resource " + resource + " since node " + nodeId + " is offline");
				}
				
				success = execute(node, resource.getStartCommand(), resource.getId(), listener);
				
				if (!success) {
					listener.error("Aborting build since resource start was not successful");
					return null;
				}
				
			}
			
			build.addAction(new ResourceEnvironmentVariableAction(resource.getId()));

		} finally {
			if (!success)
				label.release(resource);
		}
		return new Environment() {
			@Override
			public boolean tearDown(AbstractBuild build, final BuildListener listener) throws IOException,
					InterruptedException {
				try {
					
					String stopCmd = resource.getStopCommand();
					if (stopCmd != null) {
						String nodeId = resource.getNode();
						Node node = nodeId == null ? Hudson.getInstance() :  Hudson.getInstance().getNode(nodeId);
						
						if (node.toComputer() == null) {
							throw new IOException("Cannot stop resource " + resource + " since node " + nodeId + " is offline");
						}
						
						execute(node, stopCmd, resource.getId(), listener);
						
					}

					return true;
				} finally {
					listener.getLogger().println("Releasing " + resource);
					label.release(resource);
				}
			}
		};
		
	}
	
	private boolean execute(Node node, String command, String resourceId, BuildListener listener) throws InterruptedException {
		Launcher launcher = node.createLauncher(listener);
		launcher = launcher.decorateFor(node);
        FilePath ws = node.getRootPath();
        FilePath script=null;
        try {
            try {
                script = ws.createTextTempFile("hudson", ".bat", command+"\r\nexit %ERRORLEVEL%", false);
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace(listener.fatalError(Messages.CommandInterpreter_UnableToProduceScript()));
                return false;
            }

            String[] cmd = new String[] {"cmd","/c","call",script.getRemote()};

            int r;
            try {
                EnvVars envVars = EnvVars.getRemote(node.getChannel());
                // on Windows environment variables are converted to all upper case,
                // but no such conversions are done on Unix, so to make this cross-platform,
                // convert variables to all upper cases.
                envVars.put("HUDSON_RESOURCE_ID", resourceId);
                r = launcher.launch().cmds(cmd).envs(envVars).stdout(listener).pwd(ws).join();
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace(listener.fatalError(Messages.CommandInterpreter_CommandFailed()));
                r = -1;
            }
            return r==0;
        } finally {
            try {
                if(script!=null)
                script.delete();
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace( listener.fatalError(Messages.CommandInterpreter_UnableToDelete(script)) );
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
	
	private static class ResourceEnvironmentVariableAction extends InvisibleAction implements EnvironmentContributingAction {
		
		private final String id;

		public ResourceEnvironmentVariableAction(String id) {
			super();
			this.id = id;
		}

		public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
			env.put("HUDSON_RESOURCE_ID", id);
		}

	}
}
