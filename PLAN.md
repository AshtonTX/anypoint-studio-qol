# PLAN.md — Anypoint Studio QoL Plugin · Feature 1: Property Value Hover

> Status: **DRAFT for your approval (Phase 1 gate).** No implementation code will be written
> until you approve. Deep detail + citations live in `research/*.md`; this plan references them
> rather than repeating them.

## 0. What this builds
An **extensible, multi-feature** QoL plugin for **Anypoint Studio 7.25.0**. Only **Feature 1**
is built now: hovering a `${propertyName}` in the **Configuration XML** tab shows a popup with the
value **in every environment** (local/test/prod/example) side by side, with source file + line;
**Ctrl+click** jumps to the definition. Everything else is left as clean seams.

---

## 1. Verified environment (Phase 0 — ground truth)
| | |
|---|---|
| Studio | **7.25.0** (`7.25.0.202605141228`) at `/Applications/AnypointStudio.app` |
| Eclipse base | **4.31** (2024-03); `osgi.requiredJavaVersion=17` |
| Target bundles | `wst.sse.ui 1.7.900`, `wst.sse.core 1.2.1300`, `wst.xml.ui 1.2.701`, `wst.xml.core 1.2.800`, `jface.text 3.25.0`, `text 3.14.0`, `ui.workbench.texteditor 3.17.300`, `ui.editors 3.17.200`, `core.resources 3.20.100`, `core.runtime 3.31.0` |
| Build JDK | **Temurin 17** (`/Library/Java/JavaVirtualMachines/temurin-17.jdk`); host arm64 |
| Active-env property | **`env`** (`file="envs/${env}.properties"`; VM arg `-M-Denv=<env>`), configurable, default `env` |
| Env files | self-contained (same keys per env, **no base/precedence layer**) |
| Mule nature id | **`org.mule.tooling.core.muleStudioNature`** (+ builder `…muleStudioBuilder`) |
| Fixture | `plugin-testing/` (config `src/main/mule/plugin-testing.xml`, matrix `README.md`, envs in `src/main/resources/envs/`) |

Full corroboration + citations: `research/studio-target.md`, `research/project-model.md`.

---

## 2. Architecture — single bundle, feature modules over shared infra
**Decision: ONE OSGi bundle `com.studioqol`** with internal feature packages, behind one p2
feature + one update-site project. (`com.studioqol` also sorts before `org.eclipse.wst.xml.ui`,
which helps hover ordering — see §4.) Rationale and the "when to split into per-feature bundles"
seam: `research/architecture.md §1`. *(Swap the `com.studioqol` base id now if you prefer another.)*

```
mule-plugin-workspace/                      ← git root
├─ com.studioqol/                           ← THE plug-in (PDE; packaging=eclipse-plugin)
│  ├─ META-INF/MANIFEST.MF                   ← SymbolicName com.studioqol;singleton:=true; BREE JavaSE-17
│  ├─ plugin.xml                             ← all extension contributions
│  ├─ schema/feature.exsd                    ← internal feature extension point (orchestration)
│  └─ src/com/studioqol/
│     ├─ StudioQolPlugin.java                ← Activator: starts index, resource listener, features
│     ├─ core/                               ← SHARED INFRA (reused by every future feature)
│     │  ├─ index/   PropertyIndex, PropertyIndexManager, PropertyDefinition, EnvValue, SourceLocation
│     │  ├─ resource/ MuleResourceListener   ← IResourceChangeListener → keeps index fresh
│     │  ├─ env/      ActiveEnvService        ← configurable active-env-property pref accessor
│     │  └─ feature/  IStudioQolFeature, FeatureRegistry
│     ├─ feature/hover/                      ← FEATURE 1
│     │  ├─ PropertyHoverProcessor.java       ← ITextHover + ITextHoverExtension2
│     │  ├─ PlaceholderScanner.java           ← ${...} detection (incl. secure::, nested)
│     │  ├─ PropertyHoverHtml.java            ← per-env table HTML
│     │  ├─ PropertyHover{,Presenter}ControlCreator.java
│     │  └─ PropertyHyperlinkDetector.java    ← Ctrl+click go-to-definition
│     └─ ui/preferences/ StudioQolPreferencePage.java
├─ com.studioqol.feature/                    ← p2 feature (lists the bundle)
├─ com.studioqol.repository/                 ← p2 update site (category.xml [+ pom if Tycho])
├─ studio-7.25.target                        ← Target Platform pinned to the install
├─ plugin-testing/                           ← existing Mule test fixture (kept, versioned)
├─ research/                                  ← Phase 0 outputs
└─ PLAN.md, (CLAUDE.md)
```

