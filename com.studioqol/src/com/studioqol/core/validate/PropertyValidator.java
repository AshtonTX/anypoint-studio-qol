package com.studioqol.core.validate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.studioqol.core.index.EnvValue;
import com.studioqol.core.index.PropertyDefinition;
import com.studioqol.core.index.PropertyIndex;
import com.studioqol.core.index.SourceLocation;
import com.studioqol.core.usage.PropertyUsage;
import com.studioqol.core.usage.UsageIndex;

/**
 * Pure cross-check of the definition and usage indexes. Honest by design: never flags the env
 * selector property itself (runtime-only), never flags {@code secure::} usages unless a secure
 * file was actually discovered, and never sees dynamic {@code ${a${b}}} placeholders (the usage
 * scanner drops them).
 *
 * <p>Known accepted limitation: a key referenced only from another {@code .properties} value is
 * reported unused — chained {@code <global-property>} values ARE covered, since they live in the
 * scanned XML.
 */
public final class PropertyValidator {

	/** One validation problem; {@link QolMarkers} turns these into workspace markers. */
	public static final class Finding {
		public enum Type {
			/** Used in XML/DataWeave but defined nowhere in the project. */
			UNDEFINED,
			/** Defined in at least one env file but absent from one or more others. */
			MISSING_IN_ENV,
			/** Defined in env files but never referenced. */
			UNUSED
		}

		public final Type type;
		public final String key;
		public final boolean secure;
		public final SourceLocation location;
		public final List<String> missingEnvs;

		Finding(Type type, String key, boolean secure, SourceLocation location,
				List<String> missingEnvs) {
			this.type = type;
			this.key = key;
			this.secure = secure;
			this.location = location;
			this.missingEnvs = List.copyOf(missingEnvs);
		}
	}

	private PropertyValidator() {
	}

	public static List<Finding> validate(PropertyIndex index, UsageIndex usages,
			String envSelectorName) {
		List<Finding> out = new ArrayList<>();
		if (index == null || usages == null) {
			return out;
		}
		boolean secureSourceKnown = anySecureDefinition(index);

		// UNDEFINED — at every usage of a key that resolves to nothing.
		for (String key : usages.usedKeys()) {
			if (key.equals(envSelectorName) || index.lookup(key) != null) {
				continue;
			}
			for (PropertyUsage usage : usages.usagesOf(key)) {
				if (usage.secure && !secureSourceKnown) {
					continue; // can't see inside an undiscovered secure file — stay silent
				}
				out.add(new Finding(Finding.Type.UNDEFINED, key, usage.secure, usage.location,
						List.of()));
			}
		}

		for (String key : index.allKeys()) {
			PropertyDefinition def = index.lookup(key);
			if (def.kind != PropertyDefinition.Kind.ENV_FILE) {
				continue;
			}

			// MISSING_IN_ENV — the convention says every key exists in every env file
			// (including example.properties: it is the template).
			Set<String> missing = new LinkedHashSet<>(index.environments());
			missing.removeAll(def.valuesByEnv.keySet());
			SourceLocation at = firstLocation(index, def);
			if (!missing.isEmpty() && at != null) {
				out.add(new Finding(Finding.Type.MISSING_IN_ENV, key, false, at,
						new ArrayList<>(missing)));
			}

			// UNUSED — defined but never referenced anywhere we scan.
			if (!key.equals(envSelectorName) && !usages.usedKeys().contains(key) && at != null) {
				out.add(new Finding(Finding.Type.UNUSED, key, false, at, List.of()));
			}
		}
		return out;
	}

	/** The key's location in the first (index-ordered) env file that defines it. */
	private static SourceLocation firstLocation(PropertyIndex index, PropertyDefinition def) {
		for (String env : index.environments()) {
			EnvValue value = def.valuesByEnv.get(env);
			if (value != null && value.location != null && value.location.file != null) {
				return value.location;
			}
		}
		return null;
	}

	private static boolean anySecureDefinition(PropertyIndex index) {
		for (String key : index.allKeys()) {
			PropertyDefinition def = index.lookup(key);
			if (def != null && def.kind == PropertyDefinition.Kind.SECURE) {
				return true;
			}
		}
		return false;
	}
}
