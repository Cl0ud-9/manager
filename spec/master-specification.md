# Curated Android Application Manager

End-to-End Product, Architecture & Implementation Specification
Version 1.0 - Engineering Baseline

Converted from the original `.docx` master spec so it can be diffed and reviewed in git. See [amendment-44.md](amendment-44.md) for the fixes folded in after independent review.

## 1. Executive Summary
The product is a curated Android application deployment and update manager. Its purpose is to reduce the manual effort required to discover, download, verify, install, update, configure, and recover supported Android applications.
The defining UX principle is: the user should interact with the manager, not manually operate Android to accomplish the installation. User interaction remains acceptable where Android genuinely requires confirmation; manual navigation and multi-step installation instructions should be abstracted away by the manager.
The initial target is Android 11+ on arm64-v8a devices. The product is initially intended for friends and family, but the architecture should permit public distribution without requiring paid infrastructure at small scale.
## 2. Locked Product Decisions
Catalog: Start with a curated catalog. Do not initially allow arbitrary GitHub repositories.
Android: Minimum Android version is Android 11 (API 30).
Architecture: Initial supported device ABI is arm64-v8a. The artifact model remains extensible to other ABIs later.
Installation backends: No Shizuku or root dependency. Stock Android PackageInstaller is the baseline.
Normal app installation: Use UPDATE first. If the update fails, offer CLEAN_INSTALL to the user.
YouTube ReVanced: Always use CLEAN_INSTALL. The user sees Install rather than Update.
Dependencies: Use a generic dependency model. microG RE is a dependency for applications that require it; it is not globally required.
Updates: Support both app-specific Update/Install and Update All.
Rollback: Keep one previous APK locally when feasible so failed updates can be restored without paid cloud storage.
Notifications: Backend-driven release detection plus FCM notification; on-device periodic fallback.
Backend cost: Design the initial system around GitHub, GitHub Actions, GitHub Releases/static artifacts, and FCM. Avoid a paid server/database.
ReVanced build: Use ReVanced CLI in CI with controlled patch profiles.
Compatibility: Select artifacts by actual Android API level and ABI, not by hardcoded UI labels.
Post-install configuration: Optional, app-specific, version-aware workflows. Prefer build-time configuration and supported mechanisms before UI automation.
## 3. Core User Experience
### 3.1 Normal application update
```
Update available
    ↓
User taps Update
    ↓
Download APK
    ↓
Verify artifact
    ↓
Attempt in-place update
    ↓
Success → Done
    ↓
Failure → Offer Clean Install
    ↓
User confirms
    ↓
Manager performs uninstall + install
    ↓
Optional post-install workflow
    ↓
Done
```
### 3.2 YouTube ReVanced installation/update
```
New artifact available
    ↓
User taps Install
    ↓
Resolve microG dependency
    ↓
Select compatible APK for device
    ↓
Download
    ↓
Verify
    ↓
Preserve previous APK for rollback
    ↓
Uninstall existing ReVanced
    ↓
Install new APK
    ↓
Run optional post-install workflow
    ↓
Verify installation
    ↓
Done
```
The manager must not ask the user to manually find an APK, uninstall an application through Settings, browse to a file manager, or manually navigate through a multi-step setup unless the Android/application security model genuinely leaves no supported automation route.
## 4. High-Level Architecture
```
                     GitHub / Upstream Releases
                               │
                               ▼
                       GitHub Actions / CI
                    ┌──────────┼──────────┐
                    │          │          │
                 Detect     ReVanced   Manifest
                releases     builds    generation
                    │          │          │
                    └──────────┼──────────┘
                               ▼
                       GitHub Releases /
                        Static Artifacts
                               │
                         FCM + client fetch
                               │
                               ▼
                  ┌─────────────────────────┐
                  │     Android Manager     │
                  │                         │
                  │ Material 3 UI           │
                  │ Catalog                 │
                  │ Update Engine            │
                  │ Dependency Engine       │
                  │ Compatibility Engine    │
                  │ Download/Verify          │
                  │ Installation Engine      │
                  │ Workflow Engine          │
                  │ Rollback                │
                  └─────────────────────────┘
```
## 5. Android Client Architecture
Language: Kotlin.
UI: Jetpack Compose with Material 3.
Architecture: layered architecture with clear separation between UI, domain/use cases, data/network, installation, workflow, and persistence.
Use WorkManager for periodic fallback work.
Use Android PackageInstaller for package installation.
Use FCM for timely push notifications.
Use Android package metadata and supported ABI information to determine the installed package and device compatibility.
Persist installation/update state so workflows can resume after user confirmation or process death.
## 6. Suggested Android Modules / Packages
```
app/
  ui/
    home/
    apps/
    updates/
    details/
    downloads/
    settings/
  domain/
    model/
    repository/
    usecase/
    installer/
    workflow/
    dependency/
  data/
    manifest/
    github/
    downloads/
    local/
    notifications/
  platform/
    packageinstaller/
    packageinfo/
    notifications/
    workmanager/
  security/
    hash/
    apk/
    manifest/
  worker/
    updatecheck/
    download/
    cleanup/
```
## 7. Curated Catalog Model
Each supported application is represented by a profile. The Android client should not contain app-specific logic scattered throughout the codebase. App-specific behavior should be data-driven through the catalog/manifest wherever practical.
```
AppProfile
  id
  displayName
  packageName
  source
  dependencies[]
  artifacts[]
  installationMode
  postInstallWorkflow
  rollbackPolicy
  releaseNotes
  enabled
```

