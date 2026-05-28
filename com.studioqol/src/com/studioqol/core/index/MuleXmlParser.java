package com.studioqol.core.index;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Parses a Mule config XML via the WTP structured model to extract inline {@code <global-property>}
 * declarations (with source offsets) and the file(s) named by {@code <secure-properties:config>}.
 * Uses {@code IndexedRegion} so offsets line up with the editor.
 */
public final class MuleXmlParser {

	private static final String SECURE_NS = "http://www.mulesoft.org/schema/mule/secure-properties";

	public static final class GlobalProp {
		public final String name;
		public final String value;
		public final SourceLocation location;

		GlobalProp(String name, String value, SourceLocation location) {
			this.name = name;
			this.value = value;
			this.location = location;
		}
	}

	public static final class Result {
		public final List<GlobalProp> globals = new ArrayList<>();
		public final Set<String> secureFileBaseNames = new HashSet<>();
	}

	private MuleXmlParser() {
	}

	public static Result parse(IFile file) {
		Result result = new Result();
		if (file == null || !file.exists()) {
			return result;
		}
		IModelManager mm = StructuredModelManager.getModelManager();
		IStructuredModel model = null;
		try {
			model = mm.getModelForRead(file);
			if (!(model instanceof IDOMModel)) {
				return result;
			}
			IStructuredDocument sdoc = model.getStructuredDocument();
			Document doc = ((IDOMModel) model).getDocument();

			NodeList globals = doc.getElementsByTagNameNS("*", "global-property");
			for (int i = 0; i < globals.getLength(); i++) {
				Element el = (Element) globals.item(i);
				String name = el.getAttribute("name");
				if (name == null || name.isEmpty()) {
					continue;
				}
				String value = el.getAttribute("value");
				result.globals.add(new GlobalProp(name, value, locationOf(el, sdoc, file)));
			}

			NodeList secure = doc.getElementsByTagNameNS(SECURE_NS, "config");
			for (int i = 0; i < secure.getLength(); i++) {
				Element el = (Element) secure.item(i);
				String f = el.getAttribute("file");
				if (f != null && !f.isEmpty()) {
					result.secureFileBaseNames.add(baseName(f));
				}
			}
			return result;
		} catch (Exception e) {
			return result;
		} finally {
			if (model != null) {
				model.releaseFromRead();
			}
		}
	}

	private static SourceLocation locationOf(Node node, IStructuredDocument sdoc, IFile file) {
		if (node instanceof IndexedRegion) {
			IndexedRegion region = (IndexedRegion) node;
			int start = region.getStartOffset();
			int end = region.getEndOffset();
			int line = 1;
			try {
				line = sdoc.getLineOfOffset(start) + 1;
			} catch (Exception e) {
				// keep line = 1
			}
			return new SourceLocation(file, line, start, Math.max(0, end - start));
		}
		return new SourceLocation(file, 1, 0, 0);
	}

	private static String baseName(String path) {
		int slash = path.lastIndexOf('/');
		return slash >= 0 ? path.substring(slash + 1) : path;
	}
}
