# Mule 4.x Property Resolution — Model for the Property Value Hover (Feature 1)

Research agent: **agent-mule-resolution**. Scope: a *static* model of Mule 4 property
resolution, narrowed to OUR convention (self-contained per-env files) and to what is
resolvable from project files **without** running Mule. Target runtime in the fixture:
**Mule 4.11.4**; Studio **7.25.0**.

Every non-obvious claim has a citation in the **Citations** section (referenced inline as
`[C#]`).

---

## 1. Confirmed facts (fixture + convention)

These are confirmed by reading the fixture and `CLAUDE.md`; they are the ground truth the
resolver must match.

- **Env files**: `src/main/resources/envs/{local,test,prod,example,secure}.properties`,
  selected via `<configuration-properties file="envs/${env}.properties"/>`. Confirmed in
  `plugin-testing/src/main/mule/plugin-testing.xml` line 19.
- **Same keys, different values, self-contained**: every env file (`local`/`test`/`prod`/
  `example`) holds the SAME keys with different values. There is **NO shared/base file and
  NO override/precedence layer**. The resolver returns exactly one value per env file. (CLAUDE.md,
  "RESOLVED (2026-05-28)".)
- **`example.properties` is a blank template**: keys present, values mostly empty
  (`greeting=`, `http.host=`, …); `db.url` carries a placeholder-style template
  `jdbc:postgresql://<host>:<port>/<database>`. Render empty → `(empty)`; render the
  template literally (it is a real value, just illustrative). README calls for `(empty)` /
  `(template)`.
- **Active env selector**: property name is **`env`**, runtime-only, set via VM arg
  `-M-Denv=<env>` (the `-M-` prefix tells the Mule Maven plugin / Studio launcher to pass
  `-Denv=<env>` to the Mule JVM). `${env}` is never in any file — it is a system/deployment
  property supplied at launch. Confirmed by fixture + CLAUDE.md.
- **Two `<global-property>`** (lines 22, 25):
  - `app.displayName = Studio QoL Demo` (literal, env-independent).
  - `app.banner = ${app.displayName} [${env}]` (CHAINED: references another global + the
    runtime `env`).
- **Secure module in use**: `<secure-properties:config name="Secure_Config"
  file="envs/secure.properties" key="${secure.key}">` with
  `<secure-properties:encrypt algorithm="Blowfish"/>` (lines 29–34). `secure.properties`
  contains `db.password=!["c2FtcGxlLWVuY3J5cHRlZC1wYXNz"]` — an encrypted `![...]` value.
  `secure.key=demoKey1234567890` lives in each env file (a *normal*, resolvable property).
- **Other keys present in every env file**: `greeting`, `http.host`, `http.port`,
  `http.path`, `db.url`, `api.baseUrl`, `feature.newCheckout`, `secure.key`.

> Naming note: the fixture XML file is `plugin-testing.xml`; the README/CLAUDE.md sometimes
> call it `sample-app.xml`. Same file, just legacy naming in the docs. The hover plugin
> should not hard-code the file name.

---

## 2. `.properties` parsing rules (env files)

Mule loads `.properties` via the standard **`java.util.Properties`** line-oriented format.
A static parser for the hover must reproduce these rules [C5]:

### Encoding
- `Properties.load(InputStream)` is defined to use **ISO-8859-1 (Latin-1)**, one byte per
  char; non-Latin-1 chars are expected as `\uXXXX` Unicode escapes [C5].
- **Mule caveat**: Mule's runtime default char encoding is **UTF-8** (via `MULE_ENCODING`
  / `-Dmule.encoding`, default UTF-8) [C6]. There is no MuleSoft doc that *explicitly*
  states which encoding `<configuration-properties>` uses to read a `.properties` file; the
  safe assumption is the JDK default (ISO-8859-1 for `load(InputStream)`). **Practical
  guidance for the plugin**: our fixture values are pure ASCII, so encoding is moot here.
  For robustness, read bytes and decode leniently; if a value contains a literal non-ASCII
  byte, render it best-effort and do not crash. This is an **open question** (see §11) — do
  not assert a definitive Mule encoding in hover UI.

