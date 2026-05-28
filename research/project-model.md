# Per-Project Property Index — Shared Infrastructure Design

Research agent: `agent-project-model`. Target: Anypoint Studio 7.25.0 (Eclipse 4.31 base, Java 17).
Scope: the shared per-project **property INDEX** that Feature 1 (Property Value Hover + Ctrl+click)
consumes now and future features (find-usages, validation markers, env switcher) reuse.

All API claims carry a citation URL in the **Citations** section at the bottom (numbered `[n]`).

---

## 1. Confirmed facts (from the fixture, read-only)

### 1.1 Mule project nature ID — CONFIRMED (and it corrects the mission's hypothesis)
`/Users/ashton/mule-plugin-workspace/plugin-testing/.project` declares **two natures and two builders**:

```xml
<buildSpec>
    <buildCommand><name>org.eclipse.jdt.core.javabuilder</name><arguments></arguments></buildCommand>
    <buildCommand><name>org.mule.tooling.core.muleStudioBuilder</name><arguments></arguments></buildCommand>
</buildSpec>
<natures>
    <nature>org.mule.tooling.core.muleStudioNature</nature>
    <nature>org.eclipse.jdt.core.javanature</nature>
</natures>
```

- The Mule nature ID is **`org.mule.tooling.core.muleStudioNature`** — **NOT** `org.mule.tooling.core.muleNature`
  as the mission hypothesized. This is the load-bearing detection key. Corroborated externally [13].
- A Mule-specific builder **`org.mule.tooling.core.muleStudioBuilder`** is also present (secondary signal).
- The JDT java nature (`org.eclipse.jdt.core.javanature`) is also present, so a "has JDT nature" check alone
  is insufficient to identify a Mule project.

### 1.2 Property convention (matches CLAUDE.md exactly)
From `plugin-testing/src/main/mule/plugin-testing.xml`:
- Env-parameterized source: `<configuration-properties file="envs/${env}.properties" doc:name="Env properties"/>` (line 19).
- Inline global property: `<global-property name="app.displayName" value="Studio QoL Demo" .../>` (line 22).
- **Chained** global property (value embeds placeholders): `<global-property name="app.banner" value="${app.displayName} [${env}]" .../>` (line 25).
- Secure config: `<secure-properties:config name="Secure_Config" file="envs/secure.properties" key="${secure.key}">` with nested `<secure-properties:encrypt algorithm="Blowfish"/>` (lines 29–34). Namespace prefix `secure-properties` bound to `http://www.mulesoft.org/schema/mule/secure-properties`.
- Placeholders referenced in `${...}`: `greeting`, `db.url`, `feature.newCheckout`, `app.displayName`, `app.banner`, `env`, `does.not.exist`, `secure::db.password`, plus `http.host`/`http.port`/`http.path` in the `<http:listener-config>`.
- Active-env property name is **`env`** (`file="envs/${env}.properties"`; VM arg `-M-Denv=<env>`). Make configurable, default `env`.

### 1.3 Env files (self-contained, same keys per env)
`plugin-testing/src/main/resources/envs/`: `local.properties`, `test.properties`, `prod.properties`,
`example.properties` (template), `secure.properties`. Each `.properties` exercises a parsing edge case:
- Normal `key=value`: `greeting=Hello from LOCAL`.
- **Blank value**: `greeting=` and `http.host=` (in `example.properties`) → state EMPTY.
- **Placeholder-ish value with angle brackets**: `db.url=jdbc:postgresql://<host>:<port>/<database>` (template) — *not* a Mule placeholder, just literal text; still a PRESENT value.
- **Comments**: lines beginning with `#`.
- **Secure / encrypted value**: `secure.properties` has `db.password=!["c2FtcGxlLWVuY3J5cHRlZC1wYXNz"]` → state SECURE (the `![...]` form).
- All five env files were observed; `local`/`test`/`prod` carry the same key set with differing values; `example` is the blank template; `secure` holds only `db.password`.

### 1.4 mule-artifact.json
`{ "minMuleVersion": "4.11.0", "javaSpecificationVersions": ["17"] }`. Presence of this file is a useful
**secondary** Mule-project signal (see §5), but it is not a nature.

