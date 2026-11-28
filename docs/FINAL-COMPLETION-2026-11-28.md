# Notely continuation delivery — 2026-11-28

## Executive summary and verdict

**NOT READY for the full production acceptance directive.** This continuation adds a durable Room editor journal, copy-only local backup/restore, actual externally terminated-process recovery verification, optional secure release signing, stronger APK checks and a dedicated release entrypoint. It preserves the Build 50 repairs and existing native Kotlin/Compose/offline architecture. It does not implement every missing feature in the directive.

Starting state was verified with `git pull --ff-only`: branch `arena/9907d385-android-my-notely`, default `main`, HEAD `63c532c` (documentation after Build 50's `b9e24db`). Build 50 was the newest prerelease; stable `latest` still pointed at build 45. No later implementation had already fixed these issues. See [CONTINUATION-STATUS.md](CONTINUATION-STATUS.md).

## What actually changed

- Room **2→3 migration** adds `recovery_drafts` with note FK/cascade, title, document JSON, SHA-256 and generation timestamps. No destructive migration. Existing document and block IDs retained.
- Application-owned per-note journal writes stage edits independently of Activity lifecycle and normal save debounce. Superseded writes cancel safely; obsolete editor sessions cannot cancel the current session. Every acknowledged journal is transactional. Manual save updates the note and journal atomically.
- Editor loading validates and recovers a matching last-valid snapshot. Digest-invalid/stale-generation drafts are not replayed. Normal repository edits and version restores invalidate outdated drafts transactionally. The UI displays journal progress even when normal autosave is off.
- Editor navigation explicitly dispatches on Android Main; a real instrumentation failure exposed that a composition coroutine's dispatcher cannot be assumed to satisfy Android LifecycleRegistry thread checks.
- Local backup uses SAF, a versioned allowlisted ZIP manifest, SHA-256 per payload, exact column/table compatibility checks, bounded compressed/decompressed data and copy-only conflict resolution. All structured tables and referenced private media in notes/templates/versions/recovery copies are included. Credentials, lock settings and models are excluded. Restore remaps database IDs, rewrites only media-path fields, checks references and inserts transactionally. Existing data is never replaced. Cancellation cannot remove binaries after a committed restore.
- A separate BackupPanel preserves Settings layout while avoiding another oversized composable. Template application now captures new page setup before staging blocks.
- App version raised to **1.0.1 / versionCode 2**.
- Optional four-secret signing is configured without generating or committing a key. Partial secret configuration fails. Keystore is decoded into protected temporary runner storage, used for signing/alias-certificate comparison and deleted. With no secrets, debug + explicitly unsigned candidate are built.
- CI validates actual package IDs, versions, ZIP integrity and applicable signatures, saves `apk-metadata.json`, runs expanded tests and host-driven terminations, then installs/launches the **exact build-job APK** bytes before publication. It does not equate a separately signed device-job build with the published artifact. All published candidates remain prereleases.
- `.github/workflows/release.yml` provides a workflow_dispatch entrypoint reusing the gated pipeline. The existing Android CI workflow also supports manual dispatch on the assigned branch. No other branch was created, switched to, pushed or merged.

Full format/recovery contracts, limits and caveats: [BACKUP-AND-RECOVERY.md](BACKUP-AND-RECOVERY.md).

## Acceptance matrix

PASS is scoped to executed checks below, not to all possible workflows. Historical matrices remain available in REPAIR-REPORT-2026-11-28.md; this table supersedes the rows addressed in this continuation.

| Area | Status | Scope / limitation |
|---|---|---|
| Build 50 repairs | PARTIAL | Existing regressions retained; current final run listed below. |
| Schema 2→3 migration | PARTIAL | Preserves an existing disk-backed note in an added integration test; await recorded result. |
| Recovery journal / stale-write rejection / digest validation | PARTIAL | Implemented; tests include 100 rapid snapshots, old-session rejection and corrupt/stale-generation fallback. |
| Activity relaunch save/reopen/edit/save | PARTIAL | Existing real editor regression retained; current result below. |
| Force Stop and SIGKILL recovery | PARTIAL | Host-driven three-stage UI test added; no PASS until both actual kills and final assertions execute. |
| Sudden hardware power loss / real LMK / unreadable SQLite file | BLOCKED | No physical-device/power-failure setup; no full SQLite corruption recovery claim. |
| Latest keystroke before I/O acknowledgment | NOT APPLICABLE | No zero-loss guarantee for edits never committed to durable storage; documented durability boundary. |
| All-element persistence creation through UI | PARTIAL | Fixture covers modeled text/styles/headings/lists/images/tables/charts/shapes/raster drawing/handwriting/link/footnote/page setup; UI changes body text and bold. Does not create every fixture element through toolbars. |
| Section breaks / table-cell rich text / editable stroke persistence | FAIL | Model/features still incomplete; not invented to make acceptance green. |
| Local backup→delete fixture→restore→compare | PARTIAL | Implemented; isolated test uses two notebooks, complete model fixtures, genuine binaries and associated metadata. Await recorded result. |
| Copy-only restore conflicts / archive tamper/traversal/version/duplicate-ID rejection | PARTIAL | Implemented and regression tests added; exact run result below. |
| Backup encryption / authentication | PARTIAL | Explicitly unencrypted ZIP, private-location warning; hashes detect corruption, not authenticity. No secrets included. |
| Automatic orphan cleanup / interrupted restore hard-kill recovery | PARTIAL | Failed/cancelled in-process restores clean up; hard kill may leave unreferenced files. No broad destructive cleanup added. |
| Offline AI inference | FAIL | No native runtime integrated, no networking-disabled token generation executed. |
| Local-model SHA-256 manifest/resume/context/memory validation | FAIL | Existing download manager remains incomplete; no trusted model/device certification. |
| Optional online AI / Keystore / PIN | PARTIAL | Previous repairs retained; no live provider, biometric or nondestructive PIN reset certification. |
| PDF / DOCX / ODT / RTF / HTML / Markdown / TXT | PARTIAL | Existing export tests retained. ZIP/PDF structural validity is not external visual-fidelity verification. |
| Word / LibreOffice / browser screenshot rendering | BLOCKED | Not executed in this environment; no renderer binaries locally available. |
| DOCX / ODT / structured PDF import | FAIL | Still missing. |
| Indexed FTS body/tag/notebook search | FAIL | SQLite wildcard LIKE still used; no false indexed-search claim. |
| Edge/perspective multi-page scanner | FAIL | Camera/OCR is not full scanning. |
| Remaining shapes/text boxes/OCR/charts/comments/templates/attachments/versions/reminders/share | PARTIAL | Existing features retained; backup covers record/media preservation, not every control/workflow. |
| Physical 3 GB / 64 GB performance | BLOCKED | No physical hardware available. Emulator metrics are separate and limited. |
| Signature/identity/version/archive validation | PARTIAL | New CI gate implemented; signing branch cannot be tested without supplied credentials. |
| Signed production APK | BLOCKED | CI reported no signing secrets configured; no private key was fabricated. |
| Exact published debug APK installation/launch | PARTIAL | New gate; final evidence below. |
| Complete directive / production release acceptance | FAIL | Missing feature/UI/security/performance/signing requirements remain. |

## Tests and evidence

Initial expanded run: **46100501327**, commit `c1d87bc`. Compilation, 37 JVM tests (0 failed/0 skipped), lint, APK identity/version/signature checks and both assemblies passed. Android suite ran **12 tests, 1 failure, 0 skipped**. The editor navigation regression failed with `LifecycleRegistry.setCurrentState must be called on the main thread`. The test was not disabled. Publication was skipped. This prompted explicit Main dispatch. Process-harness emulator identification/diagnostics were also hardened; that failed run is NOT evidence of process recovery.

Re-verification run: **46101550900**, implementation commit `aff2a25`: **37 JVM tests and 12 Android tests passed with 0 failures/0 skipped**, including the corrected editor navigation path. The separate process harness failed before completion; no recovery PASS or release publication is claimed from this run. The host setup now explicitly reinstalls target/test APKs after AGP connected tests and reports detailed failure output. A subsequent guard/test also rejects corrupt source-journal digests before backup path rewriting can recompute their checksums. These changes require a final rerun.

Run **46102586063** (build 56, `e546ce0`): **37 JVM and 13 Android tests passed, 0 failures/0 skipped**. The external harness reached the first acknowledged rich-document snapshot but failed a zero-delay `pidof` assertion immediately after Force Stop. This is not counted as a successful process-recovery test. The harness now waits up to 10 seconds for the captured old PID(s) to disappear (ActivityManager termination can be asynchronous), reports old/new PIDs, and still fails if an old PID survives. Publication remained blocked. Workflow: https://github.com/arafatislambksmd378-lgtm/Android-My-notely/actions/runs/46101550900 .

Configured suites: existing **37 JVM tests**, expanded **13 Android tests** (existing 7 + backup/restore/security/migration 4 + recovery ordering 1 + ordinary journal regression 1). Separately, process-kill harness runs two deliberately terminated instrumentation sessions and one normal verification session. Deliberate terminations are not counted as successful completed JUnit tests. The harness requires durable acknowledgment, verifies main note was not lifecycle-flushed, checks process exit, restarts, compares all supported fixture blocks/page settings/media and edits again.

Tests use isolated in-memory/disk-named databases and disposable emulator/debug data. Backup tests do not clear the application's real database. No `@Ignore` or disabled tests were introduced. Local Android compilation remains unavailable (no JDK/SDK/Google Maven in sandbox); all Android results come from actual Actions runners. Local checks include `git diff --check`, Python script syntax and bash syntax only, not Android functionality.

## Signing and APK delivery

Required production secrets (none observed configured in the executed build):
`NOTELY_KEYSTORE_BASE64`, `NOTELY_KEYSTORE_PASSWORD`, `NOTELY_KEY_ALIAS`, `NOTELY_KEY_PASSWORD`.
Only their presence/absence is reported. No values were read back, printed or committed. The signing code compares a signed release APK's public certificate SHA-256 with the configured keystore alias. This branch remains unverified until credentials are provisioned by their owner; do not put them in chat or Git.

Unsigned release APKs cannot be installed and are not claimed install-tested. Debug APKs are debug-signed, debuggable and not a production release. Debug certificates generated on fresh runners may differ between builds, so debug builds are not a substitute for a stable production update key. Never erase user data merely to bypass signature conflicts.

Final run/release/download URLs, sizes and SHA-256 are to be recorded from successful publication. The pipeline generates a unique run-ID/attempt tag and refuses to overwrite an existing release. `apk-metadata.json` records exact build commit, run, timestamp, package/version, byte sizes, hashes and public certificate fingerprints.

## Performance and remaining work

No physical-device, long-document, frame-timing, search/export or AI measurements are invented. Background serialization, bounded image import and new bounded backup processing avoid main-thread I/O but are not a target-device benchmark. Backup limits are deliberately explicit (512 MiB aggregate, 32 MiB database JSON) and may require a future streaming/incremental format for larger libraries.

Remaining priorities: complete all-element toolbar-driven/process-death acceptance; real local inference with verified optional models and RAM/context controls; FTS migrations; full scanner/import/vector features; external export rendering; encrypted/authenticated backup and hard-kill/orphan recovery; biometric/reset/security audit; real low-end performance; provision and verify stable production signing. These are not all external blockers—many are unfinished implementation.

**Readiness verdict: NOT READY.** A successful candidate release can verify the scoped repairs without satisfying the entire production directive.
