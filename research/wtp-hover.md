# research/wtp-hover.md — Text hover (and hyperlink) in Studio's Configuration XML editor

Scope: how to contribute a `${...}` hover + Ctrl+click to the WTP/SSE `StructuredTextEditor`
used by Anypoint Studio 7.25.0 (Eclipse 4.31; `wst.sse.ui 1.7.900`, `wst.xml.ui 1.2.701`,
`jface.text 3.25.0`, `ui.workbench.texteditor 3.17.300`). Researched in-thread (the delegated
agent for this topic was cancelled).

## Confirmed facts
1. The "Configuration XML" tab is the standard WTP/SSE `StructuredTextEditor`, content type
   `org.eclipse.core.runtime.xml`. Hovers/double-click are contributed **without subclassing**
   via the `org.eclipse.wst.sse.ui.editorConfiguration` extension point. Evidence: Studio's own
   `org.eclipse.wst.xml.ui` bundle registers the default XML hover this way (verbatim below).
2. A hover is registered with `<documentationTextHover class= target=>`, keyed by **partition
   type** via `target`. For XML the partition target is **`org.eclipse.wst.xml.XML_DEFAULT`**
   (normal element text + attribute values live in this partition; comments=`XML_COMMENT`,
   CDATA=`XML_CDATA`).
3. WTP aggregates contributed hovers through `org.eclipse.wst.sse.ui.internal.taginfo.BestMatchHover`.
   Contributed hovers **cannot be explicitly sorted**; the registry orders them by **plugin id**
   (`org.eclipse.wst.sse.ui.internal.extension.RegistryReader#orderExtensions`). The effective
   winner for an offset is the first hover (in that order) whose `getHoverRegion()` returns a
   **non-null** region. (Confirms the original CLAUDE.md assumption.)
4. Hovers and hyperlinks are SEPARATE: Ctrl+click uses
   `org.eclipse.ui.workbench.texteditor.hyperlinkDetectors` (not editorConfiguration).

## Exact registration XML (verified in two shipping bundles)
Studio `org.eclipse.wst.xml.ui` v1.2.701 (the default XML hover — our coexistence partner):
```xml
<extension point="org.eclipse.wst.sse.ui.editorConfiguration">
  <sourceViewerConfiguration
     class="org.eclipse.wst.xml.ui.StructuredTextViewerConfigurationXML"
     target="org.eclipse.core.runtime.xml, org.eclipse.wst.xml.core.xmlsource"/>
  <documentationTextHover
     class="org.eclipse.wst.xml.ui.internal.taginfo.XMLTagInfoHoverProcessor"
     target="org.eclipse.wst.xml.XML_DEFAULT"/>
  <doubleClickStrategy
     class="org.eclipse.wst.xml.ui.internal.doubleclick.XMLDoubleClickStrategy"
     target="org.eclipse.wst.xml.XML_DEFAULT"/>
</extension>
```
angelozerr `eclipse-wtp-webresources` adds an ADDITIONAL hover the same way (proves coexistence):
```xml
<extension point="org.eclipse.wst.sse.ui.editorConfiguration">
  <documentationTextHover
     class="org.eclipse.wst.html.webresources.internal.ui.hover.WebResourcesHoverProcessor"
     target="org.eclipse.wst.html.HTML_DEFAULT"/>
</extension>
```
**Ours (XML):**
```xml
<extension point="org.eclipse.wst.sse.ui.editorConfiguration">
  <documentationTextHover
     class="com.studioqol.internal.hover.PropertyHoverProcessor"
     target="org.eclipse.wst.xml.XML_DEFAULT"/>
</extension>
```
`class` must implement `org.eclipse.jface.text.ITextHover` (+ `ITextHoverExtension2` for rich popups).

## BestMatchHover ordering + collision mitigation (Milestone-A de-risk)
Risk: if the default `XMLTagInfoHoverProcessor` returns a non-null region at our offset AND sorts
first by plugin id, our hover may never be consulted.

Why it is low-risk for us, and how we guarantee it:
1. **Primary (region-null fall-through).** `${...}` placeholders live inside attribute VALUES
   (`message="${greeting}"`) and element text. The default XML hover documents element/attribute
   *names* and grammar info — it returns null there. If only our hover yields a region, we win
   regardless of order. This is exactly why angelozerr's CSS-class hover coexists with the default
   HTML hover.
2. **Secondary (plugin-id tie-break).** Choose a bundle id that sorts BEFORE `org.eclipse.wst.xml.ui`
   in string order, e.g. **`com.studioqol`** (`'c' < 'o'`). Then even where both return a region,
   ours is consulted first.
3. **Validation.** Milestone A ships a hardcoded hover and empirically confirms it fires over a
   `${...}` in an attribute value and in element text. If it doesn't, the cause is ordering/partition
   and is fixed via (2) or a partition check.
