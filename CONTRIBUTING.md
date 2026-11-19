# Contributing

Kaihi provisions a VPN server over SSH and connects to it. Most of the code either runs
commands as root on someone else's machine or handles their credentials, so the bar for changes in
`core.ssh`, `core.vpn` and `core.security` is higher than the line count suggests.

## Getting set up

```sh
git clone --recurse-submodules https://github.com/amanxgawa/Kaihi.git
```

`hev-socks5-tunnel` is a submodule, so after a plain clone run
`git submodule update --init --recursive`. You need JDK 21 for the Gradle daemon (the sources
compile to Java 17 bytecode), the Android NDK (SDK Manager), and `minSdk` 26. Open in Android
Studio and run the `app` module.

The build downloads `libv2ray.aar` and verifies it against a pinned SHA-256; a mismatch fails the
build rather than warning. It does the same for `geosite.dat` (domain-based direct routing for a
handful of regions). `geoip.dat` is different: it's vendored at
`app/src/main/assets/geoip.dat` rather than downloaded, and the build only verifies it against a
pinned checksum. See `tools/geoip-dat/README.md` for why, and for the maintainer-only
regeneration step.

### Updating a pinned version

Why pin by hash at all: these are third-party binaries fetched over the network at build time. An
unverified download means a compromised release or a MITM ships arbitrary code into a VPN app with
full device permissions; the checksum turns silent tampering into a failed build.

- **`libv2rayVersion`** / **`geositeVersion`** (`app/build.gradle.kts`): find the new release tag,
  download its `libv2ray.aar` / `dlc.dat` asset, `shasum -a 256` it, and update the version string
  and `sha256.set(...)` together, never one without the other.
- **`geoip.dat`**: run `./gradlew regenerateGeoipDat` (needs Go on `PATH`), then copy the SHA-256 it
  prints into `geoipDatSha256`. Commit the regenerated `app/src/main/assets/geoip.dat` and the
  updated hash **together**, or `verifyGeoipDat` fails the build for everyone else once they drift
  apart. To change which countries it covers, edit `wantedList` in `tools/geoip-dat/config.json`
  first.

## Verifying a change

```sh
./gradlew compileDebugKotlin          # fast loop while iterating
./gradlew testDebugUnitTest lintDebug # before pushing
```

`assembleDebug` is only needed when native code or the manifest changed. `assembleRelease` is CI's
job: it is slow locally and proves nothing that CI will not.

## Branches

| Prefix | For |
|---|---|
| `feature/<short-desc>` | new functionality |
| `fix/<short-desc>` | bug fixes |
| `refactor/<short-desc>` | restructuring without behaviour change |
| `test/<short-desc>` | test-only work |
| `docs/<short-desc>` | documentation and user-facing copy |
| `ci/<short-desc>` | workflows and build tooling |
| `release/<X.Y>` | release stabilisation |
| `hotfix/<X.Y>-<short-desc>` | fix on top of a released `release/X.Y` |

kebab-case, English, no issue numbers. The `Branch name` check enforces the prefix, kebab-case and
the version on `release/` and `hotfix/`; English and issue numbers are left to review.
[`conventional-branch.sh`](.github/workflows/conventional-branch.sh) is the source of truth.

## Commits and pull request titles

Squash is the only merge method enabled, so **the pull request title becomes the commit message on
`master`**. Commits inside your branch are squashed away and can stay scruffy; the title is what
gets linted, and the check is blocking.

```
type(scope): subject
```

**Types:** `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`, `style`,
`revert`.

Two that look like types but are not:

- `ux`: user-visible copy or behaviour is `feat`, a broken layout is `fix`, a pure restructure is
  `refactor`.
- `security`: a security fix is still `fix`. Use it as a *scope* instead.

`style` means source formatting, not visual styling. Changing the Compose theme is `feat` or `fix`.

**Scopes** come from the package structure: `wizard`, `dashboard`, `hello`, `settings`, `learn`,
`vpn`, `ssh`, `storage`, `ui`, `mode`, `security`, `navigation`, `di`, `ci`, `build`, `deps`,
`release`. Omit the scope when a change has no single one. Do not write `(all)`.
[`conventional-title.sh`](.github/workflows/conventional-title.sh) is the source of truth; add to
its lists when a genuinely new module appears.

