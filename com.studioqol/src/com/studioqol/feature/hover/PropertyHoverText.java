package com.studioqol.feature.hover;

import com.studioqol.core.index.EnvValue;
import com.studioqol.core.index.PropertyDefinition;
import com.studioqol.core.index.PropertyIndex;

/**
 * Renders the hover content for a {@code ${...}} placeholder as an HTML fragment (bold labels, line
 * breaks) styled like Studio's native XML hover. Values are HTML-escaped (e.g. the {@code db.url}
 * template {@code jdbc:...//<host>:<port>/...} renders literally). When the active environment is
 * known (from a launch config) its row is highlighted. Honest about state: per-env value list, single
 * global value (raw for chained), or an explicit secure / runtime-only / unresolved flag.
 */
public final class PropertyHoverText {

	private PropertyHoverText() {
	}

	/** How the property reference is labelled in the popup header. */
	public enum LabelStyle {
		/** Configuration XML placeholder syntax: {@code ${key}} (Feature 1). */
		PLACEHOLDER {
			@Override
			String label(String key) {
				return "${" + esc(key) + "}";
			}
		},
		/** DataWeave property-function syntax: {@code p('key')} (Feature 2). */
		DATAWEAVE {
			@Override
			String label(String key) {
				return "p('" + esc(key) + "')";
			}
		};

		abstract String label(String key);
	}

	/** Renders with the default {@link LabelStyle#PLACEHOLDER} ({@code ${key}}) header. */
	public static String render(String key, boolean secure, String envProperty, String activeEnv,
			PropertyIndex index) {
		return render(key, secure, envProperty, activeEnv, index, LabelStyle.PLACEHOLDER);
	}

	/** Renders the popup, labelling the property reference per {@code style}. */
	public static String render(String key, boolean secure, String envProperty, String activeEnv,
			PropertyIndex index, LabelStyle style) {
		if (key == null || key.isEmpty()) {
			return null;
		}
		if (secure) {
			// The ${secure::key} form is placeholder-only; DataWeave callers always pass secure=false
			// (a secure-properties key still renders via the SECURE kind branch below with its style).
			return secureHtml("${secure::" + esc(key) + "}", null);
		}
		if (key.equals(envProperty)) {
			return runtimeOnlyHtml(key, activeEnv, index, style);
		}
		PropertyDefinition def = (index == null) ? null : index.lookup(key);
		if (def == null) {
			return "<b>" + style.label(key) + "</b> — unresolved<br>"
					+ "Not defined in any env file or &lt;global-property&gt;.";
		}
		switch (def.kind) {
		case ENV_FILE:
			return envHtml(key, def, index, activeEnv, style);
		case INLINE_GLOBAL:
			return globalHtml(key, def, style);
		case SECURE:
			return secureHtml(style.label(key), def);
		default:
			return null;
		}
	}

	private static String envHtml(String key, PropertyDefinition def, PropertyIndex index,
			String activeEnv, LabelStyle style) {
		boolean activeKnown = activeEnv != null && index.environments().contains(activeEnv);
		StringBuilder b = new StringBuilder();
		b.append("<b>").append(style.label(key)).append("</b> — per environment");
		if (activeKnown) {
			b.append(" · active: <b>").append(esc(activeEnv)).append("</b>");
		}
		b.append("<br>");
		for (String env : index.environments()) {
			EnvValue ev = def.valuesByEnv.get(env);
			if (ev == null) {
				continue;
			}
			boolean active = env.equals(activeEnv);
			if (active) {
				b.append("▶ ");
			}
			b.append("<b>").append(esc(env)).append("</b> : ");
			String value = displayHtml(ev);
			b.append(active ? "<b>" + value + "</b>" : value);
			if (ev.location != null && ev.location.file != null) {
				b.append("  <i>(").append(esc(ev.location.file.getName())).append(':')
						.append(ev.location.line).append(")</i>");
			}
			if (active) {
				b.append(" ← active");
			}
			b.append("<br>");
		}
		return b.toString();
	}

	private static String globalHtml(String key, PropertyDefinition def, LabelStyle style) {
		StringBuilder b = new StringBuilder();
		b.append("<b>").append(style.label(key)).append("</b> — global-property<br>");
		String raw = def.rawGlobalValue;
		b.append((raw == null || raw.isEmpty()) ? "<i>(empty)</i>" : esc(raw));
		if (def.isChainedGlobal()) {
			b.append("<br>References other properties; shown raw, not resolved.");
		}
		appendSource(b, def);
		return b.toString();
	}

	private static String secureHtml(String label, PropertyDefinition def) {
		StringBuilder b = new StringBuilder();
		b.append("<b>").append(label).append("</b> — secure<br>");
		b.append("Encrypted; decrypted at runtime. Value not shown.");
		appendSource(b, def);
		return b.toString();
	}

	private static String runtimeOnlyHtml(String key, String activeEnv, PropertyIndex index,
			LabelStyle style) {
		StringBuilder b = new StringBuilder();
		b.append("<b>").append(style.label(key)).append("</b> — runtime-only<br>");
		b.append("Supplied at launch via <code>-D").append(esc(key)).append("=&lt;value&gt;</code>")
				.append(" (<code>-M-D").append(esc(key)).append("=</code> in Studio).");
		if (activeEnv != null && !activeEnv.isEmpty()) {
			b.append("<br>Currently set to <b>").append(esc(activeEnv)).append("</b>.");
		}
		if (index != null && !index.environments().isEmpty()) {
			b.append("<br><b>environments</b> : ").append(esc(String.join(", ", index.environments())));
		}
		return b.toString();
	}

	private static void appendSource(StringBuilder b, PropertyDefinition def) {
		if (def != null && def.declaration != null && def.declaration.file != null) {
			b.append("<br><b>source</b> : ").append(esc(def.declaration.file.getName())).append(':')
					.append(def.declaration.line);
		}
	}

	private static String displayHtml(EnvValue ev) {
		if (ev.state == EnvValue.State.EMPTY) {
			return "<i>(empty)</i>";
		}
		if (ev.state == EnvValue.State.SECURE) {
			return "<i>(secure)</i>";
		}
		return esc(ev.rawValue);
	}

	private static String esc(String s) {
		if (s == null) {
			return "";
		}
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '&':
				b.append("&amp;");
				break;
			case '<':
				b.append("&lt;");
				break;
			case '>':
				b.append("&gt;");
				break;
			default:
				b.append(c);
			}
		}
		return b.toString();
	}
}
