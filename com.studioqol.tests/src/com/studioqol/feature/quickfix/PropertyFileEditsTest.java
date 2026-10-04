package com.studioqol.feature.quickfix;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.Properties;

import org.junit.Test;

public class PropertyFileEditsTest {

	@Test
	public void escapesSpecialKeysWithoutCreatingOtherProperties() throws Exception {
		String key = " #!spaces:=\\\t\f\r\nother=secret\u00e9";
		String suffix = PropertyFileEdits.missingKeySuffix("", key);
		Properties loaded = load(suffix);
		assertEquals(1, loaded.size());
		assertEquals("", loaded.getProperty(key));
		assertFalse(suffix.contains("\u00e9"));
	}

	@Test
	public void staleFixDoesNotDuplicateAnEscapedExistingKeyOrReplaceItsValue() {
		assertEquals("", PropertyFileEdits.missingKeySuffix("db\\:url=keep-this-value\n", "db:url"));
		assertEquals("", PropertyFileEdits.missingKeySuffix("k\\\n  ey=keep\n", "key"));
	}

	@Test
	public void keepsOriginalValuesEvenWithUnfinishedContinuations() throws Exception {
		for (String text : new String[] { "original=keep", "original=keep\n", "original=keep\\",
				"original=keep\\\n", "original=keep\\\r\n", "original=keep\\\\" }) {
			Properties before = load(text);
			Properties after = load(text + PropertyFileEdits.missingKeySuffix(text, "new.key"));
			assertEquals(text, before.getProperty("original"), after.getProperty("original"));
			assertEquals(text, "", after.getProperty("new.key"));
			assertEquals(text, before.size() + 1, after.size());
		}
	}

	@Test
	public void preservesWindowsAndClassicMacLineDelimiters() {
		assertEquals("\r\nkey=\r\n", PropertyFileEdits.missingKeySuffix("existing=1\r\n", "key"));
		assertEquals("\rkey=\r", PropertyFileEdits.missingKeySuffix("existing=1\r", "key"));
	}

	@Test
	public void repeatedFixIsIdempotentAndNeverCopiesAValue() throws Exception {
		String text = "other=private-value\n";
		String suffix = PropertyFileEdits.missingKeySuffix(text, "new");
		assertFalse(suffix.contains("private-value"));
		assertEquals("", load(text + suffix).getProperty("new"));
		assertEquals("", PropertyFileEdits.missingKeySuffix(text + suffix, "new"));
	}

	private static Properties load(String text) throws Exception {
		Properties properties = new Properties();
		properties.load(new StringReader(text));
		return properties;
	}
}
