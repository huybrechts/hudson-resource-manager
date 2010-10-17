package hudson.plugins.resourcemanager;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Environment;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.tasks.Messages;

import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;

public class CommandResourceType extends ResourceType {
	
	private final String startCommand;
	private final String stopCommand;
	private final String nodeId;

	@DataBoundConstructor
	public CommandResourceType(String startCommand,
			String stopCommand, String nodeId) {
		this.startCommand = Util.fixEmptyAndTrim(startCommand);
		this.stopCommand = Util.fixEmptyAndTrim(stopCommand);
		this.nodeId = Util.fixEmptyAndTrim(nodeId);
	}
	@Override
	public Environment setUp(final String id, AbstractBuild build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		boolean success = false;

			// startup
			if (startCommand != null) {
				Node node = nodeId == null ? Hudson.getInstance() : Hudson
						.getInstance().getNode(nodeId);

				if (node.toComputer() == null) {
					throw new IOException("Cannot start resource " + this
							+ " since node " + nodeId + " is offline");
				}

				success = execute(node, startCommand, id, listener);

				if (!success) {
					listener.error("Aborting build since resource start was not successful");
					return null;
				}

			}

			build.addAction(new ResourceEnvironmentVariableAction(id));

		return new Environment() {
			@Override
			public boolean tearDown(AbstractBuild build,
					final BuildListener listener) throws IOException,
					InterruptedException {

				String stopCmd = stopCommand;
				if (stopCmd != null) {
					Node node = nodeId == null ? Hudson.getInstance() : Hudson
							.getInstance().getNode(nodeId);

					if (node.toComputer() == null) {
						throw new IOException("Cannot stop resource "
								+ this + " since node " + nodeId
								+ " is offline");
					}

					execute(node, stopCmd, id, listener);

				}

				return true;
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
	public static class DescriptorImpl extends Descriptor<ResourceType> {

		@Override
		public String getDisplayName() {
			return "Start/stop using Windows batch command";
		}

	}

	public String getStartCommand() {
		return startCommand;
	}
	public String getStopCommand() {
		return stopCommand;
	}
	public String getNodeId() {
		return nodeId;
	}

}
