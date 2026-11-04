# Kaihi

Android VPN client that provisions the user's own VPS over SSH (installs Xray-core, picks a REALITY
masking domain, writes the service) and then tunnels the phone's traffic through it with VLESS +
REALITY + XHTTP. There is no Kaihi backend. Pre-1.0 and unaudited, built for users on filtered
networks; most code either runs commands as root on someone's server or handles their credentials.

## Stack

- Kotlin 2.4, Jetpack Compose + Material3 (dark theme only), one Gradle module `:app`. minSdk 26,
  targetSdk 36. Versions live in `gradle/libs.versions.toml`.
- Hilt (KSP), Navigation3, kotlinx.serialization, coroutines/Flow, Timber.
- SSH: sshj with Bouncy Castle. Xray-core runs in-process via `libv2ray.aar` (gomobile), downloaded
  at build time and pinned by SHA-256. Native tun2socks is `hev-socks5-tunnel`, a C git submodule
  built with ndk-build.
- Tests: JUnit4, Konsist, kotlinx-coroutines-test. There is no mocking library.
- UI strings exist in English and Russian.

## Structure

All paths below are under `app/src/main/java/app/kaihi/android/` unless stated.

- `core/ssh/`: SSH client, trust-on-first-use host-key pinning, remote command abstractions.
- `core/vpn/`: Xray config factories (`configfactory/`), config models (`spec/`), profile store, and
  the VPN service in the separate `:vpn` process with its IPC to the UI (`internal/`).
- `core/storage/`: `AppStorage`; each `StorageDomain` says whether it is Keystore-encrypted.
- `core/security/`: `Secret`.
- `feature/{hello,wizard,dashboard,settings}/`: the root holds only `*Key` navigation keys; the
  code is under `impl/domain` and `impl/ui`.
- `platform/`: cross-cutting code. `di` (dispatchers), `navigation`, `mode` (normal/advanced),
  `ui` (MVI base classes, theme, icons, shared components).
- `buildSrc/`: tasks that download and verify the pinned third-party binaries.
- `app/src/test/`: JVM unit tests mirroring the main packages.
- `CONTRIBUTING.md`, `SECURITY.md`: the full rules; read them before touching `core.ssh`,
  `core.vpn` or `core.security`.

## Commands

- `./gradlew compileDebugKotlin`: fast loop while iterating.
- `./gradlew testDebugUnitTest`: all unit tests (194 across 39 classes).
- `./gradlew testDebugUnitTest --tests 'app.kaihi.android.core.ssh.HostnameValidationTest'`: one
  test class; `--tests '<class>.<method>'` selects a single test.
- `./gradlew lintDebug`: Android Lint. No formatter or detekt is configured.
- `./gradlew assembleDebug`: needed only when native code or the manifest changed.

Every Android build first runs `downloadLibV2ray`, `downloadGeositeDat` and `verifyGeoipDat`, so the
first build on a clean checkout needs network access. The Gradle daemon requires JDK 21
(`gradle/gradle-daemon-jvm.properties`); sources compile to Java 17 bytecode. There is no verified
command-line way to run the app: use Android Studio's `app` configuration on a device or emulator.

## Conventions

Reference feature to copy: `feature/settings` (Key, Contract, ViewModel, Destination, navigation
module, Screen). Reference test: `StoredAppModeRepositoryTest`.

- Layering is enforced by `app/src/test/.../architecture/ArchitectureBoundariesTest`, so run it
  after moving or adding files. `core.<module>` exposes interfaces and data types, keeps
  implementations in `.internal` and Hilt bindings in `.di`. `domain` imports no Compose,
  `platform.ui` or `platform.navigation`. `ui` never imports a `core.*.internal` package. `core`
  never imports `feature`.
- Screens are MVI: a `*Contract` object with `State`, `Intent`, `Effect`; a `@HiltViewModel`
  extending `MviViewModel`; state changes through `reduce { }`, one-shot events through
  `sendEffect`. Composables read `viewModel.state.collectAsStateWithLifecycle()` and
  `viewModel.effect.CollectAsEffect { }`. Split a large ViewModel into `ViewModelDelegate`s.
