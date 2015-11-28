package hudson.plugins.resourcemanager;

import hudson.Util;
import hudson.model.Api;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.ModelObject;
import hudson.model.Hudson;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class Resource implements Serializable {

	private static final long serialVersionUID = 1L;

	@Exported
	private String id;

	@Exported
	private String label;

	@Exported
	private boolean enabled;

	private ResourceType resourceType;

	@Exported
	private transient boolean inUse;

	private transient ModelObject owner;
	
	public Resource() {};

	@DataBoundConstructor
	public Resource(String id, String label, boolean enabled, ResourceType resourceType) {
		super();
		this.label = Util.fixEmptyAndTrim(label);
		this.id = Util.fixEmptyAndTrim(id);
		this.enabled = enabled;
		this.resourceType = resourceType;
	}

	public String getId() {
		return id;
	}

	public String getLabel() {
		return label;
	}

	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public String toString() {
		return id;
	}
	
	public void setId(String id) {
		this.id = Util.fixEmptyAndTrim(id);
		
	}

	public void setLabel(String label) {
		this.label = Util.fixEmptyAndTrim(label);
		
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
	
	public ResourceType getResourceType() {
		return resourceType;
	}

	public void doSubmit(StaplerRequest req, StaplerResponse rsp)
			throws ServletException, IOException, FormException {
		Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
		
		boolean isNew = id == null;
		
		JSONObject form = req.getSubmittedForm();
		
		if (Util.fixEmptyAndTrim(form.getString("id")) == null) {
			throw new IOException("id is required");
		}
		
		req.bindJSON(this, form);
		
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
	
	public List<Descriptor<ResourceType>> getResourceTypes() {
		return ResourceType.all();
	}

	public void setResourceType(ResourceType resourceType) {
		this.resourceType = resourceType;
	}
	
	public HttpResponse doEnable(@QueryParameter boolean enable) throws IOException {
		boolean modified = this.enabled != enable;
		this.enabled = enable;
		if (modified) ResourceManager.getInstance().update();
		return HttpResponses.forwardToPreviousPage(); 
	}

	public void setOwner(ModelObject owner) {
		this.owner = owner;
	}
	
	public ModelObject getOwner() {
		return owner;
	}

	public Api getApi() {
		return new Api(this);
	}
	
}
