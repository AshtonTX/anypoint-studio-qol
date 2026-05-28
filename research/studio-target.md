# Studio Target & Build/Install Research — Anypoint Studio 7.25.0

Research agent: **agent-studio-target**. Scope: how to BUILD against and INSTALL into
this specific Anypoint Studio 7.25.0 install for a hover + Ctrl+click hyperlink plugin
(Feature 1). Read-only inspection of the local install + web corroboration. No files
were modified except this report.

---

## 1. Confirmed facts (incl. actual install layout observed)

### Product / platform identity (corroborated)

| Fact | Source of truth | Evidence |
|------|-----------------|----------|
| Anypoint Studio **7.25.0**, build `7.25.0.202605141228`, released **May 14, 2026** | local + MuleSoft release notes | p2 binary IDs `org.mule.tooling.studio_root_7.25.0.202605141228`; release notes confirm 7.25.0 / May 14 2026 |
| Product id `org.mule.tooling.branding.mulestudio`; app `org.mule.tooling.ide.app` | `configuration/config.ini` | `eclipse.product=org.mule.tooling.branding.mulestudio`, `eclipse.application=org.mule.tooling.ide.app` |
| Eclipse base **4.31 = 2024-03 SimRel** | local + MuleSoft RN + Eclipse | features `org.eclipse.platform_4.31.0.v20240229-1022`, `org.eclipse.rcp_4.31.0.v20240229-0520`, `org.eclipse.e4.rcp_4.31.0.v20240227-1638`; p2 binary `org.eclipse.platform_root_4.31.0.v20240229-1022`, `org.eclipse.rcp_root_4.31.0.v20240229-0520`. MuleSoft RN: "Eclipse 4.31". Eclipse 4.31 New&Noteworthy: "part of the Eclipse 2024-03 simultaneous release" |
| Java 17 required | local + RN | `config.ini`/`AnypointStudio.ini`: `osgi.requiredJavaVersion=17`. RN: "This version of Studio requires Java 17" (bundled Temurin 17.0.13+11) |
| macOS, Apple Silicon (arm64) | local | bundles `*.cocoa.macosx.aarch64_*`; `studio.product.executable.cocoa.macosx.aarch64`; launcher `org.eclipse.equinox.launcher.cocoa.macosx.aarch64_1.2.900`; `codesign` reports `Mach-O thin (arm64)` |

**Measured platform/editor bundle versions (all match the brief, all from the 2024-03
train — v-qualifiers `v202402xx`):**

```
org.eclipse.platform_4.31.100.v20240229-0520        (dir bundle)
org.eclipse.osgi_3.19.0.v20240213-1246              (framework, per config.ini)
org.eclipse.equinox.common_3.19.0.v20240214-0846
org.eclipse.core.runtime_3.31.0.v20240215-1631
org.eclipse.core.resources_3.20.100.v20240209-1706
org.eclipse.jface_3.33.0.v20240214-1640
org.eclipse.jface.text_3.25.0.v20240207-1054
org.eclipse.text_3.14.0.v20240207-1054
org.eclipse.ui_3.205.100.v20240131-1023
org.eclipse.ui.workbench_3.131.100.v20240221-2107
org.eclipse.ui.workbench.texteditor_3.17.300.v20240207-1054
org.eclipse.ui.editors_3.17.200.v20240207-1054
org.eclipse.wst.sse.core_1.2.1300.v202402230237
org.eclipse.wst.sse.ui_1.7.900.v202402030233
org.eclipse.wst.xml.core_1.2.800.v202308210520
org.eclipse.wst.xml.ui_1.2.701.v202308160453
```

Launcher / framework: `org.eclipse.equinox.launcher_1.6.700.v20240213-1244`,
`org.eclipse.equinox.simpleconfigurator_1.5.200.v20240209-1053`.

### Hover + hyperlink API location (verified by unzipping manifests/classes)

- `org.eclipse.jface.text` **exports** `org.eclipse.jface.text.hyperlink` (contains
  `IHyperlink`, `IHyperlinkDetector`, `IHyperlinkDetectorExtension`,
  `AbstractHyperlinkDetector`) and `org.eclipse.jface.text` (contains `ITextHover`,
  `ITextHoverExtension`, `IInformationControlCreator`). Confirmed `.class` files present.
- `org.eclipse.ui.workbench.texteditor` **defines the extension points**
  `org.eclipse.ui.workbench.texteditor.hyperlinkDetectors` and `…hyperlinkDetectorTargets`
  (verified in its `plugin.xml`: `<extension-point id="hyperlinkDetectors" schema="schema/hyperlinkDetectors.exsd"/>` and `id="hyperlinkDetectorTargets"`). It also exports `org.eclipse.ui.texteditor` (registry classes `HyperlinkDetectorRegistry`, `HyperlinkDetectorDescriptor`).
