# Notely — Verification Report

> **Build environment note (important):** This source was authored in a sandbox with outbound access
> limited to GitHub/npm/PyPI. Google Maven, Maven Central, Gradle plugins and the Android SDK were
> unreachable, so the APK could not be compiled or instrumented-tested there. The Gradle wrapper
> (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar` = Gradle 8.7) **is** included, so no wrapper
> generation step is needed. Nothing below claims a test ran that did not.

## What was verified in the sandbox, and how

| Check | Method | Result |
|---|---|---|
| Kotlin brace/string balance (all 30 source files) | char-wise scanner that skips `//`, `/* */`, `"…"`, `"""…"""`, `'…'`, `${…}` bodies | all files end at depth 0 |
| Duplicate top-level declarations per package | regex scan of every `fun`/`val`/`class`/`object` in each package | none |
| Repository/prefs API names used by UI vs. declared | declaration-vs-call-site cross-reference | no unresolved members |
| Composable call sites vs. parameter lists | named-argument comparison for 20 UI entry points | no unknown named args |
| `gradlew` POSIX shell syntax | `sh -n gradlew` | clean |
| Gradle wrapper JAR | downloaded from `gradle/gradle` v8.7.0 tag via the GitHub API, 43,453 bytes | genuine wrapper |
| Unit-test sources vs. production signatures | manual review + signature grep | matched |
| **Actually running Kotlin/Android compilation** | **impossible** — no JDK, no Android SDK, Google Maven blocked | not run |

## Feature-by-feature status

| Area | Status | Notes |
|---|---|---|
| 3-tab navigation (Home/Notebooks/Settings) | Implemented (not device-tested) | State preserved; editor is a full-screen nested route with the bottom bar hidden. |
| Home greeting/search/previews/FAB | Implemented (not device-tested) | Time-aware greeting, live `LIKE` search over title + document JSON + notebook name. |
| Notebook CRUD/archive/trash/sort/filter/bulk | Implemented (not device-tested) | Create/rename/delete/archive, icon + color, four sorts, filter chips, bulk select → move/trash/pin/export, empty trash, stats. |
| Rich-text editor (styles 1–8, formatting, lists, undo/redo, autosave) | Implemented (not device-tested) | Block-based with run-level formatting; formatting applies to the selection, or the whole focused block when nothing is selected. 60-step undo/redo. |
| Reading / paginated view | Implemented (not device-tested) | "Reading view" splits the document into real pages (`PAGE_BREAK` first, then ≤14-block soft caps) and renders header, footer, page numbers `n / N`, margins, watermark and page colour. Print/PDF is one tap away. |
| Find & replace / word count | Implemented (not device-tested) | Match case, prev/next with selection jump, replace/all **preserving run formatting**, live match count and word/char/sentence/paragraph/page stats. |
| Insert: image/table/drawing/chart/link/comment/divider/footnote/TOC/bookmark | Implemented (not device-tested) | Editable table cells; validated chart data; drawing saved as a real PNG and embedded. |
| Insert: shape/textbox/signature/watermark/header/footer/audio/video/symbols/equation/date/attachment | Implemented (not device-tested) | Audio/video are stored as attachments (the export formats have no native playback); documented limitation. |
| Drawing workspace | Implemented (not device-tested) | Pen, highlighter, eraser, line, rectangle, ellipse; 6 colours; width 2–40; undo/redo/clear; exports a 1080 px-wide PNG with the white page background. |
| Scanner / OCR / handwriting | Implemented (not device-tested) | On-device ML Kit text recognition; scanner = camera capture → OCR → text blocks. No OpenCV edge detection (documented). |
| Page setup | Implemented (not device-tested) | Orientation, A3/A4/A5/Letter/Legal/Custom (mm), independent margins, page colour, header/footer, page numbers, line spacing, watermark, border. Persisted per note and honoured by every exporter. |
| Export PDF/DOCX/ODT/RTF/HTML/MD/TXT + share/print | Implemented (not device-tested) | Real encoders, no extension renaming. PDF via `PdfDocument` with pagination; DOCX hand-built OOXML package (styles, numbering, header, table, page breaks); ODT hand-built ODF package with `mimetype` stored first. Sharing via `FileProvider` + `ACTION_SEND`. |
| Comments/bookmarks/tags/reminders | Implemented (not device-tested) | Comments add/edit/resolve/delete; reminder fires a notification and **tapping it opens the note** (cold start included); boot receiver re-arms future reminders. |
| Templates (16 built-in + custom) | Implemented (not device-tested) | Every template creates a real editable document; save-current-as-template. |
| Version history | Implemented (not device-tested) | Snapshot/preview/restore, 30 snapshots per note. |
| Settings (theme/accent/app lock/editor defaults/storage/library/about) | Implemented (not device-tested) | Light/dark/system, four accents, salted SHA-256 PIN with re-lock on background, storage usage + cache clear. |
| AI assistant (optional, bring your own key) | Implemented (not device-tested) | Device-capability check, OpenAI-compatible `/chat/completions`, 11 actions, insert/copy/regenerate. Key stored AES-GCM encrypted, never in code. |
| On-device model downloads | Implemented, **feature intentionally inert** | The Settings → On-device models sheet lists Qwen2.5-0.5B / SmolLM2-1.7B / Llama-3.2-3B GGUF options with RAM/storage compatibility checks and streams the file to app storage with progress + cancel. **No llama.cpp/MediaPipe runtime is bundled**, so a downloaded model cannot be run yet; the sheet states this explicitly. |
| Unit tests (model + exporters) | Written (not run in sandbox) | `./gradlew testDebugUnitTest`. |

## Test coverage in the repository

`app/src/test/java/com/notely/app/`

- `DocumentModelTest` — serialize/deserialize round-trip (text runs, headings, tables, charts, images,
  footnotes, page setup), blank/corrupt input, stats, paper sizes, find/replace (case sensitivity,
  formatting preservation), run reflow, range styling, TOC heading extraction.
- `ExportersTest` — plain text, Markdown, HTML, RTF, DOCX (ZIP entries, relationships resolve,
  table/page-break markup), ODT (`mimetype` first and uncompressed, `<table:table>`), PDF magic bytes
  and page objects, filename sanitising.

Both suites are pure-JVM (only `org.json` is needed, which is declared as a test dependency), so they run
without an emulator. `Exporters.toPdf` is the one Android-framework-dependent method under test — it needs
a real `PdfDocument`, so it is exercised only by the instrumented/manual pass.

## Manual QA checklist (for the build machine)

1. Install; verify launch, the three tabs, and that tab state survives switching.
2. Create a notebook and a note; type with formatting; close and reopen — formatting persists.
3. Insert image/table/chart/drawing/link/footnote; reopen — all elements persist.
4. Draw with every tool, then check the inserted PNG matches the canvas.
5. Find & replace with case sensitivity; confirm replaced text keeps the surrounding bold/italic.
6. Page setup → Reading view: page count, header, footer, watermark.
7. Export all seven formats; open them in Word/LibreOffice/Acrobat/browser; share via the chooser.
8. Set a reminder, kill the app, wait — tap the notification, confirm the note opens.
9. Trash/restore/empty; snapshot/restore; templates; dark mode; PIN lock and background re-lock.
10. OCR a photo with text; voice input; AI with your own key (insert, replace, copy).
11. Background the app mid-edit and return — no data loss.

## Known limitations (deliberate, documented in-app where user-visible)

- DOCX and ODT embed image bytes in the package (`word/media/…`, `Pictures/…`). HTML and Markdown
  reference the local file path instead, which is inherent to those formats.
- Image handling in DOCX/ODT/PDF is implemented but only unit-verified at the archive level
  (the exported ZIP contains the media part); the rendered layout was not opened in Word or
  LibreOffice here, so pixel-level fidelity is unverified.
- Drawing output is a raster PNG, not vector strokes.
- No OpenCV rectification in the scanner; capture → OCR only.
- Spell check uses the platform `SpellCheckerService`; a device with no spell-checker pack gets no squiggles.
- Search is indexed `LIKE`, not FTS5.
- No import of external DOCX/ODT files.
- On-device LLM inference is not wired (model downloads only).