- **Feature registration:** custom internal extension point `com.studioqol.feature` +
  `IStudioQolFeature` + `FeatureRegistry` (lazy `createExecutableExtension`). Editor-facing parts
  (hover, hyperlink) register through the *platform* extension points in §4; the internal point is
  for lifecycle/orchestration only. (`research/architecture.md §2`.)
- **Activator (`AbstractUIPlugin`)** owns shared-infra lifetime; features get infra via a
  `StudioQolContext` (and a static `getDefault()` accessor for platform-instantiated hover/hyperlink
  classes). Lazy-start is fine (opening a Mule XML triggers it). (`research/architecture.md §5`.)
- **Preferences:** bundle-scoped store; one `org.eclipse.ui.preferencePages` page; default env-prop
  name `env`; reads go through `ActiveEnvService`. (`research/architecture.md §4`.)

---

## 3. Shared infrastructure — the per-project property index
Reused by Feature 1 now and by future features (find-usages, markers, env switcher). Full design +
snippets: `research/project-model.md`.

- **Project keying & detection:** per `IProject`, via `hasNature("org.mule.tooling.core.muleStudioNature")`,
  fallback `mule-artifact.json`.
- **Freshness:** one workspace `IResourceChangeListener` (`POST_CHANGE`) + delta visitor filtered to
  `src/main/resources/envs/*.properties` and `src/main/mule/*.xml`; rebuild the affected project's
  index in a background `Job` (listener runs off-UI-thread, delta is call-scoped → copy out, rebuild
  async, publish immutable snapshots).
- **XML offsets (source file:line for hover + Ctrl+click):** WTP SSE
  `StructuredModelManager` → `getExistingModelForRead` (live editor model) else `getModelForRead(IFile)`
  (closed file) → `IDOMModel`; read `IndexedRegion.getStartOffset()` and
  `IStructuredDocument.getLineOfOffset()`; **always `releaseFromRead()`**. Attribute nodes are
  `IndexedRegion`, so we get the `value="…"` span precisely.
- **`.properties` key location (go-to-def target):** scan the `IDocument` lines honoring
  `java.util.Properties` grammar (`=`/`:`/whitespace separators, `#`/`!` comments, escapes, blank
  values); return the **key** region.
- **Public API (stable seam):**
  `PropertyIndex.forProject(IProject)` → `lookup(key)`, `allKeys()`, `all()`, `environments()`,
  `activeEnvPropertyName()`, `referencesTo(key)`;
  `PropertyDefinition{key, Map<env,EnvValue> valuesByEnv, SourceLocation declaration, Kind(ENV_FILE|INLINE_GLOBAL|SECURE), rawGlobalValue}`;
  `EnvValue{env, rawValue, SourceLocation location, State(PRESENT|EMPTY|SECURE)}`;
  `SourceLocation{IFile, line(1-based), offset, length}`.
- **API-stability caveat:** the WTP model classes are `…internal.provisional…` packages — pin to
  Studio's exact WTP versions and expect discouraged-access warnings (used by the angelozerr/JSON
  prior art; de-facto stable).

---

## 4. Feature 1 design