### Comments
- A line whose **first non-whitespace char is `#` or `!`** is a comment and is ignored.
  Comments **cannot** span lines via continuation — each comment line needs its own
  indicator [C5]. (All our fixture comments use `#`.)

### Separators
- The key ends at the **first unescaped `=`, `:`, or whitespace** after the first
  non-whitespace char. If that separator is `=` or `:` it is consumed, and whitespace
  around it is skipped. So `k=v`, `k:v`, `k = v`, `k : v`, and `k v` all yield key `k`,
  value `v` [C5]. (Our files use `=` exclusively.)

### Whitespace
- Leading whitespace on a line is skipped. Space, tab (`\t`), and form feed (`\f`) are
  whitespace. A line of only whitespace is **blank** and ignored. Trailing whitespace
  **after** the separator IS part of the value (only the run immediately after the
  separator is skipped) [C5]. Implication: `greeting=Hello ` keeps the trailing space —
  the hover may want to visibly mark trailing spaces, but must not silently trim in a way
  that misrepresents the runtime value.

### Empty / value-less keys
- `key=` and bare `key` both yield the **empty string `""`** [C5]. This is exactly the
  `example.properties` case → render **`(empty)`**.

### Line continuation
- A backslash `\` at end of a (natural) line continues the logical line; the backslash, the
  terminator, and leading whitespace of the next line are removed. An even number `2n` of
  trailing backslashes is `n` literal backslashes (no continuation); an odd number
  continues [C5].

### Escapes
- Recognized: `\t \n \r \f`, `\uXXXX` (single `u` only), `\\`, `\:`, `\=`, `\'`, `\"`.
- **Not** recognized: octal escapes; `\b` is *not* backspace. A backslash before any other
  char is silently dropped (`\z` → `z`) [C5].

### Per-env value-set strategy (the headline behavior)
For a hovered `${key}` the resolver should:
1. Enumerate every `*.properties` in `src/main/resources/envs/` that is a *candidate target*
   of a `<configuration-properties file="envs/${env}.properties"/>` declaration (i.e.
   files matching the static portion `envs/<*>.properties`; in practice
   local/test/prod/example, **excluding** `secure.properties`, which is owned by the
   secure-properties config, not the plain `configuration-properties`).
2. Parse each with the rules above; look up `key`.
3. Build a per-env table: `{ local: v1, test: v2, prod: v3, example: (empty)/template }`,
   each row carrying **source file path + line number** (needed for the popup and for
   Ctrl+click go-to-def).
4. If `env` (active) is known (from launch config / preference), **highlight that row**;
   otherwise show all rows unhighlighted.
5. If the key is absent from **all** candidate files → state **unresolved** (CASE 7).

Because OUR env files are self-contained (no base/override), each row is independent — do
NOT merge or let one file override another. (This is convention-specific; Mule's *general*
multi-file behavior is first-wins — see §7.)

---

## 3. `<configuration-properties file="envs/${env}.properties"/>` semantics

- `<configuration-properties>` loads a `.properties` **or** `.yaml` file from
  `src/main/resources` (or an absolute path). `String` is the only supported value type in
  both formats [C1].
- The **file attribute itself may contain a placeholder**. MuleSoft explicitly documents
  this pattern: "set the file to depend on a property (for example, `env`) to determine
  which file to use", e.g. `<configuration-properties file="${env}-properties.yaml"/>` [C1].
  Our `file="envs/${env}.properties"` is exactly this idiom.
- Consequence for static analysis: **which** file loads is **runtime-dependent** (`${env}`
  is only known at launch). Statically we cannot pick one file, so we **enumerate the
  candidates** by substituting each discovered env name into the static template and
  showing them side by side. This is the core justification for the per-env table.
- Multiple `<configuration-properties/>` tags are allowed [C1] (precedence in §7).

---

## 4. `<global-property>` and chained/nested values