## 8. Source Types
GitHub release source: for applications whose official release artifacts are published on GitHub.
Managed/custom source: for artifacts produced by the project's own CI pipeline, such as the ReVanced channel.
The Android client should consume the project's central manifest rather than independently polling every GitHub repository.
## 9. Central Manifest
The central manifest is the Android client's authoritative catalog of available artifacts and deployment metadata.
```json
{
  "schemaVersion": 1,
  "generatedAt": "...",
  "apps": [
    {
      "id": "youtube-revanced",
      "packageName": "...",
      "dependencies": [
        { "id": "microg-re", "required": true }
      ],
      "installation": {
        "mode": "CLEAN_INSTALL"
      },
      "postInstallWorkflow": "youtube-revanced-setup",
      "artifacts": [
        {
          "versionName": "...",
          "versionCode": 123,
          "minSdk": 30,
          "maxSdk": 30,
          "abis": ["arm64-v8a"],
          "downloadUrl": "...",
          "sha256": "...",
          "certificateSha256": "..."
        },
        {
          "versionName": "...",
          "versionCode": 123,
          "minSdk": 31,
          "maxSdk": 999,
          "abis": ["arm64-v8a"],
          "downloadUrl": "...",
          "sha256": "...",
          "certificateSha256": "..."
        }
      ]
    }
  ]
}
```
## 10. GitHub Release Ingestion
A CI process periodically checks tracked repositories for new releases.
For each repository, identify the appropriate APK asset using explicit catalog rules rather than guessing based solely on filename.
Validate that the artifact is actually an APK and that its package/signing metadata match expectations.
Generate or update the central manifest.
Publish release metadata/artifacts.
Trigger FCM notification when a new supported artifact becomes available.
A backend/CI polling interval of approximately 15 minutes is the target for release detection. This is a detection target, not a guarantee of end-to-end delivery time. FCM provides the notification path, while the client uses a periodic WorkManager fallback. WorkManager periodic work has a 15-minute minimum but is intentionally inexact because of battery optimization and Doze behavior.
## 11. ReVanced Build Pipeline
ReVanced CLI is appropriate for the managed build pipeline. Its current documentation supports patching APKs, selecting or disabling patches, exclusive patch selection, and patch-specific options.
```
Upstream YouTube APK
        ↓
Compatibility/source validation
        ↓
Select ReVanced patch profile
        ↓
Run ReVanced CLI
        ↓
Sign output
        ↓
APK validation
        ↓
Artifact metadata generation
        ↓
Publish
        ↓
Manifest update
        ↓
FCM notification
```
## 12. ReVanced Patch Profiles
Patch configuration is controlled by the project rather than relying blindly on the default patch set. ReVanced CLI supports enabling/disabling patches and setting patch options.
```
revanced/
  profiles/
    youtube-modern/
      patches/configuration
    youtube-legacy/
      patches/configuration
```
Modern profile: intended for Android 12+ and may include the selected Material You patch if testing confirms compatibility.
Legacy profile: intended for Android 11 and excludes the patch(es) that have been verified to cause compatibility problems.
The Android manager does not need to know why a patch was removed. It only needs artifact compatibility metadata.
Patch profiles must be versioned so a future ReVanced patch-set change can be reproduced and audited.
## 13. Android Compatibility and Artifact Selection
The system should select an artifact based on API level and ABI. Do not encode assumptions such as 'Android 11 means legacy' inside the client beyond the artifact metadata supplied by the manifest.
```
Device
  ├── SDK_INT
  └── supported ABIs
        ↓
Filter artifacts
  ├── minSdk <= SDK
  ├── maxSdk >= SDK
  └── ABI supported
        ↓
Choose highest compatible version
```

V1 device ABI: arm64-v8a.
The schema should keep ABI as extensible metadata so armeabi-v7a, x86_64, x86, or future ABIs can be added without redesigning the data model.
The initial build/test matrix should focus on Android 11+ arm64-v8a devices.
## 14. microG Dependency Management
microG is not a global prerequisite. It is represented as a per-application dependency.
```
YouTube ReVanced
  dependencies:
    microg-re (required)
```

```
LTECleaner
  dependencies:
    none
```

