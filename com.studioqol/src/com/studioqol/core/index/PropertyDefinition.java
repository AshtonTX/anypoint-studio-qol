package com.studioqol.core.index;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One logical property: either per-environment file values, an inline {@code <global-property>},
 * or a key owned by a secure-properties file.
 */
public final class PropertyDefinition {

	public enum Kind { ENV_FILE, INLINE_GLOBAL, SECURE }

	public final String key;
	public final Kind kind;
	public final Map<String, EnvValue> valuesByEnv; // ENV_FILE; empty otherwise
	public final String rawGlobalValue;             // INLINE_GLOBAL; null otherwise
	public final SourceLocation declaration;        // INLINE_GLOBAL / SECURE; null for ENV_FILE

	private PropertyDefinition(String key, Kind kind, Map<String, EnvValue> valuesByEnv,
			String rawGlobalValue, SourceLocation declaration) {
		this.key = key;
		this.kind = kind;
		this.valuesByEnv = valuesByEnv == null ? Collections.emptyMap()
				: Collections.unmodifiableMap(new LinkedHashMap<>(valuesByEnv));
		this.rawGlobalValue = rawGlobalValue;
		this.declaration = declaration;
	}

	public static PropertyDefinition envFile(String key, Map<String, EnvValue> valuesByEnv) {
		return new PropertyDefinition(key, Kind.ENV_FILE, valuesByEnv, null, null);
	}

	public static PropertyDefinition global(String key, String rawValue, SourceLocation declaration) {
		return new PropertyDefinition(key, Kind.INLINE_GLOBAL, null, rawValue, declaration);
	}

	public static PropertyDefinition secure(String key, SourceLocation declaration) {
		return new PropertyDefinition(key, Kind.SECURE, null, null, declaration);
	}

	/** True when an inline global-property value embeds other {@code ${...}} references. */
	public boolean isChainedGlobal() {
		return rawGlobalValue != null && rawGlobalValue.contains("${");
	}
}