### 1.5 Mule secure-properties semantics (for state detection, §4)
Mule references secure values with the **`secure::` prefix** (e.g. `${secure::db.password}`) and stores the
encrypted value wrapped as **`![...]`** in the properties file; the runtime decrypts implicitly given the key
(VM arg). The plugin must **detect** these forms and report "secure, decrypted at runtime" rather than show a
value [14]. Both forms are present in the fixture.

---

## 2. File-watching design

### 2.1 Mechanism
Use the Eclipse resources change mechanism: register one workspace-wide `IResourceChangeListener` for
`POST_CHANGE`, then walk the delta tree with an `IResourceDeltaVisitor`, filtering to the files we care about
(the `envs/*.properties` files and the Mule config XML(s)) [1][2][3][4]. On a relevant ADDED/REMOVED/CHANGED
(with CONTENT) delta, mark the owning project's index dirty (or re-parse just the changed file).

### 2.2 Lifecycle (register in activator, remove on stop)
```java
public final class PropertyIndexPlugin extends AbstractUIPlugin {
    private static PropertyIndexPlugin instance;
    private PropertyIndexManager indexManager;          // owns per-project PropertyIndex instances
    private IResourceChangeListener resourceListener;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;
        indexManager = new PropertyIndexManager();
        resourceListener = new PropertyResourceListener(indexManager);
        // POST_CHANGE fires AFTER the workspace tree is updated; delta is available. [1][3]
        ResourcesPlugin.getWorkspace().addResourceChangeListener(
                resourceListener, IResourceChangeEvent.POST_CHANGE);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        try {
            if (resourceListener != null) {
                ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceListener);
                resourceListener = null;
            }
            if (indexManager != null) { indexManager.dispose(); indexManager = null; }
        } finally {
            instance = null;
            super.stop(context);
        }
    }
}
```
Registration API: `ResourcesPlugin.getWorkspace().addResourceChangeListener(listener, IResourceChangeEvent.POST_CHANGE)`
and `removeResourceChangeListener(listener)` [1][5]. (An OSGi `registerService` variant exists with
`IResourceChangeListener.getMaskProperties(...)`, but the explicit `addResourceChangeListener` form is the
documented, conventional one [3].)

### 2.3 Listener + delta-filtering visitor
```java
final class PropertyResourceListener implements IResourceChangeListener {
    private final PropertyIndexManager manager;
    PropertyResourceListener(PropertyIndexManager manager) { this.manager = manager; }

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        if (event.getType() != IResourceChangeEvent.POST_CHANGE) return;   // [4]
        IResourceDelta root = event.getDelta();
        if (root == null) return;
        // The delta object is valid ONLY for the duration of this call — do all reads now. [2][6]
        final Set<IProject> affected = new HashSet<>();
        try {
            root.accept(delta -> {                                          // IResourceDeltaVisitor [2]
                IResource res = delta.getResource();
                if (res.getType() != IResource.FILE) return true;           // descend into containers
                IFile file = (IFile) res;
                if (!isRelevant(file)) return true;

                int kind = delta.getKind();                                 // ADDED/REMOVED/CHANGED [8]
                if (kind == IResourceDelta.CHANGED
                        && (delta.getFlags() & IResourceDelta.CONTENT) == 0) {
                    // pure marker/timestamp change with no content change -> ignore for content,
                    // but still relevant if MOVED_FROM/MOVED_TO. [8]
                    if ((delta.getFlags()
                            & (IResourceDelta.MOVED_FROM | IResourceDelta.MOVED_TO
                               | IResourceDelta.REPLACED)) == 0) {
                        return true;
                    }
                }
                affected.add(file.getProject());
                return true;
            });
        } catch (CoreException e) {
            // log; never throw out of resourceChanged
        }
        // Re-parse OFF this thread (see threading note) — schedule a Job per affected project.
        for (IProject p : affected) manager.scheduleRebuild(p);
    }

    /** envs/*.properties under src/main/resources, or a Mule config XML under src/main/mule. */
    private boolean isRelevant(IFile file) {
        IProject project = file.getProject();
        if (project == null || !MuleProjects.isMuleProject(project)) return false; // §5
        IPath p = file.getProjectRelativePath();
        String ext = file.getFileExtension();
        if ("properties".equalsIgnoreCase(ext)
                && p.toString().startsWith("src/main/resources/envs/")) return true;
        if ("xml".equalsIgnoreCase(ext)
                && p.toString().startsWith("src/main/mule/")) return true;        // Mule config dir
        return false;
    }
}
```
`getKind()` returns `ADDED` / `REMOVED` / `CHANGED` (also `ADDED_PHANTOM`/`REMOVED_PHANTOM`); `getFlags()`
exposes `CONTENT`, `MOVED_FROM`, `MOVED_TO`, `REPLACED`, `MARKERS`, etc. `accept(IResourceDeltaVisitor)`,
`getResource()`, `getFullPath()`, `findMember(IPath)` are the navigation methods [8]. `findMember(IPath)` can
short-circuit when you only care about one known sub-path [2].

