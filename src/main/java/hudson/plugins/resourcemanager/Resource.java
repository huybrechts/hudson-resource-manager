package hudson.plugins.resourcemanager;

import hudson.Util;
import hudson.model.Hudson;
import hudson.model.Descriptor.FormException;

import java.io.IOException;
import java.io.Serializable;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class Resource implements Serializable {

	private static final long serialVersionUID = 1L;

	private String id;
	private String label;
	private String startCommand;
	private String stopCommand;
	private String node;
	private boolean enabled;

	private boolean inUse;
	
	public Resource() {};

	@DataBoundConstructor
	public Resource(String id, String label, String startCommand,
			String stopCommand, String node, boolean enabled) {
		super();
		this.id = id;
		this.label = label;
		this.startCommand = Util.fixEmptyAndTrim(startCommand);
		this.stopCommand = Util.fixEmptyAndTrim(stopCommand);
		this.node = Util.fixEmptyAndTrim(node);
		this.enabled = enabled;
	}

	public String getId() {
		return id;
	}

	public String getLabel() {
		return label;
	}

	public String getStartCommand() {
		return startCommand;
	}

	public String getStopCommand() {
		return stopCommand;
	}

	public String getNode() {
		return node;
	}

	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public String toString() {
		return id;
	}
	
	public void setId(String id) {
		this.id = id;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public void setStartCommand(String startCommand) {
		this.startCommand = startCommand;
	}

	public void setStopCommand(String stopCommand) {
		this.stopCommand = stopCommand;
	}

	public void setNode(String node) {
		this.node = node;
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

	public void doSubmit(StaplerRequest req, StaplerResponse rsp)
			throws ServletException, IOException, FormException {
		Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
		
		boolean isNew = id == null;
		
		Resource r = req.bindJSON(Resource.class, req.getSubmittedForm());
		if (Util.fixEmptyAndTrim(r.id) == null) {
			throw new IOException("id is required");
		}
		String node = Util.fixEmptyAndTrim(r.node);
		if (node != null && Hudson.getInstance().getNode(node) == null) {
			throw new IOException("unknown node: " + node);
		}
		
		req.bindJSON(this, req.getSubmittedForm());
		this.label = Util.fixEmptyAndTrim(this.label);
		this.id = Util.fixEmptyAndTrim(this.id);
		this.startCommand = Util.fixEmptyAndTrim(this.startCommand);
		this.stopCommand = Util.fixEmptyAndTrim(this.stopCommand);
		this.node = Util.fixEmptyAndTrim(this.node);
		
		if (isNew) {
			ResourceManager.getInstance().addResource(this);
		} else {
			ResourceManager.getInstance().update();
		}
		
		
		rsp.sendRedirect(Hudson.getInstance().getRootUrl() + "resourceManager");
	}
	
	public void doDelete(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
		if (req.getMethod().equals("POST")) {
			ResourceManager.getInstance().removeResource(this);
			rsp.sendRedirect(Hudson.getInstance().getRootUrl() + "resourceManager");
		} else {
			rsp.forward(this, "confirmDelete", req);
		}
	}
}