- `<global-property name="x" value="y"/>` defines a placeholder inline in XML [C1].
- Global properties **may reference** values from `.properties`/`.yaml` files or secure
  properties — i.e. a global property's value can embed `${...}` placeholders that resolve
  against higher-priority sources [C1]. This is what makes
  `app.banner = ${app.displayName} [${env}]` legal: `${app.displayName}` resolves to
  another global; `${env}` resolves to the runtime system property.
- **Static handling**: show the **RAW value verbatim** (`${app.displayName} [${env}]`) and
  annotate that it references other properties. Optionally, do a *shallow, safe* expansion
  of references we can resolve statically (e.g. `app.displayName → "Studio QoL Demo"`) but
  **NEVER resolve `${env}`** (runtime-only) — so a fully-expanded value is impossible
  statically. Recommended hover content: raw value + "references: `app.displayName`
  (=`Studio QoL Demo`), `env` (runtime-only)". Do not present a fabricated final string.
- Constraint to respect (real-world): a configuration property's placeholders **cannot
  depend on properties loaded from another `configuration-properties` file** [C1][C2];
  global properties can depend on config properties but not vice-versa across config files.
  Our fixture doesn't hit this, but the resolver should not assume arbitrary cross-file
  chaining works.
- **Escaping**: `\${...}` in a value/message is a *literal* `${...}`, NOT a placeholder
  [C1]. The hover should recognize a leading backslash and treat the token as literal text
  (no resolution, no "unresolved" warning).

---

## 5. Secure properties — detection rules (NEVER decrypt)

The secure-properties (a.k.a. Secure Configuration Properties) module encrypts values at
rest; decryption happens **only at runtime** with a key supplied at deploy time. The hover
must **flag, never decrypt or guess** [C3][C4][C7].

### Three independent detection signals (any one ⇒ "secure")
1. **Placeholder prefix `secure::`** — `${secure::db.password}`. The `secure::` prefix is
   **required** to read any value from a secure-properties file (even unencrypted ones)
   [C3][C4]. Detection rule: a placeholder whose key begins with `secure::` is secure.
2. **`![...]` value form in a file** — encrypted values are written as `![encryptedValue]`
   with no spaces/chars outside the brackets; in YAML they must be quoted (`"![...]"`)
   [C3][C4]. Detection rule: a value matching `^\s*!\[.*\]\s*$` is an encrypted secure
   value → render "secure / encrypted, decrypted at runtime", never the bracket contents.
3. **`<secure-properties:config>` element** — its `file=` attribute names the secure file
   (`envs/secure.properties` here); keys loaded from that file are secure regardless of
   whether each individual value is wrapped in `![...]` [C3][C4]. Detection rule: any key
   resolved from a file that is the target of a `secure-properties:config` is secure.

### Key vs. encrypted value (important nuance)
- The Blowfish key is supplied via `key="${secure.key}"`. `secure.key` is a **normal
  property** present in each env file (`demoKey1234567890`) — it **is statically
  resolvable** and can be shown like any other property (it's not itself secret-at-rest in
  this fixture; though teams often inject it at runtime too).
- The **encrypted values** (`![...]`) are **NOT** resolvable and must never be shown
  decrypted. Distinguish clearly: the *key property* (`secure.key`) is resolvable; the
  *secure values* it would unlock are not.

### Algorithm / mode (for documentation completeness)
- `<secure-properties:encrypt>` is **required even with defaults** [C3][C4].
- Algorithms: **AES (default)**, Blowfish, DES, DESede, RC2, RCA, plus JCE-provided ones.
- Modes: **CBC (default)**, CFB, ECB, OFB. Our fixture uses `algorithm="Blowfish"` (mode
  omitted ⇒ CBC default) [C3][C4][C7].
- Runtime key delivery: passed to the Mule runtime as a system/environment property at
  deploy time (docs example uses `runtime.property`); here that indirection is
  `key="${secure.key}"` [C3][C4].

---

## 6. `${env}` and other runtime-only properties

