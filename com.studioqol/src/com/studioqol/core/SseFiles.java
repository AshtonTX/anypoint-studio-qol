package com.studioqol.core;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.text.IDocument;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;

/**
 * Resolves the workspace {@link IFile} backing an editor document. Tries the WTP SSE model first
 * (the Configuration XML editor's model), then falls back to the shared text file buffer.
 */
public final class SseFiles {

	private SseFiles() {
	}

	public static IFile fileFor(IDocument document) {
		if (document == null) {
			return null;
		}
		IFile viaModel = viaSseModel(document);
		return (viaModel != null) ? viaModel : viaFileBuffers(document);
	}

	private static IFile viaSseModel(IDocument document) {
		if (!(document instanceof IStructuredDocument)) {
			return null;
		}
		IModelManager mm = StructuredModelManager.getModelManager();
		IStructuredModel model = null;
		try {
			model = mm.getExistingModelForRead((IStructuredDocument) document);
			return (model == null) ? null : toFile(model.getBaseLocation());
		} catch (Exception e) {
			return null;
		} finally {
			if (model != null) {
				model.releaseFromRead();
			}
		}
	}

	private static IFile viaFileBuffers(IDocument document) {
		try {
			ITextFileBuffer buffer = FileBuffers.getTextFileBufferManager().getTextFileBuffer(document);
			if (buffer == null) {
				return null;
			}
			return fromWorkspacePath(buffer.getLocation());
		} catch (Exception e) {
			return null;
		}
	}

	private static IFile toFile(String baseLocation) {
		if (baseLocation == null || baseLocation.isEmpty()) {
			return null;
		}
		return fromWorkspacePath(new Path(baseLocation));
	}

	private static IFile fromWorkspacePath(IPath path) {
		if (path == null) {
			return null;
		}
		IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
		return (file != null && file.exists()) ? file : null;
	}
}