- `org.eclipse.ui.editors` exports `org.eclipse.ui.editors.text`.
- The XML / SSE editor's hyperlink **target id** is `org.eclipse.core.runtime.xml` (and
  `org.eclipse.wst.xml.core.xmlsource`) — verified in `org.eclipse.wst.xml.ui` `plugin.xml`
  (`target="org.eclipse.core.runtime.xml, org.eclipse.wst.xml.core.xmlsource"`). This is
  the `hyperlinkDetectorTarget` to bind a detector to so it fires in the Configuration XML
  (Mule config) editor, which is an SSE/WST structured XML editor.
- `org.eclipse.wst.sse.ui` re-exports `org.eclipse.ui.editors` and requires
  `org.eclipse.ui.genericeditor [1.3.0,2.0.0)` (present), `org.eclipse.jface.text`, etc.

### Actual install layout observed (`ls /Applications/AnypointStudio.app/Contents/Eclipse`)

```
AnypointStudio.ini      <- launcher config (-startup, -vm, -vmargs)
artifacts.xml           <- p2 artifact repo for the install (652 KB)
configuration/          <- runtime config (see below)
features/               <- 49 entries (org.eclipse.platform_4.31.0…, org.mule.*, …)
license/
META-INF/
p2/                     <- p2 profile/agent data for the install
plugins/                <- 531 entries / bundles.info lists 533 bundles
splash_inv.bmp
```

- **NO `dropins/` folder exists** by default. It is *not* present; it would have to be
  created. (The dropins mechanism is still supported — see below.)
- `configuration/` contains: `config.ini`, `org.eclipse.equinox.simpleconfigurator/bundles.info`
  (533 lines — the authoritative bundle list, start levels), `org.eclipse.osgi/` (bundle
  cache), `org.eclipse.core.runtime/`, `org.eclipse.update/`, `org.eclipse.equinox.app/`,
  `org.eclipse.equinox.launcher/`, `org.eclipse.e4.ui.css.swt.theme/`, `.settings/`.
- `p2/` contains `org.eclipse.equinox.p2.engine/profileRegistry/DefaultProfile.profile`
  (the install's p2 profile is named **`DefaultProfile`**, confirmed by
  `eclipse.p2.profile=DefaultProfile` in `config.ini`) plus
  `org.eclipse.equinox.p2.core/cache/` and metadata/artifact repo prefs. `eclipse.p2.data.area=@config.dir/../p2`.
- **This is a p2-provisioned product** (it has `artifacts.xml`, a p2 profile registry, and
  simpleconfigurator) — not a loose unzipped SDK. That has implications for delivery
  (below).
- **The full p2 stack is installed**, including the **UI** and **director**:
  `org.eclipse.equinox.p2.ui.sdk_1.3.300`, `org.eclipse.equinox.p2.ui_2.8.300`,
  `org.eclipse.equinox.p2.operations`, `org.eclipse.equinox.p2.director_2.6.300`,
  `org.eclipse.equinox.p2.director.app_1.3.300`, **`org.eclipse.equinox.p2.reconciler.dropins_1.5.300`**,
  `org.eclipse.equinox.p2.directorywatcher_1.4.300`, `org.eclipse.equinox.p2.metadata/artifact.repository`,
  `org.eclipse.equinox.p2.touchpoint.eclipse/natives`, `org.eclipse.equinox.p2.updatesite`.
  → **"Help → Install New Software…" is available**, headless `p2.director` is available,
  and the **dropins reconciler is present** (so a created `dropins/` would be honored).

### macOS bundle / signing / permissions (verified)

- `codesign -dv` on `/Applications/AnypointStudio.app`:
  `Authority=Developer ID Application: salesforce.com (62J96EUJ9N)`,
  `flags=0x10000(runtime)` (hardened runtime), `Notarization Ticket=stapled`,
  `Sealed Resources version=2 rules=13 files=4071`, `Identifier=studio.product`.
- The app carries `com.apple.quarantine` and `com.apple.provenance` xattrs (downloaded via
  Chrome). The `plugins/` dir also inherits a `com.apple.quarantine` xattr.
- **Permissions: the install is fully WRITABLE by the current user** — owner `ashton:admin`,
  mode `drwxr-xr-x`, and `test -w` returns writable for both `Contents/Eclipse` and
  `Contents/Eclipse/plugins`. So there is no *filesystem permission* blocker to dropping
  files in. (This is because it lives in `/Applications` owned by the user, not SIP-protected
  system paths; SIP does **not** protect arbitrary `/Applications/*.app`.)
