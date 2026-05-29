# CLAUDE.md — Anypoint Studio QoL Plugin

> Working name: **Anypoint Studio QoL** (bundle id: **`com.studioqol`**).
> This is a **multi-feature** quality-of-life plugin for Anypoint Studio. Architect it so
> features are independent modules over shared infrastructure. **Feature 1 (the only one
> built for now): Property Value Hover.** Leave clean seams for future features — don't
> build them yet.

## Status (2026-05-29) — Feature 1 COMPLETE; Feature 2 COMPLETE
**Feature 1 (Property Value Hover) is built, installed, and verified in Studio 7.25.0.** Latest build
`com.studioqol 0.1.0.202605290536` (Features 1 & 2). In the **Configuration XML** editor:
- Hover `${key}` → HTML popup (native style, "Press F2 for focus"): per-env value table from
  `envs/*.properties` with `file:line`; honest **secure / runtime-only / unresolved / (empty)** flags;
  HTML-escaped values; nested `${a${b}}` and `${secure::}` handled.
- **Active-env row highlighted**, detected from the most-recently-launched run config's `-Denv` and
  **persisted across restarts** (launch listener + eager startup; per-project preference).
- **Ctrl/⌘+click** a `${key}` → jumps to its definition (per env file, or the inline global / secure file).

**Feature 2 (DataWeave `p('...')` hover + navigation) is built and verified in Studio 7.25.0.** Hovering
`p('key')` / `Mule::p('key')` in the **Transform Message** editor, a **Logger `fx` expression** field, or
a standalone **`.dwl`** shows the same per-env popup as Feature 1 (header reads `p('key')`), with
**F2 / move-into-popup** focus (select + copy) and **Ctrl/⌘-click** navigation to the definition.
Implemented **editor-agnostically** at the SWT level — a global `Display` `MouseHover`/`MouseDown` filter
that recognizes DataWeave `StyledText`s by host-composite package prefixes and suppresses the native
Xtext hover only on a `p('...')` token (no Xtext/DFL dependency; no `plugin.xml`/`MANIFEST` change). The
earlier "not feasible" verdict applied only to Xtext-internal routes. Design + the two version-coupled
package prefixes (the one place to update after a Studio upgrade): **`research/feature2-dataweave-hover.md`**.