### 2.4 Threading & validity caveats (IMPORTANT)
- **The listener runs on whatever thread triggered the change, NOT necessarily the UI thread.** Per the
  Eclipse resource-deltas article: *"Workspace operations can occur in any thread, and resource change
  listeners will run in whatever thread that triggered the operation."* [2] So index data structures must be
  thread-safe (e.g. `ConcurrentHashMap` keyed by `IProject`, immutable `PropertyIndex` snapshots swapped
  atomically). Do **not** touch SWT/UI from inside `resourceChanged`.
- **During POST_CHANGE the workspace is locked against modification** and the delta is only valid for the
  duration of the call: *"This event object (and the resource delta within it) is valid only for the duration
  of the invocation of this method"* and *"further changes to resources may be disallowed"* [6][2]. So: copy
  out the affected `IFile`/`IProject` set synchronously, and do the actual (re)parse + index build in a
  background `org.eclipse.core.runtime.jobs.Job` (which also keeps `resourceChanged` fast). Reading an `IFile`
  for parse is fine inside a Job; we are not modifying resources.
- Parsing reads file contents — do it lazily/asynchronously, never block the notifying thread on large files.

---

## 3. XML offset/line strategy (`configuration-properties`, `global-property`)

We need, per declaration, the **source `IFile` + line + character offset/length** so the hover can cite the
source and Ctrl+click can jump to it. Two candidate approaches:

### 3.1 Approach A — WTP SSE structured model (RECOMMENDED)
WTP's Structured Source Editor model gives DOM nodes that also implement `IndexedRegion`, exposing
`getStartOffset()`/`getEndOffset()` directly — no manual line counting, and it is the *same* model the
Configuration XML editor uses, so offsets line up exactly with what the user sees [9][10][11].

Key insight for "works whether or not the file is open": try `getExistingModelForRead(...)` first (reuses the
editor's live model if the file is open), then fall back to `getModelForRead(IFile)` (parses the file from
disk when closed) [9][12]. **Always** `releaseFromRead()` in a `finally` — these are reference-counted [9][12].

```java
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMNode;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMAttr;
import org.w3c.dom.*;

List<XmlDecl> parseMuleXml(IFile file) throws Exception {
    IModelManager mgr = StructuredModelManager.getModelManager();
    IStructuredModel model = mgr.getExistingModelForRead(file);   // live editor model if open
    if (model == null) model = mgr.getModelForRead(file);         // else parse from disk [9][12]
    List<XmlDecl> out = new ArrayList<>();
    try {
        if (!(model instanceof IDOMModel)) return out;
        IDOMDocument doc = ((IDOMModel) model).getDocument();
        IStructuredDocument sdoc = model.getStructuredDocument(); // for offset -> line [10][12]

        // configuration-properties (any namespace) -> file="envs/${env}.properties"
        collect(doc.getElementsByTagNameNS("*", "configuration-properties"), sdoc, file, out, "file");
        // global-property name= value=
        NodeList gps = doc.getElementsByTagNameNS("*", "global-property");
        for (int i = 0; i < gps.getLength(); i++) {
            IDOMNode el = (IDOMNode) gps.item(i);
            IDOMAttr nameAttr  = (IDOMAttr) el.getAttributes().getNamedItem("name");
            IDOMAttr valueAttr = (IDOMAttr) el.getAttributes().getNamedItem("value");
            if (nameAttr == null) continue;
            String key = nameAttr.getValue();
            // attribute nodes are themselves IndexedRegion -> precise value position for go-to-def
            SourceLocation loc = locOf((IndexedRegion) valueAttr, sdoc, file);  // value text region
            out.add(new XmlDecl(key, valueAttr == null ? null : valueAttr.getValue(),
                                loc, Kind.INLINE_GLOBAL));
        }
        // secure-properties:config file=... -> Kind.SECURE source declaration
        collect(doc.getElementsByTagNameNS(
                "http://www.mulesoft.org/schema/mule/secure-properties", "config"),
                sdoc, file, out, "file");
    } finally {
        model.releaseFromRead();                                  // ALWAYS release [9][12]
    }
    return out;
}

/** A DOM node/attr is an IndexedRegion; convert its offset range to file+line+offset+length. */
SourceLocation locOf(IndexedRegion region, IStructuredDocument sdoc, IFile file) throws Exception {
    int start = region.getStartOffset();
    int end   = region.getEndOffset();                            // [10][11]
    int line  = sdoc.getLineOfOffset(start);                      // 0-based; +1 for display [7][10]
    return new SourceLocation(file, line + 1, start, end - start);
}
```
Notes:
- `doc.getElementsByTagNameNS("*", "local-name")` ignores the namespace prefix, so it matches both
  default-namespace `<configuration-properties>` and prefixed elements robustly.
