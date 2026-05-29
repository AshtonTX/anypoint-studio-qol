package com.studioqol.feature.hover;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.PropertyNavigation;
import com.studioqol.core.PropertyNavigation.Target;
import com.studioqol.core.SseFiles;
import com.studioqol.core.index.PropertyIndex;

/**
 * Ctrl+click navigation from a {@code ${key}} placeholder to its definition: the key's line in each
 * env file (one link per environment), the inline {@code <global-property>}, or the secure file.
 * Resolvable-but-runtime/unresolved placeholders produce no hyperlink.
 */
public class PropertyHyperlinkDetector extends AbstractHyperlinkDetector {

	@Override
	public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region,
			boolean canShowMultipleHyperlinks) {
		if (textViewer == null || region == null) {
			return null;
		}
		IDocument doc = textViewer.getDocument();
		IRegion placeholder = PlaceholderScanner.find(doc, region.getOffset());
		if (placeholder == null) {
			return null;
		}
		String inner = innerOf(doc, placeholder);
		if (inner == null || inner.isEmpty()) {
			return null;
		}
		boolean secure = inner.startsWith("secure::");
		String key = (secure ? inner.substring("secure::".length()) : inner).trim();
		if (key.isEmpty()) {
			return null;
		}
		IFile file = SseFiles.fileFor(doc);
		if (file == null) {
			return null;
		}
		PropertyIndex index = StudioQolPlugin.indexManager().get(file.getProject());
		if (index == null) {
			return null;
		}
		List<Target> targets = PropertyNavigation.targets(index, key);
		if (targets.isEmpty()) {
			return null;
		}
		List<IHyperlink> links = new ArrayList<>();
		for (Target target : targets) {
			links.add(new PropertyHyperlink(placeholder, target.location, target.label));
		}
		if (!canShowMultipleHyperlinks && links.size() > 1) {
			return new IHyperlink[] { links.get(0) };
		}
		return links.toArray(new IHyperlink[0]);
	}

	private static String innerOf(IDocument doc, IRegion region) {
		try {
			String full = doc.get(region.getOffset(), region.getLength());
			if (full.length() < 3) {
				return null;
			}
			return full.substring(2, full.length() - 1).trim();
		} catch (BadLocationException e) {
			return null;
		}
	}
}
