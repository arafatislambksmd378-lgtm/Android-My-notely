# Notely — Architecture

## Overview

Offline-first single-module Android app. Kotlin + Jetpack Compose (Material 3) + Room + DataStore.
No backend, no account, no mandatory network. All data lives in Room (`notely.db`) + app-private files.

## Module map

| Module | Responsibility |
|---|---|
| `ui/NavHost.kt` | 3-tab bottom navigation (Home/Notebooks/Settings) + editor route; state preserved via `saveState`/`restoreState`. |
| `ui/theme/` | Design tokens (exact palette), light/dark/system themes, 4 accents, claymorphism primitives (`ClayCard`, `ClayIconTile`, buttons, chips, color dots). |
| `ui/home/` | Greeting, live search, notebook previews, recent notes, FAB create flow. |
| `ui/notebooks/` | Notebook CRUD, sort/filter, bulk ops, trash, statistics. |
| `ui/settings/` | Workspace, appearance, app lock, editor defaults, AI config, storage, library, about. |
| `ui/editor/` | Rich-text editor, formatting toolbar, insert panel, page setup, find/replace, comments, bookmarks, tags, export, versions, AI, templates, drawing, OCR. |
| `doc/` | Structured document model (`DocumentBlock` + `TextRun` + `TableData` + `ChartData`), JSON serialization (`org.json`), statistics, find/replace. |
| `data/db/` | Room entities/DAOs/database. |
| `data/repo/` | Repositories (notebook/note/tag/comment/bookmark/footnote/version/template/reminder) + 16 built-in templates. |
| `data/prefs/` | DataStore preferences (theme, accent, workspace, editor defaults, PIN hash, recents, AI config). |
| `export/` | PDF/DOCX/ODT/RTF/HTML/MD/TXT engines + FileProvider sharing. |
| `ai/` | Device capability detection, optional OpenAI-compatible provider client, and explicit on-device model downloads. |
| `reminder/` | `AlarmManager` + notification receiver. |

## Document data model

A note's `documentJson` is a JSON object:

```json
{
  "v": 1,
  "blocks": [
    {"id": "...", "type": "HEADING", "hl": 1, "runs": [{"t": "Title", "b": true, "fs": 28.0, ...}], ...},
    {"type": "TABLE", "tbl": {"r": 2, "c": 2, "hr": true, "cells": ["A","B","1","2"]}},
    {"type": "CHART", "cht": {"k": "bar", "t": "T", "lb": ["X"], "vl": [3.0]}},
    {"type": "IMAGE", "img": "/data/.../images/img_....jpg", "cap": ""},
    {"type": "FOOTNOTE", "fn": "...", "fnr": 1}
  ],
  "page": {"ori": "portrait", "paper": "A4", "mt": 48, "mb": 48, "ml": 48, "mr": 48, "pc": -1, "hd": "", "ft": "", "pn": false, "ls": 1.55, "wm": ""}
}
```

Block types (19): `PARAGRAPH, HEADING (1–8), BULLET, NUMBERED, CHECKLIST, IMAGE, TABLE, CHART,
DIVIDER, DRAWING, FOOTNOTE, PAGE_BREAK, TEXTBOX, SHAPE, ATTACHMENT, VIDEO, AUDIO, SIGNATURE, TOC`.
Every block has a stable UUID, alignment, line spacing, indent level. Text runs carry
bold/italic/underline/strike/font/size/color/highlight/super/sub/link.

The same model drives editing, persistence, preview, statistics, search, and every export engine —
no parallel incompatible models.

Deserialization is defensive: a corrupt or unknown block is skipped instead of discarding the whole
note, and a truncated document JSON still yields every block that parsed.

## Database schema (Room v2)

- `notebooks(id, name, icon, colorIndex, archived, trashed, createdAt, updatedAt)`
- `notes(id, notebookId, title, documentJson, cardColorIndex, pinned, favorite, archived, trashed, reminderAt, viewMode, createdAt, updatedAt)` — indexed on notebookId/updatedAt/trashed. `viewMode` ("edit"/"view") was added by `MIGRATION_1_2`.
- `tags(id, name UNIQUE)`, `note_tags(noteId, tagId)` (many-to-many).
- `comments(id, noteId, text, author, resolved, createdAt)`, `bookmarks(id, noteId, name, blockId, createdAt)`,
  `footnotes(id, noteId, refNumber, text)`, `versions(id, noteId, documentJson, createdAt)` (retention: 30/note),
  `templates(id, name, documentJson, builtin)`, `reminders(id, noteId, atMillis, label)`.