Do NOT try to remove/replace the default hover — coexist.

## ITextHover / ITextHoverExtension2 flow (recommended design)
Prefer the angelozerr style: **return a non-null region ONLY when we actually have something to
show**, so we never "win" a region and then render nothing.
```java
public class PropertyHoverProcessor
    implements ITextHover, ITextHoverExtension, ITextHoverExtension2 {

  private IInformationControlCreator hoverCC, presenterCC;

  @Override public IRegion getHoverRegion(ITextViewer v, int offset) {
    IDocument doc = (v == null) ? null : v.getDocument();
    if (doc == null) return null;
    return PlaceholderScanner.find(doc, offset);   // null unless cursor is on a ${...}
  }

  @Override public Object getHoverInfo2(ITextViewer v, IRegion region) {
    String key = PlaceholderScanner.extractKey(v.getDocument(), region); // strips ${ } & secure::
    // Milestone A: return a constant HoverInput when region != null.
    // Milestone B: resolve via PropertyIndex.forProject(file.getProject()).lookup(key) -> HTML table.
    String html = PropertyHoverHtml.render(key /*, PropertyDefinition */);
    return (html == null) ? null : new HoverInput(html); // HoverInput carries the HTML string
  }

  @Override @Deprecated public String getHoverInfo(ITextViewer v, IRegion r) {
    Object i = getHoverInfo2(v, r); return (i == null) ? null : i.toString();
  }

  @Override public IInformationControlCreator getHoverControlCreator() {
    if (hoverCC == null) hoverCC = new PropertyHoverControlCreator(getPresenterControlCreator());
    return hoverCC;
  }
  public IInformationControlCreator getPresenterControlCreator() {
    if (presenterCC == null) presenterCC = new PropertyPresenterControlCreator();
    return presenterCC;
  }
}
```
Note: implementing the interfaces directly avoids the internal base
`org.eclipse.wst.sse.ui.internal.taginfo.AbstractHoverProcessor` (angelozerr extends it, but it is
internal API and unnecessary for plain-text `${...}` scanning).

## Rich popup: IInformationControlCreator (per-env TABLE + clickable source links)
Use `BrowserInformationControl` (full HTML → real `<table>`, clickable links) when available, else
`DefaultInformationControl`. This is angelozerr's `WebResourcesHoverControlCreator` /
`PresenterControlCreator` pattern (F2 focuses the sticky control; a `LocationListener` intercepts
link clicks → open the env file at the right line).
```java
public class PropertyHoverControlCreator extends AbstractReusableInformationControlCreator {
  private final IInformationControlCreator presenter;
  public PropertyHoverControlCreator(IInformationControlCreator p) { this.presenter = p; }
  @Override public IInformationControl doCreateInformationControl(Shell parent) {
    if (BrowserInformationControl.isAvailable(parent)) {
      return new BrowserInformationControl(parent, JFaceResources.DIALOG_FONT, "Press F2 for focus") {
        @Override public IInformationControlCreator getInformationPresenterControlCreator() {
          return presenter; // the focusable/sticky variant (toolbar, selectable, link-clickable)
        }
      };
    }
    return new DefaultInformationControl(parent, "Press F2 for focus");
  }
}
```
Dependency caveat: `org.eclipse.jface.internal.text.html.BrowserInformationControl` and
`HTMLTextPresenter` are **internal** to `org.eclipse.jface.text` (x-internal, but resolvable;
JDT + angelozerr both use them — expect a discouraged-access warning, not a failure). Pure-API
fallback `new DefaultInformationControl(parent, new HTMLTextPresenter(false))` renders only a
limited HTML subset (no real tables) — acceptable for Milestone A, not for the final per-env table.

## getHoverRegion: detecting ${...} (incl. ${secure::key} and nested ${a${b}})
Placeholders don't span lines, so scan the current line with a brace stack and return the
**innermost** `${...}` span containing the offset.
```java
final class PlaceholderScanner {
  static IRegion find(IDocument doc, int offset) {
    try {
      IRegion line = doc.getLineInformationOfOffset(offset);
      int base = line.getOffset();
      String t = doc.get(base, line.getLength());
      int rel = offset - base;
      java.util.Deque<Integer> stack = new java.util.ArrayDeque<>();
      IRegion best = null;
      for (int i = 0; i < t.length(); i++) {
        if (i + 1 < t.length() && t.charAt(i) == '$' && t.charAt(i + 1) == '{') { stack.push(i); i++; }
        else if (t.charAt(i) == '}' && !stack.isEmpty()) {
          int s = stack.pop(), endEx = i + 1;
          if (rel >= s && rel < endEx && (best == null || (endEx - s) < best.getLength()))
            best = new Region(base + s, endEx - s);   // innermost = smallest containing span
        }
      }
      return best;
    } catch (BadLocationException e) { return null; }
  }
  static String extractKey(IDocument doc, IRegion r) throws BadLocationException {
    String s = doc.get(r.getOffset(), r.getLength());   // "${secure::db.password}"
    s = s.substring(2, s.length() - 1).trim();           // "secure::db.password"
    return s.startsWith("secure::") ? s.substring("secure::".length()) : s;
  }
  static boolean isSecure(IDocument doc, IRegion r) throws BadLocationException {
    return doc.get(r.getOffset(), r.getLength()).startsWith("${secure::");
  }
}
```

