<!-- The title is the squash commit subject and this body is its message, word for word.
     Write for someone reading `git log` in a year. Delete sections that don't apply.
     Stacked PR? Say so in a comment, not here: anything written here lands on master. -->

<!-- Why: what was wrong or missing before, and what is true after this PR. -->

## What

<!-- One bullet per change, bold lead-in. Say what deliberately did not change. -->
- **…**

## Breaking

<!-- Delete if none. Persisted/IPC model breaks need `!` in the title and the footer below.
     Also list effects on servers already deployed or on existing profiles. -->

BREAKING CHANGE: …

## Security

<!-- Delete if not touching core/ssh, core/vpn, core/security, core/storage or a pinned binary.
     Remote-script inputs (ShellHost.of / constants), secrets kept out of logs and toString,
     fingerprint stored only after auth, untrusted input handling, digest source for any pin bump. -->

## Verification

- `./gradlew testDebugUnitTest lintDebug`: N tests, 0 failed; lint clean.
<!-- + `assembleDebug` if native code, the manifest or app/build.gradle.kts changed. -->
- Tests that were broken once to confirm they fail: …
- Manual: <!-- device / emulator / mock VPS / real VPS, and what you saw -->
- Not covered: <!-- what was not tested, and why -->

## Docs and follow-up

<!-- Delete if none. README / SECURITY / AGENTS changes; known limitations;
     anything to do after merge (required checks, redeploy notes, release notes). -->
