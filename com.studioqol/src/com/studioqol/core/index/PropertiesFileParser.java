package com.studioqol.core.index;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;

import com.studioqol.core.ResourceText;

/**
 * {@link Properties}-compatible parser that also records the source location of each key and
 * flags encrypted {@code ![...]} values. Escapes and continued lines are decoded before indexing;
 * the last occurrence of a duplicate key wins, just as it does when loading a properties file.
 */
public final class PropertiesFileParser {

	/** One parsed entry: decoded value text, the KEY's source location, and its state. */
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
		return parse(file, ResourceText.read(file));
	}

	/** Parses a text snapshot, including an unsaved editor document. {@code file} may be null. */
	public static Map<String, Entry> parse(IFile file, String text) {
		Map<String, Entry> out = new LinkedHashMap<>();
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
				int i = skipWs(raw, 0);
				if (i >= raw.length()) {
					continue; // blank line
				}
				char c0 = raw.charAt(i);
				if (c0 == '#' || c0 == '!') {
					continue; // comment
				}
				int firstLine = line;
				int entryStart = lineStart;
				int keyStart = lineStart + i;
				StringBuilder logical = new StringBuilder();
				List<Integer> offsets = new ArrayList<>();
				while (true) {
					boolean continued = hasContinuation(raw);
					int end = raw.length() - (continued ? 1 : 0);
					for (; i < end; i++) {
						logical.append(raw.charAt(i));
						offsets.add(lineStart + i);
					}
					if (!continued || line + 1 >= lines) {
						break;
					}
					info = doc.getLineInformation(++line);
					lineStart = info.getOffset();
					raw = doc.get(lineStart, info.getLength());
					i = skipWs(raw, 0);
				}
				Properties entry = new Properties();
				try {
					entry.load(new StringReader(text.substring(entryStart, lineStart + info.getLength())));
				} catch (IllegalArgumentException | IOException e) {
					continue; // malformed Unicode escape; preserve other valid entries
				}
				if (entry.isEmpty()) {
					continue;
				}
				String k = entry.stringPropertyNames().iterator().next();
				String value = entry.getProperty(k);
				EnvValue.State state;
				if (isEncrypted(value)) {
					state = EnvValue.State.SECURE;
				} else if (value.isEmpty()) {
					state = EnvValue.State.EMPTY;
				} else {
					state = EnvValue.State.PRESENT;
				}
				int keyLength = keyLength(logical);
				int keyEnd = keyLength == 0 ? keyStart : offsets.get(keyLength - 1) + 1;
				SourceLocation loc = new SourceLocation(file, firstLine + 1, keyStart,
						keyEnd - keyStart);
				out.put(k, new Entry(value, loc, state));
			} catch (BadLocationException e) {
				// skip malformed line
			}
		}
		return out;
	}

	private static boolean hasContinuation(String line) {
		int slashes = 0;
		for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
			slashes++;
		}
		return slashes % 2 != 0;
	}

	private static int keyLength(CharSequence line) {
		int i = 0;
		while (i < line.length()) {
			char ch = line.charAt(i);
			if (ch == '\\' && i + 1 < line.length()) {
				i += 2;
			} else if (ch == '=' || ch == ':' || ch == ' ' || ch == '\t' || ch == '\f') {
				break;
			} else {
				i++;
			}
		}
		return i;
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
