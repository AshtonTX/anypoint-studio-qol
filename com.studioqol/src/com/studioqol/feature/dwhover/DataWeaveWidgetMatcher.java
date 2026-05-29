package com.studioqol.feature.dwhover;

import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

/**
 * Decides whether an SWT {@link StyledText} belongs to one of Studio's DataWeave editors — the only
 * Studio-internal coupling Feature 2 has, and it is by class-<em>name</em> only (no Mule type is
 * imported, no reflection, no OSGi fragment).
 *
 * <p>All DataWeave editors (the standalone {@code .dwl} editor, the Transform Message editor, and the
 * embedded expression-mode fields such as a Logger's {@code fx} Message) are JFace source viewers over
 * a {@link StyledText}, but there is no back-reference from the widget to its viewer/document. So we
 * identify the editor by walking the SWT parent chain to the {@link Shell} and matching the package
 * prefixes of two kinds of host composite:
 * <ul>
 *   <li><b>DataWeave-specific editor composites</b> (these <em>are</em> SWT widgets in the chain):
 *       {@code org.mule.tooling.dfl.ui} (standalone {@code DFLEditor}) and
 *       {@code org.mule.tooling.ui.dfl.contribution} (Transform Message
 *       {@code …editor.script.DataWeaveEditor}).</li>
 *   <li><b>Mule property-panel hosts.</b> An embedded expression editor (e.g. a Logger's {@code fx}
 *       Message) is an Xtext <em>viewer</em>, not a widget, so it leaves no DataWeave-specific class in
 *       the parent chain — the chain is plain SWT composites up to the property page it lives in. The
 *       nearest reliable markers are {@code org.mule.tooling.ui.modules.core.widgets.meta.propertypage}
 *       ({@code PageWithControlsProvider}) and {@code org.mule.tooling.ui.widgets.tab}
 *       ({@code VerticalPropertiesTabFolder}). Because the filter only fires on a {@code StyledText}
 *       and the expression editor is the only {@code StyledText} in those panels, this does not
 *       over-trigger in practice (and our token scan further gates on a {@code p('...')} call).</li>
 * </ul>
 * If MuleSoft renames these packages in a future Studio, this list is the single place to update.
 *
 * <p>The result is cached per {@code StyledText} instance (a widget's ancestry never changes after
 * creation), so the global mouse filter does the parent walk at most once per editor widget. This
 * class is only ever touched on the UI thread, so the cache needs no synchronization.
 */
public final class DataWeaveWidgetMatcher {

	private static final String[] HOST_PREFIXES = {
			// DataWeave-specific editor composites (real widgets in the parent chain):
			"org.mule.tooling.dfl.ui",                                    // standalone .dwl DFLEditor
			"org.mule.tooling.ui.dfl.contribution",                       // Transform Message editor
			// Mule property-panel hosts (for embedded expression-mode editors, e.g. a Logger fx field):
			"org.mule.tooling.ui.modules.core.widgets.meta.propertypage", // PageWithControlsProvider
			"org.mule.tooling.ui.widgets.tab",                            // VerticalPropertiesTabFolder
	};

	private final Map<StyledText, Boolean> cache = new WeakHashMap<>();

	/** @return {@code true} when {@code widget} is (inside) a Studio DataWeave editor. */
	public boolean isDataWeaveWidget(Control widget) {
		if (widget == null || widget.isDisposed()) {
			return false;
		}
		if (widget instanceof StyledText) {
			Boolean cached = cache.get(widget);
			if (cached != null) {
				return cached;
			}
			boolean result = walk(widget);
			cache.put((StyledText) widget, result);
			return result;
		}
		return walk(widget);
	}

	private static boolean walk(Control widget) {
		for (Control c = widget; c != null && !(c instanceof Shell); c = c.getParent()) {
			String className = c.getClass().getName();
			for (String prefix : HOST_PREFIXES) {
				if (className.startsWith(prefix)) {
					return true;
				}
			}
		}
		return false;
	}
}