- `${env}` is a **system/deployment property**, not in any file. It is supplied at launch
  (`-M-Denv=<env>` → JVM `-Denv=<env>`). Statically there is **no value**; the resolver
  must flag it **runtime-only** and may list the available env files (local/test/prod/
  example) as the *candidate* values `env` could take.
- General principle: any `${key}` that (a) is not defined by any `<global-property>` and
  (b) is not found in any enumerated `configuration-properties` file, but (c) is plausibly
  a system/deployment property, should be shown as **runtime-only / supplied at launch**
  rather than "unresolved". In practice the hover cannot always distinguish "runtime-only"
  from "typo/undefined" (CASE 7) without a known list. Reasonable heuristic: treat the
  configured active-env property name (`env`, configurable) and well-known Mule system
  props as runtime-only; everything else absent from files → unresolved. (See §11.)
- Note: deployment & system properties also **outrank** file/global properties (§7), so at
  runtime `${env}` etc. cannot be shadowed by a file entry of the same name.

---

## 7. Mule's GENERAL resolution precedence (for real-world projects)

Even though OUR env files are self-contained, document this so the shared property-index
module is correct for other projects [C1][C2].

**Precedence hierarchy (highest → lowest)** — higher wins when the same name is defined in
multiple places [C1]:
1. **Deployment properties** (e.g. set on deploy / CloudHub properties)
2. **System properties** (`-D...` JVM args — this is where `env` lives)
3. **Environment properties** (OS environment variables)
4. **Application properties** (`configuration-properties`, secure config properties, custom)
5. **Global properties** (`<global-property>`) — lowest

Corollaries:
- A lower-tier property **can derive its value** from a higher tier (an application property
  can reference system/deployment props), but **not the reverse** [C1].
- **Multiple `<configuration-properties/>` files**: if the same key appears in more than
  one referenced file, **"the first definition will be preserved"** ⇒ **FIRST-WINS** (the
  first `<configuration-properties>` declaration in document order takes precedence) [C1].
  This is the opposite of the "last-wins" assumption common in some other property systems —
  see §10.
- **Cross-config-file chaining is disallowed**: a configuration property's placeholders
  "cannot depend on the properties loaded from another configuration property" [C1].
- Components that load files at startup cannot depend on application/global properties due
  to load-time ordering [C1].

OUR convention sidesteps all of this: exactly one plain `<configuration-properties>` whose
target is chosen at runtime, files are self-contained, no overlap to arbitrate. So
precedence does not change any of CASES 1–8; it matters only if a future real project adds
overlapping files or a base layer.

---

## 8. CASES 1–8 — resolvable vs runtime-only vs secure MATRIX

Values below are taken directly from the fixture env files.

| # | Placeholder | Source | State | Expected hover content |
|---|-------------|--------|-------|------------------------|
| 1 | `${greeting}` | `envs/*.properties` | **Resolvable (per-env)** | Table: local `Hello from LOCAL`, test `Hello from TEST`, prod `Hello from PROD`, example `(empty)`. Source: `src/main/resources/envs/*.properties` + line 2. Highlight active env row if known. |
| 2 | `${db.url}` | `envs/*.properties` | **Resolvable (per-env)** | local `jdbc:postgresql://localhost:5432/app`; test `jdbc:postgresql://test-db.internal:5432/app`; prod `jdbc:postgresql://prod-db.internal:5432/app`; example `jdbc:postgresql://<host>:<port>/<database>` (template). Source + line 8/10. |
| 3 | `${feature.newCheckout}` | `envs/*.properties` | **Resolvable (per-env)** | local `true`, test `true`, prod `false`, example `(empty)`. Highlights env-dependent behavior. Line 11/13. |
| 4 | `${app.displayName}` | inline `<global-property>` | **Resolvable (single, env-independent)** | Single value `Studio QoL Demo`; source `plugin-testing.xml` line 22; note "not env-specific (global-property)". |
| 5 | `${app.banner}` | inline `<global-property>` (chained) | **Resolvable RAW only / partially** | Raw value `${app.displayName} [${env}]`; note it references `app.displayName` (=`Studio QoL Demo`, resolvable) and `env` (**runtime-only**, NOT resolved). Do not show a fully-expanded string. Source line 25. |
| 6 | `${env}` | runtime system/deployment property | **Runtime-only** | Flag "runtime-only — set via `-Denv=<value>` (`-M-Denv=` in Studio)". MAY list available env files (local/test/prod/example) as candidate values. No file/line. |
| 7 | `${does.not.exist}` | none | **Unresolved** | Flag "unresolved — not defined in any env file or `<global-property>`". Negative test. |
| 8 | `${secure::db.password}` | `envs/secure.properties` (`![...]`) via `secure-properties:config` | **Secure** | Flag "secure / encrypted — decrypted at runtime". Source `src/main/resources/envs/secure.properties` line 6. NEVER show the `![...]` payload as a value, NEVER decrypt. (Detected by all three signals: `secure::` prefix, `![...]` form, and being the `secure-properties:config` file.) |

