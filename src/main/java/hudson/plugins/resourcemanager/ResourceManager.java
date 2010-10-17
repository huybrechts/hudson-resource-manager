package hudson.plugins.resourcemanager;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Descriptor.FormException;
import hudson.model.AbstractBuild;
import hudson.model.ManagementLink;
import hudson.model.Hudson;
import hudson.model.PeriodicWork;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

@Extension
public class ResourceManager extends ManagementLink {

	private List<Resource> resources = new ArrayList<Resource>();
	private transient Map<String, Label> labels = Collections
			.synchronizedMap(new HashMap<String, Label>());

	public ResourceManager() {
		try {
			if (getConfigFile().exists())
				new XmlFile(getConfigFile()).unmarshal(this);
		} catch (IOException e) {
			e.printStackTrace();
		}

		buildLabels();

	}

	public String getDisplayName() {
		return "Resource Manager";
	}

	@Override
	public String getIconFileName() {
		return "package.gif";
	}

	@Override
	public String getUrlName() {
		return "resourceManager";
	}

	public synchronized void doSubmit(StaplerRequest req, StaplerResponse rsp)
			throws ServletException, IOException, FormException {
		Hudson.getInstance().checkPermission(Hudson.ADMINISTER);

		JSONObject form = req.getSubmittedForm();
		this.resources = req.bindJSONToList(Resource.class,
				form.get("resources"));

		buildLabels();

		save();

		rsp.forwardToPreviousPage(req);
	}

	public synchronized void doSubmitNewResource(StaplerRequest req,
			StaplerResponse rsp) throws IOException, ServletException {
		Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
		Resource resource = req
				.bindJSON(Resource.class, req.getSubmittedForm());
		resources.add(resource);

		update();
	}

	public Map<String, Label> getLabels() {
		return labels;
	}

	public synchronized List<Resource> getResources() {
		return Collections.unmodifiableList(resources);
	}

	public synchronized Resource getResource(String id) {
		for (Resource resource : resources) {
			if (resource.getId().equals(id)) {
				return resource;
			}
		}
		return null;
	}

	public synchronized void removeResource(Resource resource)
			throws IOException {
		resources.remove(resource);
		update();
	}

	public Resource getNewResource() {
		return new Resource();
	}

	private File getConfigFile() {
		return new File(Hudson.getInstance().getRootDir(), getClass().getName()
				+ ".xml");
	}

	public void save() throws IOException {
		new XmlFile(getConfigFile()).write(this);
	}

	private synchronized void buildLabels() {
		Map<String, List<Resource>> map = new HashMap<String, List<Resource>>();
		for (Resource resource : resources) {
			if (!resource.isEnabled())
				continue;
			String label = resource.getLabel();
			if (label != null) {
				List<Resource> list = map.get(label);
				if (list == null) {
					map.put(label, list = new ArrayList<Resource>());
				}
				list.add(resource);
			} else {
				map.put(resource.getId(), Arrays.asList(resource));
			}
		}

		Map<String, Label> newLabels = new HashMap<String, Label>();
		for (Map.Entry<String, List<Resource>> e : map.entrySet()) {
			Label l = labels.get(e.getKey());
			if (l != null) {
				l.update(e.getValue());
			} else {
				l = new Label(e.getKey(), e.getValue());
			}
			newLabels.put(e.getKey(), l);
		}

		labels = newLabels;
	}

	public static ResourceManager getInstance() {
		return ManagementLink.all().get(ResourceManager.class);
	}

	public synchronized void update() throws IOException {
		buildLabels();
		save();
	}

	public synchronized void addResource(Resource resource) throws IOException {
		resources.add(resource);
		update();
	}

	public synchronized HttpResponse doCleanup() {
		for (Label l : getLabels().values()) {
			for (Resource r : l.getResources()) {
				synchronized (r) {
					if (r.isInUse() && r.getOwner() instanceof AbstractBuild) {
						AbstractBuild build = ((AbstractBuild) r.getOwner());
						if (!build.isBuilding() && !build.hasntStartedYet()) {
							l.release(r);
						}
					}
				}
			}
		}
		return HttpResponses.forwardToPreviousPage();
	}

	@Extension
	public static class ResourceManagerCleanup extends PeriodicWork {
		@Override
		public long getRecurrencePeriod() {
			return 60000;
		}

		@Override
		protected void doRun() throws Exception {
			getInstance().doCleanup();
		}

	}
}
