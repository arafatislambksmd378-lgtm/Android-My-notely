# Build 59 post-release audit

Date: 2026-11-28. This is a baseline audit, not a declaration that the requested completion phases are done.

## Repository identity and provenance

- Assigned branch: `arena/9907d385-android-my-notely`.
- Remote branch audited: `320d425` (documentation); delivered APK source: `5a99db01c698eb626b177759673c6f99e405ada9`.
- Local session initially reported base HEAD `2cdb10b` with earlier work unstaged/untracked. Fetched assigned remote branch and compared **all 85 remote tracked files byte-for-byte** with workspace files: zero mismatches. Used a mixed reset to align HEAD/index to fetched `320d425`, preserving working file contents. No checkout, replacement implementation, data deletion, or alternate branch was used.
- `gh release view notely-46105415788-1` confirms tag target and release body both identify `5a99db01c698eb626b177759673c6f99e405ada9`.
- Published debug metadata: 62,943,113 bytes, SHA-256 `510da4d7190582499d0c2621dce5c160d1e9d5e65293ddc8b132b4ec0380679d`.
- Published unsigned metadata: 45,490,929 bytes, SHA-256 `9c541383a3b1c37aacec9733be9272c78a095051a080e8bd2134b4735e621575`.
- A fresh `gh release download` failed with EOF at the redirected GitHub release-asset host. **Independent binary hash recomputation NOT TESTED** in this audit. Metadata/source association is confirmed, not a fresh binary attestation.
- Read `FINAL-COMPLETION-2026-11-28.md`; historical Build 59 evidence is 37 JVM + 15 Android tests, zero failed/skipped, lint and recovery/exact-debug-install gates passed. These are historical results, not new executions.

## Source-inspected acceptance matrix

PASS below is scoped to historical executed evidence. PARTIAL means implementation exists but the entire requested acceptance is not demonstrated. FAIL means required implementation is absent/incomplete. NOT TESTED distinguishes missing new execution from implementation absence.

