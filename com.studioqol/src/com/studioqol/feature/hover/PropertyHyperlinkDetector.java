package com.studioqol.feature.hover;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
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
import com.studioqol.core.scan.PlaceholderKey;
import com.studioqol.core.scan.PlaceholderScanner;

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
		PlaceholderKey pk = PlaceholderKey.of(doc, placeholder);
		if (pk == null) {
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
		List<Target> targets = PropertyNavigation.targets(index, pk.key, pk.secure);
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
}