## Ctrl+click go-to-definition (Milestone C)
Register a hyperlink detector (separate extension point):
```xml
<extension point="org.eclipse.ui.workbench.texteditor.hyperlinkDetectors">
  <hyperlinkDetector
     class="com.studioqol.internal.hyperlink.PropertyHyperlinkDetector"
     id="com.studioqol.hyperlink.property"
     name="%hyperlink.property.name"
     targetId="org.eclipse.wst.xml.core.xmlsource"/>   <!-- XML source editor hyperlink target -->
</extension>
```
angelozerr used `targetId="org.eclipse.wst.html.core.htmlsource"` for HTML; the XML analogue is
`org.eclipse.wst.xml.core.xmlsource` (the same id seen in the `sourceViewerConfiguration` target
above) — **verify in Milestone C**. Implement `IHyperlinkDetector.detectHyperlinks()` reusing
`PlaceholderScanner.find()`; return an `IHyperlink` whose `open()` opens the env file (or inline
global-property) and selects the key's line via the property index's `SourceLocation`.

## Required bundle dependencies (hover + hyperlink)
Require-Bundle: `org.eclipse.jface.text`, `org.eclipse.ui.workbench.texteditor`,
`org.eclipse.wst.sse.ui`, `org.eclipse.wst.sse.core`, `org.eclipse.core.resources`,
`org.eclipse.core.runtime`, `org.eclipse.ui`, `org.eclipse.ui.editors`
(`org.eclipse.wst.xml.ui`/`wst.xml.core` only if we touch the XML DOM — see project-model.md).
Internal: `org.eclipse.jface.internal.text.html` (for BrowserInformationControl).

## Open questions
- Exact XML hyperlink `targetId` (`org.eclipse.wst.xml.core.xmlsource` assumed) — confirm in Milestone C.
- Whether `XMLTagInfoHoverProcessor` ever returns a non-null region inside an attribute value
  (would make mitigation #2 mandatory) — settle empirically in Milestone A.
- Final `BestMatchHover` tie-break is assumed = plugin-id order; validated by the smoke test.

## Contradictions with original assumptions
- None material. The editorConfiguration mechanism holds; the precise element is
  `documentationTextHover` (not a generic `texthover`), and BestMatchHover plugin-id ordering is
  confirmed (and is genuinely unsortable) — the original assumption was correct.

## Citations
- WTP SSE Editor Configuration design: https://eclipse.dev/webtools/wst/components/sse/designs/EditorConfiguration.html
- ITextHoverExtension2 API: https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/jface/text/ITextHoverExtension2.html
- hyperlinkDetectors extension point: https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/extension-points/org_eclipse_ui_workbench_texteditor_hyperlinkDetectors.html
- angelozerr plugin.xml: https://github.com/angelozerr/eclipse-wtp-webresources/blob/master/org.eclipse.a.wst.html.webresources.ui/plugin.xml
- WebResourcesHoverProcessor.java: https://github.com/angelozerr/eclipse-wtp-webresources/blob/master/org.eclipse.a.wst.html.webresources.ui/src/org/eclipse/wst/html/webresources/internal/ui/hover/WebResourcesHoverProcessor.java
- WebResourcesHoverControlCreator.java + PresenterControlCreator.java: same repo, .../internal/ui/hover/
- CSSHyperlinkDetector.java: https://github.com/angelozerr/eclipse-wtp-webresources/blob/master/org.eclipse.a.wst.html.webresources.ui/src/org/eclipse/wst/html/webresources/internal/ui/hyperlink/CSSHyperlinkDetector.java
- BestMatchHover plugin-id ordering (RegistryReader#orderExtensions): WTP SSE behavior, surfaced via Eclipse community discussion (forum thread t/674676; the live page now returns a service-shutdown notice) — corroborated by local bundle evidence and angelozerr's working coexisting hover.
- Local ground truth: `/Applications/AnypointStudio.app/Contents/Eclipse/plugins/org.eclipse.wst.xml.ui_1.2.701.v202308160453.jar!/plugin.xml`
