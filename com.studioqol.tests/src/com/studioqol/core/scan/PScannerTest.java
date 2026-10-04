package com.studioqol.core.scan;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

public class PScannerTest {

	@Test
	public void recognizesBothQuoteStylesAndMuleQualifiedCalls() {
		List<PScanner.PHit> hits = PScanner.scanLine("p('host') ++ Mule::p ( \"port\" )");
		assertEquals(2, hits.size());
		assertEquals("host", hits.get(0).key);
		assertEquals("port", hits.get(1).key);
	}

	@Test
	public void lookupUsesHalfOpenTokenOffsets() {
		String text = "prefix p('host') suffix";
		int start = text.indexOf("p(");
		PScanner.PHit hit = PScanner.find(text, start);
		assertNotNull(hit);
		assertEquals("p('host')", text.substring(hit.start, hit.end));
		assertNotNull(PScanner.find(text, hit.end - 1));
		assertNull(PScanner.find(text, hit.end));
		assertNull(PScanner.find(text, start - 1));
		assertNull(PScanner.find(text, -1));
	}

	@Test
	public void rejectsDynamicArgumentsAndUnfinishedCalls() {
		for (String text : List.of("p('prefix.' ++ suffix)", "p(variable)", "p('host'",
				"p('host)", "p('')", "p(\n'host'\n)", "p('one', 'two')")) {
			assertTrue(text, PScanner.scanLine(text).isEmpty());
		}
	}

	@Test
	public void rejectsUnrelatedFunctionsAndIdentifiers() {
		for (String text : List.of("map('host')", "prop('host')", "dump('host')",
				"Other::p('host')", "object.p('host')", "_p('host')", "$p('host')")) {
			assertTrue(text, PScanner.scanLine(text).isEmpty());
		}
	}

	@Test
	public void handlesEscapedQuotesAndBackslashesInLiteralKeys() {
		List<PScanner.PHit> hits = PScanner.scanLine("p('don\\'t') ++ p(\"path\\\\name\")");
		assertEquals(2, hits.size());
		assertEquals("don't", hits.get(0).key);
		assertEquals("path\\name", hits.get(1).key);
	}

	@Test
	public void decodesEscapedUnicodeAndRejectsRuntimeInterpolation() {
		List<PScanner.PHit> hits = PScanner.scanLine("p('caf\\u00e9') ++ p('\\$literal')");
		assertEquals(2, hits.size());
		assertEquals("caf\u00e9", hits.get(0).key);
		assertEquals("$literal", hits.get(1).key);
		assertTrue(PScanner.scanLine("p('prefix.$(env)')").isEmpty());
	}

	@Test
	public void emptyInputHasNoHits() {
		assertTrue(PScanner.scanLine(null).isEmpty());
		assertTrue(PScanner.scanLine("").isEmpty());
		assertNull(PScanner.find(null, 0));
	}
}