- Caveat that *does* bite: writing into `Contents/` (adding `dropins/`, adding to `plugins/`,
  rewriting `bundles.info`) **invalidates the app's code signature / sealed resources**.
  Because the app is already notarized and was Gatekeeper-assessed on first launch, normal
  re-launch will still run (macOS does not re-verify the full seal on every launch once
  approved), but: re-stapling/`spctl` checks will fail, an OS/Studio update can overwrite or
  refuse, and on some hardened-runtime configs a broken seal can cause launch issues. Treat
  in-bundle modification as "works but unsupported / fragile."

---

## 2. Target Platform pinned to THIS install

### Why pin to the install (prevents "compiles but won't install")

If you compile against generic `download.eclipse.org/releases/2024-03` p2 metadata, your
bundle may resolve against *versions or bundles that Studio doesn't actually ship* (Studio
is a curated subset + Mule bundles + its own qualifiers, e.g. `wst.xml.core_1.2.800`
is a 2023-08 build, not the 2024-03 one). The plugin then compiles, but at install time p2
can't satisfy `Require-Bundle`/`Import-Package` version ranges against the *real* running
profile → "cannot complete the install because one or more required items could not be
found" / unresolved bundle at runtime. Pinning the target platform to
`/Applications/AnypointStudio.app/Contents/Eclipse` makes the compile-time classpath ==
the runtime profile, so "resolves in IDE" ⇒ "installs into Studio."

### 2a. PDE IDE target definition (`.target`) — Directory + Profile/Installation form

A `.target` file is XML (`<?pde?>` + `<target>` + `<locations>`). PDE supports several
location `type`s; for pinning to a local install the relevant ones are
**`Directory`** (treat a folder of bundles as the target) and **`Profile`** (a.k.a. the
"Installation" location in the UI — read an installed product, including its features and
config). Recommended: a **Profile/Installation** location pointing at the Eclipse home so
PDE reads the install exactly (features + plugins + profile), optionally plus a `Directory`
location at `plugins/`.

`studio-7.25.target` (PDE-style):

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<?pde version="3.8"?>
<target name="Anypoint Studio 7.25.0 (Eclipse 4.31)" sequenceNumber="1">
  <locations>
    <!-- Read the installed product (features + plugins + p2 profile) directly. -->
    <location path="/Applications/AnypointStudio.app/Contents/Eclipse" type="Profile"/>

    <!-- Optional/alternative: treat the plugins dir as a plain bundle pool. -->
    <location path="/Applications/AnypointStudio.app/Contents/Eclipse/plugins" type="Directory"/>
  </locations>
  <targetJRE path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-17"/>
  <environment>
    <os>macosx</os>
    <ws>cocoa</ws>
    <arch>aarch64</arch>
    <nl>en_US</nl>
  </environment>
</target>
```

Notes:
- Use **one** of the two locations to avoid duplicate-bundle noise; `Profile` is the most
  faithful ("Installation" in the Add Location wizard → "point to a folder containing an
  Eclipse installation"). `Directory` is simpler and also works for a hover/hyperlink plugin
  whose deps are all plain bundles.
- For comparison, the *generic* (non-pinned) form uses an `InstallableUnit` location:
  ```xml
  <location includeAllPlatforms="false" includeMode="planner" includeSource="true" type="InstallableUnit">
    <repository location="https://download.eclipse.org/releases/2024-03"/>
    <unit id="org.eclipse.platform.feature.group" version="0.0.0"/>
  </location>
  ```
  This is what you do NOT want as the sole source if the goal is install-into-Studio.
- Set the target via Preferences → Plug-in Development → Target Platform → (the new target)
  → Set as Active, then run/debug as an **Eclipse Application** launched against this Studio
  (or just self-host) to smoke-test the hover.

### 2b. Tycho (headless Maven) — pin to the local install, no network

Two viable approaches; both keep the build offline (no `download.eclipse.org` needed):

**Approach A — directory/profile p2 repo over the local install (recommended for headless).**
A p2-provisioned product *is itself a p2 repository* (it has `artifacts.xml` + p2 metadata).
Point Tycho at it as a `p2`/`installation` repository in the `.target`, then reference the
`.target` from `target-platform-configuration`.

`studio-7.25-tycho.target` (Tycho reads InstallableUnit locations; use the install as the repo):

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<?pde version="3.8"?>
<target name="studio-7.25-tycho" sequenceNumber="1">
  <locations>
    <location includeAllPlatforms="false" includeMode="slicer" includeSource="false" type="InstallableUnit">
      <!-- The Studio install acts as a local p2 repo (has artifacts.xml + metadata). -->
      <repository location="file:/Applications/AnypointStudio.app/Contents/Eclipse/"/>
      <unit id="org.eclipse.jface.text" version="0.0.0"/>
      <unit id="org.eclipse.text" version="0.0.0"/>
      <unit id="org.eclipse.ui.workbench.texteditor" version="0.0.0"/>
      <unit id="org.eclipse.ui.editors" version="0.0.0"/>
      <unit id="org.eclipse.core.runtime" version="0.0.0"/>
      <unit id="org.eclipse.core.resources" version="0.0.0"/>
      <unit id="org.eclipse.wst.sse.ui" version="0.0.0"/>
      <unit id="org.eclipse.wst.sse.core" version="0.0.0"/>
      <unit id="org.eclipse.wst.xml.ui" version="0.0.0"/>
      <unit id="org.eclipse.wst.xml.core" version="0.0.0"/>
      <unit id="org.eclipse.ui.genericeditor" version="0.0.0"/>
    </location>
  </locations>
  <environment><os>macosx</os><ws>cocoa</ws><arch>aarch64</arch></environment>
</target>
```
Use `includeMode="slicer"` (no environment closure surprises) when pulling individual units
from the install. If the install's `artifacts.xml`/metadata is awkward to consume directly,
**Approach B** is the fallback.