| Feature | Status | Source/evidence and outstanding acceptance |
|---|---|---|
| Preserve Build 59 source | PASS | All 85 remote tracked files matched before audit changes; application files unchanged in this audit. |
| Durable journal, stable IDs, stale-session rejection | PASS | `recovery/DocumentRecovery.kt`, Room v3, recovery ordering tests; historical acknowledged-snapshot tests. Not a guarantee for uncommitted keystrokes. |
| Force Stop and SIGKILL recovery | PASS | Historical Build 59 host harness and `ProcessRecoveryInstrumentedTest`; a fresh run has not been executed. Not real LMK/power-loss evidence. |
| Backup integrity, copy-only restore, attachments | PASS | Scoped historical `BackupRestoreInstrumentedTest`; `backup/LocalBackup.kt` retains basenames and validates paths/digests. Full-library/hard-kill restore remains unverified. |
| Backward-compatible migration | PASS | Existing 1→2 and 2→3 migrations retained; scoped historical disk-backed migration test, not every real user database. |
| Bold, italic, underline, strike, super/subscript, clear formatting | PARTIAL | `EditorScreen.kt`, `BlockEditor.kt`, run model/editing helpers exist. Requires selection-boundary and toolbar-driven save/reopen acceptance for each operation. |
| Headings 1–8, fonts, size, colors, highlight | PARTIAL | Model and toolbar implementations exist; rich fixtures are not proof that every UI path works. |
| Lists, checklists, indent, alignment, spacing | PARTIAL | Block transformations and rendering exist; nested numbering and all paragraph-setting roundtrips need independent acceptance. |
| Undo/redo | PARTIAL | Editor keeps 60 block-list snapshots in `remember`; not a full document snapshot including page setup/title and not persistent history. Needs branch-after-undo, mixed-operation and recreation tests. |
| Image import/edit/display | PARTIAL | Bounded staged import and sampled decode in `ImageOps`; rotate/crop tools exist. EXIF/orientation and large/corrupt media acceptance incomplete. |
| Tables | PARTIAL | Structured cells exist; rich text within cells incomplete. Needs toolbar create/edit/reopen/export tests. |
| Drawing/handwriting | PARTIAL | Raster preservation covered by fixture; editable stroke persistence incomplete. |
| Links, shapes, charts, embedded content | PARTIAL | Block types/rendering/export fallbacks exist; no exhaustive per-control acceptance. SafeLinks rejects active schemes; link policy is not whole-document security certification. |
| Section breaks/text boxes | FAIL | Complete requested layout semantics not established; do not equate a visual block with section pagination. |
| Notebook create/rename/delete/archive/restore | PARTIAL | Repository/DAO methods exist; full navigation/bulk acceptance not demonstrated. |
| Move/copy/merge notes and notebooks | PARTIAL | Repository move/duplicate/merge methods exist; linked metadata and all conflict cases require tests. |
| Sort/filter/favorites/pin/trash/bulk | PARTIAL | Observers and controls exist; exhaustive combination and bulk atomicity tests absent. |
| Document reopen with rich content | PARTIAL | Historical complex fixture and process tests cover modeled data; not every element created through UI. |
| Indexed full-text search (FTS5) | FAIL | `Daos.kt` searchAll/search use wildcard LIKE on title/documentJson, limited to 80/50 results. JSON keys can match; no body/tag/notebook FTS index. |
| Bengali/English indexed behavior | NOT TESTED | No FTS implementation, tokenizer/normalization/word-boundary acceptance or device SQLite capability probe. Android minimum API 26 does not itself establish FTS5 availability on every vendor SQLite build. |
| Safe attachment/file import | PARTIAL | `PrivateFiles`, staged/bounded copies, file/link safety tests exist. Arbitrary attachment import is not structured office-document import. |
| DOCX/ODT/structured PDF import | FAIL | Complete semantic import absent. |
| Camera/gallery capture | PARTIAL | Existing image pathways; not complete scanning workflow certification. |
| Multi-page scanner, perspective correction, enhancement | FAIL | Existing image crop/rotate and PDF exporter do not establish a complete scanner session, edge detection, homography or page reordering. |
| Offline OCR | PARTIAL | `OcrSheet.kt` uses bundled ML Kit default Latin recognizer. Do not advertise Bengali OCR; offline cold-install and corrupted-input acceptance unexecuted here. |
| PDF | PARTIAL | Android Canvas exporter and structural tests; independent Bengali glyph/layout/page-number verification outstanding. |
| DOCX | PARTIAL | OOXML ZIP generator with image support; independent Word/LibreOffice layout verification outstanding. |
| ODT | PARTIAL | ODF ZIP generator; independent office rendering outstanding. |
| RTF | PARTIAL | Generator and tests; Unicode and supported-element independent rendering outstanding. |
| HTML | PARTIAL | Escaped generation and link filtering; browser visual acceptance outstanding. |
| Markdown/TXT | PARTIAL | Generators exist; plain-text/lossy format fallbacks must not be described as preserving full layout. |
| Margins/header/footer/page numbers/Bengali exports | NOT TESTED | No new independent renderer run; source generation is not visual validation. |
| Offline AI token generation | FAIL | `AiAssistant.kt` contains model download/device caps/provider calls; no native inference dependency in Gradle and no network-disabled generation evidence. |
| Trusted model hashes/resume/license/RAM certification | FAIL | No complete certified model/runtime pipeline. Do not download large unverified models or claim compatibility from file presence. |
| Optional online AI | PARTIAL | Provider HTTP path exists; no live-provider test or credentials used in audit. |
| PIN/Keystore/private files | PARTIAL | Existing protections retained; no fresh adversarial audit, biometric/reset certification, or whole-library encryption claim. |
| Actual ~3 GB device | BLOCKED | No attached physical Android device or local adb. Historical emulator metrics must remain labeled emulator-only. |
| Cold start/memory/large document/storage | PARTIAL | Historical emulator home startup 894 ms and PSS 106,025 KB; no physical, long-document, frame-time or AI profiling. |
| Fresh complete regression/lint/emulator gates | NOT TESTED | No local java/adb available. Must execute Actions for subsequent application changes. |
| Production signing | BLOCKED | No owner-provided production signing credentials; no keys created/read/exposed. |
| New APK/release for this audit | NOT TESTED | Application unchanged; no redundant or falsely labeled release created. Build 59 remains delivered baseline. |
| All original requirements | FAIL | Substantial implementation and acceptance work remains. |

## Completion sequencing and safety gates

1. Add document-wide history tests and toolbar-driven persistence tests before altering editor state ownership; journal/session semantics must remain intact.
2. Evaluate device SQLite FTS5 availability explicitly. Do not unconditionally migrate to a virtual table unsupported on a user's device. Design full-content extraction from structured runs/cells and linked metadata, safe literal query construction, English/Bengali normalization tests, transactional index updates and restore/recovery rebuild coverage. Keep derived search data out of backup compatibility contracts where possible.
3. Implement scanner sessions and bounded processing separately from note mutation. Keep capture originals until save succeeds; test cancellation and interrupted imports.
4. Add independent export-rendering jobs and documented lossy-element mappings. Never claim font fidelity based solely on ZIP/PDF magic bytes.
5. Evaluate a licensed native runtime/model with real ABI/APK/RAM budgets before AI integration. Successful offline inference is mandatory for any completion claim.
6. For every application phase, run full existing Build 59 safety gates plus new tests, inspect failures, and publish only after all mandatory gates pass. Never skip a regression to obtain green CI.

No application implementation or release is claimed from this baseline audit. The requested phases 2–7 remain unfinished; this document makes that explicit rather than relabeling existing partial features as complete.
