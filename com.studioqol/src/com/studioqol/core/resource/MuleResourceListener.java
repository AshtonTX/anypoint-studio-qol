package com.studioqol.core.resource;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.index.PropertyIndexManager;

/**
 * Invalidates a project's cached index when its env {@code .properties} or Mule config XML change,
 * so the next hover rebuilds, and announces the change on the {@link ProjectChangeBus}. Runs off
 * the UI thread; only does cheap delta inspection here.
 */
public final class MuleResourceListener implements IResourceChangeListener {

	private final PropertyIndexManager manager;

	public MuleResourceListener(PropertyIndexManager manager) {
		this.manager = manager;
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		if (event.getType() != IResourceChangeEvent.POST_CHANGE) {
			return;
		}
		IResourceDelta root = event.getDelta();
		if (root == null) {
			return;
		}
		Set<IProject> affected = new HashSet<>();
		try {
			root.accept(delta -> {
				IResource res = delta.getResource();
				if (res.getType() == IResource.FILE && isRelevant((IFile) res)) {
					affected.add(res.getProject());
				}
				return true;
			});
		} catch (CoreException e) {
			return;
		}
		for (IProject p : affected) {
			manager.invalidate(p);
			StudioQolPlugin.changeBus().fire(p, ProjectChangeBus.Kind.INDEX);
		}
	}

	private static boolean isRelevant(IFile file) {
		String ext = file.getFileExtension();
		String path = file.getProjectRelativePath().toString();
		if ("properties".equalsIgnoreCase(ext) && path.startsWith("src/main/resources/envs/")) {
			return true;
		}
		return "xml".equalsIgnoreCase(ext) && path.startsWith("src/main/mule/");
	}
}