Build: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home mvn -B -f pom.xml clean verify`
→ p2 site at `com.studioqol.repository/target/repository`. Install: Help → Install New Software → Add →
Local… → that folder → select "Anypoint Studio QoL" → restart. (Re-install after Studio auto-updates.)

## Feature 1 — Property Value Hover
When the developer hovers over a `${propertyName}` placeholder in the **Configuration XML**
view of a Mule config file, show a popup with the property's value **in every environment**
(local / test / prod / …), its source file, and line number. Ctrl+click navigates to the
definition.

---

## OUR property convention (this is the important part — design to it)
- Environment property files live in **`src/main/resources/envs/`**.
- They are **`.properties`** files named by environment: `local.properties`,
  `test.properties`, `prod.properties`, and `example.properties` (a committed template
  with blank/placeholder values; the real `local.properties` is usually gitignored).
- They are selected with a parameterized declaration, e.g.
  `<configuration-properties file="envs/${env}.properties"/>`.
- The **same keys exist across all env files with different values**. So the headline
  hover behavior is a **per-environment table**, not a single value:

  ```
  db.url
    local   : jdbc:postgresql://localhost:5432/app
    test    : jdbc:postgresql://test-db.internal:5432/app
    prod    : jdbc:postgresql://prod-db.internal:5432/app
    example : (template)
  source: src/main/resources/envs/*.properties
  ```

- The active environment comes from the `env` placeholder, normally set at runtime via a
  VM arg like `-Denv=local` (or `-M-Denv=local`). **CONFIRM the exact property name in our
  real project** (could be `env`, `mule.env`, or custom) — make it configurable. When the
  active env is known, highlight that row; when unknown, just show all of them.

---

## Environment (verified Phase 0 — 2026-05-28; corroborating citations in `research/studio-target.md`)
- Anypoint Studio version: **7.25.0** (build `7.25.0.202605141228`); product `org.mule.tooling.branding.mulestudio`
- Studio install path: **`/Applications/AnypointStudio.app`** (Eclipse home: `/Applications/AnypointStudio.app/Contents/Eclipse`)
- Underlying Eclipse release: **4.31** (2024-03). Key target bundles (from `<eclipse>/plugins/`):
  `org.eclipse.wst.sse.core 1.2.1300`, `wst.sse.ui 1.7.900`, `wst.xml.core 1.2.800`, `wst.xml.ui 1.2.701`,
  `jface.text 3.25.0`, `text 3.14.0`, `ui.workbench.texteditor 3.17.300`, `ui.editors 3.17.200`,
  `core.resources 3.20.100`, `core.runtime 3.31.0`. `osgi.requiredJavaVersion=17`.
- JDK to build the plugin: **Temurin 17 (system)** — `/Library/Java/JavaVirtualMachines/temurin-17.jdk`
  (Studio bundles Temurin 17.0.12 at `…/plugins/org.mule.tooling.jdk.macosx.aarch64_1.4.1/Contents/Home`; host is arm64).
- Active-env property name: **`env`** (confirmed in `plugin-testing.xml`: `file="envs/${env}.properties"`; VM arg `-M-Denv=<env>`).
  Expose as a configurable preference, default `env`.
- Mule runtime (fixture): **4.11.4**; mule-maven-plugin 4.9.1.
- Target Platform: **pin to Studio 7.25.0's exact bundle versions** (prevents "compiles, won't install").

---

## Verified facts (RE-VERIFY in Phase 0; cite source URLs in `research/`)
- Studio's "Configuration XML" tab is the **standard Eclipse WTP/SSE
  `StructuredTextEditor`**, content type `org.eclipse.core.runtime.xml` — not a custom editor.
- Contribute a hover via `org.eclipse.wst.sse.ui.editorConfiguration` + `ITextHover` /
  `ITextHoverExtension2` (Extension2 → rich HTML popup via `IInformationControlCreator`).
  No editor subclassing required.
- WTP aggregates hovers through `BestMatchHover`, ordered by **plugin ID** → possible
  collisions. Design around it; smoke-test that ours fires.
- Go-to-definition: `org.eclipse.ui.workbench.texteditor.hyperlinkDetectors`.
- Prior art: `angelozerr/eclipse-wtp-webresources`, `angelozerr/eclipse-wtp-json`.

---

## Architecture (approved 2026-05-28 — see PLAN.md)
- Bundle layout: **single OSGi bundle `com.studioqol`** with internal feature packages
  (`core/` shared infra, `feature/hover/`, `ui/preferences/`), behind one p2 feature
  (`com.studioqol.feature`) + one update site (`com.studioqol.repository`). Multi-bundle split
  is a documented later seam. (`com.studioqol` also sorts before `org.eclipse.wst.xml.ui` → wins
  hover ordering.)
- Feature registration: internal extension point **`com.studioqol.feature`** + `IStudioQolFeature`
  + `FeatureRegistry` (lifecycle/orchestration). Editor-facing hover/hyperlink register via the
  platform points (`editorConfiguration`, `hyperlinkDetectors`).
- **Shared infra (reused by future features):** the per-project Mule property index/model
  (parses `configuration-properties`/`global-property`, watches `envs/*.properties`,
  resolves keys, tracks source file + line per env). Mule project detection via nature
  **`org.mule.tooling.core.muleStudioNature`**. Build this clean — later features depend on it.
- Resolution engine: per-env table from `envs/*.properties` (local/test/prod/example, excluding
  the secure file); honest **secure / runtime-only / unresolved / (empty)** states, never decrypt
  or fabricate. General Mule precedence (deployment>system>environment>application>global; multiple
  config files = **first-wins**) encoded for real-world projects.

## Future feature backlog (DO NOT BUILD — architecture should leave room)
Env value comparison popup (part of F1) · quick env switcher · "find all usages" of a
property · markers/quick-fixes for undefined or unused properties · quick-open env file ·
DataWeave `p('...')` hover. List exists only so the module/infra design anticipates them.

> **DataWeave `p('...')` hover** was investigated and is **not feasible via supported APIs** (closed
> Xtext editor; no external hover hook). See `research/feature2-dataweave-hover.md` for full findings
> and the unsupported routes a future agent could spike.

---

## Build & install (decided — see PLAN.md §5, research/studio-target.md)
- Toolchain: **Tycho 4.0.13** + Maven 3.9 + **Temurin 17**
  (`JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`).
- Target Platform: **`studio-7.25.target`** pinned to `/Applications/AnypointStudio.app/Contents/Eclipse`
  (Profile/Directory location) — guarantees "resolves in IDE ⇒ installs in Studio".
- Build: `mvn -V clean verify` → p2 update site at `com.studioqol.repository/target/repository`.
- Inner dev loop: run as a **self-hosted Eclipse Application** against the pinned target (no install).
- Install into Studio: **Help → Install New Software → Add → Local…** → select `target/repository`
  → pick the feature → restart. Headless alt: `p2.director` (profile `DefaultProfile`, destination the
  Eclipse home). **No `dropins/` by default.** Caveat: installing writes inside the signed `.app`
  (breaks the notarization seal; still launches; re-install after Studio auto-updates).

---

## Property resolution — what is statically resolvable
**Resolvable from project files:**
- `${key}` from `envs/<env>.properties` — return the value **for each env file present**
- `<global-property name= value=/>` inline in the XML (incl. chained values embedding `${…}`)
- `${env}`-parameterized file declarations → enumerate `envs/*.properties` candidates

**Surface as a special state — do NOT guess a value:**
- `${env}` itself and other system/runtime props → "runtime-only (e.g. `-Denv=local`)"
- Secure properties (`${secure::key}`, encrypted `![…]`) → "secure, decrypted at runtime"
- Keys not found in any env file → "unresolved / not defined in project"

**RESOLVED (2026-05-28):** No shared/base file — env files are **self-contained** (every key
duplicated per env, different values). The resolver returns exactly one value per env file; there is
no override/precedence layer. (Still document Mule's general precedence for real-world projects.)

---

## Test fixture
`test-fixture/` is a minimal Mule app matching our convention. Every `<logger>` in
`src/main/mule/sample-app.xml` is a labeled case; `test-fixture/README.md` has the
expected-hover matrix (= the manual test checklist for Phase 3).

> **In THIS workspace the fixture is realized as `plugin-testing/`** (the plugin project will live
> alongside it): config = `plugin-testing/src/main/mule/plugin-testing.xml`, matrix =
> `plugin-testing/README.md`, env files = `plugin-testing/src/main/resources/envs/`.

Fastest way to open in Studio: create a new Mule Project via the wizard (so the generated
`pom.xml` matches your runtime), then drop in `sample-app.xml` and the `envs/` folder.

---

## Working conventions
- **Smoke-test the hover first.** Ship a hardcoded-string hover and confirm it fires inside
  Studio's Configuration XML editor BEFORE writing any resolver logic.
- Smallest change that proves integration, then add fidelity.
- Keep Feature 1 self-contained; don't entangle it with future features, but DO put shared
  logic in the reusable property-index module.
- Cite a source URL for every Eclipse / WTP / Mule API claim.
- Incremental commits, one logical change each.
- Ask before any step that depends on local environment (Studio path, version, JDK, env name).