### 4a. Hover registration & the BestMatchHover collision (de-risked)
Register (verified against Studio's own `wst.xml.ui` + angelozerr): `research/wtp-hover.md`.
```xml
<extension point="org.eclipse.wst.sse.ui.editorConfiguration">
  <documentationTextHover class="com.studioqol.feature.hover.PropertyHoverProcessor"
                          target="org.eclipse.wst.xml.XML_DEFAULT"/>
</extension>
```
- WTP aggregates hovers via `BestMatchHover`, **ordered by plugin id** (unsortable). Winner = first
  hover whose `getHoverRegion()` is non-null.
- **Mitigation (primary):** our `getHoverRegion` returns non-null **only on a `${...}` token**, where
  the default `XMLTagInfoHoverProcessor` returns null → we win regardless of order.
- **Mitigation (secondary):** bundle id `com.studioqol` sorts before `org.eclipse.wst.xml.ui`.
- **Validation:** Milestone A proves firing empirically (the entire point of the smoke test).

### 4b. Hover implementation
`PropertyHoverProcessor implements ITextHover, ITextHoverExtension2`:
- `getHoverRegion` → `PlaceholderScanner.find(doc, offset)` (line-scoped brace-matching stack; returns
  the **innermost** `${…}`; handles `${secure::key}` and nested `${a${b}}`).
- `getHoverInfo2` → look up the key in `PropertyIndex` and render HTML; **returns null when there's
  nothing to show** (so we never win a region then render blank).
- Popup via `BrowserInformationControl` (full HTML table + clickable source links) with
  `DefaultInformationControl` fallback; focusable/sticky presenter creator (F2). `BrowserInformationControl`
  is an internal jface package (JDT/angelozerr use it) — note the dependency.

### 4c. Resolution strategy — honest, never fabricated (`research/mule-resolution.md §8`)
Per-env table is built from `envs/*.properties` matching the `envs/${env}.properties` template
(**local/test/prod/example**, excluding the secure file). Active-env row highlighted *when known*
(best-effort: launch-config `-Denv=`/preference; else all rows shown unhighlighted).

| Case | `${…}` | State | Hover shows |
|---|---|---|---|
| 1 `greeting` / 2 `db.url` / 3 `feature.newCheckout` | env files | **Resolvable per-env** | value table local/test/prod/example + file:line |
| 4 `app.displayName` | inline `global-property` | **Resolvable single** | one value, not env-specific + file:line |
| 5 `app.banner` | chained global | **Raw only** | raw `${app.displayName} [${env}]` + note refs (never a fabricated final string) |
| 6 `env` | system/deploy prop | **Runtime-only** | "set via `-Denv=…`"; may list candidate env files |
| 7 `does.not.exist` | none | **Unresolved** | "not defined in any env file or global-property" |
| 8 `secure::db.password` | `secure.properties` `![…]` | **Secure** | "secure / decrypted at runtime" — **never decrypt/guess** |

**Hard rules:** never decrypt `![…]` / `secure::`; never resolve `${env}`; never invent a value for
unresolved keys; `(empty)` for blank, render `example` templates verbatim. Secure detection = any of:
`secure::` prefix, `![…]` value, or key owned by a `secure-properties:config` file. (`secure.key`
itself is a normal, resolvable property — distinct from the encrypted values.)

> General Mule precedence (deployment > system > environment > application > global; multiple
> config-properties = **first-wins**) is encoded in the shared resolver for real-world projects, but
> our self-contained fixture needs no arbitration.

### 4d. Ctrl+click go-to-definition (Milestone C)
```xml
<extension point="org.eclipse.ui.workbench.texteditor.hyperlinkDetectors">
  <hyperlinkDetector class="com.studioqol.feature.hover.PropertyHyperlinkDetector"
     id="com.studioqol.hyperlink.property" name="%hyperlink.name"
     targetId="org.eclipse.wst.xml.core.xmlsource"/>   <!-- verify in Milestone C -->
</extension>
```
`detectHyperlinks` reuses `PlaceholderScanner`; `IHyperlink.open()` opens the env file (or inline
global) and selects the key line via the index `SourceLocation`. Secure/runtime/unresolved → no
hyperlink (or an informative no-op).

---

## 5. Build & packaging
- **Target Platform** pinned to the install via `studio-7.25.target` (PDE `Profile`/`Directory`
  location at `/Applications/AnypointStudio.app/Contents/Eclipse`) — guarantees "resolves in IDE ⇒
  installs in Studio". (`research/studio-target.md §2`.)
- **Inner dev loop:** run as a **self-hosted Eclipse Application** against the pinned target (this
  launches a nested instance from Studio's own bundles — effectively Studio — with **no install**),
  for fast iteration and the Milestone-A smoke test.
- **Installable deliverable (into your real Studio):** see Decision A below. There is **no `dropins/`**
  by default; the sanctioned path is a **p2 update site → Help → Install New Software** (or headless
  `p2.director`, profile `DefaultProfile`). Caveat: any install writes inside the signed `.app` and
  breaks its notarization seal — it still launches, but a Studio auto-update may revert the plugin
  (re-install after updates). (`research/studio-target.md §3`.)
- **Required `Require-Bundle`:** `org.eclipse.ui`, `core.runtime`, `core.resources`, `jface.text`,
  `text`, `ui.workbench.texteditor`, `ui.editors`, `wst.sse.ui`, `wst.sse.core` (+ `wst.xml.ui`/
  `wst.xml.core` for DOM offsets). `Bundle-RequiredExecutionEnvironment: JavaSE-17`.

---

## 6. Phased implementation order (de-risk integration FIRST)
Each milestone ends with a **build → install → test** checkpoint where I give you copy-pasteable
steps and pause for your results (Phases 3/4 interleaved).

- **Milestone A — Scaffold + smoke-test hover.** Single bundle + `.target` + `documentationTextHover`
  registration; `PropertyHoverProcessor` returns a **hardcoded** popup over any `${…}`. *Exit:* in
  your Studio's Configuration XML tab, hovering `${greeting}` (in `message="${greeting}"`) and a text
  `${…}` shows the popup — proving the hover fires with no BestMatchHover collision. No resolver yet.
- **Milestone B — Property index + per-env resolver.** Shared infra (index, resource listener, Mule
  detection, WTP offsets, `.properties` scan) + resolver; hover renders the real per-env table with
  file:line and honest secure/runtime/unresolved states; active-env preference + row highlight. *Exit:*
  CASES 1–8 match `plugin-testing/README.md`.
- **Milestone C — Ctrl+click go-to-definition.** Hyperlink detector → jump to the key's line. *Exit:*
  Ctrl+click `${db.url}` → env file at the key line; `${app.displayName}` → the `global-property`.
- **Milestone D — Polish.** Rich table formatting, active-env highlight, `(empty)`/template rendering,
  nested/secure/missing-file edge cases, preference UI; finalize the p2 update site + install docs.
  *Exit:* full README matrix passes via a clean "Install New Software".

---

## 7. Future-feature seams (built later, NOT now)
`research/architecture.md §3` maps each backlog item to platform extension points, all confirmed
present in 4.31: env switcher / quick-open / find-usages → `commands`+`handlers`+`menus`+`bindings`;
undefined/unused markers → `core.resources.markers`+`builders`+`ui.ide.markerResolution`; per-project
env override → `propertyPages`. The shared index already exposes `referencesTo(key)`, `environments()`,
`all()` for these. Note: a future **DataWeave `p('…')` hover** likely uses the **generic/LSP editor**
path, *not* `editorConfiguration` (DataWeave isn't a WTP/SSE editor).

---

## 8. Decisions I need from you (and my recommendations)
- **A — Installable build toolchain** (asked below): Tycho p2 update site (reproducible, the research
  recommendation) vs. PDE "Export → Install into host" (fastest, no Maven). Inner-loop is self-hosted
  either way.
- **B — git init** (asked below): initialize a repo at the workspace root for incremental commits.
- **C — Bundle base id:** proposing **`com.studioqol`** (clean; also wins hover ordering). Tell me if
  you want a different base (e.g. `com.ashton.studioqol`).

## 9. Risks / open items (tracked, low-to-medium)
- WTP `internal.provisional` APIs — pin versions; expect discouraged-access (mitigated, proven by prior art).
- Hyperlink `targetId` (`org.eclipse.wst.xml.core.xmlsource`) — confirm in Milestone C.
- `.app` seal invalidation on install — re-install after Studio updates.
- Active-env detection is best-effort; if unknown, show all env rows (honest default).
