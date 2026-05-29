package com.studioqol.feature.hover;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;

import com.studioqol.core.PropertyNavigation;
import com.studioqol.core.index.SourceLocation;

/** Opens a property's definition file and selects the defining key (via shared {@link PropertyNavigation}). */
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
		PropertyNavigation.open(target);
	}
}
