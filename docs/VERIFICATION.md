> Continuation update: see [FINAL-COMPLETION-2026-11-28.md](FINAL-COMPLETION-2026-11-28.md) for schema 3 recovery, local backup/restore, new process tests and current delivery evidence. The earlier figures below remain historical.

> Historical report. For the current repair scope, verification and unresolved acceptance criteria, see [REPAIR-REPORT-2026-11-28.md](REPAIR-REPORT-2026-11-28.md).

# Notely — Verification Report

Everything below was produced by a pipeline that actually ran. Where something was **not** verified,
it says so. Raw logs are not reachable from the authoring environment, so all evidence quoted here was
read back from GitHub check-run annotations, the CI-posted device report (issue #1) and release assets.

- Branch: `arena/20d1dbca-android-my-notely` (from `0858c47`)
- Workflow: `.github/workflows/android-ci.yml` — two jobs, `build` and `device`
- Toolchain: JDK 17 (Temurin), Gradle 8.7 wrapper, AGP via `settings.gradle.kts`, compileSdk 34, minSdk 26

## 1. What actually executed

| Stage | Command | Result |
|---|---|---|
| Clean dependency resolution | `./gradlew …` with a cold-ish Gradle cache | resolved (Android SDK installed by the workflow, not by a third-party action) |
| Kotlin compile | `:app:compileDebugKotlin` | **success** |
| JVM unit tests | `:app:testDebugUnitTest` | **27 run, 0 failed, 0 skipped** |
| Android Lint | `:app:lintDebug` | **success** (no errors; `continue-on-error` is set as a safety net) |
| Debug APK | `:app:assembleDebug` | **success** |
| Release APK (minified, unsigned) | `:app:assembleRelease` | **success** |
| APK install on Android 14 (API 34, x86_64) | `adb install -r -t` | **OK** |
| Cold launch | `am start -W` | **Status: ok, TotalTime ≈ 1.5–1.7 s** (emulator, cold, no snapshot) |
| Foreground activity | `dumpsys activity activities` | `com.notely.app.debug/com.notely.app.MainActivity` |
| Crash on launch | `logcat -b crash` | **clean**, no `FATAL EXCEPTION` |
| PSS after launch | `dumpsys meminfo` | **~103 MB** (105 858 KB) |
| Instrumented tests | `:app:connectedDebugAndroidTest` | **PASSED** |

Measured APK sizes (release assets of build 37):

| Artifact | Size | Notes |
|---|---|---|
| `app-debug.apk` | 62 860 797 B (60 MB) | debuggable, **not** minified — the honest size for development |
| `app-release-unsigned.apk` | 45 457 709 B (44 MB) | R8-minified and resource-shrunk, needs signing before distribution |

The debug APK is large because it is unminified and includes `compose.ui-tooling`. The release figure
(44 MB) is the meaningful one; it is still heavy because `material-icons-extended` and ML Kit
text-recognition are bundled. That is a size, not a correctness, statement.

## 2. Environment limitations (stated plainly)

- **PDF can only be tested on a device.** Under Robolectric, even a bare
  `PdfDocument() → startPage → finishPage → writeTo` throws
  `IllegalStateException("document is closed!")` because the native PDF writer is absent. A minimal
  probe test proved this (`PdfSmokeTest`), so the PDF assertions were moved to
  `app/src/androidTest/…/PdfExportInstrumentedTest.kt` and to the emulator job. They pass there.
- The authoring sandbox cannot reach Google Maven or the Android SDK. **All** build/test evidence in this
  document therefore comes from the GitHub Actions runners, not from the sandbox.
- No physical device was available. All on-device figures are from an **emulator** (API 34, x86_64,
  KVM-accelerated, software GPU). Emulator timing is indicative, not a real-phone benchmark.

## 3. Unit tests (27, all passing)

`app/src/test/java/com/notely/app/`

- **`DocumentModelTest` (11)** — serialize/deserialize round-trip, image block round-trip, blank input,
  stats, all paper sizes, find/replace, replace-all preserving run formatting, reflow while typing,
  range styling limited to the selection, headings feeding the TOC, plain-text preview.
- **`ExportersTest` (10)** — plain text, Markdown (incl. escaping of user text), HTML (incl. escaped table
  of contents), RTF (incl. `\uN?` escaping for Word), DOCX package validity, ODT package validity
  (`mimetype` first, stored), filename sanitising (`safeName`).
- **`IntegrityTest` (6)** — PBKDF2 PIN hash is salted and verifiable, legacy SHA-256 hashes still unlock
  and are detected for migration, corrupt stored hash never unlocks, every block type survives two
  save/load cycles, a corrupt element is skipped rather than fatal, drawing export produces a real PNG.

These run under Robolectric (`@Config(sdk = [33])`) where the platform is needed; the rest are pure JVM.

## 4. Instrumented tests (3, passing on API 34)

`app/src/androidTest/java/com/notely/app/PdfExportInstrumentedTest.kt`

- `pdfIsWellFormedAndPaginated` — `%PDF` magic, ≥ 2 page objects for a document with a heading, a long
  paragraph, a table, a page break, plus header/footer/watermark.
- `pdfHandlesEmptyAndHugeDocuments` — empty document still yields a valid PDF; 400 paragraphs paginate.
- `everyPageSizeExports` — A3, A4, A5, Letter and Legal, portrait and landscape, all produce valid output.

## 5. Feature matrix

Legend: **T** = tested automatically or on the emulator · **M** = implemented, verified only by reading
the code path · **P** = partially implemented · **N** = not implemented.

| Area | Status | Evidence / limit |
|---|---|---|
| Three destinations: Home / Notebooks / Settings | **T** | App launches to Home; bottom bar is the only navigation surface. |
| Home: greeting, live search, note previews, FAB | **T** (launch) / **M** (search) | Search is SQL `LIKE` over title + document JSON + notebook name; not FTS. |
| Notebooks: create/rename/delete/archive, icon+colour, sorts, filter chips, bulk select → move/trash/pin/export, empty trash, stats | **M** | Bulk actions and empty-trash are not covered by automated tests. |
| Trash: restore, empty | **M** | Same DAO path as notebooks. |
| Rich-text editor: styles 1–8, bold/italic/underline/strike/highlight, colour, alignment, indent, bullet/numbered/check/star/point lists, nested lists, zoom, full-screen, cut/copy/paste/select-all | **T** (model) / **M** (UI) | Run-level formatting rounds-trips in `DocumentModelTest`; list nesting persists via the block model. Gestures were not exercised on the emulator. |
| Undo/redo (60 steps), autosave | **M** | Not automatically tested. |
| Find & replace with case sensitivity, prev/next, replace-all **preserving formatting** | **T** | `findAndReplace`, `replaceAllPreservesRunFormatting`. |
| Word/char/sentence/paragraph/page statistics | **T** | `statsCompute`. |
| Reading / paginated view (headers, footers, page numbers, margins, watermark, page colour) | **M** | Uses the same `PageSetup` the exporters honour. |
| Insert: image, table (merge/split/resize), drawing, chart, link, comment, divider, footnote, TOC, bookmark | **T** (persistence) / **M** (editing) | `everyBlockTypeSurvivesTwoSaveLoadCycles`; DOCX/ODT/PDF include tables, footnotes and TOC. |
| Insert: shape, text box, signature, watermark, header/footer, page numbers, date/time, attachment, video/audio, page/section breaks, symbols, equation | **P** | Video/audio are stored as attachments — the export formats have no player, and the UI says so. Shape/text box are drawn as images. |
| Drawing workspace (pen, highlighter, eraser, line, rect, ellipse, colours, widths, undo/redo/clear) | **T** (output is a real PNG) | `drawingExportIsAnActualPng`. Raster, not vector. |
| Scanner / OCR / handwriting (ML Kit, on-device) | **M** | No automated coverage; needs a camera or a seeded image. No OpenCV rectification. |
| Templates (16 built-in + custom) | **M** | Each creates a real editable document. |
| Page setup: A3/A4/A5/Letter/Legal/Custom mm, orientation, independent margins, page colour, header/footer, page numbers, line spacing, watermark, border | **T** (sizes) / **M** (rest) | `pageSetupSizes` + `everyPageSizeExports`; the rest persists through the model round-trip. |
| Export: PDF | **T** | Instrumented: magic bytes, pagination, page sizes. |
| Export: DOCX | **T** (package) / **M** (fidelity) | Valid OOXML ZIP with `word/media` for images. Not opened in Microsoft Word here. |
| Export: ODT | **T** (package) / **M** (fidelity) | `mimetype` stored first and uncompressed; `Pictures/` for images. Not opened in LibreOffice here. |
| Export: RTF | **T** (escaping) | `\uN?` escapes so Word renders non-Latin text. Not opened in Word here. |
| Export: HTML | **T** (structure + escaping) | Rendered layout not checked in a browser here. |
| Export: Markdown | **T** | Escaping of `\ * _ \` # \| [ ]`, real TOC from headings. |
| Export: TXT | **T** | — |
| Share (FileProvider + `ACTION_SEND`) and print | **M** | Not automated. |
| Comments / bookmarks / tags / reminders | **M** | Reminder taps deep-link into the note; boot receiver re-arms future reminders. Notification delivery not asserted automatically. |
| Version history (30 snapshots, preview, restore) | **M** | Not automated. |
| PIN lock | **T** | PBKDF2-SHA256, 150 000 iterations, 16-byte random salt, constant-time comparison, legacy SHA-256 migrated on next unlock. `EncryptedSharedPreferences`/Keystore is **not** used — the hash lives in DataStore, which is app-private but not hardware-backed. |
| Settings: theme, accent, editor defaults, storage, cache clear, about | **M** | Not automated. |
| AI assistant (bring your own key, OpenAI-compatible, 11 actions, insert/replace/copy) | **M** | Key encrypted with AES-GCM; no key is compiled into the app. Requires the user's own endpoint — untested here. |
| On-device model downloads | **P — deliberately inert** | Downloads with RAM/storage checks, progress and cancel, but **no llama.cpp/MediaPipe runtime is bundled**, so a downloaded model cannot run. The UI states this. No model is auto-loaded; nothing is loaded at all on a 3 GB device. |

## 6. Save → close → restart → reopen → edit → save

The persistence path is covered at the model level (`everyBlockTypeSurvivesTwoSaveLoadCycles`,
`serializeDeserializeRoundtrip`) and the app was installed, launched and exercised on the emulator.
An **end-to-end scripted UI round trip** (type → kill → relaunch → edit → save) was **not** automated;
it remains the top item on the manual checklist.

## 7. Performance

Measured on the emulator (API 34, x86_64, KVM, software GPU — not a 3 GB phone):

| Metric | Value |
|---|---|
| Cold start to first frame | ~1.5 s (`TotalTime 1496 ms`), ~1.7 s on a later run |
| Total PSS after launch | ~103 MB |
| Release APK | 44 MB (minified) |

No frame-timing profile, no scroll jank measurement and no long-session memory growth test was run. The
3 GB/64 GB target was **not** physically tested. Lifecycle-aware state collection
(`collectAsStateWithLifecycle`, 59 sites) was adopted so flows stop collecting while backgrounded.

## 8. Manual checklist still outstanding

1. Type → force-stop → relaunch → edit → save, and confirm nothing is lost.
2. Every insert tool through the UI, then reopen.
3. Draw with each tool; compare the inserted PNG with the canvas.
4. Open the seven exports in Word, LibreOffice, Acrobat and a browser.
5. Reminder → kill app → wait → tap the notification; cold start included.
6. Trash/restore/empty, snapshots, templates, dark mode, PIN lock and background re-lock.
7. OCR on a photo; voice input; AI with your own endpoint.
8. Background mid-edit and return.
9. Run on a real 3 GB / 64 GB device and record actual timings.

## 9. Known limitations

- Images embed in DOCX (`word/media/`) and ODT (`Pictures/`); HTML and Markdown reference local paths.
  Layout fidelity in Word/LibreOffice was not verified here.
- Drawing output is a raster PNG, not vector strokes.
- No OpenCV rectification in the scanner.
- Spell check depends on a platform spell-checker pack being present.
- Search is `LIKE`, not FTS5.
- No import of external DOCX/ODT.
- On-device LLM inference is not wired; downloads only.
- PIN hashing is PBKDF2 in DataStore, not Keystore-backed.

## Final continuation candidate: Build 59

Published version **1.0.1 (2)** from `5a99db0`: [release and downloads](https://github.com/arafatislambksmd378-lgtm/Android-My-notely/releases/tag/notely-46105415788-1), [successful workflow](https://github.com/arafatislambksmd378-lgtm/Android-My-notely/actions/runs/46105415788). **37 JVM + 15 Android tests, zero failures/skips**, lint and exact debug APK install/launch passed; separate Force Stop and SIGKILL recovery passed. Debug-signed and unsigned artifacts only; **NOT production ready**. Full hashes, limitations and acceptance matrix: [final completion report](FINAL-COMPLETION-2026-11-28.md).
