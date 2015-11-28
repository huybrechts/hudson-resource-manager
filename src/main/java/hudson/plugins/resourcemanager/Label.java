package hudson.plugins.resourcemanager;

import hudson.model.Api;
import hudson.model.ModelObject;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Semaphore;

@ExportedBean
public class Label {

	private List<Resource> resources;
	private Semaphore semaphore;
	private final String name;
	
	public Label(String name, List<Resource> resources) {
		this.name = name;
		this.resources = new ArrayList<Resource>(resources);
		this.semaphore = new Semaphore(resources.size());
	}

	public synchronized void update(List<Resource> newResources) {
		List<Resource> toRemove = new ArrayList<Resource>(resources);
		toRemove.removeAll(newResources);

		for (Resource r : toRemove) {
			if (!r.isInUse()) {
				// remove this resource from the semaphore if is not in use (else it would already be removed)
				if (!semaphore.tryAcquire()) {
					throw new AssertionError(
							"could not acquire a resource that should be available");
				}
			}
		}
		
		List<Resource> toAdd = new ArrayList<Resource>(newResources);
		toAdd.removeAll(resources);
		semaphore.release(toAdd.size());

		this.resources = newResources;

	}

	@Exported
	public List<Resource> getResources() {
		return new ArrayList<Resource>(resources);
	}

	public Resource acquire(ModelObject owner) throws InterruptedException {
		semaphore.acquire();
		synchronized (this) {
			List<Resource> availableResources = getAvailableResources();
			Resource result = availableResources.get(new Random().nextInt(availableResources.size()));
			result.setInUse(true);
			result.setOwner(owner);
			return result;
		}
	}

	public void release(Resource resource) {
		synchronized (this) {
			resource.setInUse(false);
			resource.setOwner(null);
			if (resources.contains(resource)) {
				semaphore.release();
			}
		}
	}

	public String getName() {
		return name;
	}

	public String toString() {
		if (resources.size() == 1 && resources.get(0).getId().equals(name)) {
			return name;
		} else {
			return name + " " + resources;
		}
	}

	public hudson.model.Resource getResource() {
		if (resources.isEmpty()) {
			return null;
		} else {
			return new hudson.model.Resource(null, name, resources.size());
		}
	}

	public synchronized List<Resource> getAvailableResources() {
		List<Resource> result = new ArrayList<Resource>();
		for (Resource r: resources) {
			if (!r.isInUse()) result.add(r); 
		}
		return result;
	}

	public Api getApi() {
		return new Api(this);
	}
}
