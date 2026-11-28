> Continuation update: see [FINAL-COMPLETION-2026-11-28.md](FINAL-COMPLETION-2026-11-28.md) for schema 3 recovery, local backup/restore, new process tests and current delivery evidence. The earlier figures below remain historical.

> Historical report. For the current repair scope, verification and unresolved acceptance criteria, see [REPAIR-REPORT-2026-11-28.md](REPAIR-REPORT-2026-11-28.md).

# Notely — Delivery Report

Branch: `arena/20d1dbca-android-my-notely` (from `0858c47`)
Final verified run: GitHub Actions **46036906770** — both jobs success (build, test, lint, package, emulator install/launch, instrumented tests).

### Download

| Artifact | Size | Link |
|---|---|---|
| `app-debug.apk` (installable) | 62 860 797 B (60 MB) | https://github.com/arafatislambksmd378-lgtm/Android-My-notely/releases/latest/download/app-debug.apk |
| `app-release-unsigned.apk` (R8-minified, needs signing) | 45 457 709 B (44 MB) | https://github.com/arafatislambksmd378-lgtm/Android-My-notely/releases/latest/download/app-release-unsigned.apk |

The APK is **not bit-reproducible** (packaging embeds a build timestamp), so hashes change on every run.
The exact SHA-256 of each published asset is printed in that release's notes:

```bash
gh release view --json body --jq .body            # table of asset · size · sha256
sha256sum app-debug.apk                           # compare after downloading
```

Install:

```bash
adb install -r app-debug.apk
```

The debug APK was installed and launched on an Android 14 (API 34) x86_64 emulator as part of CI.
The release APK is R8-minified and resource-shrunk but **unsigned**; it must be signed before it can be
installed on a real phone.

## B. Source

28 Kotlin files, 9 163 lines under `app/src/main/java/com/notely/app/`, plus 4 test files. Structures:

```
data/        Room entities, DAOs, repositories, DataStore preferences
doc/         block document model, serializer, stats, find/replace, page setup
export/      PDF, DOCX, ODT, RTF, HTML, Markdown, TXT encoders
ui/          Home, Notebooks, Settings, editor (block editor, sheets, OCR)
util/        security (PBKDF2 PIN), images, reminders, spell check, speech
```

## C. Build and test commands (all executed in CI)

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest     # needs a device/emulator
```

Local prerequisites: JDK 17 and the Android SDK with API 34. The Gradle 8.7 wrapper is committed.

## D. Feature matrix

The full matrix, with per-area evidence and the exact limitation for every partially implemented item,
is in `docs/VERIFICATION.md` § 5. Summary of counts:

| Bucket | Areas |
|---|---|
| **Implemented and tested** (automated or emulator) | 3-tab navigation; app launch; find & replace preserving formatting; statistics; block persistence for every block type; drawing → real PNG; PIN hashing (PBKDF2, salted, constant-time); page sizes A3/A4/A5/Letter/Legal; PDF output incl. pagination; DOCX/ODT package validity; RTF/HTML/Markdown/TXT encoders |
| **Implemented but not fully tested** | Home search; notebooks CRUD/bulk/trash; the whole rich-text toolbar and list nesting; reading view; most insert tools; scanner/OCR; templates; version history; reminders/notifications; comments/bookmarks/tags; settings; AI assistant (needs the user's own endpoint) |
| **Partially implemented** | Video/audio are attachments, not players; shape/text box are raster images; export **layout fidelity** in Word/LibreOffice/Acrobat unverified |
| **Not implemented** | Import of external DOCX/ODT; on-device LLM inference (downloads work, no runtime bundled); FTS search (uses `LIKE`); OpenCV scanner rectification |
| **Unsupported by design** | Hardware-backed PIN storage (DataStore PBKDF2 instead of Keystore) — documented |

## E. Technical report

The important engineering facts, including everything that went wrong:

1. **PDF page lifecycle.** `finishPage()` was called twice for the final page, and `ensureSpace()` could
   request two pages back to back. Both throw `IllegalStateException("document is closed!")`. Fixed by a
   `hasLivePage` flag, a single finish before `writeTo()`, clamping oversized blocks, and deferring a
   trailing page break.
2. **PDF is untestable on the JVM.** A bare `PdfDocument` lifecycle fails under Robolectric, proven with a
   throwaway probe test. The assertions moved to `androidTest`, where they pass on API 34.
3. **Kotlin 2.x Compose.** A stale `composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }` block was
   dead configuration and was removed.
4. **Lifecycle-aware collection.** 59 `collectAsState` call sites moved to
   `collectAsStateWithLifecycle` (with `initialValue`, not `initial`), so flows stop while backgrounded.
5. **Markdown export.** User text is escaped (`\ * _ \` # | [ ]`); the table of contents is generated from
   real headings instead of a static placeholder.
6. **CI feedback loop.** Raw workflow logs are unreachable from the authoring environment, so the workflow
   publishes results as check-run annotations, posts a device report to issue #1, and attaches APKs to a
   release. That is how every number in this report was read back.

## F. Delivery status

| Requirement | Status |
|---|---|
| Clean build, debug + release compile | **Done** — CI success |
| Unit tests | **Done** — 27/27 |
| Lint | **Done** — success, no errors |
| Dependency verification | **Done** — every dependency resolves in CI |
| APK produced | **Done** — both APKs, attached to a release |
| Install + launch on a device | **Done** — emulator, API 34, no crash |
| Instrumented tests | **Done** — 3/3, including real PDF output |
| Save → close → restart → reopen → edit → save | **Model-level only**; scripted UI round trip **not** automated |
| Export fidelity in Word/LibreOffice/Acrobat | **Not verified** |
| Physical 3 GB / 64 GB device testing | **Not done** — emulator only |

Not a single test in this report is marked as passing without having run.