- Attribute nodes (`IDOMAttr`) are also `IndexedRegion`, so we can target the **value** text precisely for
  Ctrl+click. The webresources prior art does exactly this — iterating `getAttributes()`, casting each item to
  `IndexedRegion`, and using `contains(offset)` to find the attribute under the cursor [11].
- `IStructuredModel.getIndexedRegion(int offset)` is the inverse (offset → node), useful for the hover's
  hyperlink detector to find which `${...}` the cursor is on (the hover/hyperlink side lives in a sibling
  research note; here we just expose offsets) [9][11].
- API-stability caveat: these live in `...internal.provisional...` packages (the package name literally
  contains `internal`). They are de-facto stable and used by the webresources/JSON prior art, but require
  importing internal packages and `Import-Package`/`x-friends` in the manifest. Pin to Studio 7.25.0's exact
  WTP versions (`wst.sse.core 1.2.1300`, `wst.xml.core 1.2.800`) as CLAUDE.md mandates. See Open questions.

### 3.2 Approach B — plain `IDocument`/SAX with line tracking (fallback only)
Read the file into a JFace `IDocument` (or SAX with a `Locator` for line/column) and hand-track positions.
SAX `Locator` gives line/column at the *end* of the start tag, not the attribute value range, so you would
still scan the line text to locate the `value="..."` span. This works without WTP but: (a) does not reuse the
editor's live (possibly-unsaved) model, so offsets can drift from what the user sees when the file is dirty;
(b) duplicates parsing the editor already did. **Recommend A; keep B only as a no-WTP fallback** (e.g. unit
tests outside a running workbench).

**Recommendation: Approach A (WTP SSE) for both hover-source reporting and Ctrl+click.** It is the same model
the Configuration XML editor uses, offsets are exact for open *and* closed files, and `IndexedRegion` gives
offsets for free.

---

## 4. `.properties` key-location strategy (go-to-definition target)

`.properties` is *not* XML; WTP SSE does not model it as a DOM. JDT contributes a `.properties` content type
(`org.eclipse.jdt.core.javaProperties`, aliased by `org.eclipse.core.runtime.properties`, charset ISO-8859-1)
[15], but there is no public structured AST we can rely on for arbitrary key offsets. The robust, dependency-
free approach is to scan the file's `IDocument` lines and match the requested key, honoring `.properties`
grammar: leading whitespace allowed; `=` **or** `:` **or** whitespace separates key from value; `#` or `!` at
line start is a comment; a backslash escapes the separator and continues lines; blank value is legal.

