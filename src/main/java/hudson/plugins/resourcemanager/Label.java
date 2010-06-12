package hudson.plugins.resourcemanager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;

public class Label {

	private List<Resource> allResources;
	private List<Resource> availableResources;
	private Semaphore sem;
	private final String name;

	private final hudson.model.Resource resource;

	public Label(String name, List<Resource> Resources) {
		this.name = name;
		this.allResources = Collections.synchronizedList(new ArrayList<Resource>(Resources));
		this.availableResources = new ArrayList<Resource>(Resources);
		this.sem = new Semaphore(Resources.size());
		this.resource = new hudson.model.Resource(null, name, Resources.size());
	}

	public synchronized void update(List<Resource> Resources) {
		ArrayList<Resource> newAvailableResources = new ArrayList<Resource>(Resources);
		Iterator<Resource> it = newAvailableResources.iterator();
		while (it.hasNext()) {
			Resource resource = it.next();
			if (allResources.contains(resource) && !availableResources.contains(resource)) { // so it is in use
				it.remove();
			}
		}
		
		allResources = Resources;
		availableResources = newAvailableResources;
		sem = new Semaphore(availableResources.size());
	}

	public List<Resource> getResources() {
		return allResources;
	}

	public Resource acquire() throws InterruptedException {
		sem.acquire();
		synchronized (this) {
			Resource result = availableResources.remove(0);
			if (result.isInUse()) {
				throw new AssertionError("acquired resource that is in use");
			}
			result.setInUse(true);
			return result;
		}
	}

	public void release(Resource resource) {
		synchronized (this) {
			resource.setInUse(false);
			if (allResources.contains(resource)) {
				availableResources.add(resource);
				sem.release();
			}
		}
	}

	public String getName() {
		return name;
	}

	public String toString() {
		if (allResources.size() == 1 && allResources.get(0).getId().equals(name)) {
			return name;
		} else {
			return name + " " + allResources;
		}
	}

	public hudson.model.Resource getResource() {
		return resource;
	}

	public List<Resource> getAvailableResources() {
		return availableResources;
	}

}
