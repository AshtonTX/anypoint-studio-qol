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
import com.studioqol.core.MuleProjectLayout;

/**
 * Invalidates a project's cached indexes when relevant sources change — env {@code .properties} or
 * Mule config XML for the property index; Mule config XML or {@code .dwl} files for the usage index
 * — and announces the change on the {@link ProjectChangeBus}. Runs off the UI thread; only does
 * cheap delta inspection here.
 */
public final class MuleResourceListener implements IResourceChangeListener {

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		if (event.getType() != IResourceChangeEvent.POST_CHANGE) {
			return;
		}
		IResourceDelta root = event.getDelta();
		if (root == null) {
			return;
		}
		Set<IProject> propertyAffected = new HashSet<>();
		Set<IProject> usageAffected = new HashSet<>();
		try {
			root.accept(delta -> {
				IResource res = delta.getResource();
				if (res.getType() == IResource.FILE) {
					IFile file = (IFile) res;
					if (isPropertyRelevant(file)) {
						propertyAffected.add(file.getProject());
					}
					if (isUsageRelevant(file)) {
						usageAffected.add(file.getProject());
					}
				}
				return true;
			});
		} catch (CoreException e) {
			return;
		}
		for (IProject p : propertyAffected) {
			StudioQolPlugin.indexManager().invalidate(p);
		}
		for (IProject p : usageAffected) {
			StudioQolPlugin.usageManager().invalidate(p);
		}
		Set<IProject> affected = new HashSet<>(propertyAffected);
		affected.addAll(usageAffected);
		for (IProject p : affected) {
			StudioQolPlugin.changeBus().fire(p, ProjectChangeBus.Kind.INDEX);
		}
	}

	private static boolean isPropertyRelevant(IFile file) {
		String ext = file.getFileExtension();
		String path = file.getProjectRelativePath().toString();
		if ("properties".equalsIgnoreCase(ext) && path.startsWith(MuleProjectLayout.ENVS_DIR + "/")) {
			return true;
		}
		return isMuleXml(ext, path);
	}

	private static boolean isUsageRelevant(IFile file) {
		String ext = file.getFileExtension();
		String path = file.getProjectRelativePath().toString();
		if ("dwl".equalsIgnoreCase(ext) && path.startsWith(MuleProjectLayout.RESOURCES_DIR + "/")) {
			return true;
		}
		return isMuleXml(ext, path);
	}

	private static boolean isMuleXml(String ext, String path) {
		return "xml".equalsIgnoreCase(ext) && path.startsWith(MuleProjectLayout.MULE_DIR + "/");
	}
}
