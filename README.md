# Notely — Offline-First Android Notebook & Document Editor

Current continuation status and delivery: [FINAL-COMPLETION-2026-11-28.md](docs/FINAL-COMPLETION-2026-11-28.md).
New: Room recovery journal, local backup/restore, and optional CI signing configuration.
See [backup/recovery contracts and limits](docs/BACKUP-AND-RECOVERY.md). Full production acceptance is **not complete**.

Professional offline-first notebook, rich-text document editor and productivity app.
Premium Soft Claymorphism + Material 3, built with Kotlin + Jetpack Compose + Room.

**Primary color:** `#8950F5` · **Min SDK:** 26 (Android 8.0) · **Target SDK:** 34 · **Version:** 1.0.0

## Build & install (APK)

Build 50 repair/acceptance report: [`docs/REPAIR-REPORT-2026-11-28.md`](docs/REPAIR-REPORT-2026-11-28.md).
Baseline inventory: [`docs/BASELINE-2026-11-28.md`](docs/BASELINE-2026-11-28.md).
Historical delivery report: [`docs/DELIVERY.md`](docs/DELIVERY.md).

Current verified development build: [build 50](https://github.com/arafatislambksmd378-lgtm/Android-My-notely/releases/tag/ci-build-50)
(`b9e24db`): compile, **37 JVM tests**, lint and the **7-test Android emulator suite** passed.
Debug APK is debug-signed/installable; release APK is **unsigned**, not production-ready.
The acceptance report documents remaining features and the editor runtime crash found and fixed by the new UI regression.


> **Production status: NOT READY.** Full offline AI, backup/restore and process-death acceptance remain incomplete.
> The historical baseline APK was produced by GitHub Actions and
> installed and launched on an Android 14 (API 34) emulator; see `docs/VERIFICATION.md` for the measured
> results. Prebuilt artifacts:
>
> - `app-debug.apk` — https://github.com/arafatislambksmd378-lgtm/Android-My-notely/releases/latest/download/app-debug.apk (60 MB, installable)
> - `app-release-unsigned.apk` — R8-minified (44 MB); sign it before distribution
>
> All builds and tests run in CI — `.github/workflows/android-ci.yml` — on every push.

Prerequisites: JDK 17, Android SDK (API 34), internet access for Maven/Google dependencies.

The Gradle wrapper is committed (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` for
Gradle 8.7), so no wrapper generation step is needed.

```bash
# 1. Build a debug APK:
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# 2. Build a release APK (add your signing config first):
./gradlew assembleRelease

# 3. Install on a connected device/emulator:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and press Run.

Verified result of the commands above (CI, build 37): `compileDebugKotlin` success,
`testDebugUnitTest` 27/27, `lintDebug` success, `assembleDebug` success, `assembleRelease` success.
On the emulator: install OK, cold start ~1.5 s, no crash, instrumented tests passed.

## Run tests

```bash
./gradlew testDebugUnitTest          # 37 JVM tests: document/export/security/file regressions
./gradlew connectedDebugAndroidTest  # PDF, Room, editor, backup/restore and journal tests (needs a device)
```

## Existing feature inventory (not full acceptance certification)

- **3-tab navigation:** Home, Notebooks, Settings (persistent bottom bar, state preserved).
- **Home:** time-aware greeting, live search (title + document content + notebook), notebook
  preview cards, recent notes, floating square `+` create button.
- **Notebooks:** create/rename/delete/archive/restore, icon + color, sort (recent/created/oldest/A–Z/Z–A),
  filter (all/pinned/favorite/archived/images/tags), bulk select → move/trash/pin/export,
  trash with restore + empty-trash, statistics.
- **Rich-text editor:** Normal/Title/Subtitle/Heading 1–8 via horizontally scrollable Style toolbar;
  bold/italic/underline/strikethrough, font size, text & highlight color, 4 alignments, line spacing,
  bullets/numbers/checklists, nested indent, superscript/subscript, clear formatting,
  upper/lower/title case, undo/redo, auto-save (debounced, transactional Room write) with save status.
- **Reading view:** paginated preview of the finished document (page colour, margins, header, footer,
  `n / N` page numbers, watermark), with "Back to editing" and "Print / PDF" always one tap away.
- **Insert panel:** image (gallery/camera, saved to app storage), editable table (add rows/cols),
  drawing workspace (pen/highlighter/eraser/line/rectangle/ellipse, 6 colours, adjustable stroke,
  undo/redo/clear, saved as a 1080 px PNG and inserted),
  chart (bar/line/pie/column/area, validated data), link, comment, divider, footnote (numbered),
  auto table-of-contents from headings, bookmark, page break, date/time, attachment, shape,
  text box, signature, watermark, header/footer, audio/video attachments, symbols, equation,
  handwriting (drawing), OCR (on-device ML Kit), document scanner (camera → OCR → PDF-ready text).
- **Note options:** card color, page setup, find & replace (match case, prev/next, replace/all, match count),
  word/character/sentence/paragraph/page counts, comments (add/edit/resolve/reopen/delete),
  bookmarks, tags (create/assign/delete, cross-notebook), move to notebook, reminder (AlarmManager +
  notification that opens the note; re-armed after reboot), share & export, version history
  (snapshot/preview/restore, capped at 30),
  AI assistant (optional), templates, make a copy, pin, delete (trash + confirm).
- **Page setup:** portrait/landscape, A4/A3/A5/Letter/Legal, independent margins, page color,
  header/footer, page numbers, line spacing, watermark — persisted per note and honored by PDF export.
- **Export (real engines, no extension renaming):** PDF (`PdfDocument`, paginated with margins, header/footer,
  page numbers, images and charts), DOCX (hand-built OOXML with styles, numbering, headers and embedded
  images), ODT (hand-built OpenDocument with `mimetype` first), RTF (ASCII-safe `\uN?` escapes for Unicode),
  HTML, Markdown, TXT — shared via `FileProvider` + `ACTION_SEND` (WhatsApp/Gmail/Drive/Bluetooth…),
  saved to a folder of your choice, or sent to print. Filenames are sanitised and timestamped.
- **Templates:** 16 built-ins (meeting notes, journal, planner, CV, invoice…) + save-current-as-template,
  all creating real editable documents.
- **Settings:** workspace name/icon, light/dark/system theme, accent (purple/green/blue/yellow),
  app lock (salted PBKDF2 PIN with legacy migration, re-locks when backgrounded), editor defaults (font/size/line spacing),
  auto-save, spelling toggle, storage usage + cache clear, library (templates), about, optional AI
  provider config (no hardcoded keys) and explicit on-device model downloads.
- **AI assistant (optional):** device capability check (RAM/storage/arch → recommended model class),
  OpenAI-compatible chat completions with user-supplied URL/model/key (key stored AES-GCM encrypted),
  11 actions (generate/rewrite/grammar/summarize/expand/translate/titles/outline/explain/ideas/report),
  insert/copy/regenerate. "Replace" rewrites the prose and keeps tables, charts, images and drawings.
  Settings also offers explicit GGUF downloads (Qwen2.5-0.5B / SmolLM2-1.7B / Llama-3.2-3B) with
  RAM + storage compatibility checks and progress; the app states plainly that no inference runtime is
  bundled yet. Core app never depends on any of it.
- **Search:** SQLite wildcard `LIKE` over title + serialized document; this is not indexed full-content search.
- **Data integrity:** Room transactions, version retention and trash. Confirmed repository transaction and
  editor-navigation save defects are repaired; no claim of complete process-death recovery or foreign-key enforcement.

## Architecture

See `docs/ARCHITECTURE.md` for module map, data model, export pipeline and security notes.
See `docs/VERIFICATION.md` for the honest feature-by-feature status report.

## Project layout

```
app/src/main/java/com/notely/app/
  MainActivity.kt, NotelyApp.kt        — entry + manual DI
  ui/                                  — NavHost, theme, Home/Notebooks/Settings, Editor, Drawing, OCR
  data/db/                             — Room entities, DAOs, database
  data/repo/                           — repositories + built-in templates
  data/prefs/                          — DataStore preferences
  doc/                                 — structured document model, stats, find/replace
  export/                              — PDF/DOCX/ODT/RTF/HTML/MD/TXT engines
  ai/                                  — optional AI assistant + device caps
  reminder/                            — notification receiver
  util/                                — time formatting, PIN hashing
app/src/test/                          — JVM unit tests (model + exporters)
```

## Release signing and process-recovery verification

The existing Android CI workflow builds and verifies candidates; `release.yml` provides a manual reusable entrypoint.
Production signing is optional and requires **all four** repository Actions secrets:
`NOTELY_KEYSTORE_BASE64`, `NOTELY_KEYSTORE_PASSWORD`, `NOTELY_KEY_ALIAS`, `NOTELY_KEY_PASSWORD`.
Do not paste them into issues/chat or commit a keystore. With no secrets, CI publishes a debug-signed APK and an explicitly **unsigned** release candidate, never an invented production signature.

CI drives actual `am force-stop` and app-UID SIGKILL between three instrumentation sessions.
This tests acknowledged Room journal snapshots, not every uncommitted keystroke or hardware power loss.
The manual backup is unencrypted, copy-only on restore, excludes credentials/models, and has explicit size limits.
See the current report for executed results and unfinished AI/import/FTS/scanner/UI acceptance work.
