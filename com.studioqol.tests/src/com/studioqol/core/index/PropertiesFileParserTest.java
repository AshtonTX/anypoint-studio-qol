package com.studioqol.core.index;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;

public class PropertiesFileParserTest {

	@Test
	public void decodesEscapedKeysAndValuesLikeJavaProperties() throws Exception {
		String text = "# comment\n"
				+ "db\\:url = jdbc\\:postgresql\\://localhost/app\n"
				+ "space\\ key: escaped\\ value\\nnext\\tcolumn\n"
				+ "unicode.\\u006bey=\\u00e9\n"
				+ "path=C:\\\\local\\\\file\n"
				+ "empty=\n";
		assertMatchesJavaProperties(text);
		assertEquals(EnvValue.State.EMPTY, PropertiesFileParser.parse(null, text).get("empty").state);
	}

	@Test
	public void readsContinuedValuesAndLocatesContinuedKeys() throws Exception {
		String text = "joined=first\\\r\n  second\\\r\n\tthird\r\n"
				+ "continued\\\r\n  .key=value\r\n"
				+ "literal=ends\\\\\r\nnext=separate\r\n";
		assertMatchesJavaProperties(text);
		Map<String, PropertiesFileParser.Entry> parsed = PropertiesFileParser.parse(null, text);
		assertEquals("firstsecondthird", parsed.get("joined").value);
		SourceLocation location = parsed.get("continued.key").location;
		assertEquals(4, location.line);
		assertEquals(text.indexOf("continued"), location.offset);
		assertEquals("continued\\\r\n  .key", text.substring(location.offset, location.offset + location.length));
	}

	@Test
	public void lastDuplicateWinsIncludingItsNavigationLocation() {
		String text = "key=old\n  key=new\n";
		PropertiesFileParser.Entry result = PropertiesFileParser.parse(null, text).get("key");
		assertEquals("new", result.value);
		assertEquals(2, result.location.line);
		assertEquals(text.lastIndexOf("key"), result.location.offset);
		assertEquals(3, result.location.length);
	}

	@Test
	public void recognizesEncryptedValuesAfterEscapeAndContinuationDecoding() {
		String text = "encoded=\\u0021[cipher]\ncontinued=![abc\\\n  def]\n";
		Map<String, PropertiesFileParser.Entry> parsed = PropertiesFileParser.parse(null, text);
		assertEquals(EnvValue.State.SECURE, parsed.get("encoded").state);
		assertEquals(EnvValue.State.SECURE, parsed.get("continued").state);
	}

	@Test
	public void malformedUnicodeDoesNotHideOtherEntries() {
		Map<String, PropertiesFileParser.Entry> parsed = PropertiesFileParser.parse(null,
				"before=ok\nbad=\\uNOPE\nafter=ok\n");
		assertEquals(2, parsed.size());
		assertEquals("ok", parsed.get("before").value);
		assertEquals("ok", parsed.get("after").value);
	}

	@Test
	public void commentBackslashesDoNotContinueAndTrailingBackslashesMatchJava() throws Exception {
		assertMatchesJavaProperties("# ignored\\\nkey=value\n! ignored\\\nlast=end\\");
		assertMatchesJavaProperties("value=before\\\n\nafter=ok\n");
		assertMatchesJavaProperties("\\\n#key=value\n");
		assertMatchesJavaProperties("\\\n!key=value\n");
		assertMatchesJavaProperties("\\\n  key=value\n");
	}

	private static void assertMatchesJavaProperties(String text) throws Exception {
		Properties expected = new Properties();
		expected.load(new StringReader(text));
		Map<String, PropertiesFileParser.Entry> actual = PropertiesFileParser.parse(null, text);
		assertEquals(expected.stringPropertyNames(), actual.keySet());
		for (String key : expected.stringPropertyNames()) {
			assertEquals(key, expected.getProperty(key), actual.get(key).value);
		}
	}
}
