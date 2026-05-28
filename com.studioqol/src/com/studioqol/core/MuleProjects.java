package com.studioqol.core;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

/** Detects Mule Studio projects so the property index ignores everything else. */
public final class MuleProjects {

	/** Confirmed in the fixture's .project (NOT the older "muleNature"). */
	public static final String MULE_NATURE_ID = "org.mule.tooling.core.muleStudioNature";
	public static final String MULE_ARTIFACT = "mule-artifact.json";

	private MuleProjects() {
	}

	public static boolean isMuleProject(IProject project) {
		if (project == null || !project.isAccessible()) {
			return false;
		}
		try {
			if (project.hasNature(MULE_NATURE_ID)) {
				return true;
			}
		} catch (CoreException e) {
			// fall through to the descriptor check
		}
		return project.getFile(MULE_ARTIFACT).exists();
	}
}
