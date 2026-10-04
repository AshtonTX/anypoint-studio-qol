package com.studioqol.core.scan;

import static org.junit.Assert.*;

import java.util.List;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.junit.Test;

public class PlaceholderScannerTest {

	@Test
	public void scansMultipleReferencesAndSecurePrefixes() {
		String text = "${ db.host } / ${secure:: password }";
		List<PlaceholderScanner.Hit> hits = PlaceholderScanner.scanLine(text);
		assertEquals(2, hits.size());
		assertEquals("db.host", hits.get(0).key);
		assertFalse(hits.get(0).secure);
		assertEquals("password", hits.get(1).key);
		assertTrue(hits.get(1).secure);
		assertEquals("${secure:: password }", text.substring(hits.get(1).start, hits.get(1).end));
	}

	@Test
	public void nestedReferencesKeepTheResolvableInnerKey() {
		List<PlaceholderScanner.Hit> hits = PlaceholderScanner.scanLine("${db.${env}.host}");
		assertEquals(2, hits.size());
		assertEquals("env", hits.get(0).key);
		assertFalse(hits.get(0).dynamic);
		assertEquals("db.${env}.host", hits.get(1).key);
		assertTrue(hits.get(1).dynamic);
	}

	@Test
	public void lookupChoosesInnermostSpanWithDocumentOffsets() {
		String text = "first line\r\nvalue=${db.${env}.host}";
		Document document = new Document(text);
		int inner = text.indexOf("${env}");
		IRegion region = PlaceholderScanner.find(document, inner + 3);
		assertEquals(new Region(inner, "${env}".length()), region);
		assertEquals("env", PlaceholderScanner.extractKey(document, region));
		assertNull(PlaceholderScanner.find(document, text.length()));
		assertNull(PlaceholderScanner.find(document, -1));
		assertNull(PlaceholderScanner.find(document, text.length() + 1));
	}

	@Test
	public void ignoresIncompleteAndEmptyInput() {
		assertTrue(PlaceholderScanner.scanLine(null).isEmpty());
		assertTrue(PlaceholderScanner.scanLine("").isEmpty());
		assertTrue(PlaceholderScanner.scanLine("${unfinished").isEmpty());
		assertNull(PlaceholderScanner.find(null, 0));
		Document empty = new Document("${secure:: }");
		assertNull(PlaceholderKey.of(empty, new Region(0, empty.getLength())));
	}

	@Test
	public void parsesSecureDocumentKey() {
		Document document = new Document("${ secure:: db.password }");
		PlaceholderKey key = PlaceholderKey.of(document, new Region(0, document.getLength()));
		assertNotNull(key);
		assertEquals("db.password", key.key);
		assertTrue(key.secure);
	}

	@Test
	public void refusesRegionsWithoutPlaceholderDelimiters() {
		for (String text : List.of("not-a-placeholder", "${unterminated", "x{key}", "${}")) {
			Document document = new Document(text);
			assertNull(text, PlaceholderKey.of(document, new Region(0, document.getLength())));
		}
	}
}