Before installation, resolve all required dependencies. Check whether the dependency package is installed. If missing, offer installation through the manager.
After dependency installation, continue the parent application's workflow.
Support dependency version constraints later if an application requires a minimum/maximum compatible dependency version.
## 15. Installation Engine
The stock Android path is the baseline. PackageInstaller is the Android platform mechanism used to manage package installation sessions. It can expose user-action requirements when Android requires user involvement.
The installation engine must model user-action-required states explicitly rather than treating them as failures.
```
INSTALL_REQUESTED
  ↓
DOWNLOADING
  ↓
VERIFYING
  ↓
INSTALLING
  ├── SUCCESS → INSTALLED
  ├── USER_ACTION_REQUIRED → WAITING_FOR_USER
  │                              ↓
  │                         USER_CONFIRMED
  │                              ↓
  │                         INSTALLING
  └── FAILURE → INSTALL_FAILED
```
## 16. Installation Modes
UPDATE: normal applications. Attempt an in-place update first.
CLEAN_INSTALL: uninstall the existing package and install the new APK. YouTube ReVanced uses this mode by design.
For a normal application whose UPDATE attempt fails, offer CLEAN_INSTALL as an explicit fallback with a warning about loss of application data.
The user should not choose an implementation strategy. The app profile determines it. The UI should expose 'Update' or 'Install' based on the application behavior rather than exposing technical installation terminology.
## 17. Normal Update Failure Flow
```
Attempt UPDATE
    ↓
Failure
    ↓
Show:
  'The application could not be updated normally.
   A clean installation can be attempted.
   This may remove local application data.'
    ↓
User chooses Clean Install
    ↓
Preserve previous APK where possible
    ↓
Uninstall
    ↓
Install new APK
    ↓
Post-install workflow if any
    ↓
Success / recovery
```
## 18. YouTube ReVanced Clean Install
```
Resolve microG dependency
        ↓
Select device-compatible artifact
        ↓
Download
        ↓
Verify hash + certificate
        ↓
Preserve installed APK for rollback
        ↓
Uninstall existing ReVanced
        ↓
Install new APK
        ↓
Handle Android-required confirmation
        ↓
Run configuration workflow
        ↓
Verify
        ↓
Complete
```
## 19. APK Verification and Security
Verify HTTPS transport.
Verify SHA-256 against the manifest before installation.
Verify expected package name.
Verify expected signing certificate/digest where applicable.
Reject artifacts whose package/signing identity does not match catalog expectations.
Do not install an artifact solely because it is reachable at a download URL.
Treat the central manifest and release pipeline as security-sensitive infrastructure.
For ReVanced patch inputs, the current CLI also has signature and build-provenance verification mechanisms for patch artifacts; the CI should use the strongest verification supported by the chosen toolchain rather than routinely bypassing it.
## 20. APK Signing Strategy
Third-party APKs: do not re-sign them. Preserve the original developer signature.
Project-produced ReVanced APKs: sign using a dedicated release keystore controlled by the project.
The release keystore must never be committed to source control.
Store signing material as CI secrets or equivalent protected credentials.
Maintain an offline backup of the release keystore and credentials.
Never print passwords or signing material in CI logs.
Use the same release key for future versions of the project's own ReVanced distribution so the signing identity remains stable.
## 21. Rollback
Rollback is designed to remain free of cloud storage costs.
```
Before replacing current APK:
    current.apk → local rollback storage
```

```
Install new APK
    ↓
Success → retain rollback copy
Failure → attempt restoration
```
Initial retention policy: one previous APK per supported app.
Rollback artifacts are stored locally on the device.
Rollback should be attempted only when the previous APK is still compatible with the installed state.
The manager should report when rollback is impossible rather than claiming success.
## 22. Post-Install Workflow Engine
A workflow is optional, application-specific, and version-aware. Applications without configuration have no workflow.
```
AppProfile
  postInstallWorkflow:
    null
    OR
    workflow-id
```

```
Workflow
  id
  compatible app versions
  steps[]
  failure behavior
  verification
```
For ReVanced/GmsCore configuration, the desired conceptual flow may include launching YouTube, navigating to ReVanced/GMS settings, changing settings such as device registration/cloud messaging, and restarting. The implementation must prefer build-time configuration, supported intents/configuration APIs, or other stable mechanisms before relying on Accessibility UI automation.
Do not hardcode YouTube UI coordinates.
Do not assume UI labels are stable across versions/locales.
Version workflows independently so a future app UI change does not require a manager architecture rewrite.
If automation is not technically possible, present a precise user action and provide a direct entry point where possible.
A workflow must report success/failure and never silently claim configuration succeeded.
## 23. Update All Orchestration
```
User taps Update All
        ↓
Resolve pending updates
        ↓
Resolve dependencies
        ↓
Select compatible artifacts
        ↓
Download/verify required artifacts
        ↓
Determine safe installation order
        ↓
Install sequentially
        ↓
Run post-install workflows
        ↓
Record results
        ↓
Show summary
```

