package com.studioqol.core.usage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;

import com.studioqol.core.MuleProjectLayout;
import com.studioqol.core.ResourceText;
import com.studioqol.core.index.SourceLocation;
import com.studioqol.core.scan.PScanner;
import com.studioqol.core.scan.PlaceholderScanner;

/**
 * Builds a {@link UsageIndex} by line-scanning {@code src/main/mule/*.xml} (both {@code ${...}}
 * placeholders and inline-DataWeave {@code p('...')} calls, e.g. in {@code #[...]} attributes or
 * CDATA scripts) and {@code src/main/resources/**.dwl} ({@code p('...')} only). Purely textual —
 * same philosophy as the hover scanners, so occurrences inside XML comments are counted too.
 * Dynamic placeholders ({@code ${a${b}}}) are skipped; their inner non-dynamic parts still count.
 */
public final class UsageScanner {

	public UsageIndex build(IProject project) {
		Map<String, List<PropertyUsage>> byKey = new LinkedHashMap<>();
		try {
			IFolder mule = project.getFolder(new Path(MuleProjectLayout.MULE_DIR));
			if (mule.exists()) {
				for (IResource r : mule.members()) {
					if (r.getType() == IResource.FILE
							&& "xml".equalsIgnoreCase(((IFile) r).getFileExtension())) {
						scanFile((IFile) r, true, byKey);
					}
				}
			}
			IFolder resources = project.getFolder(new Path(MuleProjectLayout.RESOURCES_DIR));
			if (resources.exists()) {
				resources.accept(res -> {
					if (res.getType() == IResource.FILE
							&& "dwl".equalsIgnoreCase(((IFile) res).getFileExtension())) {
						scanFile((IFile) res, false, byKey);
					}
					return true;
				});
			}
		} catch (Exception e) {
			// Return whatever we managed to scan; an empty usage index is valid.
		}
		return new UsageIndex(byKey);
	}

	/** @param xml whether to also scan for {@code ${...}} placeholders (Mule config XML only). */
	private static void scanFile(IFile file, boolean xml, Map<String, List<PropertyUsage>> byKey) {
		String text = ResourceText.read(file);
		if (text == null) {
			return;
		}
		IDocument doc = new Document(text);
		try {
			for (int line = 0; line < doc.getNumberOfLines(); line++) {
				IRegion info = doc.getLineInformation(line);
				String raw = doc.get(info.getOffset(), info.getLength());
				if (xml) {
					for (PlaceholderScanner.Hit hit : PlaceholderScanner.scanLine(raw)) {
						if (hit.dynamic || hit.key.isEmpty()) {
							continue;
						}
						add(byKey, new PropertyUsage(hit.key, hit.secure,
								location(file, line, info, hit.start, hit.end),
								PropertyUsage.Origin.XML_PLACEHOLDER));
					}
				}
				for (PScanner.PHit hit : PScanner.scanLine(raw)) {
					add(byKey, new PropertyUsage(hit.key, false,
							location(file, line, info, hit.start, hit.end),
							PropertyUsage.Origin.DW_CALL));
				}
			}
		} catch (org.eclipse.jface.text.BadLocationException e) {
			// skip the rest of this file
		}
	}

	private static SourceLocation location(IFile file, int line, IRegion lineInfo, int start, int end) {
		return new SourceLocation(file, line + 1, lineInfo.getOffset() + start, end - start);
	}

	private static void add(Map<String, List<PropertyUsage>> byKey, PropertyUsage usage) {
		byKey.computeIfAbsent(usage.key, k -> new ArrayList<>()).add(usage);
	}
}