Read the document without needing an open editor via a JFace text `FileBuffer`, or just read `IFile` bytes
into a `Document`:
```java
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

/** Find the KEY occurrence (not the value) so Ctrl+click lands on the key. Returns null if absent. */
SourceLocation findKeyLocation(IFile file, String wantedKey) throws Exception {
    String text = new String(file.getContents().readAllBytes(),
                             file.getCharset() == null ? "ISO-8859-1" : file.getCharset());
    IDocument doc = new Document(text);
    int nLines = doc.getNumberOfLines();                          // [7]
    for (int line = 0; line < nLines; line++) {
        IRegion info = doc.getLineInformation(line);              // offset+len, excludes delimiter [7]
        int lineStart = info.getOffset();
        String raw = doc.get(lineStart, info.getLength());        // [7]

        int i = 0, n = raw.length();
        while (i < n && (raw.charAt(i) == ' ' || raw.charAt(i) == '\t')) i++; // leading ws
        if (i >= n) continue;                                     // blank line
        char c0 = raw.charAt(i);
        if (c0 == '#' || c0 == '!') continue;                     // comment line

        int keyStart = i;
        StringBuilder key = new StringBuilder();
        // key ends at first unescaped '=', ':' or whitespace
        while (i < n) {
            char ch = raw.charAt(i);
            if (ch == '\\' && i + 1 < n) { key.append(raw.charAt(i + 1)); i += 2; continue; }
            if (ch == '=' || ch == ':' || ch == ' ' || ch == '\t') break;
            key.append(ch); i++;
        }
        int keyEnd = i;                                           // exclusive, before separator/ws
        if (key.toString().equals(wantedKey)) {
            int offset = lineStart + keyStart;
            int length = keyEnd - keyStart;
            return new SourceLocation(file, line + 1, offset, length); // line is 1-based for display
        }
    }
    return null;
}
```
Edge cases the scanner handles (all present or implied by the fixture): `key=` with blank value
(`greeting=`), `key : value` spacing, escaped `=` inside a key (`a\=b=v`), `#`/`!` comments, and values that
merely *look* like placeholders (`db.url=...<host>...`). For multi-line (backslash-continued) keys the key is
always on the first physical line, so single-line scanning suffices for the key region; if you later need the
full *value* span, continue while the previous line ends in an odd number of backslashes.

Returning the **character `IRegion`** (offset+length) and **line** lets the hyperlink detector build an
`IHyperlink` that opens the file and selects the key, and lets markers (future) attach to the exact key.

---

## 5. Mule-project detection

Primary signal: the **`org.mule.tooling.core.muleStudioNature`** nature (§1.1), checked with
`IProject.hasNature(String)` [16]. Guard with `isOpen()` and `isAccessible()` (closed projects have no
members and `hasNature` throws if not accessible). Secondary corroboration: the
`org.mule.tooling.core.muleStudioBuilder` build command, and presence of `mule-artifact.json` at the project
root (Mule 4 app marker).

```java
public final class MuleProjects {
    public static final String MULE_NATURE_ID   = "org.mule.tooling.core.muleStudioNature";
    public static final String MULE_BUILDER_ID  = "org.mule.tooling.core.muleStudioBuilder";
    public static final String MULE_ARTIFACT    = "mule-artifact.json";

    public static boolean isMuleProject(IProject project) {
        if (project == null || !project.isAccessible() || !project.isOpen()) return false; // [16]
        try {
            if (project.hasNature(MULE_NATURE_ID)) return true;            // primary [16]
        } catch (CoreException e) { /* fall through */ }
        // Fallback for projects imported without the nature applied: artifact descriptor present.
        return project.getFile(MULE_ARTIFACT).exists();                    // IProject.getFile [16]
    }
}
```
Key the whole index per `IProject` (a `PropertyIndexManager` holding a
`ConcurrentHashMap<IProject, PropertyIndex>`), so each Mule project has an isolated index, and we ignore
non-Mule projects entirely.

---

## 6. Index API sketch (interfaces all features build on)

Designed so Feature 1 reads it for hover + Ctrl+click, and future features (find-usages, markers,
env-switcher) reuse the same model without re-parsing. Immutable snapshots make it safe to publish from the
background parse Job and read from the UI thread.