Do not install all applications concurrently. Dependencies must be installed before dependents.
A failure in one application should not unnecessarily prevent unrelated updates.
The final screen should clearly show success, skipped, failed, and clean-install-required items.
## 24. Notification Strategy
CI/backend detects new releases.
Manifest is updated.
FCM notification is sent for relevant newly available artifacts.
The client validates the manifest again before presenting the update.
WorkManager periodically checks the manifest as a fallback for missed notifications.
Notifications should deep-link into the relevant application/update screen.
The client should not depend on FCM as the sole source of truth; FCM is a wake/notification mechanism, while the manifest remains authoritative.
## 25. Backend and Zero-Cost Strategy
Use GitHub repositories for source/configuration.
Use GitHub Actions for scheduled release detection and ReVanced builds.
Use GitHub Releases or another free static artifact mechanism available to the project for APK artifacts.
Use a static manifest rather than maintaining a paid database/API initially.
Use Firebase Cloud Messaging for push notifications.
Avoid a VPS, managed database, Redis, queue system, or paid object storage in V1.
Keep the architecture modular so a dedicated backend can be added later if scale or operational requirements justify it.
## 26. CI/CD Responsibilities
```
Scheduled CI
  ↓
Check tracked repositories
  ↓
Detect new releases
  ↓
For ReVanced:
  ├── obtain supported source APK
  ├── select patch profile
  ├── run CLI
  ├── sign
  ├── validate
  └── publish
  ↓
Update manifest
  ↓
Publish manifest/artifacts
  ↓
Send notification
```
Build logs must contain enough information to diagnose failures without exposing secrets.
Each generated artifact should record source version, patch profile/version, CLI version, signing identity digest, target API range, ABI, and SHA-256.
A failed ReVanced build must not replace the last known-good artifact.
The manifest should only advertise artifacts that passed validation.
## 27. Versioning and Release Metadata
Every artifact should have enough metadata to make the release reproducible and diagnosable.
```
ArtifactMetadata
  appId
  packageName
  versionName
  versionCode
  sourceVersion
  buildProfile
  buildToolVersion
  minSdk
  maxSdk
  abis[]
  sha256
  certificateSha256
  releaseDate
  releaseNotes
  workflowVersion
  dependency constraints
```
## 28. State Machine and Recovery
```
IDLE
 ↓
UPDATE_AVAILABLE
 ↓
PREPARING
 ↓
DOWNLOADING
 ↓
VERIFYING
 ↓
READY_TO_INSTALL
 ↓
INSTALLING
 ├─ USER_ACTION_REQUIRED → WAITING_FOR_USER → INSTALLING
 ├─ SUCCESS → POST_INSTALL
 └─ FAILURE → RECOVERY
                      ├─ UPDATE retry
                      ├─ CLEAN_INSTALL offer
                      └─ ROLLBACK
 ↓
VERIFYING_INSTALLATION
 ↓
COMPLETED
```
## 29. Persistence Requirements
Persist installed catalog state and last-known manifest version.
Persist active installation/workflow state so Android process death does not lose the workflow.
Persist download metadata and verification results.
Persist rollback artifact references.
Persist workflow version used for a deployment.
Persist failure diagnostics suitable for an in-app support screen.
## 30. UI Structure
Home: overall status, updates available, recent activity.
Apps: curated application catalog.
Updates: all pending updates with individual actions and Update All.
App Details: installed version, available version, compatibility, dependencies, release notes, installation action.
Downloads/Activity: current and historical deployment status.
Settings: update-check preferences, notification preferences, storage management, diagnostic information.
The UI should not expose implementation details such as PackageInstaller sessions, manifest internals, or workflow engine terminology to ordinary users.
## 31. Material 3 Design Principles
Use Material 3 components and dynamic/adaptive layouts.
Prioritize a clear update status hierarchy.
Use prominent primary actions for Install/Update.
Show technical detail progressively rather than overwhelming the main screen.
Use clear states: available, downloading, verifying, waiting for user, installing, configuring, completed, failed.
Make destructive clean-install operations explicit and confirmable.
## 32. Error Handling Requirements
No network: show retry and retain pending update state.
Manifest unavailable: use last known manifest if safe; never fabricate availability.
APK hash mismatch: reject and delete/quarantine artifact.
Certificate mismatch: reject and report a security failure.
Insufficient storage: report required/free space and stop safely.
PackageInstaller user action required: pause and resume after user action.
Normal update failure: offer clean installation.
Clean install failure: attempt rollback where feasible.
Workflow failure: report exactly which step failed and do not claim completion.
ReVanced build failure: keep previous known-good release advertised.
## 33. Testing Strategy
Unit tests for manifest parsing, compatibility selection, dependency resolution, version comparison, and installation-state transitions.
Instrumented tests for package detection, storage, notification deep links, and PackageInstaller integration where feasible.
Device matrix: Android 11, 12, 13, 14, 15+ on representative arm64-v8a devices.
Test normal update success.
Test normal update failure followed by clean install.
Test YouTube clean install.
Test missing microG dependency.
Test incompatible Android artifact selection.
Test incompatible ABI selection.
Test corrupted APK/hash mismatch.
Test certificate mismatch.
Test process death during download/install/workflow.
Test user cancellation and resumption.
Test rollback after failed deployment.
Test Update All with dependency ordering.
Test FCM notification plus missed-notification fallback.
## 34. Operational Safety
Never automatically advertise a newly built artifact before validation.
Keep the last known-good release available.
Support disabling/withdrawing a broken release through the manifest.
Do not automatically downgrade unless explicitly designed and supported.
Keep build provenance and release metadata.
Monitor CI failures and artifact generation failures.
Document how to revoke/replace compromised signing or manifest credentials.
## 35. ReVanced-Specific Operational Notes
The current ReVanced CLI is a command-line application using ReVanced Patcher and supports patch selection, patch options, installation/uninstallation via supported tooling, and verification of patch bundle signatures/provenance.
The project should not assume that a patch name, option, compatible YouTube version, or patch bundle remains stable forever. CI should explicitly pin and record tool/patch versions, validate builds, and fail closed when compatibility changes.
The distribution model for compiled patched YouTube APKs should be reviewed separately for applicable licensing, trademark, platform-policy, and takedown risks before public distribution. The technical architecture should avoid making the entire manager dependent on one fragile artifact URL.
## 36. Implementation Phases
Phase 1 - Android shell: Kotlin, Compose, Material 3, navigation, app catalog, local persistence.
Phase 2 - Manifest and GitHub release ingestion: static manifest schema, curated app definitions, version detection.
Phase 3 - Download and verification: download manager, SHA-256, package/signature validation.
Phase 4 - PackageInstaller: normal UPDATE flow, user-action handling, installation state persistence.
Phase 5 - Clean-install fallback: uninstall/install orchestration, warnings, rollback APK capture.
Phase 6 - Dependency engine: microG dependency and generic dependency model.
Phase 7 - Update All: dependency resolution, ordering, sequential deployment, result summary.
Phase 8 - ReVanced CI: CLI integration, patch profiles, Android 11/12+ artifacts, signing, validation, publishing.
Phase 9 - FCM and WorkManager fallback: notification delivery and periodic safety checks.
Phase 10 - Workflow engine: versioned app-specific post-install workflows; start with the safest non-UI mechanisms.
Phase 11 - Rollback and recovery hardening.
Phase 12 - Device-matrix testing and release hardening.
Phase 13 - Optional public distribution after friend/family validation.
## 37. Definition of Done for V1
A user can install the manager on Android 11+ arm64-v8a.
The manager displays the curated supported application catalog.
The manager detects available releases through the central manifest.
The user can install/update an application without manually navigating through file managers or Settings.
Android-required confirmations are handled as resumable user-action states.
Normal applications attempt UPDATE first.
Failed normal updates can offer CLEAN_INSTALL.
YouTube ReVanced uses CLEAN_INSTALL by default.
microG is resolved only for applications that declare it as a dependency.
The manager chooses the correct Android/ABI artifact automatically.
ReVanced builds are generated by CI using controlled patch profiles.
ReVanced Android 11 and Android 12+ profiles can produce different artifacts.
Artifacts are hash-verified and certificate-checked.
At least one previous APK can be retained locally for rollback.
Update All works with dependency ordering.
FCM notifications work and WorkManager provides fallback checks.
Post-install workflows are optional and can be versioned per application.
A broken release can be withdrawn without shipping a new manager APK.
The initial infrastructure can operate without paid servers.
## 38. Decisions Intentionally Deferred
Adding non-arm64 ABIs.
Allowing arbitrary user-added repositories.
Paid backend/cloud infrastructure.
Large-scale public distribution operations.
Advanced accessibility automation.
Automatic downgrade policies.
Multiple-version rollback retention.
Additional application sources beyond GitHub and managed artifacts.
Advanced analytics/telemetry.
## 39. Independent AI Review Questions
This document is intended to be given to another AI/software architect for critique. Ask the reviewer to challenge the following specifically:
Is PackageInstaller sufficient for the required stock-Android installation UX, including uninstall/install and resumable user-action flows?
Are there Android-version-specific restrictions that invalidate any part of the proposed clean-install or update workflow?
Is the manifest schema sufficient for ABI, API-level, package, signing, dependency, workflow, and rollback metadata?
Is the proposed GitHub Actions + GitHub Releases + FCM zero-cost architecture robust enough for small public distribution?
Are the ReVanced CLI build profiles reproducible and maintainable?
Is the signing strategy safe, and what additional key-management controls should be added?
What are the safest ways to implement the described ReVanced/GmsCore post-install configuration without fragile UI automation?
What should be tested on Android 11 through current Android versions?
What failure modes are missing from the state machine?
What legal, licensing, platform-policy, or distribution risks need to be addressed before public release?
## 40. Primary Technical References
ReVanced CLI documentation confirms patch selection, disabling/enabling patches, exclusive selection, patch-specific options, and artifact installation utilities.
Android PackageInstaller is the platform installation API and includes user-action/status mechanisms relevant to the manager's resumable installation flow.
Android WorkManager periodic work is intentionally inexact and subject to battery optimization/Doze; the minimum periodic interval is 15 minutes.
## 41. Final Product Definition
The final product is a curated, Material 3 Android 11+ application manager that turns application distribution into managed deployment workflows. The user chooses an application and taps Install or Update. The manager resolves dependencies, selects the correct artifact, downloads and verifies it, performs the appropriate update or clean-install strategy, handles Android-required user actions, executes optional application-specific configuration workflows, verifies the result, and retains a local rollback artifact.
The first supported device ABI is arm64-v8a. The infrastructure is designed around free GitHub/CI/static-artifact services plus FCM, with no paid backend required for the initial scale. ReVanced is treated as a managed build channel produced by ReVanced CLI in CI with explicit patch profiles and Android-version-specific artifacts.
The architecture deliberately separates generic deployment mechanics from application-specific recipes. This is the core extensibility mechanism: adding another application should primarily mean adding its catalog profile, artifacts/source rules, dependencies, installation mode, and optional workflow rather than modifying the core installer.