**Approach B — `<location type="Directory">` at `plugins/` (works without any p2 metadata).**
Tycho 4.x supports Directory locations; the simplest pin for a few plain-bundle deps:
```xml
<location path="/Applications/AnypointStudio.app/Contents/Eclipse/plugins" type="Directory"/>
```

`pom.xml` wiring (`packaging=eclipse-plugin`), with the `.target` referenced as a file:

```xml
<properties>
  <tycho-version>4.0.13</tycho-version>
</properties>
<build>
  <plugins>
    <plugin>
      <groupId>org.eclipse.tycho</groupId>
      <artifactId>tycho-maven-plugin</artifactId>
      <version>${tycho-version}</version>
      <extensions>true</extensions>
    </plugin>
    <plugin>
      <groupId>org.eclipse.tycho</groupId>
      <artifactId>target-platform-configuration</artifactId>
      <version>${tycho-version}</version>
      <configuration>
        <target>
          <file>${project.basedir}/../studio-7.25-tycho.target</file>
        </target>
        <environments>
          <environment><os>macosx</os><ws>cocoa</ws><arch>aarch64</arch></environment>
        </environments>
        <!-- Only needed if you add plain Maven jar deps; not required for pure Eclipse deps. -->
        <pomDependencies>consider</pomDependencies>
        <executionEnvironment>JavaSE-17</executionEnvironment>
      </configuration>
    </plugin>
  </plugins>
</build>
```
`<target>` accepts `<artifact>` (a target GAV in the reactor/remote), `<file>` (local file),
or `<uri>`. `<pomDependencies>` values: `ignore` | `consider` (adds OSGi-bundle Maven deps,
ignores non-bundles) | `wrapAsBundle`. For a hover/hyperlink plugin all deps are Eclipse
bundles, so `pomDependencies` can be omitted; keep it only if you later add a plain jar.

Because the target points at the local install, **Tycho needs no network access to a p2 repo.**

---

## 3. Delivery options compared + RECOMMENDATION

Context that drives the decision (all verified on this box): single solo developer; must
install into **this** Studio; the install is **p2-provisioned**, ships the **full p2 UI +
director + dropins reconciler**, has **no `dropins/` folder yet**, is **user-writable** but
is a **signed+notarized macOS .app with sealed resources**.

