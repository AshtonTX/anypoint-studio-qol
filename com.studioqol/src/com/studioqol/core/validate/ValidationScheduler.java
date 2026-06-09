package com.studioqol.core.validate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;

import com.studioqol.core.MuleProjects;
import com.studioqol.core.resource.ProjectChangeBus;

/**
 * Debounces validation to one {@link PropertyValidationJob} per project: every index change
 * cancels the pending run and reschedules 500 ms out, so rapid saves coalesce. Active-env changes
 * are ignored — validation results are env-independent by design.
 */
public final class ValidationScheduler implements ProjectChangeBus.Listener {

	private static final long DEBOUNCE_MS = 500;

	private final Map<IProject, PropertyValidationJob> jobs = new ConcurrentHashMap<>();

	@Override
	public void changed(IProject project, ProjectChangeBus.Kind kind) {
		if (kind == ProjectChangeBus.Kind.INDEX) {
			schedule(project);
		}
	}

	public void schedule(IProject project) {
		if (project == null || !MuleProjects.isMuleProject(project)) {
			return;
		}
		PropertyValidationJob job = jobs.computeIfAbsent(project, PropertyValidationJob::new);
		job.cancel();
		job.schedule(DEBOUNCE_MS);
	}

	/** Validates every open Mule project — run at startup, since markers are transient. */
	public void validateAllOpen() {
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (project.isAccessible()) {
				schedule(project);
			}
		}
	}

	public void dispose() {
		for (PropertyValidationJob job : jobs.values()) {
			job.cancel();
		}
		jobs.clear();
	}
}