- A new screen needs a `@Serializable` `*Key : NavKey` at the feature root, a `Destination<Key>` in
  `impl/ui/navigation`, and a Hilt `@Binds @IntoMap @NavDestination(Key::class)` module. Without the
  binding, navigation crashes at runtime.
- Inject dispatchers with `@IoDispatcher` / `@MainDispatcher`.
- Rethrow `CancellationException` in every `catch (Throwable)`. Core suspend APIs that can fail
  (`SshConnection`, `XrayControl`) return `Result<T>`.
- Hold secrets in `core.security.Secret` (data layer) and `platform.ui.util.UiSecret` (UI state);
  convert in the state mappers, see `WizardStateMappers.kt`. Both mask `toString`.
- Remote scripts are `BashScriptCommand<T>`s run as root via `bash -c`. Interpolate only
  `ShellHost.of(...)` values or compile-time constants. SNI candidates come from certificates
  controlled by strangers on the VPS's subnet, so treat them as attacker input.
- Persisted and IPC-serialized models (`XrayVpnProfile`, `XraySubscription`, `XrayRuntimeConfig`)
  must still decode data written by older versions: give every new field a default.
- Put user-visible text in `res/values/<feature>_strings.xml` and the same keys in
  `res/values-ru/`; state carries `UiText`, not literals.
- Icons are hand-written `ImageVector`s in `platform/ui/icons/sources/`, exposed via `KaihiIcons`.
  Spacing comes from `MaterialTheme.dimens`.
- Log with Timber. Release builds log WARN and above.
- Tests earn their place by guarding code that fails silently or expensively: wire formats, routing
  policy, trust decisions, parsing of input the app did not author. Use fakes and loopback sockets,
  take golden values from an outside oracle, and break the implementation once to confirm the
  assertion fails. Watch for assertions that pass on absence (`indexOfFirst` returning `-1`).
- Branches are `feature|fix|refactor|test|docs|ci/<kebab-case>`. Pull request titles are
  Conventional Commits, `type(scope): subject`, 72 characters at most; the allowed scopes are
  listed in `.github/workflows/conventional-title.sh`.

## Do not

- Edit `app/src/main/cpp/hev-socks5-tunnel/` (submodule) or hand-edit `app/src/main/assets/geoip.dat`.
- Change a pinned version without its digest in the same change: `libv2rayVersion`,
  `geositeVersion`, `geoipDatSha256` in `app/build.gradle.kts`; Xray-core in
  `InstallXrayRuntimeCommand`; RealiTLScanner in `ScanSniCommand`.
- Skip or weaken SHA-256 verification of any downloaded binary.
- Rename or move `core/vpn/internal/TProxyService` or its package: native code binds to it by name
  (`-DPKGNAME` in `app/build.gradle.kts`, keep rule in `app/src/main/keepRules/rules.keep`).
- Persist SSH passwords or keys. Store a host fingerprint only after authentication succeeds.
- Drop the SOCKS credentials or the blackhole rules (`::/0`, UDP/443) from the client Xray config.
- Read, print or commit `keystore.properties`, `local.properties` or any `*.jks`.
- Run `./gradlew regenerateGeoipDat` (maintainer-only, needs Go and network) or
  `./gradlew assembleRelease` (CI's job).
- Add a mocking library, or call `Dispatchers.IO` / `Dispatchers.Main` in non-composable code.
- Edit `AGENTS.md`, `CLAUDE.md` or anything under `.claude/`.

## Definition of done

- `./gradlew testDebugUnitTest lintDebug` passes, including `ArchitectureBoundariesTest`.
- `./gradlew assembleDebug` passes when native code, the manifest, or `app/build.gradle.kts` changed.
- Every new or changed user-facing string exists in both `values/` and `values-ru/`.
- `README.md` and `SECURITY.md` still match the behaviour, if the change touches what they claim.