## 42. Master Specification Amendments - Final Product Decisions
This section supersedes or refines earlier sections where later product decisions were made. It is intentionally explicit so that an independent AI/engineering reviewer can distinguish the final baseline from earlier exploratory options.
### 42.1 Final Product Philosophy
The manager is a deployment system, not merely an APK downloader. A user should normally perform one high-level action such as Install or Update, while the manager resolves compatibility, dependencies, downloads, verification, installation, recovery, and optional configuration underneath.
The system should minimize user navigation. Android-required confirmation is acceptable; asking users to manually find files, uninstall applications through Settings, or follow long installation instructions is not the desired UX.
### 42.2 Final Release Model
```
Upstream release / curated build
        ↓
Automated build + validation
        ↓
Publish
        ↓
Everyone receives the latest release
        ↓
Problem discovered?
   ├── No → continue
   └── Yes → withdraw release
                  ↓
             users who have it
             can roll back
                  ↓
             corrected build
                  ↓
                publish
No percentage-based staged rollout.
No separate 'known-good' release concept.
No mandatory pre-release/canary program.
```
The latest published release is offered to everyone as soon as it is available and has passed the project's automated validation.
Retain a stable pointer because it provides an explicit fallback/reference version, but do not introduce a complex release taxonomy.
Support a withdrawn state so the maintainer can immediately stop offering a problematic artifact without shipping a new manager APK.
If automated upstream patching produces a problematic build, the maintainer may adjust the patch configuration manually, build a corrected artifact, validate it, and publish it to all users.
Custom/curated builds must retain provenance metadata identifying the upstream source version and the project's patch/build profile.
### 42.3 Final Release Metadata
latest
stable
withdrawn[]