| Option | Reproducibility | Friction | Exact install action | macOS/signing notes |
|---|---|---|---|---|
| **(a) p2 update site via Tycho** | **High** — versioned, hash-checked IUs; same site installs identically anywhere; p2 records install in the profile so it's cleanly uninstallable/updatable | Medium one-time setup (POMs, target, feature project); trivial repeat builds | Build a `category.xml` + `eclipse-repository` → `mvn clean verify` produces `…/target/repository`. Install via **Help → Install New Software → Add… → Local… →** select that `repository` folder (uncheck "group items by category" if needed) → select feature → Next/Finish → restart. **OR** headless: `eclipsec -application org.eclipse.equinox.p2.director -repository file:<repo> -installIU <feature>.feature.group -destination /Applications/AnypointStudio.app/Contents/Eclipse -profile DefaultProfile` | p2 writes into `plugins/` + rewrites `bundles.info`/profile → **modifies the bundle**, but this is the *sanctioned* mechanism Studio itself uses; user-writable so it succeeds. Same sealed-resource caveat applies but p2 keeps the install internally consistent. |
| **(b) PDE export (Export → Deployable plug-ins/features)** | Medium — reproducible in the IDE but tied to whoever has the IDE/target set up; no Maven pin unless you also do (a) | **Lowest** to first artifact for a solo dev already in the IDE | In IDE: select project → **File → Export → Plug-in Development → Deployable plug-ins and features** → either (i) "Install into host. Repository:" pointing at this Studio (does a p2 install directly), or (ii) export a `Directory`/Archive then drop the jar in or use Install New Software on the generated repo. | "Install into host" path = same as (a) at runtime (p2). Plain "copy jar to plugins/" = same sealed-resource caveat + you must restart with `-clean`. |
| **(c) dropins folder** | **Low** — no version metadata, no dependency checking, easy to get a silently-unresolved bundle; ordering/`-clean` quirks | Low mechanically (copy one jar) but **high debugging risk** | `mkdir -p /Applications/AnypointStudio.app/Contents/Eclipse/dropins` then copy `your.plugin_1.0.0.jar` into it; relaunch (ideally once with `-clean`). The `org.eclipse.equinox.p2.reconciler.dropins` bundle (present) picks it up. | **Creates a new file inside the signed bundle** → breaks the seal; `dropins/` doesn't exist so you're adding to `Contents/`. Works because dir is writable, but unsupported and wiped by Studio updates. |

### RECOMMENDATION: **(a) p2 update site built with Tycho**, installed via "Install New Software" (with `p2.director` as the headless/CI alternative).

Rationale for a solo dev who must land this in *this* Studio:
1. **It is the only option that is both reproducible and dependency-safe.** Tycho + a
   pinned `.target` guarantees the bundle resolves against the exact bundles Studio runs, so
   "builds" ⇒ "installs." p2 verifies `Require-Bundle`/`Import-Package` at install time, so
   you find missing deps immediately instead of as a silent no-op (the classic dropins
   failure mode).
2. **Clean lifecycle.** p2 records the IU in `DefaultProfile`, so you can update or uninstall
   from the Studio UI; dropins/manual-copy leave no managed record.
3. **The machinery is already present** — `Install New Software` (p2 UI), the `p2.director`
   app, and even the dropins reconciler are all installed, so no prerequisite setup in Studio.
4. **CI/automation friendly** — the same `target/repository` installs headlessly via
   `p2.director` (profile `DefaultProfile`, destination the Eclipse home) for scripted setup.

When to deviate: for the *innermost* dev loop, run/debug the plugin as a self-hosted Eclipse
Application against the pinned target (no install needed at all); use **PDE Export "Install
into host"** for a quick manual install while iterating; reserve **dropins** only for a
throwaway smoke test, understanding it breaks the .app seal and is overwritten on update.

> Important macOS note regardless of option: every install method ultimately writes inside
> `/Applications/AnypointStudio.app/Contents/…`, invalidating the Developer-ID seal/staple.
> It still launches (already Gatekeeper-approved; quarantine bit clears after first run), but
> a Studio auto-update may revert your plugin. For durability, re-install after Studio
> updates, or keep an automated `p2.director` install step.

---

## 4. Required bundle dependencies (hover + hyperlinkDetector)

Eclipse best practice: use **`Import-Package`** for stable, widely-shared text/runtime API
(decouples from bundle identity/versioning), and **`Require-Bundle`** for things consumed as
a whole, for extension-point-defining bundles, and for the WST/SSE editor stack (its API is
distributed across bundles that are conventionally required, and it itself uses
`Require-Bundle`). For this plugin:

**MANIFEST.MF `Require-Bundle` (recommended set):**
```
Require-Bundle: org.eclipse.ui;bundle-version="[3.205.0,4.0.0)",
 org.eclipse.core.runtime;bundle-version="[3.31.0,4.0.0)",
 org.eclipse.core.resources;bundle-version="[3.20.0,4.0.0)",
 org.eclipse.jface.text;bundle-version="[3.25.0,4.0.0)",
 org.eclipse.text;bundle-version="[3.14.0,4.0.0)",
 org.eclipse.ui.workbench.texteditor;bundle-version="[3.17.0,4.0.0)",
 org.eclipse.ui.editors;bundle-version="[3.17.0,4.0.0)",
 org.eclipse.wst.sse.ui;bundle-version="[1.7.0,2.0.0)",
 org.eclipse.wst.sse.core;bundle-version="[1.2.0,2.0.0)"
```
Add only if you touch the XML model / generic editor directly (often not needed if you bind
to the SSE editor via the `hyperlinkDetectorTarget` id):
```
 org.eclipse.wst.xml.core;bundle-version="[1.2.0,2.0.0)",
 org.eclipse.wst.xml.ui;bundle-version="[1.2.0,2.0.0)",
 org.eclipse.ui.genericeditor;bundle-version="[1.3.0,2.0.0)"
```

