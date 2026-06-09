package com.studioqol.core.index;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;

import com.studioqol.core.ResourceText;

/**
 * Minimal {@code java.util.Properties}-style line parser that ALSO records the source location of
 * each key (so Ctrl+click can land on the definition) and flags encrypted {@code ![...]} values.
 * Single-line values only (the convention's env files have no backslash continuations).
 */
public final class PropertiesFileParser {

	/** One parsed entry: raw value text, the KEY's location, and its state. */
	public static final class Entry {
		public final String value;
		public final SourceLocation location;
		public final EnvValue.State state;

		Entry(String value, SourceLocation location, EnvValue.State state) {
			this.value = value;
			this.location = location;
			this.state = state;
		}
	}

	private PropertiesFileParser() {
	}

	public static Map<String, Entry> parse(IFile file) {
		Map<String, Entry> out = new LinkedHashMap<>();
		if (file == null || !file.exists()) {
			return out;
		}
		String text = ResourceText.read(file);
		if (text == null) {
			return out;
		}
		IDocument doc = new Document(text);
		int lines = doc.getNumberOfLines();
		for (int line = 0; line < lines; line++) {
			try {
				IRegion info = doc.getLineInformation(line);
				int lineStart = info.getOffset();
				String raw = doc.get(lineStart, info.getLength());
				int n = raw.length();
				int i = skipWs(raw, 0);
				if (i >= n) {
					continue; // blank line
				}
				char c0 = raw.charAt(i);
				if (c0 == '#' || c0 == '!') {
					continue; // comment
				}
				int keyStart = i;
				StringBuilder key = new StringBuilder();
				while (i < n) {
					char ch = raw.charAt(i);
					if (ch == '\\' && i + 1 < n) {
						key.append(raw.charAt(i + 1));
						i += 2;
						continue;
					}
					if (ch == '=' || ch == ':' || ch == ' ' || ch == '\t' || ch == '\f') {
						break;
					}
					key.append(ch);
					i++;
				}
				int keyEnd = i;
				String k = key.toString();
				if (k.isEmpty()) {
					continue;
				}
				i = skipWs(raw, i);
				if (i < n && (raw.charAt(i) == '=' || raw.charAt(i) == ':')) {
					i++;
				}
				i = skipWs(raw, i);
				String value = raw.substring(i);
				EnvValue.State state;
				if (isEncrypted(value)) {
					state = EnvValue.State.SECURE;
				} else if (value.isEmpty()) {
					state = EnvValue.State.EMPTY;
				} else {
					state = EnvValue.State.PRESENT;
				}
				SourceLocation loc = new SourceLocation(file, line + 1, lineStart + keyStart,
						keyEnd - keyStart);
				out.putIfAbsent(k, new Entry(value, loc, state));
			} catch (BadLocationException e) {
				// skip malformed line
			}
		}
		return out;
	}

	private static int skipWs(String s, int i) {
		while (i < s.length()) {
			char c = s.charAt(i);
			if (c != ' ' && c != '\t' && c != '\f') {
				break;
			}
			i++;
		}
		return i;
	}

	private static boolean isEncrypted(String v) {
		String t = v.trim();
		return t.startsWith("![") && t.endsWith("]");
	}
}
