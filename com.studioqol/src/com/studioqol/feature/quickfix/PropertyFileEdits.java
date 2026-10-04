package com.studioqol.feature.quickfix;

import com.studioqol.core.index.PropertiesFileParser;

/** Text-only, value-preserving edits used by the add-property quick fix. */
final class PropertyFileEdits {

	private PropertyFileEdits() {
	}

	/** Returns the suffix to append, or an empty string if the key already exists. */
	static String missingKeySuffix(String text, String key) {
		if (text == null || key == null || key.isEmpty()
				|| PropertiesFileParser.parse(null, text).containsKey(key)) {
			return "";
		}
		String delimiter = lineDelimiter(text);
		// A blank natural line also terminates a dangling continuation, so the new key can never
		// become part of the preceding property's value.
		String prefix = text.isEmpty() ? ""
				: (text.endsWith("\n") || text.endsWith("\r")) ? delimiter : delimiter + delimiter;
		return prefix + escapeKey(key) + "=" + delimiter;
	}

	private static String lineDelimiter(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\r') {
				return i + 1 < text.length() && text.charAt(i + 1) == '\n' ? "\r\n" : "\r";
			}
			if (text.charAt(i) == '\n') {
				return "\n";
			}
		}
		return "\n";
	}

	private static String escapeKey(String key) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < key.length(); i++) {
			char ch = key.charAt(i);
			switch (ch) {
			case '\\': case ' ': case '=': case ':': case '#': case '!':
				out.append('\\').append(ch);
				break;
			case '\n': out.append("\\n"); break;
			case '\r': out.append("\\r"); break;
			case '\t': out.append("\\t"); break;
			case '\f': out.append("\\f"); break;
			default:
				if (ch < 0x20 || ch > 0x7e) {
					// ASCII escapes round-trip even in an ISO-8859-1 workspace file.
					out.append(String.format("\\u%04x", (int) ch));
				} else {
					out.append(ch);
				}
			}
		}
		return out.toString();
	}
}
