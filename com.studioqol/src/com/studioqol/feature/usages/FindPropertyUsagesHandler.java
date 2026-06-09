package com.studioqol.feature.usages;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import com.studioqol.core.SseFiles;
import com.studioqol.core.scan.PlaceholderKey;
import com.studioqol.core.scan.PlaceholderScanner;

/**
 * "Find Property Usages": runs a {@link PropertyUsageQuery} for the {@code ${key}} placeholder
 * under the caret of the active text editor. Quietly tells the status line (and does nothing else)
 * when the caret is not on a placeholder.
 */
public final class FindPropertyUsagesHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) {
		IEditorPart part = HandlerUtil.getActiveEditor(event);
		ITextEditor editor = part instanceof ITextEditor ? (ITextEditor) part
				: part == null ? null : part.getAdapter(ITextEditor.class);
		if (editor == null) {
			return null;
		}
		ISelection selection = editor.getSelectionProvider().getSelection();
		IDocument doc = editor.getDocumentProvider().getDocument(editor.getEditorInput());
		if (!(selection instanceof ITextSelection) || doc == null) {
			return null;
		}
		IRegion placeholder = PlaceholderScanner.find(doc, ((ITextSelection) selection).getOffset());
		PlaceholderKey pk = PlaceholderKey.of(doc, placeholder);
		IFile file = SseFiles.fileFor(doc);
		if (pk == null || file == null) {
			editor.getEditorSite().getActionBars().getStatusLineManager()
					.setMessage("Place the caret on a ${property} placeholder to find its usages.");
			return null;
		}
		NewSearchUI.runQueryInBackground(new PropertyUsageQuery(file.getProject(), pk.key, pk.secure));
		return null;
	}
}