**Subject:** imperative (`add`, not `added`), lowercase, no trailing period, one change per commit.
If you need "and" to describe it, it is probably two pull requests. Title length is capped at 72;
GitHub appends ` (#123)` on squash.

Breaking changes take a `!` (`feat(vpn)!: drop the legacy profile format`) plus a
`BREAKING CHANGE:` footer in the description.

The description becomes the commit body, so write it for someone reading `git log` in a year: the
reason and the consequence, not a restatement of the diff. The
[pull request template](.github/pull_request_template.md) lists what to cover; delete the sections
that don't apply, and put notes meant only for reviewers, such as a stacked PR's merge order, in a
comment rather than the description.

## Pull requests

`master` takes no direct pushes and no force pushes, and the rule applies to admins too. Every
change goes through a pull request that must pass `verify`, `release-build`, `Conventional Commits`
and `Branch name`, and must be up to date with `master` before merging.

That last rule exists because of *semantic conflicts*: two branches touching different files merge
cleanly and still fail to compile. A green check from before the base moved proves nothing, so the
branch is re-checked after it catches up.

## Architecture

Kaihi follows Clean Architecture inside a single Gradle module. Packages stand in for modules, so
`internal` blocks nothing here, and
[`ArchitectureBoundariesTest`](app/src/test/java/app/kaihi/android/architecture/ArchitectureBoundariesTest.kt)
enforces every rule in this section. A change to a rule changes this section and the test together.

### Layers

| Package | Ring | Holds |
| --- | --- | --- |
| `core.<module>` (outside `.internal`, `.di`) | Entities and ports | Data, enum, sealed and value types, exceptions, interfaces, pure stateless code (parsers, config factories, extension functions) |
| `feature.<name>.impl.domain` | Use cases | Coordinators, use cases, feature models, interfaces the feature owns, `SshCommand`s |
| `feature.<name>.impl.ui`, `feature.<name>.impl.data`, `core.<module>.internal` | Adapters | ViewModels and screens; implementations of the interfaces above |
| `core.<module>.di`, `feature.<name>.impl.di`, `feature.<name>.impl.ui.navigation.di`, the app root | Wiring | Hilt modules, `MainActivity`, `KaihiApp` |
| `platform.<module>` | Infrastructure | Dispatcher qualifiers, navigation, the UI kit |

Dependencies point inward: an adapter depends on the ports it implements or calls, a use case on
entities and ports, and nothing inward knows about an adapter. `platform` sits beside the rings:
any layer may use it, and it uses nothing in the app.

### Core module order

```
security ← storage ← mode ← ssh ← vpn
```

A core module may import only itself and modules to its left. A new core module is added to this
line and to `CORE_MODULE_ORDER` in the test in the same change.

### Allowed imports

| Code in | May import from the app | Must not import |
| --- | --- | --- |
| `platform.<module>` | Its own and other `platform` modules, `R`, `BuildConfig` | Anything in `core`, `feature` or the app root |
| `core.<module>` public | Its own module, core modules to its left, `platform.di` | `platform.ui`, `platform.navigation`, `feature`, the app root; framework or I/O code: `android.*`, `androidx.*` (except `androidx.annotation`), sshj, Bouncy Castle, libv2ray, `java.io.File*`, `javax.net`, `java.net.*` except `URI`, `URLEncoder`, `URLDecoder`, `IDN` |
| `core.<module>.internal` | As the public API, plus any library and the Android framework | `feature`, the app root (except `R`, `BuildConfig`) |
| `core.<module>.di` | Its own module, including `.internal` | |
| `feature.<name>.impl.domain` | Its own `domain`, public `core`, `platform.di` | `android.*`, `androidx.*` (except `androidx.annotation`), `platform.ui`, `platform.navigation`, its own `ui` or `data`, other features, the raw key-value store (`AppStorage`, `StorageDomain`, `ExperienceKeys`) |
| `feature.<name>.impl.data` | Its own `domain`, public `core`, `platform`, the Android framework | Its own `ui`, other features |
| `feature.<name>.impl.ui` | Its own `domain`, `platform`; from public `core`: data, enum, value, annotation and sealed types, `data object`s, exceptions, top-level functions and values; another feature's root `*Key`, only from `impl.ui.navigation` | `core` interfaces (other than sealed ones), `object`s, abstract and plain classes; its own `data` |
| The app root | Public `core`, `platform`, feature root `*Key`s | `.internal`, `.impl` |

