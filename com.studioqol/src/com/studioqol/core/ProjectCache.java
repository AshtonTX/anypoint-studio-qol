package com.studioqol.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.eclipse.core.resources.IProject;

/**
 * Per-Mule-project cache of immutable snapshots (property index, usage index, …). Builds lazily on
 * first {@link #get}, rebuilds lazily after {@link #invalidate}. Thread-safe.
 */
public final class ProjectCache<T> {

	private final Map<IProject, T> cache = new ConcurrentHashMap<>();
	private final Function<IProject, T> builder;

	public ProjectCache(Function<IProject, T> builder) {
		this.builder = builder;
	}

	/** @return the snapshot for a Mule project (building/caching on demand), or {@code null} otherwise. */
	public T get(IProject project) {
		if (!MuleProjects.isMuleProject(project)) {
			return null;
		}
		return cache.computeIfAbsent(project, builder);
	}

	public void invalidate(IProject project) {
		if (project != null) {
			cache.remove(project);
		}
	}

	public void dispose() {
		cache.clear();
	}
}
