package com.studioqol.core.resource;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.core.resources.IProject;

import com.studioqol.StudioQolPlugin;

/**
 * Per-project change notifications shared by all features: {@code INDEX} when the property/usage
 * indexes were invalidated by a resource change, {@code ACTIVE_ENV} when the user switched the
 * active environment. Deliberately not a listener API on the index managers — a {@code .dwl}
 * change must trigger revalidation without touching the property index.
 */
public final class ProjectChangeBus {

	public enum Kind {
		INDEX, ACTIVE_ENV
	}

	/** Note: {@link #changed} is called on an ARBITRARY thread; schedule a Job/asyncExec as needed. */
	public interface Listener {
		void changed(IProject project, Kind kind);
	}

	private final List<Listener> listeners = new CopyOnWriteArrayList<>();

	public void addListener(Listener listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}

	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	public void fire(IProject project, Kind kind) {
		for (Listener listener : listeners) {
			try {
				listener.changed(project, kind);
			} catch (RuntimeException e) {
				StudioQolPlugin.log(e); // one broken listener must not starve the rest
			}
		}
	}
}
