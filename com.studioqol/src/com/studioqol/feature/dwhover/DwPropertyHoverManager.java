package com.studioqol.feature.dwhover;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.internal.text.html.HTMLTextPresenter;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.editors.text.EditorsUI;

import com.studioqol.StudioQolPlugin;
import com.studioqol.core.DwProjectResolver;
import com.studioqol.core.env.ActiveEnvResolver;
import com.studioqol.core.env.ActiveEnvService;
import com.studioqol.core.index.PropertyIndex;
import com.studioqol.feature.dwhover.PScanner.PHit;
import com.studioqol.feature.hover.PropertyHoverText;

/**
 * Owns the single DataWeave hover popup, reproducing the behavior of Feature 1's Configuration XML
 * hover (which gets it for free from JFace's {@code ITextHover} framework). Because Feature 2 hooks the
 * editors from outside via a global mouse filter, that behavior is driven manually here:
 * <ul>
 *   <li><b>Hover</b> — a lightweight, non-focusable popup with the "Press F2 for focus" affordance.</li>
 *   <li><b>Move into it</b> — when the pointer moves from the token into the popup, the popup is
 *       replaced in place by a resizable, selectable control (the affordance drops away), so the user
 *       can drag-select and copy the text; moving the pointer back out of the popup dismisses it.</li>
 *   <li><b>F2</b> — focus-locks that selectable control (keyboard path): it then stays regardless of
 *       pointer position until it loses focus.</li>
 * </ul>
 * The keep-alive zone is the union of the token's on-screen box and the popup's box, so moving from one
 * to the other never falls through a gap. All methods run on the UI thread.
 */
public final class DwPropertyHoverManager {

	private IInformationControl control;
	private boolean enriched;     // swapped to the selectable control (via move-in or F2)
	private boolean focusLocked;  // F2 pressed → stays until focus lost (vs move-in → dismiss on exit)
	private StyledText anchor;
	private int tokenStart;       // absolute (widget) offset of the token start, inclusive
	private int tokenEnd;         // absolute (widget) offset of the token end, exclusive
	private String shownKey;
	private String currentHtml;
	private Rectangle bounds;     // display-coordinate bounds of the current popup
	private Listener editorDismissListener;
	private Listener displayMoveFilter;
	private FocusListener focusOutListener;

	/** Show (or refresh) the popup for {@code hit} found on the line beginning at {@code lineStartOffset}. */
	public void showFor(StyledText widget, PHit hit, int lineStartOffset) {
		if (widget == null || widget.isDisposed() || hit == null) {
			return;
		}
		int absStart = lineStartOffset + hit.start;
		int absEnd = lineStartOffset + hit.end;
		if (isShowing() && anchor == widget && hit.key.equals(shownKey) && absStart == tokenStart) {
			return; // already showing this exact token — don't flicker
		}
		hide();

		String html = buildHtml(hit);
		if (html == null || html.isEmpty()) {
			return;
		}
		Shell shell = widget.getShell();
		if (shell == null) {
			return;
		}
		DefaultInformationControl hover = new DefaultInformationControl(shell,
				EditorsUI.getTooltipAffordanceString(), new HTMLTextPresenter(true));
		// setSizeConstraints must precede setInformation: the presenter lays out / line-limits the HTML
		// against the max width/height, so unset constraints collapse the popup to a single line.
		hover.setSizeConstraints(500, 300);
		hover.setInformation(html);
		Point size = hover.computeSizeHint();
		hover.setSize(size.x, size.y);
		Point widgetLoc = widget.getLocationAtOffset(absStart);
		Point displayLoc = widget.toDisplay(widgetLoc.x, widgetLoc.y + widget.getLineHeight(absStart));
		hover.setLocation(displayLoc);
		hover.setVisible(true);

		control = hover;
		enriched = false;
		focusLocked = false;
		anchor = widget;
		tokenStart = absStart;
		tokenEnd = absEnd;
		shownKey = hit.key;
		currentHtml = html;
		bounds = new Rectangle(displayLoc.x, displayLoc.y, size.x, size.y);
		installListeners(widget);
	}

	/** @return {@code true} when a popup is currently showing (it is disposed and nulled on {@link #hide}). */
	public boolean isShowing() {
		return control != null;
	}

	/** Hides and disposes the current popup (and removes its listeners/filters), if any. */
	public void hide() {
		removeListeners();
		if (control != null) {
			if (focusOutListener != null) {
				control.removeFocusListener(focusOutListener);
				focusOutListener = null;
			}
			control.dispose();
			control = null;
		}
		anchor = null;
		shownKey = null;
		currentHtml = null;
		bounds = null;
		enriched = false;
		focusLocked = false;
		tokenStart = tokenEnd = -1;
	}

	/** Lifecycle teardown (plugin stop). */
	public void dispose() {
		hide();
	}