Why each:
- `org.eclipse.jface.text` — `ITextHover`/`ITextHoverExtension`, `IInformationControl`,
  and the `org.eclipse.jface.text.hyperlink` package (`IHyperlink`, `IHyperlinkDetector`,
  `AbstractHyperlinkDetector`). **The core of both features.**
- `org.eclipse.text` — `IDocument`, regions, document partitioning (transitively needed by
  jface.text APIs you call).
- `org.eclipse.ui.workbench.texteditor` — **defines the extension points**
  `…hyperlinkDetectors` and `…hyperlinkDetectorTargets` you contribute to; `HyperlinkDetectorRegistry`.
- `org.eclipse.ui.editors` — `org.eclipse.ui.editors.text` (editor/text infrastructure;
  `editorHover`/`annotationHover` wiring lives in this stack).
- `org.eclipse.wst.sse.ui` — the Mule Configuration XML editor is a WST **StructuredTextEditor**;
  required for SSE-specific editor integration / `SourceViewerConfiguration` and to ensure
  your detector binds to the SSE editor. (It re-exports `org.eclipse.ui.editors`.)
- `org.eclipse.wst.sse.core` — structured document/model (`IStructuredDocument`,
  `IStructuredModel`) if you read the XML structurally for hover/hyperlink resolution.
- `org.eclipse.core.resources` — `IFile`/`IResource`/workspace, to resolve targets across
  files (e.g., go-to-definition into another config file).
- `org.eclipse.core.runtime` — `IAdaptable`, extension registry, `IPath`, `Platform`.
- `org.eclipse.ui` — workbench (`IWorkbenchPage`, `IEditorPart`) for opening/revealing the
  target on Ctrl+click.
- (optional) `org.eclipse.wst.xml.core` / `org.eclipse.wst.xml.ui` — DOM-level XML model
  (`IDOMNode`, `IDOMElement`) and the XML editor's hyperlink target ids; only if you parse
  the DOM directly. `org.eclipse.ui.genericeditor` — only if you also target the generic editor.

**Extension points to declare in `plugin.xml`** (provided by the bundles above, all present):
- `org.eclipse.ui.workbench.texteditor.hyperlinkDetectors` — register your
  `AbstractHyperlinkDetector`, with `targetId="org.eclipse.core.runtime.xml"` (the XML/SSE
  editor target, verified above) so Ctrl+click works in the Mule config editor.
- For hover: contribute an `ITextHover` via the SSE editor's
  `SourceViewerConfiguration`/editor configuration extension, or
  `org.eclipse.ui.editors.annotationTypes`/text-hover mechanisms. (The hover side is wired
  through the SSE editor configuration rather than a single global extension point.)

`Bundle-RequiredExecutionEnvironment: JavaSE-17` (matches `osgi.requiredJavaVersion=17`).

---

## 5. Minimal toolchain + build commands

### Toolchain

