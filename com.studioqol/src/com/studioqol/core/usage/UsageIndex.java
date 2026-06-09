package com.studioqol.core.usage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable per-project snapshot of property usages ({@code ${key}} in XML, {@code p('key')} in
 * DataWeave). Mirror of {@link com.studioqol.core.index.PropertyIndex} for the reference side.
 */
public final class UsageIndex {

	private final Map<String, List<PropertyUsage>> byKey;
	private final int total;

	public UsageIndex(Map<String, List<PropertyUsage>> byKey) {
		Map<String, List<PropertyUsage>> copy = new LinkedHashMap<>();
		int n = 0;
		for (Map.Entry<String, List<PropertyUsage>> e : byKey.entrySet()) {
			copy.put(e.getKey(), List.copyOf(e.getValue()));
			n += e.getValue().size();
		}
		this.byKey = Collections.unmodifiableMap(copy);
		this.total = n;
	}

	/** @return all usages of {@code key}, in file/line order; empty when unused. */
	public List<PropertyUsage> usagesOf(String key) {
		List<PropertyUsage> usages = key == null ? null : byKey.get(key);
		return usages == null ? List.of() : usages;
	}

	public Set<String> usedKeys() {
		return byKey.keySet();
	}

	public int totalCount() {
		return total;
	}
}