```java
package com.<org>.studioqol.index;

/** Entry point: one index per Mule IProject. Cheap to call; manager caches + keeps fresh via §2. */
public interface PropertyIndex {
    /** Returns the (possibly cached) index for the project, or null if not a Mule project. */
    static PropertyIndex forProject(IProject project) { /* PropertyIndexManager.get(project) */ }

    PropertyDefinition lookup(String key);          // exact key, null if unknown
    Collection<String> allKeys();                   // every key known across env files + inline globals
    Collection<PropertyDefinition> all();           // for find-usages / markers iteration
    Set<String> environments();                     // {"local","test","prod","example",...} discovered
    String activeEnvPropertyName();                 // configurable; default "env"
    /** Reverse map for future find-usages: every ${key} reference site in Mule XML(s). */
    Collection<PropertyReference> referencesTo(String key);
}

/** One logical property and its per-environment values. */
public final class PropertyDefinition {
    public enum Kind { ENV_FILE, INLINE_GLOBAL, SECURE }
    String key;
    Map<String, EnvValue> valuesByEnv;              // env name -> value (ENV_FILE only); empty otherwise
    SourceLocation declaration;                     // for INLINE_GLOBAL: the <global-property>; SECURE: config
    Kind kind;
    String rawGlobalValue;                          // INLINE_GLOBAL raw value (may contain ${...} chains)
}

/** A property's value in ONE environment file. */
public final class EnvValue {
    public enum State { PRESENT, EMPTY, SECURE }     // EMPTY = key present, value blank; SECURE = ![...]/secure::
    String env;                                      // "local", "prod", ...
    String rawValue;                                 // verbatim text after the separator (null if absent)
    SourceLocation location;                         // points at the KEY in that env file (§4)
    State state;
}

/** Where something lives in the workspace: file + 1-based line + char offset + length. */
public final class SourceLocation {
    IFile file;
    int line;                                        // 1-based, for display
    int offset;                                      // 0-based char offset into the document
    int length;                                      // length of the highlighted span (key or value)
}

/** A ${key} use site in a Mule config XML (future find-usages / markers). */
public final class PropertyReference {
    String key;
    SourceLocation location;                         // the ${...} occurrence in the XML
    boolean secure;                                  // referenced via ${secure::...}
}
```

How each feature uses it:
- **Feature 1 hover:** `forProject(p).lookup(key)` → render `valuesByEnv` as the per-env table; for
  `INLINE_GLOBAL` show `rawGlobalValue`; for `SECURE`/`EMPTY` show the special state text. Source line/file
  come from each `EnvValue.location` / `declaration`.
- **Feature 1 Ctrl+click:** resolve the `${key}` under the cursor (offset → key via WTP `getIndexedRegion`),
  then open `EnvValue.location` (env file key) or `declaration` (inline global) and select `[offset,length]`.
- **Find-usages (future):** `referencesTo(key)` returns every `PropertyReference` (built while parsing XML in §3).
- **Markers/quick-fixes (future):** iterate `all()` and `referencesTo`; flag references whose key is absent
  from `allKeys()` (undefined) or keys never referenced (unused); attach markers at `SourceLocation`.
- **Env switcher (future):** `environments()` + `activeEnvPropertyName()` enumerate launch-config choices.

---

## 7. Open questions
1. **Internal/provisional WTP API.** `IModelManager`, `IStructuredModel`, `IndexedRegion`, `IDOMModel` live in
   `...internal.provisional...` packages [9][10][11]. Confirm Studio 7.25.0's bundles export them to us
   (manifest `Import-Package` or `x-friends`), and pin exact versions (`wst.sse.core 1.2.1300`,
   `wst.xml.core 1.2.800`). Smoke-test that `getModelForRead(IFile)` works on a *closed* file inside Studio.
2. **Multiple Mule config XMLs.** The fixture has one (`plugin-testing.xml`), but `src/main/mule/` may hold
   several. Index should scan *all* `*.xml` under `src/main/mule/` (and possibly `src/test/mule/`). Confirm the
   canonical Mule config source directory(ies) for Studio 7.25.0 projects.
3. **`configuration-properties` file dir conventions.** We assume `envs/` under `src/main/resources`. Other
   real projects may point `file=` elsewhere; should the index follow the literal `file=` path (after
   substituting `${env}`) rather than hard-coding `src/main/resources/envs/`? Likely yes for generality — keep
   the `envs/` filter for the watcher but resolve actual sources from `file=` declarations.
4. **`getCharset()` for properties.** `.properties` default is ISO-8859-1 [15], but Studio/JDT may set UTF-8 on
   the file. Use `IFile.getCharset()` (done in §4); confirm Studio's default for env files.
5. **Chained globals.** `app.banner=${app.displayName} [${env}]` — does the index pre-resolve chains for the
   hover, or just show the raw value and let the hover note the embedded refs? Proposed: store `rawGlobalValue`
   verbatim; optionally expose a resolver later. (Out of scope for the index data model itself.)
6. **Build vs. POST_CHANGE.** Do we also need `POST_BUILD` (e.g. the Mule builder regenerating files)? POST_CHANGE
   should cover content edits; confirm no relevant files are produced only at build time.

## 8. Contradictions with the mission's stated assumptions
1. **Nature ID.** The mission asked to verify whether the nature is `org.mule.tooling.core.muleNature` "or
   another." It is **`org.mule.tooling.core.muleStudioNature`** (the `...Studio...` variant), confirmed in the
   fixture `.project` and corroborated externally [13]. Using `muleNature` would silently match nothing.