- **Build JDK: Temurin 17** at `/Library/Java/JavaVirtualMachines/temurin-17.jdk` (matches
  Studio's Java 17 requirement; compile target `JavaSE-17`).
- **Maven 3.9.x** (Tycho 4.x requires Maven 3.9).
- **Eclipse Tycho 4.0.13** — last 4.0.x / Java-17-friendly line. Tycho 3.x/4.x require Java
  17 *to run*; Tycho **4.0.x** targets Eclipse **2023-09 onward** (covers 4.31/2024-03);
  4.0.13 is the final 4.0.x (subsequent Tycho 5/6 pull in Java 21 deps, so avoid them for a
  Java-17 build). Tycho 4.0.x is therefore the recommended, still-installable choice here.
- **Network:** because the `.target` points at the local Studio install (Section 2b), Tycho
  needs **no access to a remote p2 repo**. (If you instead used a `download.eclipse.org`
  InstallableUnit location you would need network + risk the version-skew problem in §2.)

### Project layout (minimal)

```
mule-qol-plugin/                  (parent, packaging=pom; pins tycho-version)
  studio-7.25-tycho.target        (the pinned target from §2b)
  com.example.muleqol/            (packaging=eclipse-plugin; the hover+hyperlink bundle)
  com.example.muleqol.feature/    (packaging=eclipse-feature)
  com.example.muleqol.site/       (packaging=eclipse-repository; category.xml)
```

### Build commands

```bash
# Use Temurin 17 for the build
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home

# Full build → produces the p2 update site at com.example.muleqol.site/target/repository
mvn -V clean verify

# (faster inner loop: compile/package the plugin only)
mvn -pl com.example.muleqol clean package
```

### Install into THIS Studio

Preferred (UI): **Help → Install New Software… → Add… → Local…** → select
`com.example.muleqol.site/target/repository` → pick the feature → Finish → restart.

Headless alternative (scriptable; profile = `DefaultProfile`, destination = Eclipse home):
```bash
ECLIPSE=/Applications/AnypointStudio.app/Contents/Eclipse
"$ECLIPSE/../MacOS/AnypointStudio" \
  -application org.eclipse.equinox.p2.director \
  -repository "file:$PWD/com.example.muleqol.site/target/repository" \
  -installIU com.example.muleqol.feature.feature.group \
  -destination "$ECLIPSE" \
  -profile DefaultProfile
# (Studio has no separate eclipsec; use the product launcher in Contents/MacOS, app closed.)
```

Inner dev loop without installing: set the `.target` active in PDE, then Run → Run As →
**Eclipse Application** (self-hosted) to smoke-test the hover and Ctrl+click in a Mule
Configuration XML editor.

---

## 6. Open questions

1. **Headless `p2.director` launcher on this product.** The install ships
   `org.eclipse.equinox.p2.director.app` but there is **no standalone `eclipsec`** binary
   visible; the documented pattern is `eclipsec -application …`. On macOS you'd invoke the
   product executable in `Contents/MacOS/AnypointStudio` with the director args (Studio not
   running). This *should* work but was **not executed** (read-only research). Verify before
   relying on it for CI.
2. **Does the Studio `artifacts.xml`/metadata cleanly serve as a Tycho p2 repo?** The install
   has `artifacts.xml` and a p2 profile but I did not see a top-level `content.xml`/
   `content.jar` (metadata may live under the profile registry). If Tycho's InstallableUnit
   location can't read it directly, use the **`Directory` location at `plugins/`** (Approach
   B) — that definitely works for plain bundles. Untested end-to-end here.
3. **Exact hover wiring for the SSE/Mule editor.** `ITextHover` for a StructuredTextEditor is
   contributed via the SSE `SourceViewerConfiguration`/editor configuration, not a single
   global extension point. The precise hook (e.g. overriding the SSE viewer configuration vs.
   an `org.eclipse.wst.sse.ui.editorConfiguration`-style contribution) needs a code spike.
   The hyperlink side (`hyperlinkDetectors` + `targetId=org.eclipse.core.runtime.xml`) is
   confirmed.
4. **Whether Studio auto-updates will clobber an installed feature**, and whether a broken
   code-seal ever blocks launch under the hardened runtime on a future macOS. Not tested.
5. **`com.apple.quarantine` on `plugins/`** — adding files there inherits/needs no special
   xattr handling for the running app, but freshly added jars could themselves be quarantined
   if downloaded; clear with `xattr -dr com.apple.quarantine <jar>` if a Gatekeeper prompt
   appears. Minor.

---

## 7. Contradictions with stated assumptions

1. **No `dropins/` folder exists.** The brief lists dropins as a candidate and asks "where
   `dropins/` … live." **There is no `dropins/` directory in this install** — it would have
   to be created under `Contents/Eclipse/dropins`. The dropins *mechanism* is supported
   (the `org.eclipse.equinox.p2.reconciler.dropins` bundle is installed), but the folder is
   absent by default. (Not a contradiction in capability, but a correction to the implicit
   assumption that dropins is ready to use.)
2. **Eclipse version number nuance.** `projects.eclipse.org/releases/2024-03` lists the
   "Eclipse Project" sub-project as **4.30.0** while the packaging/SDK is **4.31.0**. The
   install unambiguously ships the **4.31** platform/RCP/e4 features and the Studio release
   notes say "Eclipse 4.31," and Eclipse's own 4.31 New&Noteworthy says 4.31 == 2024-03.
   So the brief's "Eclipse base 4.31 / 2024-03" is **correct**; just be aware the bare
   "Eclipse Platform" component carries a `4.31.x` bundle version (`org.eclipse.platform_4.31.100`)
   even though one Eclipse sub-project page shows 4.30.0 for the train. No contradiction with
   ground truth.