```
artifact:
  sourceVersion
  buildProfile
  patchProfile
  workflowVersion
  minSdk
  maxSdk
  abis[]
  sha256
  certificateSha256
```
The stable pointer is the explicit fallback/reference release. It is not a second distribution track and does not imply staged rollout.
### 42.4 Automatic Download Policy
Background downloading is supported. Background installation is not automatic.
```
New release detected
        ↓
FCM notification
        ↓
Automatic downloads enabled?
      /          \
    YES           NO
     ↓             ↓
Background       Wait for
download         user action
     ↓
Verify
     ↓
Ready to install
     ↓
User taps Install/Update
     ↓
Android-required confirmation
     ↓
Installation
```

Default: automatic downloads ON. User setting: Automatic downloads ON/OFF.
When automatic downloads are disabled, the manager still notifies the user that an update is available, but does not download the APK until the user requests it.
There is no automatic installation. Installation always begins from an explicit user action.
Downloads must be resumable and survive normal app lifecycle interruptions.
### 42.5 Background and Resumable Download Requirements
Downloads must continue when the user leaves the manager UI, subject to Android background-execution constraints.
Use persistent background work and a download implementation capable of resuming partial files.
Persist download state and byte progress.
Handle network loss without restarting from zero.
Verify the completed artifact before marking it ready for installation.
A completed, verified APK may remain locally cached until it is installed, replaced, withdrawn, or removed by storage management policy.
```
QUEUED
  ↓
DOWNLOADING
  ├── network lost → INTERRUPTED
  │                    ↓
  │                 RESUMING
  │                    ↓
  └────────────────────┘
  ↓
COMPLETED
  ↓
VERIFIED
  ↓
READY_TO_INSTALL
  ↓
INSTALLED / CLEANED
```
### 42.6 Storage Preflight
Before beginning a background download or installation, estimate required storage.
Account for APK size, partial download, temporary installation space, rollback copy, and safety margin.
If storage is insufficient, do not begin the operation.
Show the user the estimated requirement and available space.
Storage preflight is a V1 requirement.
### 42.7 Network Handling
Do not introduce complicated network-policy controls in V1.
If an update is large and the user is on mobile data, provide a clear warning rather than silently blocking the operation.
The user can choose whether to proceed.
Network loss during a download pauses/interupts the download and allows it to resume later.
### 42.8 ABI Strategy
V1 supports arm64-v8a.
The artifact schema remains extensible to armeabi-v7a, x86_64, x86, riscv64, or other future ABIs if required.
Do not build/test additional ABIs until a real product requirement exists.
The client determines the device ABI using Android's supported ABI information and chooses a compatible artifact automatically.
### 42.9 Security Verification - Final
Every managed artifact should pass two distinct checks before installation.
1. **SHA-256** - confirms the downloaded bytes match the artifact advertised by the manifest.

2. **APK signing certificate** - confirms the package is signed by the expected signing identity.

Package name must also match the catalog entry. A hash mismatch is a hard failure. A certificate mismatch is a hard security failure.
The manager must not offer a 'continue anyway' bypass for these checks.
For third-party APKs, preserve and verify the developer's original signing identity; do not re-sign.
For project-produced ReVanced APKs, use the project's controlled release signing key consistently across releases.
ReVanced CLI supports APK signing through keystore-related options; it should not be assumed that a generic ReVanced signing identity automatically represents the project's distribution. The project must explicitly control and record its signing identity.
### 42.10 ReVanced Build and Signing Model
```
Source YouTube APK
       ↓
ReVanced CLI
       ↓
Patch profile
       ↓
Patched APK
       ↓
Project release signing
       ↓
Validation
       ↓
Publish
```
Use explicit patch profiles rather than relying blindly on defaults.
Support separate modern and legacy profiles.
The Android 11 profile may omit the Material You patch if testing confirms that this is the compatibility fix for the selected patch set.
The Android 12+ profile may include Material You if testing confirms compatibility.
Record ReVanced CLI version, patch bundle/version, source version, patch profile, and artifact metadata for reproducibility.
If a manually adjusted build is required, record the exact configuration used.
### 42.11 Dependency Engine - Final
Dependencies are generic. microG RE is simply one dependency used by applications that require it.
```
Application
   ↓
dependencies[]
   ↓
Resolve dependency graph
   ↓
Check installed versions
   ↓
Install/update dependencies first
   ↓
Install/update dependent application
```

Do not assume all applications require microG.
Dependency checks should consider package presence and, where declared, compatible dependency versions.
Update All must topologically order installations so dependencies are updated before dependents.
If microG requires an update and YouTube ReVanced depends on it, microG is processed first.
### 42.12 Installation Modes - Final
```
Normal application:
  UPDATE
    ↓ failure
  Offer CLEAN_INSTALL
```

