package com.studioqol.core.index;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IProject;

import com.studioqol.core.MuleProjects;

/** Per-project cache of {@link PropertyIndex} snapshots. Rebuilds lazily after invalidation. */
public final class PropertyIndexManager {

	private final Map<IProject, PropertyIndex> cache = new ConcurrentHashMap<>();
	private final PropertyIndexBuilder builder = new PropertyIndexBuilder();

	/** @return the index for a Mule project (building/caching on demand), or {@code null} otherwise. */
	public PropertyIndex get(IProject project) {
		if (!MuleProjects.isMuleProject(project)) {
			return null;
		}
		return cache.computeIfAbsent(project, builder::build);
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