3. **`text_3.14.0` symbolic name.** Brief abbreviates as `text_3.14.0`; the exact bundle is
   `org.eclipse.text_3.14.0.v20240207-1054`. (Confirms, just disambiguating.)
4. **Permissions vs. SIP framing.** The brief asks about "permissions/SIP" implications of
   writing to `dropins/`/`plugins/`. Measured reality: those dirs are **user-writable** and
   **SIP does not protect `/Applications/AnypointStudio.app`** (SIP guards system locations,
   not arbitrary app bundles). The real constraint is **code-signing/notarization seal
   invalidation**, not a filesystem/SIP write block. (Refines the assumption.)

---

## 8. Citations (URL per claim)

- Anypoint Studio 7.25.0 release notes — "Eclipse 4.31", "requires Java 17", Temurin 17.0.13+11,
  release date May 14 2026, supported OS (macOS 12–26):
  https://docs.mulesoft.com/release-notes/studio/anypoint-studio-7.25.0-with-4.11-runtime-release-notes
- Anypoint Studio release notes index:
  https://docs.mulesoft.com/release-notes/studio/anypoint-studio
- Eclipse 4.31 == 2024-03 simultaneous release (New & Noteworthy):
  https://eclipse.dev/eclipse/news/4.31/
- Eclipse 2024-03 release record (date Mar 13 2024; sub-project version table):
  https://projects.eclipse.org/releases/2024-03
- Eclipse 4.31 platform build id (`4.31.0.I20240229-0520`):
  https://download.eclipse.org/releases/2024-03/202403131000/buildInfo/archive/download.eclipse.org/releases/2024-03/202403131000/index/org.eclipse.platform.ide_4.31.0.I20240229-0520.html
- p2 director application command line (`-application org.eclipse.equinox.p2.director`,
  `-repository`, `-installIU`, `-destination`, `-profile`):
  https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/p2_director.html
- PDE Target Platform concept / target definitions are `.target` XML:
  https://help.eclipse.org/latest/topic/org.eclipse.pde.doc.user/concepts/target.htm ;
  https://github.com/eclipse-pde/eclipse.pde/blob/master/docs/Target_Definitions.md
- PDE target location wizards (Installation / Directory / InstallableUnit location types):
  https://help.eclipse.org/latest/topic/org.eclipse.pde.doc.user/guide/tools/target_shared/location_edit_installation_wizard.htm ;
  https://help.eclipse.org/latest/topic/org.eclipse.pde.doc.user/guide/tools/target_shared/location_edit_directory_wizard.htm
- `.target` XML example (InstallableUnit + repository + unit, `<?pde?>`, environments):
  https://www.vogella.com/tutorials/EclipseTargetPlatform/article.html
- Tycho `target-platform-configuration` mojo — `<target>` `<artifact>`/`<file>`/`<uri>`,
  `<environments>`, `<pomDependencies>` (ignore/consider/wrapAsBundle):
  https://tycho.eclipseprojects.io/doc/4.0.0/target-platform-configuration/target-platform-configuration-mojo.html ;
  https://tycho.eclipseprojects.io/doc/latest/target-platform-configuration/target-platform-configuration-mojo.html
- Tycho `pomDependencies=consider` semantics:
  https://wiki.eclipse.org/Tycho/How_Tos/Dependency_on_pom-first_artifacts ;
  https://flames-of-code.netlify.app/blog/maven-and-tycho-deps/
- Tycho 4.x requires Maven 3.9 / Tycho 3.x+ requires Java 17 runtime; 4.0.x targets
  2023-09+; 4.0.13 final 4.0.x (later Tycho needs Java 21):
  https://github.com/eclipse-tycho/tycho/blob/tycho-4.0.x/RELEASE_NOTES.md ;
  https://github.com/eclipse-tycho/tycho/blob/main/RELEASE_NOTES.md ;
  https://www.eclipse.org/lists/tycho-dev/msg02086.html
- Tycho project / releases:
  https://projects.eclipse.org/projects/technology.tycho/releases/4.0.8 ;
  https://github.com/eclipse-tycho/tycho/releases
- Tycho general docs:
  https://tycho.eclipseprojects.io/
- Local install evidence (read-only inspection, this machine): `codesign -dv`, `ls`,
  `configuration/config.ini`, `configuration/org.eclipse.equinox.simpleconfigurator/bundles.info`,
  `p2/org.eclipse.equinox.p2.engine/profileRegistry/DefaultProfile.profile`, and `unzip -p`
  of bundle `MANIFEST.MF`/`plugin.xml` files under
  `/Applications/AnypointStudio.app/Contents/Eclipse/plugins/`.