2. **JDT nature also present.** A Mule Studio project also carries `org.eclipse.jdt.core.javanature`; detection
   must key on the Mule-specific nature, not "is a Java project."
3. **`.properties` "escaped `=`" wording.** The mission says handle "escaped `=`". Confirmed valid (`\=` in a
   key) and handled, but note Mule env files in the fixture do not actually use it — it is a defensive case,
   not an observed one.
4. **WTP models properties files — it does not.** The mission groups XML and `.properties` under SSE; only the
   XML is a WTP DOM model. `.properties` needs the plain-`IDocument` line scanner of §4 (JDT's properties
   content type is just a content-type/charset alias, not a structured model we can query for key offsets) [15].

---

## Citations (URL per claim)
- [1] IResourceChangeListener (Eclipse Platform API) — `resourceChanged`, registration, event-validity note:
  https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/resources/IResourceChangeListener.html
- [2] "How You've Changed!" (Eclipse resource-deltas article) — visitor pattern, kind/flags filtering,
  threading ("listeners run in whatever thread that triggered the operation"), delta expiry:
  https://www.eclipse.org/articles/Article-Resource-deltas/resource-deltas.html
- [3] "Tracking resource changes" (Platform Plug-in Developer Guide) — registration + POST_CHANGE usage:
  https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/resAdv_events.htm
- [4] resourceChanged POST_CHANGE filtering example (programcreek):
  https://www.programcreek.com/java-api-examples/org.eclipse.core.resources.IResourceChangeListener
- [5] IWorkspace.addResourceChangeListener / removeResourceChangeListener (registration/lifecycle), referenced via [1][3].
- [6] Event/delta validity ("valid only for the duration of the invocation"; "further changes ... may be
  disallowed") — IResourceChangeListener JavaDoc: see [1].
- [7] IDocument (JFace text) — getNumberOfLines, getLineInformation, getLineOfOffset, getLineOffset,
  getLineLength, get(int,int):
  https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/jface/text/IDocument.html
- [8] IResourceDelta — getKind (ADDED/REMOVED/CHANGED/…), getFlags (CONTENT/MOVED_FROM/MOVED_TO/REPLACED/MARKERS),
  accept/getResource/getFullPath/findMember:
  https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/resources/IResourceDelta.html
- [9] IDOMModel usage (StructuredModelManager.getModelManager, getExistingModelForRead, getModelForRead(IFile),
  cast to IDOMModel, getDocument, releaseFromRead in finally) — examples:
  https://www.javatips.net/api/org.eclipse.wst.xml.core.internal.provisional.document.idommodel
- [10] IDOMModel.getStructuredDocument (offset↔line via IStructuredDocument) — examples:
  https://www.tabnine.com/code/java/methods/org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel/getStructuredDocument
- [11] IndexedRegion (cast Node/IDOMAttr to IndexedRegion, getStartOffset/getEndOffset, getAttributes+contains,
  IStructuredModel.getIndexedRegion(offset)) — examples:
  https://www.javatips.net/api/org.eclipse.wst.sse.core.internal.provisional.indexedregion
- [12] IDOMModel#releaseFromRead (reference-counted release pattern) — examples:
  https://www.programcreek.com/java-api-examples/?class=org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel&method=releaseFromRead
- [13] Mule nature `org.mule.tooling.core.muleStudioNature` + `muleStudioBuilder` corroboration (MuleSoft help
  / forums on applying Mule nature on import):
  https://help.mulesoft.com/s/question/0D52T00004mXSlgSAG/applying-mule-nature-to-a-maven-project-on-import
- [14] Mule Secure Configuration Properties — `secure::` prefix and `![...]` encrypted form, runtime decryption:
  https://docs.mulesoft.com/mule-runtime/latest/secure-configuration-properties
- [15] Eclipse "Contributing content types" — `org.eclipse.jdt.core.javaProperties` aliased by
  `org.eclipse.core.runtime.properties`, base `text`, charset ISO-8859-1:
  https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/runtime_content_contributing.htm
- [16] IProject — hasNature(String), getNature, isOpen, getFile, getFolder:
  https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/resources/IProject.html
- [17] Eclipse WTP WebResources (prior art: SSE hover/hyperlink/indexing over IDOMModel + IndexedRegion):
  https://github.com/angelozerr/eclipse-wtp-webresources
