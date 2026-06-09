package com.studioqol.feature.hover;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextHoverExtension2;
import org.eclipse.jface.text.ITextViewer;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.SseFiles;
import com.studioqol.core.env.ActiveEnvResolver;
import com.studioqol.core.env.ActiveEnvService;
import com.studioqol.core.index.PropertyIndex;
import com.studioqol.core.scan.PlaceholderKey;
import com.studioqol.core.scan.PlaceholderScanner;

/**
 * Feature 1 hover. Fires only on a {@code ${...}} token (so it never collides with the built-in XML
 * hover) and resolves the placeholder against the per-project {@link PropertyIndex}, rendering an
 * HTML popup: a per-environment value list (active env highlighted when known), or an explicit
 * secure / runtime-only / unresolved flag. Never decrypts or fabricates.
 */
public class PropertyHoverProcessor
		implements ITextHover, ITextHoverExtension, ITextHoverExtension2 {

	private IInformationControlCreator controlCreator;

	@Override
	public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
		if (textViewer == null) {
			return null;
		}
		return PlaceholderScanner.find(textViewer.getDocument(), offset);
	}

	@Override
	public Object getHoverInfo2(ITextViewer textViewer, IRegion hoverRegion) {
		return computeHtml(textViewer, hoverRegion);
	}

	@Override
	@Deprecated
	public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		return computeHtml(textViewer, hoverRegion);
	}

	@Override
	public IInformationControlCreator getHoverControlCreator() {
		if (controlCreator == null) {
			controlCreator = new PropertyHoverControlCreator();
		}
		return controlCreator;
	}

	private String computeHtml(ITextViewer textViewer, IRegion hoverRegion) {
		if (textViewer == null || hoverRegion == null) {
			return null;
		}
		IDocument doc = textViewer.getDocument();
		PlaceholderKey pk = PlaceholderKey.of(doc, hoverRegion);
		if (pk == null) {
			return null;
		}
		String envProperty = new ActiveEnvService().envPropertyName();
		IFile file = SseFiles.fileFor(doc);
		IProject project = (file == null) ? null : file.getProject();
		PropertyIndex index = (project == null) ? null : StudioQolPlugin.indexManager().get(project);
		String activeEnv = (project == null) ? null : ActiveEnvResolver.resolve(project, envProperty);
		return PropertyHoverText.render(pk.key, pk.secure, envProperty, activeEnv, index);
	}
}
