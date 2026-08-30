# 44. Master Specification Amendment - Deployment and Trust Model Fixes

This section supersedes/refines earlier sections where the independent review in section 39/42.28 surfaced gaps. It captures four fixes agreed after review.

## 44.1 Re-entrant WAITING_FOR_USER Per Step

Earlier state machines (section 15, section 28, section 42.17) model `WAITING_FOR_USER` as a single global state around `INSTALLING`. In practice, a non-privileged Android app can never silently uninstall another package - `PackageInstaller.uninstall()` always raises a system confirmation dialog, independent of the install confirmation dialog. CLEAN_INSTALL therefore requires two separate user-confirmed checkpoints, not one.

```
CLEAN_INSTALL
UNINSTALLING
  |- WAITING_FOR_USER (uninstall confirm) -> UNINSTALLING
  |- SUCCESS -> INSTALLING
  \- FAILURE -> RECOVERY
INSTALLING
  |- WAITING_FOR_USER (install confirm) -> INSTALLING
  |- SUCCESS -> INSTALLED
  \- FAILURE -> RECOVERY
```

`WAITING_FOR_USER` must carry a `step` field (`UNINSTALL_CONFIRM` | `INSTALL_CONFIRM`) rather than being a bare enum value, so the UI can show the right copy and so persistence/resume logic (section 29, section 42.17) knows which system dialog to re-arm after process death.

Update All (section 23, section 42.21) must expect multiple sequential system dialogs when several pending updates are clean-installs, and the UI copy should set that expectation up front rather than surprise the user mid-run.

## 44.2 Manager Self-Update

The specification previously covered how managed apps update but not how the manager application itself updates, since it is not distributed through Play Store initially.

The manager treats itself as a reserved entry in its own manifest (for example `id: "self"`), using the same artifact/version/signing metadata shape as any other `AppProfile`. It is not exposed in the Apps catalog UI; it surfaces only in Settings as "Check for manager updates."

```
Settings -> Check for manager updates
    v
Fetch + verify manifest self entry
    v
Compare installed manager version vs manifest version
    v
Newer available -> download, verify, install (same PackageInstaller/UPDATE path as any app)
    v
No newer version -> report up to date
```

Because a self-update replaces the running process's own APK, the install step must be triggered from a foreground context that survives the process being killed mid-session; persist the deployment state before commit exactly as section 42.17 already requires for any other deployment, so the manager can detect and report a completed or failed self-update on next launch.

## 44.3 Manifest Signing

Section 42.9 verifies artifact hashes and signing certificates, but the manifest carrying those hashes was itself only protected by HTTPS transport. A compromised publishing credential could rewrite the manifest and its hashes together.

The manifest is distributed with a detached Ed25519 signature (minisign-compatible). The public key ships baked into the manager APK. Before any manifest content is trusted for compatibility, dependency, or artifact decisions, the client verifies the signature against the bundled public key. A manifest that fails signature verification is treated as unavailable, and the client falls back to the last known-good, signature-verified manifest (section 32).

```
Fetch manifest.json + manifest.json.sig
    v
Verify signature against bundled public key
    |- valid   -> use manifest, cache as last-known-good
    \- invalid -> discard, fall back to cached last-known-good manifest
```

The signing private key is generated offline, stored only as a GitHub Actions encrypted secret used at publish time, and is never committed to source control - the same handling already specified for the APK release keystore in section 20.

## 44.4 First-Run Onboarding

The core UX principle (section 3, section 42.1) is that the user should not need to manually navigate Android to get an app installed. Three Android-required steps were previously left implicit rather than being part of the designed flow:

- Grant "install unknown apps" for the manager (required once, per section 20/section 42.9's controlled-signing model).
- Grant the notification permission (Android 13+) so FCM/local notifications in section 24/section 42.22 actually reach the user.
- A plain-language heads-up that Google Play Protect may show a warning when installing a ReVanced-patched YouTube APK, since it is signed by the project's key rather than Google's, and that this is expected and not a sign of a broken build.

```
First launch
    v
Explain: install-unknown-apps grant needed once
    v
System permission flow
    v
Request notification permission
    v
Explain: Play Protect may warn on ReVanced installs - this is expected
    v
Proceed to Home
```

This onboarding sequence runs once, before the Apps catalog is reachable, so these Android-required confirmations are anticipated rather than discovered mid-deployment.

## 44.5 Change Log Addendum

- Added re-entrant WAITING_FOR_USER with an explicit step field for clean-install's two system dialogs.
- Added manager self-update as a reserved manifest entry, surfaced only in Settings.
- Added manifest detached-signature verification (Ed25519/minisign) ahead of HTTPS-only trust.
- Added a first-run onboarding flow covering install-unknown-apps, notification permission, and Play Protect expectation-setting.
