package com.studioqol.core.index;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable per-project snapshot of resolvable properties. Consumed by the hover (and later features). */
public final class PropertyIndex {

	private final Map<String, PropertyDefinition> byKey;
	private final List<String> environments;

	public PropertyIndex(Map<String, PropertyDefinition> byKey, List<String> environments) {
		this.byKey = Collections.unmodifiableMap(new LinkedHashMap<>(byKey));
		this.environments = List.copyOf(environments);
	}

	/** @return the definition for {@code key}, or {@code null} if not defined in the project. */
	public PropertyDefinition lookup(String key) {
		return key == null ? null : byKey.get(key);
	}

	public Set<String> allKeys() {
		return byKey.keySet();
	}

	/** Discovered environment names, ordered local, test, prod, example, then the rest. */
	public List<String> environments() {
		return environments;
	}
}
