package com.studioqol.feature.dwhover;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import com.studioqol.StudioQolPlugin;
import com.studioqol.feature.dwhover.PScanner.PHit;

/**
 * Global SWT event filter (registered on the {@code Display}) that adds the DataWeave property hover
 * and Ctrl/⌘-click navigation to editors Feature 2 does not own. Display filters run before a widget's
 * own listeners, so when the pointer rests on / Ctrl-clicks a {@code p('key')} token inside a DataWeave
 * editor we act and cancel the event ({@code event.type = SWT.None}) so Studio's native Xtext
 * hover/hyperlink does not also fire. Every other widget/token/click is left completely untouched, so
 * native behavior is preserved everywhere else.
 *
 * <p>Registered for {@code SWT.MouseHover} (→ hover popup) and {@code SWT.MouseDown} (→ navigation when
 * {@code MOD1} is held). The whole callback is guarded by try/catch — a filter that threw would break
 * mouse handling for the entire workbench.
 */
public final class DwEditorEventFilter implements Listener {

	private final DataWeaveWidgetMatcher matcher;
	private final DwPropertyHoverManager hoverManager;
	private final DwPropertyNavigator navigator;

	public DwEditorEventFilter(DataWeaveWidgetMatcher matcher, DwPropertyHoverManager hoverManager,
			DwPropertyNavigator navigator) {
		this.matcher = matcher;
		this.hoverManager = hoverManager;
		this.navigator = navigator;
	}

	@Override
	public void handleEvent(Event event) {
		try {
			if (event.type == SWT.MouseHover) {
				handleHover(event);
			} else if (event.type == SWT.MouseDown) {
				handleClick(event);
			}
		} catch (Exception e) {
			StudioQolPlugin.log(e);
		}
	}

	private void handleHover(Event event) {
		Located located = locate(event);
		if (located == null) {
			return; // not a p('...') in a DataWeave editor — leave the native hover alone
		}
		// We own this token: suppress the native hover, then show ours.
		event.type = SWT.None;
		event.doit = false;
		hoverManager.showFor(located.widget, located.hit, located.lineStart);
	}

	private void handleClick(Event event) {
		if ((event.stateMask & SWT.MOD1) == 0 || event.button != 1) {
			return; // only a Ctrl/⌘ + left-click is navigation; everything else passes through
		}
		Located located = locate(event);
		if (located == null) {
			return; // not a p('...') — leave the native Ctrl/⌘-click (e.g. function navigation) alone
		}
		// We own this click: suppress it (so the editor does not move the caret / start a selection),
		// close any hover, then navigate to the property's definition.
		event.type = SWT.None;
		event.doit = false;
		hoverManager.hide();
		navigator.navigate(located.widget, located.hit);
	}

	/**
	 * Resolves the {@code p('...')} token under an event's widget point. Returns {@code null} unless the
	 * event widget is a DataWeave-editor {@link StyledText} and the pointer is over a {@code p('key')}.
	 */
	private Located locate(Event event) {
		if (!(event.widget instanceof StyledText)) {
			return null; // cheapest reject for the overwhelming majority of events
		}
		StyledText widget = (StyledText) event.widget;
		if (widget.isDisposed() || !matcher.isDataWeaveWidget(widget)) {
			return null;
		}
		// Display-filter mouse coordinates are relative to the receiving widget (the StyledText).
		int offset = widget.getOffsetAtPoint(new Point(event.x, event.y));
		if (offset < 0) {
			return null; // not over a character
		}
		int line = widget.getLineAtOffset(offset);
		int lineStart = widget.getOffsetAtLine(line);
		PHit hit = PScanner.find(widget.getLine(line), offset - lineStart);
		return (hit == null) ? null : new Located(widget, hit, lineStart);
	}

	/** The DataWeave token located under an event point. */
	private static final class Located {
		final StyledText widget;
		final PHit hit;
		final int lineStart;

		Located(StyledText widget, PHit hit, int lineStart) {
			this.widget = widget;
			this.hit = hit;
			this.lineStart = lineStart;
		}
	}
}
