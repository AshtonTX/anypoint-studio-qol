package com.studioqol.feature.hover;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.index.SourceLocation;

/** Opens a property's definition file and selects the defining key. */
public class PropertyHyperlink implements IHyperlink {

	private final IRegion region;
	private final SourceLocation target;
	private final String text;

	public PropertyHyperlink(IRegion region, SourceLocation target, String text) {
		this.region = region;
		this.target = target;
		this.text = text;
	}

	@Override
	public IRegion getHyperlinkRegion() {
		return region;
	}

	@Override
	public String getTypeLabel() {
		return null;
	}

	@Override
	public String getHyperlinkText() {
		return text;
	}

	@Override
	public void open() {
		if (target == null || target.file == null) {
			return;
		}
		try {
			IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			if (window == null) {
				return;
			}
			IWorkbenchPage page = window.getActivePage();
			if (page == null) {
				return;
			}
			IEditorPart editor = IDE.openEditor(page, target.file, true);
			if (editor instanceof ITextEditor && target.offset >= 0) {
				((ITextEditor) editor).selectAndReveal(target.offset, Math.max(0, target.length));
			}
		} catch (Exception e) {
			StudioQolPlugin.log(e);
		}
	}
}
