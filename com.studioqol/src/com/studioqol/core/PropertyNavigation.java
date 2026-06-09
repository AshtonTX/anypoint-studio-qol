package com.studioqol.core;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.index.EnvValue;
import com.studioqol.core.index.PropertyDefinition;
import com.studioqol.core.index.PropertyIndex;
import com.studioqol.core.index.SourceLocation;

/**
 * Shared "go to a property's definition" logic, reusable by any feature. Resolves a key to its
 * definition target(s) and opens a target in an editor at the defining line. The same resolution
 * Feature 1's {@code PropertyHyperlinkDetector} performs for {@code ${key}} in the Configuration XML
 * editor, factored out so Feature 2's DataWeave Ctrl/⌘-click ({@code p('key')}) reuses it.
 */
public final class PropertyNavigation {

	private PropertyNavigation() {
	}

	/** A navigable definition target: where it is, plus a human label for a chooser menu. */
	public static final class Target {
		public final SourceLocation location;
		public final String label;

		Target(SourceLocation location, String label) {
			this.location = location;
			this.label = label;
		}
	}

	/**
	 * @return the definition target(s) for {@code key}: one per environment file for an env-file
	 *         property (each with a {@code file (env)} label), or the single declaration for an inline
	 *         global / secure property. Empty when the key is unknown or has no locatable definition.
	 */
	public static List<Target> targets(PropertyIndex index, String key) {
		List<Target> out = new ArrayList<>();
		if (index == null || key == null) {
			return out;
		}
		PropertyDefinition def = index.lookup(key);
		if (def == null) {
			return out;
		}
		switch (def.kind) {
		case ENV_FILE:
			for (String env : index.environments()) {
				EnvValue ev = def.valuesByEnv.get(env);
				if (ev != null && ev.location != null && ev.location.file != null) {
					out.add(new Target(ev.location,
							"Open " + ev.location.file.getName() + "  (" + env + ")"));
				}
			}
			break;
		case INLINE_GLOBAL:
		case SECURE:
			if (def.declaration != null && def.declaration.file != null) {
				out.add(new Target(def.declaration, "Open " + def.declaration.file.getName()));
			}
			break;
		default:
			break;
		}
		return out;
	}

	/** Opens {@code target}'s file and selects the defining key. No-op on a null/fileless target. */
	public static void open(SourceLocation target) {
		if (target != null) {
			openAt(target.file, target.offset, target.length);
		}
	}

	/** Opens {@code file} in an editor and selects {@code [offset, offset+length)}. */
	public static void openAt(IFile file, int offset, int length) {
		if (file == null) {
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
			IEditorPart editor = IDE.openEditor(page, file, true);
			if (editor instanceof ITextEditor && offset >= 0) {
				((ITextEditor) editor).selectAndReveal(offset, Math.max(0, length));
			}
		} catch (Exception e) {
			StudioQolPlugin.log(e);
		}
	}
}
