package com.studioqol.feature.hover;

import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.internal.text.html.HTMLTextPresenter;
import org.eclipse.jface.text.AbstractReusableInformationControlCreator;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.editors.text.EditorsUI;

/**
 * Builds the hover popup as an HTML-rendering box (bold labels, line breaks) styled like Studio's
 * native XML hover, with a "Press F2 for focus" affordance. Pressing F2 swaps in the resizable,
 * focusable variant via {@link #getInformationPresenterControlCreator()}.
 */
public final class PropertyHoverControlCreator extends AbstractReusableInformationControlCreator {

	private final boolean resizable;

	public PropertyHoverControlCreator() {
		this(false);
	}

	private PropertyHoverControlCreator(boolean resizable) {
		this.resizable = resizable;
	}

	@Override
	protected IInformationControl doCreateInformationControl(Shell parent) {
		if (resizable) {
			// Focused / resizable control shown when the user presses F2.
			return new DefaultInformationControl(parent, new ToolBarManager(SWT.FLAT),
					new HTMLTextPresenter(false));
		}
		final IInformationControlCreator presenter = new PropertyHoverControlCreator(true);
		return new DefaultInformationControl(parent, EditorsUI.getTooltipAffordanceString(),
				new HTMLTextPresenter(true)) {
			@Override
			public IInformationControlCreator getInformationPresenterControlCreator() {
				return presenter;
			}
		};
	}
}