Auxiliary (not numbered cases but present in the XML — the resolver will meet these):
- `${http.host}` / `${http.port}` / `${http.path}` (listener config) → **Resolvable
  per-env** (host/port/path identical across local/test/prod; empty in example).
- `${api.baseUrl}` → **Resolvable per-env** (differs per env; empty in example).
- `${secure.key}` → **Resolvable per-env** = `demoKey1234567890` (local/test/prod), empty
  in example. NOTE: this is the *key property*, resolvable; contrast with CASE 8's secure
  *value*.

---

## 9. Open questions

1. **`.properties` read encoding in Mule** — no MuleSoft doc explicitly states whether
   `<configuration-properties>` reads `.properties` as ISO-8859-1 (JDK default for
   `Properties.load(InputStream)`) or UTF-8 (Mule's data default via `mule.encoding`). For
   our ASCII fixture it doesn't matter. Recommend: parse leniently, don't assert an encoding
   in the UI. (See §2, [C5][C6].)
2. **Distinguishing runtime-only from undefined** — without a configured/known list of
   system/deployment property names, the hover cannot reliably tell `${env}` (runtime-only,
   CASE 6) from `${does.not.exist}` (typo, CASE 7). Mitigation: make the active-env property
   name a preference (default `env`) and treat it (plus a small set of well-known Mule system
   props) as runtime-only; treat all other file-absent keys as unresolved.
3. **Which env files count as `configuration-properties` candidates** — we infer
   local/test/prod/example by globbing `envs/*.properties` minus the `secure-properties`
   file. If a project has unrelated `.properties` in `envs/`, this could over-enumerate.
   Mitigation: only enumerate files whose name matches the static template around `${env}`
   (here `envs/<X>.properties`) and exclude files claimed by a `secure-properties:config`.
4. **YAML support** — our convention is `.properties`, but Mule equally supports `.yaml`
   [C1]. Feature 1 can scope to `.properties`; the shared index should leave room for YAML
   (hierarchical keys, `String`-only values) later.
5. **Secure key shown or hidden?** — `secure.key` is statically resolvable here
   (`demoKey1234567890`) and is not itself an `![...]` value, so by our rules it is
   "resolvable". But it is *semantically* a secret. Product decision: show it as a normal
   value, or special-case any key literally named `*.key`/used in `secure-properties
   key="..."` as sensitive? Documented; defer to product.

---

## 10. Contradictions with prior assumptions

- **"last-wins?" → NO, it's FIRST-WINS.** The mission asks whether multiple
  `configuration-properties` is last-wins. MuleSoft documents the opposite: when a key is in
  multiple referenced files, **the first definition is preserved** (first-wins) [C1]. Flag
  this clearly — anyone porting intuition from systems where later overrides earlier will be
  wrong on Mule. (Does not affect our self-contained fixture.)
- **Global properties are LOWEST priority**, below application/file properties [C1]. A
  natural assumption that an inline `<global-property>` "overrides" a file is **backwards**:
  a file (application) property of the same name would win. Our fixture has no name clash
  (`app.displayName`/`app.banner` exist only as globals), so no behavioral impact — but the
  shared resolver must encode global = lowest.
- **README/CLAUDE.md filename drift** — docs reference `sample-app.xml`; the real fixture is
  `plugin-testing.xml`. Not a Mule contradiction, just stale naming; do not hard-code.
- **No contradiction on self-containment** — the convention (no base/override) is consistent
  with Mule's model; Mule *permits* layering but does not require it, so a self-contained
  single-source-per-env setup is valid and makes the per-env table unambiguous.

---

## 11. Citations (URL per claim)

- **[C1] MuleSoft — Configuring Properties (mule-runtime, latest)**:
  https://docs.mulesoft.com/mule-runtime/latest/configuring-properties
  Source for: `<configuration-properties>` file attr + `.properties`/`.yaml` + `String`-only;
  file attr may contain `${env}` placeholder ("set the file to depend on a property…");
  `<global-property>` and global referencing config/secure props; precedence hierarchy
  (deployment > system > environment > application > global); FIRST-WINS for duplicate keys
  across files ("the first definition will be preserved"); cross-config-file dependency
  restriction; `\${...}` escaping; lower tiers derive from higher tiers; components-load-time
  limitation.
- **[C2] MuleSoft docs source (GitHub, v4.4) — configuring-properties.adoc**:
  https://github.com/mulesoft/docs-mule-runtime/blob/v4.4/modules/ROOT/pages/configuring-properties.adoc
  (verbatim corroboration of the precedence list, first-wins wording, and the
  "cannot depend on the properties loaded from another configuration property" restriction).
- **[C3] MuleSoft — Secure Configuration Properties (mule-runtime, latest)**:
  https://docs.mulesoft.com/mule-runtime/latest/secure-configuration-properties
  Source for: `<secure-properties:config>` (file, key); `<secure-properties:encrypt>`
  (algorithm, mode) required even with defaults; `${secure::key}` prefix required;
  `![value]` encrypted form (must be quoted in YAML); runtime key delivery
  (`runtime.property`); algorithms (AES default, Blowfish, DES, DESede, RC2, RCA); modes
  (CBC default, CFB, ECB, OFB).
- **[C4] MuleSoft docs source (GitHub, v4.4) — secure-configuration-properties.adoc**:
  https://github.com/mulesoft/docs-mule-runtime/blob/v4.4/modules/ROOT/pages/secure-configuration-properties.adoc
  (verbatim corroboration of `secure::` prefix, `![...]` format, Blowfish/CBC, key-at-deploy).
- **[C5] Oracle — java.util.Properties Javadoc (Java SE 8)**:
  https://docs.oracle.com/javase/8/docs/api/java/util/Properties.html
  Source for: ISO-8859-1 for `load(InputStream)`; `#`/`!` comments (cannot continue);
  `=`/`:`/whitespace separators; whitespace handling; empty-value semantics (`key=` →
  `""`); backslash line continuation and even/odd backslash rule; escapes
  (`\t \n \r \f \uXXXX \\ \: \=`), octal not supported, `\b` not backspace, unknown escape
  silently dropped.
- **[C6] MuleSoft — Mule runtime default encoding (UTF-8 via `mule.encoding`/`MULE_ENCODING`)**:
  https://docs.mulesoft.com/mule-runtime/latest/about-mule-configuration
  (Mule's data default encoding is UTF-8; used to caveat the `.properties` read-encoding
  open question — Mule does not explicitly document the config-file read encoding).
- **[C7] Secure Properties tool / connector usage (Blowfish + CBC modes; encrypt tool)**:
  https://docs.mulesoft.com/anypoint-code-builder/int-create-secure-configs
  and connector overview: https://docs.mulesoft.com/release-notes/mule-runtime/secure-properties
  (corroborates Blowfish algorithm + CBC/CFB/ECB/OFB modes and the secure-properties module
  versions/runtime support).
