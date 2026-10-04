package com.studioqol.feature.compare;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.studioqol.core.index.EnvValue;
import com.studioqol.core.index.PropertyDefinition;
import com.studioqol.core.index.PropertyIndex;

/**
 * Pure table model for the Env Comparison view: one row per env-file property key, one cell per
 * environment. {@code hasProblem} uses the same definition as the MISSING_IN_ENV marker (absent
 * from at least one env file) — one source of truth for "problem".
 */
public final class ComparisonModel {

	public static final class Row {
		public final String key;
		public final Map<String, EnvValue> byEnv;
		public final boolean hasProblem;
		/** Different stored values/states, including a definition missing from an environment. */
		public final boolean hasDifferences;

		Row(String key, Map<String, EnvValue> byEnv, boolean hasProblem, boolean hasDifferences) {
			this.key = key;
			this.byEnv = byEnv;
			this.hasProblem = hasProblem;
			this.hasDifferences = hasDifferences;
		}

		/** Search only names: values (including encrypted text) are never searchable. */
		public boolean matches(String keyQuery, boolean problemsOnly, boolean differencesOnly) {
			String query = keyQuery == null ? "" : keyQuery.trim().toLowerCase(Locale.ROOT);
			return (!problemsOnly || hasProblem) && (!differencesOnly || hasDifferences)
					&& key.toLowerCase(Locale.ROOT).contains(query);
		}
	}

	public final List<String> environments;
	public final List<Row> rows;

	private ComparisonModel(List<String> environments, List<Row> rows) {
		this.environments = List.copyOf(environments);
		this.rows = List.copyOf(rows);
	}

	public static ComparisonModel build(PropertyIndex index) {
		if (index == null) {
			return new ComparisonModel(List.of(), List.of());
		}
		Map<String, PropertyDefinition> envFileDefs = new TreeMap<>();
		for (String key : index.allKeys()) {
			PropertyDefinition def = index.lookup(key);
			if (def != null && def.kind == PropertyDefinition.Kind.ENV_FILE) {
				envFileDefs.put(key, def);
			}
		}
		List<Row> rows = new ArrayList<>();
		for (Map.Entry<String, PropertyDefinition> e : envFileDefs.entrySet()) {
			Map<String, EnvValue> byEnv = e.getValue().valuesByEnv;
			boolean problem = !byEnv.keySet().containsAll(index.environments());
			rows.add(new Row(e.getKey(), byEnv, problem, problem || differentValues(byEnv)));
		}
		return new ComparisonModel(index.environments(), rows);
	}

	/** Compare stored secure text without displaying it or implying equality of decrypted values. */
	private static boolean differentValues(Map<String, EnvValue> byEnv) {
		EnvValue first = null;
		for (EnvValue value : byEnv.values()) {
			if (first == null) {
				first = value;
			} else if (first.state != value.state || !Objects.equals(first.rawValue, value.rawValue)) {
				return true;
			}
		}
		return false;
	}
}