Rules that apply everywhere:

- Nothing reaches into another module's `core.<module>.internal` or `feature.<name>.impl`.
- `feature.<name>` holds only `*Key` navigation keys at its root, and every `*Key` has a
  `Destination<Key>` under that feature's `.impl`, or navigation fails at runtime instead of at
  build time.
- Only the app root itself imports from the app root, other than `R` and `BuildConfig`.
- `ExperienceKeys` is used only in `core.<module>.internal`: each stored flag has one owner,
  which exposes it through an interface.
- Public `core` objects and top-level declarations hold no `var`.

`core.security.Secret` is a data-layer type. UI state holds `platform.ui.util.UiSecret` instead,
and the conversion happens in the state mappers.

### Deliberate exceptions to strict Clean Architecture

1. **Shared ports.** Strictly, each use case owns the interfaces it calls. Here, features share
   `SshConnection`, `XrayControl` and similar interfaces from `core`, so they aren't copied into
   every feature.
2. **UI reads core types.** Strictly, presentation sees only use-case output models. Here, `ui`
   may render `core` data, enum and sealed types such as `XrayEngineState`, so we don't write a
   mapper for every enum. It may not call `core` behaviour: interfaces and objects stay out of `ui`.
3. **Commands live in the domain.** A wizard `SshCommand` is a pure value, a bash script plus an
   output parser, that runs only through the `SshConnection` interface. A provisioning interface
   with a data-layer implementation would add a forwarding method per command, in the code that
   runs as root, and isolate nothing.
4. **Xray JSON in core.** `core.vpn.configfactory` builds Xray's wire format in the public API. It
   is pure, and the routing and blackhole policy it encodes is business logic.

A change that goes beyond one of these exceptions is a violation, not a new exception.

Never hardcode a dispatcher, inject a `CoroutineDispatcher`. Flows must handle cancellation
without swallowing `CancellationException`.

## Tests

Coverage is not a target. What earns a test is code that fails *silently* (wrong output rather
than a crash) and code whose failure is expensive: wire formats, routing policy, trust decisions,
anything parsing input the app did not author.

Thin adapters over JNI, the Go runtime or the Android framework are deliberately left to
instrumented tests and reading, not wrapped in interfaces for the sake of a number.

Two habits worth keeping:

- **Check that an assertion has teeth.** Break the implementation on purpose and confirm the test
  fails. Watch in particular for assertions that pass on absence: `indexOfFirst` returns `-1`,
  and `-1` compares before every real index, so an ordering check alone survives a deleted rule.
- **Prefer fakes to mocks.** There is no mocking library here on purpose. A loopback `ServerSocket`
  says more about a SOCKS client than a recorded call ever will.

Golden values must come from an outside oracle (fingerprints from `ssh-keygen`, digests from the
upstream release) so a test cannot agree with a bug by restating it.

## Security-sensitive areas

Read the surrounding code before changing any of these:

- **Shell interpolation.** Anything reaching a remote script goes through `ShellHost`, which
  validates at construction. Candidate SNI domains come from certificates controlled by whoever
  shares the VPS's subnet. Treat them as attacker input.
- **Downloaded binaries.** Xray-core and RealiTLScanner are pinned by SHA-256 and verified before
  they are installed or executed. Bump the version and the digest together.
- **Host keys.** Trust-on-first-use: the fingerprint is stored only after authentication succeeds.
  Do not reorder that.
- **Credentials.** SSH passwords and keys are never persisted, only the host fingerprint and the
  connection profile are, encrypted under an Android Keystore key. Keep secrets in `Secret` /
  `UiSecret` so they cannot reach a log through a generated `toString`.

Please report vulnerabilities privately rather than in a public issue.