Search uses indexed `LIKE` over `title` + `documentJson` (avoids loading full contents for previews;
previews deserialize only the first non-blank block).

## Editor engine

- `LazyColumn` of blocks; text blocks use `BasicTextField` + `TextFieldValue` with `AnnotatedString`
  spans built from runs (bold/italic/underline/strike/color/highlight/size).
- Formatting toolbar applies to the focused block (selection-aware via `TextFieldValue`); style row
  scrolls horizontally (Normal → Heading 8).
- Undo/redo stacks (60 snapshots), debounced auto-save (700 ms) → transactional Room write,
  save status line (`Saving…` → `Saved · h:mm a`). Leaving the screen flushes a pending save.
- Reading view splits the document into real pages: hard `PAGE_BREAK` first, then soft caps of
  14 blocks per page, with page colour, margins, header, footer, `n / N` page numbers and watermark.
- Images/attachments/drawings are copied into app-private storage; blocks store absolute paths.

## Export pipeline

- **PDF:** `android.graphics.pdf.PdfDocument`, paginated with margins, page colour, header/footer,
  page numbers, wrapped text, tables, footnotes, charts (bar/line/area/pie), images, page breaks,
  watermark and border.
- **DOCX:** hand-built OOXML package — `[Content_Types].xml`, `_rels/.rels`, `word/document.xml`,
  `word/styles.xml` (Heading 1–8), `word/numbering.xml` (bullet/decimal lists), optional
  `word/header1.xml` + `word/footer1.xml` with a real `PAGE` field, and `word/media/imageN.*`
  for embedded pictures with correct relationships. `w:pPr` children follow the schema sequence.
- **ODT:** hand-built OpenDocument package — `mimetype` stored first and uncompressed,
  `META-INF/manifest.xml`, `content.xml`, `styles.xml`, `meta.xml`, plus `Pictures/imageN.*`.
  Media types are sniffed from the file header, not guessed from the extension.
- Export filenames are sanitised and timestamped (`Title-20261127-104500.pdf`) so repeated exports
  never silently overwrite the file the user just shared.
- **RTF/HTML/MD/TXT:** string builders preserving headings, formatting, lists, tables, links, footnotes.
- Sharing via `FileProvider` (`exports/`, `images/`, `drawings/`, `attachments/`) + `ACTION_SEND`
  with read-URI grant (WhatsApp/Gmail/Drive/Bluetooth/Quick Share).

## AI integration (optional)

- `AiAssistant.deviceCaps()` reads RAM (`ActivityManager.MemoryInfo`), free storage (`StatFs`),
  ABI → recommends a model class (3B/1B quantized or cloud).
- Provider: user-configured OpenAI-compatible `/chat/completions` (base URL + model + key in DataStore;
  key never hardcoded, never logged). 11 actions; insert/copy/regenerate; never overwrites without confirm.
- Core app is fully functional with AI disabled/offline.

## Security & privacy

- PIN stored as a salted SHA-256 hash in DataStore; the app re-locks on `ON_STOP`.
- The AI API key is stored AES-GCM encrypted (Android Keystore, alias `notely_ai_key`).
- No document content in logs; no network calls except the optional user-configured AI endpoint.
- FileProvider URIs with temporary read grants; attachment/image validation by extension/content-type
  via `GetContent` picker; links opened only through user action.
- Trash (not hard delete) for notes/notebooks; permanent delete requires explicit confirm (empty trash).

## Performance

- Lazy lists everywhere; previews deserialize minimally; images downsampled by the system picker pipeline
  and stored compressed (JPEG/PNG); version retention capped; no polling — `AlarmManager` one-shots only;
  WorkManager not needed (no periodic work). Targets 3 GB RAM devices: no AI model auto-load.

## Known limitations (honest)

- DOCX/ODT export is a faithful subset (no complex styles, no floating anchors) — images are embedded,
  but the original document data is always preserved in Room; re-import of external DOCX/ODT is not included.
- HTML export references images by absolute app-private path rather than inlining base64.
- Drawing strokes are persisted as PNG snapshots (high-quality image), not as replayable vector strokes.
- Scanner workflow = camera/gallery → OCR → editable text + PDF-ready export (no OpenCV edge detection).
- Spell check uses the platform `SpellCheckerService`; a device without a spell-checker pack shows no
  squiggles. Checking is debounced 450 ms and bounded to 20 000 characters per block.
- FTS5 was replaced with indexed `LIKE` for Room compatibility (documented tradeoff).