```
YouTube ReVanced:
  CLEAN_INSTALL only
```
The UI should not expose technical implementation details unless useful to the user.
For normal applications, an update failure should produce an actionable clean-install option.
For clean installation, preserve the previous APK for rollback whenever technically possible before uninstalling the existing package.
The user must explicitly confirm destructive clean-install behavior.
### 42.13 Rollback - Final
Retain one previous APK locally per application initially.
Rollback is a recovery mechanism, not a cloud backup system.
Before replacing/uninstalling the current package, capture or preserve the previous APK where feasible.
If the new deployment fails, offer rollback when a compatible previous APK exists.
Do not promise restoration of application data after a clean uninstall; rollback preserves the APK, not necessarily the application's data.
The maintainer may also point the stable release at the previous version if a published release needs to be withdrawn.
### 42.14 Post-Install Workflow - Final
Post-install configuration is optional and application-specific. Some applications will have no workflow.
```
App
 ├── postInstallWorkflow = null
 └── postInstallWorkflow = workflow-id
```

Workflows are version-aware.
A workflow can change independently when the target application's UI or settings change.
Prefer build-time configuration, supported intents/deep links, and supported application configuration APIs.
Use Accessibility automation only where genuinely necessary and after confirming there is no more stable supported mechanism.
Never rely on fixed screen coordinates.
The workflow must persist progress and resume after user confirmation/process interruption.
If automation is not possible, provide a precise user-guided action and a direct entry point where possible.
For a no-configuration app, installation completes after package installation succeeds.
### 42.15 Installation Success Criteria
For a normal application: package installation success is sufficient for V1.
For an application with a required post-install workflow: package installation plus successful completion of the required workflow is the completion condition.
Do not build a generic runtime 'health monitoring' system in V1.
For YouTube ReVanced, the workflow should verify the configuration steps it actually controls rather than attempting to prove that every aspect of YouTube is healthy.
### 42.16 Deployment Plan Architecture
Introduce a DeploymentPlan as the central domain object. The UI requests a deployment; the domain layer first computes a complete plan, then the executor performs it.
```
DeploymentPlan
  app
  targetArtifact
  deviceCompatibility
  dependencies
  installationStrategy
  preflightRequirements
  verificationRequirements
  rollbackAvailability
  workflow
  orderedSteps
```

```
Catalog
   ↓
Compatibility Engine
   ↓
Dependency Resolver
   ↓
Deployment Planner
   ↓
Preflight
   ↓
Executor
   ↓
Verifier
   ↓
Recovery / Rollback
```
This separation keeps the UI independent of PackageInstaller and prevents application-specific logic from being scattered through the installer.
### 42.17 Deployment State Persistence
Persist deployment state so an interrupted operation can resume.
Model WAITING_FOR_USER as a normal state, not an error.
Persist current step, artifact, download progress, verification result, workflow step, and rollback information.
The application must reconstruct the current deployment after process death.
```
IDLE
 ↓
PREPARING
 ↓
PREFLIGHT
 ↓
DOWNLOADING
 ↓
VERIFYING
 ↓
READY_TO_INSTALL
 ↓
INSTALLING
 ├── WAITING_FOR_USER → INSTALLING
 ├── SUCCESS → POST_INSTALL
 └── FAILURE → RECOVERY
                      ├── retry
                      ├── clean install
                      └── rollback
 ↓
COMPLETED
```
### 42.18 Diagnostics
Keep lightweight local deployment IDs for troubleshooting.
Provide an in-app diagnostic report that can be copied/shared by the user.
Include manager version, Android version, ABI, relevant app/package state, manifest version, last successful deployment, last attempted deployment, and failure state.
Do not introduce analytics or behavioral tracking in V1.
### 42.19 App Support Status
Support explicit statuses such as SUPPORTED, BETA/TESTING, DEPRECATED, and TEMPORARILY_UNAVAILABLE where useful.
If an app cannot currently be built or supported, explain why instead of silently showing no update.
Compatibility explanations should identify Android API, ABI, dependency, or artifact constraints when those are the reason.
### 42.20 Version Controls
Users may skip a specific version per application.
The manager should offer a later version if one becomes available.
The manifest retains latest, stable, and withdrawn concepts.
A withdrawn version must never be offered for new installation/update.
The manager should show release notes directly rather than forcing users to visit GitHub.
### 42.21 Update All - Final
```
Update All
   ↓
Find pending updates
   ↓
Resolve dependencies
   ↓
Select compatible artifacts
   ↓
Storage/network preflight
   ↓
Download/verify
   ↓
Dependency-first ordering
   ↓
Install sequentially
   ↓
Run workflows
   ↓
Show per-app result summary
```

Do not install unrelated applications concurrently.
A failure in one application should not automatically cancel unrelated deployments.
The final summary must distinguish completed, skipped, waiting for user, failed, and rolled-back items.
### 42.22 Notification Architecture - Final
FCM is the fast notification path.
The manifest is the source of truth; FCM payloads should not be trusted as the complete release definition.
When an update notification arrives, the client fetches/validates current manifest data before presenting the final deployment action.
Use WorkManager as a periodic fallback check because push delivery can be missed.
Do not promise exact client polling intervals because Android background execution is intentionally inexact.
Notification channels should separate Updates, Installation, Security, and Failures.
Use user-visible high-priority notifications only where appropriate.
### 42.23 Client Update Checking Policy
```
Backend / CI:
  target release-detection cadence ≈ 15 minutes
```