	/**
	 * Replace the lightweight hover with a resizable, selectable control in the same place (the "Press
	 * F2" affordance drops away). {@code lock} = the F2 path: also take focus and keep it open until
	 * focus is lost; otherwise (move-in path) it is dismissed when the pointer leaves it.
	 */
	private void enrich(boolean lock) {
		if (control == null || enriched || anchor == null || anchor.isDisposed() || currentHtml == null) {
			return;
		}
		Shell shell = anchor.getShell();
		if (shell == null) {
			hide();
			return;
		}
		enriched = true; // set before the swap so a re-entrant move event won't enrich again
		focusLocked = lock;

		DefaultInformationControl focusable = new DefaultInformationControl(shell,
				new ToolBarManager(SWT.FLAT), new HTMLTextPresenter(false));
		focusable.setSizeConstraints(600, 400);
		focusable.setInformation(currentHtml);
		Point size = focusable.computeSizeHint();
		int width = Math.max(size.x, bounds.width);
		int height = Math.max(size.y, bounds.height);
		focusable.setLocation(new Point(bounds.x, bounds.y));
		focusable.setSize(width, height);

		IInformationControl previous = control;
		control = focusable;            // swap the reference before disposing the old (never null)
		bounds = new Rectangle(bounds.x, bounds.y, width, height);
		focusable.setVisible(true);
		if (previous != null) {
			previous.dispose();
		}
		if (lock) {
			focusable.setFocus();
		}
		// Add focus-out dismissal after this event cycle, so the focus transfer above does not trip a
		// spurious focusLost that would immediately close the just-opened control.
		final IInformationControl enrichedControl = focusable;
		shell.getDisplay().asyncExec(() -> {
			if (control == enrichedControl && enriched && !shell.isDisposed()) {
				focusOutListener = FocusListener.focusLostAdapter(e -> hide());
				enrichedControl.addFocusListener(focusOutListener);
			}
		});
	}

	/** Editor-widget events that dismiss the popup outright (F2 enriches/locks instead). */
	private static final int[] DISMISS_TYPES = {
			SWT.MouseDown, SWT.KeyDown, SWT.MouseWheel, SWT.MouseVerticalWheel, SWT.Dispose,
	};

	private void installListeners(StyledText widget) {
		editorDismissListener = e -> {
			if (e.type == SWT.KeyDown && e.keyCode == SWT.F2 && !focusLocked) {
				e.doit = false;
				enrich(true);
			} else {
				hide();
			}
		};
		for (int type : DISMISS_TYPES) {
			widget.addListener(type, editorDismissListener);
		}
		final Display display = widget.getDisplay();
		displayMoveFilter = e -> onMouseMove(display);
		display.addFilter(SWT.MouseMove, displayMoveFilter);
	}

	/**
	 * Keep-alive / enrich / dismiss decision on every pointer move while the popup is showing. Stays
	 * while the pointer is within the token-or-popup zone (auto-enriching on entry into the popup);
	 * otherwise dismisses, unless the control is F2 focus-locked.
	 */
	private void onMouseMove(Display display) {
		if (anchor == null || anchor.isDisposed() || control == null || bounds == null) {
			hide();
			return;
		}
		Point cursor = display.getCursorLocation();
		Rectangle tokenBox = tokenScreenBox();
		Rectangle keepZone = (tokenBox == null) ? bounds : tokenBox.union(bounds);
		if (keepZone.contains(cursor)) {
			if (!enriched && bounds.contains(cursor)) {
				enrich(false); // moved into the popup → make it selectable/copyable
			}
			return;
		}
		if (focusLocked) {
			return; // F2-locked: stays until it loses focus
		}
		hide();
	}

	/** The token's on-screen rectangle (display coordinates), or {@code null} if unavailable. */
	private Rectangle tokenScreenBox() {
		if (anchor == null || anchor.isDisposed()) {
			return null;
		}
		Point start = anchor.getLocationAtOffset(tokenStart);
		Point end = anchor.getLocationAtOffset(tokenEnd);
		int lineHeight = anchor.getLineHeight(tokenStart);
		Point topLeft = anchor.toDisplay(start.x, start.y);
		int width = (end.y == start.y) ? Math.max(8, end.x - start.x) : 8;
		return new Rectangle(topLeft.x, topLeft.y, width, lineHeight);
	}

	private void removeListeners() {
		if (displayMoveFilter != null) {
			Display display = Display.getDefault();
			if (display != null && !display.isDisposed()) {
				display.removeFilter(SWT.MouseMove, displayMoveFilter);
			}
			displayMoveFilter = null;
		}
		if (editorDismissListener != null && anchor != null && !anchor.isDisposed()) {
			for (int type : DISMISS_TYPES) {
				anchor.removeListener(type, editorDismissListener);
			}
		}
		editorDismissListener = null;
	}

	/**
	 * Builds the popup HTML for {@code p('key')} by reusing Feature 1's stateless renderer — the same
	 * per-environment value table, active-env highlight, and honest secure / runtime-only / unresolved
	 * states shown for {@code ${key}} in the Configuration XML editor, with a {@code p('key')} header.
	 */
	private String buildHtml(PHit hit) {
		IProject project = DwProjectResolver.resolveActiveMuleProject();
		PropertyIndex index = (project == null) ? null : StudioQolPlugin.indexManager().get(project);
		String envProperty = new ActiveEnvService().envPropertyName();
		String activeEnv = (project == null) ? null : ActiveEnvResolver.resolve(project, envProperty);
		return PropertyHoverText.render(hit.key, false, envProperty, activeEnv, index,
				PropertyHoverText.LabelStyle.DATAWEAVE);
	}
}