```
Client:
  FCM = primary
  WorkManager = fallback
  automatic download = user-controlled
  installation = always user-triggered
```
The 15-minute value applies to the project's release-detection process, not as a guarantee that Android clients will poll every 15 minutes.
### 42.24 UI / Design Direction - Final
The application should use Kotlin + Jetpack Compose with Material 3 Expressive as the design foundation. Do not build the entire UI around pure glassmorphism.
Material 3 Expressive provides the component, color, typography, shape, and motion foundation.
Use dynamic color where appropriate.
Use a restrained brand accent rather than excessive saturated color.
Use glass/translucent effects selectively for premium surfaces such as a navigation bar, headers, or special status cards.
Prioritize readability, accessibility, performance, and hierarchy over decorative effects.
Use smooth spring-based motion and state transitions.
Use animated progress/state transitions during deployments.
Use clear states: available, downloading, verifying, ready, installing, waiting for user, configuring, completed, failed.
Keep navigation simple: Home, Apps, Updates, Settings is a suitable baseline.
```
Make Install/Update the dominant actions.
Use an animated deployment timeline to communicate Download → Verify → Install → Configure → Complete.
```
The intended aesthetic is premium, minimal, modern, colorful, smooth, and Pixel/Google-quality rather than visually overloaded.
### 42.25 Final V1 Scope
Android 11+.
arm64-v8a.
Curated application catalog.
GitHub release ingestion.
Managed ReVanced build channel using ReVanced CLI.
Custom ReVanced patch profiles.
Android-version-specific ReVanced artifacts.
Generic dependency engine with microG RE as a dependency example.
Background and resumable downloads.
Automatic download setting, ON by default.
No automatic installation.
PackageInstaller-based installation with resumable user-action states.
Normal update with clean-install fallback.
YouTube ReVanced clean install only.
Local one-version rollback.
SHA-256 and signing-certificate verification.
Storage preflight.
Update All with dependency ordering.
FCM notifications.
WorkManager fallback.
Optional version-aware post-install workflows.
Latest/stable/withdrawn release controls.
Version skipping.
Release notes.
Compatibility explanations.
Local diagnostics.
Material 3 Expressive UI with selective modern translucency.
Zero paid backend requirement for initial scale.
### 42.26 Explicitly Out of Scope for V1
Shizuku.
Root.
Automatic APK installation without user confirmation.
Staged percentage rollout.
Complex canary release infrastructure.
Known-good release as a separate release concept.
Multiple-version cloud rollback.
Delta APK updates.
Arbitrary user-added repositories.
Analytics/behavioral tracking.
Paid backend infrastructure.
Broad ABI coverage beyond arm64-v8a.
Generic cross-app health monitoring.
Fragile coordinate-based UI automation.
### 42.27 Final Architectural Principle
The manager should remain generic while application-specific behavior is declared as metadata and workflows. Adding a new supported application should normally require a catalog profile, source/release rules, artifact metadata, dependencies if any, installation mode, and an optional workflow, not modifications to the core installation engine.
The most important boundary is: the backend/CI decides what artifacts exist and are distributable; the Android client determines what the specific device can use and executes the deployment safely. The UI remains a presentation layer over a persistent deployment state machine.
### 42.28 Independent Review - Updated Questions
Is the PackageInstaller-based clean-install and update flow reliable across Android 11 through current Android versions without root or Shizuku?
Are there Android restrictions that prevent one application from uninstalling/reinstalling another package through the intended user-confirmed flow?
Is the proposed background/resumable download design appropriate for large APKs under modern Android background-execution restrictions?
Is the manifest secure enough if the client treats it as the authoritative catalog?
Should the manifest itself be cryptographically signed in addition to HTTPS and APK verification?
Is SHA-256 plus signing-certificate verification implemented correctly for both third-party APKs and project-produced ReVanced APKs?
Is the release withdrawal/latest/stable model sufficient without staged rollout or a separate known-good concept?
Is local one-version rollback actually feasible for every installation path, especially after a clean uninstall?
What is the most reliable non-Accessibility mechanism for the desired ReVanced/GmsCore post-install configuration?
Are the ReVanced CLI inputs, patch profiles, signing process, and source APK acquisition reproducible and compliant with applicable distribution requirements?
Is GitHub/GitHub Actions/GitHub Releases/static manifest/FCM sufficient for the intended free public distribution scale?
What security, privacy, licensing, trademark, platform-policy, or distribution risks remain before public release?
## 43. Specification Change Log
Added final background-download and resumable-download requirements.
Added user-controlled automatic-download pause/disable behavior.
Removed staged rollout from the final product model.
Removed the separate known-good release concept.
Added latest/stable/withdrawn release controls.
Added release withdrawal as an emergency control.
Clarified that manually corrected ReVanced builds can be published globally after validation.
Added signing-certificate verification in addition to SHA-256.
Clarified that ReVanced CLI signing is not the same as an assumed universal ReVanced distribution certificate.
Added storage preflight.
Added dependency-first Update All behavior.
Simplified post-install success criteria to package-installed for normal apps and package-installed + workflow-complete for configured apps.
Added DeploymentPlan as the core deployment-domain abstraction.
Added lightweight diagnostics and deployment IDs.
Added skip-version, support status, release notes, and compatibility explanation.
Added final Material 3 Expressive + selective modern translucency UI direction.
Clarified backend release detection vs Android client fallback checking.
Added final V1 scope and explicit out-of-scope list.
