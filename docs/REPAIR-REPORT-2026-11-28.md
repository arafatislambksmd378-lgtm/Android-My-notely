> Continuation update: see [FINAL-COMPLETION-2026-11-28.md](FINAL-COMPLETION-2026-11-28.md) for schema 3 recovery, local backup/restore, new process tests and current delivery evidence. The earlier figures below remain historical.

# Notely repair and acceptance report — 2026-11-28

## A. Executive summary

**Verdict: NOT READY.** This change set repairs confirmed data-integrity, security, file-handling and CI defects in the existing native Kotlin/Compose application. It does not implement the full master directive. In particular, actual offline AI inference, complete local backup/restore, complete import, fully structured graphics, and the mandatory process-termination UI workflow remain missing. No cloud service or mandatory account was added. UI design and database schema version are unchanged.

Baseline inventory and historical release facts: [BASELINE-2026-11-28.md](BASELINE-2026-11-28.md). Earlier `VERIFICATION.md` and `DELIVERY.md` describe historical builds only; they do not certify this change set. The original Blueprint and reference screenshot attachments are unavailable in this checkout.

## B–D. Implementation and root causes

| Module | Root cause / actual correction |
|---|---|
| DocumentModel | Tolerant JSON parsing silently dropped malformed blocks and replaced unknown types with paragraphs. Added a separate strict editable-read API that rejects malformed/future documents, duplicate IDs, inconsistent table dimensions and chart data. Tolerant parsing is retained only for previews. |
| Editor composition | The real editor workflow exposed an Android dex VerifyError that launch/PDF tests never reached. Split the large editor into non-inline layout/overlay composition boundaries; verified by the previously failing editor test in build 50. |
| EditorScreen | Navigation launched a composition-scoped save then removed the composition. Writes now use application scope, serialize snapshots off-main and are serialized with a mutex. Back awaits save; failures keep the editor open. Revision checks prevent an older completed save from clearing a newer dirty edit. System back, background and disposal flush pending content. Snapshot/copy flush before reading Room. Failed document reads show a non-editable recovery message and preserve original bytes. |
| Repositories / NotelyApp | `@Transaction` on a manually instantiated repository had no Room-generated implementation. Injected database and used `withTransaction` for merge, snapshot/prune and version restoration. Merge validates both notebooks; restore validates input and snapshots the current version before replacement. |
| NotelyDatabase | Removed destructive fallback. Missing migrations now fail without intentionally deleting data. No schema change or new migration was required by this repair. |
| PrivateFiles / ImageOps | Attachment names could overwrite prior files; missing streams could produce nonexistent "successful" paths; unlimited/full-resolution decoding preceded scaling. Imports now use UUID names, sanitized display names, bounded streaming, private directory validation and atomic sync/rename writes; interrupted files are cleaned. Images are staged (50 MiB cap) and sampled before decode; attachments cap at 100 MiB. Bitmap ownership is released in finally. |
| Exporters / SafeLinks | HTML escaping did not reject `javascript:` or other active schemes. HTML/ODT/DOCX/Markdown now omit unsafe hyperlink wrappers while retaining visible text. Only HTTP(S) without userinfo and mailto are allowed. Editor URL opening uses the same validation. |
| AiAssistant | Hand-assembled request JSON mishandled control characters/model names. Uses JSONObject/JSONArray. HTTPS provider URLs required; redirects disabled; response capped at 2 million characters; connection always disconnected. Partial model downloads are no longer counted as installed GGUF models. This is NOT local inference. |
| Preferences / Settings | AI enable toggle and blank-key saves erased saved credentials. Nullable updates preserve the existing encrypted key; explicit Remove key is provided. Keystore encryption failures now fail the update instead of pretending an empty key was saved. PIN derivation moved off-main. |
| AppLockGate / MainActivity | Five failed PIN attempts permanently disabled input without a real retry timer, while repeated taps could launch concurrent checks. Verification is serialized, cooldown persisted in DataStore, retry countdown shown, and exceptions handled without discarding notes. Atomic initial lock configuration prevents rendering the app shell before lock preferences load. Existing PBKDF2 and legacy SHA-256 verification compatibility is retained. Malformed KDF records are bounded; temporary PBE password data is cleared. |
| CI | Lint and release packaging previously allowed failures; release publication did not wait for the emulator. Both stages are now required; publishing requires build AND device success, checks APK ZIP integrity, and marks builds as prereleases targeted at their exact commit. No production signature is invented. |

## C. Feature / acceptance matrix

Status rules: PASS requires executed relevant tests, PARTIAL means implementation without full required workflow evidence, FAIL means missing/broken, BLOCKED means a specific external verification/deployment prerequisite is unavailable. Entries below are conservative and refer to the directive, not just compilation.

| Requirement group | Status | Evidence or remaining requirement |
|---|---|---|
| Native Kotlin/Compose/offline core architecture | PARTIAL | Preserved; no cloud dependency introduced; broad offline UI workflow not fully exercised. |
| Compilation / configured unit tests / lint / Android tests | PASS | Build 50: compile, 37 JVM tests, lint and the configured 7-test Android suite passed. This does not imply the missing required acceptance suite exists. |
| Notebook CRUD, archive, trash, bulk actions | PARTIAL | Existing code retained; complete UI suite missing. |
| Notebook merge atomicity | PASS | Real Room merge/rejection and injected SQLite-failure rollback regressions passed on API 34. |
| Strict document rejection cases | PASS | Five strict-reader tests passed (valid styled data, corruption, future version/type, duplicate IDs/table inconsistency, blank legacy document). Complete recovery tooling remains PARTIAL. |
| All rich styles, nested lists, checklists, links | PARTIAL | Existing model/editor; serializer coverage is not full UI workflow proof. |
| Images / tables / charts / block order / page setup | PARTIAL | Existing model/export; editor regression fixture checks preservation; no all-element creation/restart UI proof. |
| Comments / footnotes / bookmarks / tags / metadata | PARTIAL | Existing separate Room tables/model; cross-feature round-trip not comprehensively exercised. |
| Headers / footers / page numbers / breaks / TOC | PARTIAL | Existing models/exporters; fidelity limitations remain. |
| Sections / table-cell rich formatting | FAIL | No complete structured implementation. |
| Transaction-safe saves and safe navigation | PARTIAL | Repaired and regression added; abrupt process termination remains unverified. |
| Debounced autosave, focus/background persistence | PARTIAL | Debounce and background/disposal flush present; durable pre-debounce journal and full focus-loss coverage missing. |
| Process-death recovery / mandatory 20-step UI test | FAIL | Activity relaunch regression is explicitly not this test. |
| Room migrations / referential integrity | PARTIAL | v1→v2 preserved, destructive fallback removed; exported schema history, FK migration and full migration tests missing. |
| File integrity / orphan cleanup / low-storage recovery | PARTIAL | Atomic bounded imports repaired; global refcount cleanup, hashes and complete recovery missing. |
| Local AI actual inference / streaming / stop / memory management | FAIL | No JNI/runtime integration; model download manager is not inference. |
| Device-aware models / SHA-256 manifest / resumable download | PARTIAL | RAM/storage checks exist; trusted manifest/checksum/resume missing. No 3 GB compatibility certification. |
| Eleven AI actions / selection replacement / cursor / undo | PARTIAL | Online actions exist; true selection/context/streaming acceptance incomplete. |
| Online AI transport / key management / consent | PARTIAL | Transport and key update defects repaired; no live provider request in this run, consent workflow requires review. |
| Indexed full-content/tag/notebook search | FAIL | Current leading-wildcard SQLite LIKE is not FTS and not indexed content search. |
| Find/replace and match navigation | PARTIAL | Existing implementation and model tests, not complete UI suite. |
| Editable vector shapes / text boxes | PARTIAL | Existing basic objects, not full geometry/style/export acceptance. |
| Drawing tools / structured strokes / handwriting | PARTIAL | Existing drawing workspace; saved output is raster PNG, not persisted editable stroke data. |
| True document scanner | FAIL | Camera/OCR is not edge detection + perspective correction + multi-page scanner. |
| Audio/video/attachments | PARTIAL | Bounded unique imports repaired; playback/MIME/rename/share tests incomplete. |
| DOCX / ODT / PDF / Markdown import | FAIL | Complete structured import missing. |
| PIN / Keystore / biometrics / secure reset | PARTIAL | Retry and startup defects repaired; biometric unlock and nondestructive recovery not complete; PIN verifier is not document encryption. |
| Privacy / no account / offline availability | PARTIAL | No mandatory network added; OS backup policy and complete offline workflows still need review. |
| Low-end startup/memory/scrolling/export benchmarks | BLOCKED | No physical 3 GB device available; historical emulator numbers are not target-device evidence. |
| UI palette / toolbars / dark mode / accessibility | PARTIAL | Existing design retained, not screenshot/accessibility certified. |
| Reference screenshot fidelity | BLOCKED | Reference assets absent. |
| Complete stress/integration/UI suite | FAIL | Targeted regressions only; thousands-of-notes/process-kill/storage-pressure workflows missing. |
| Local application backup→delete→restore | FAIL | No complete implementation or round-trip evidence. |
| Signed production release | BLOCKED | No production keystore provisioned to this session; never substitute a debug key. |

## E. AI report

No local inference runtime was integrated or verified. Existing catalog references Qwen2.5 0.5B Q4, SmolLM2 1.7B Q4 and Llama 3.2 3B Q4, but their downloads/compatibility are not a device certification. CPU/GPU runtime, tokenizer/chat template, cancellation, KV-cache/context controls, trusted manifests, checksum/resume, model switching/unloading, low-memory recovery and inference tests remain required. Android SDK access via CI does not by itself resolve or verify these implementations. No real model or provider inference test was executed here.

## F. Data integrity

No schema version bump; migration 1→2 preserved. Strict editing rejects corrupt/future content rather than saving its partial preview. Existing document bytes are left in Room on a load failure. Application-owned flush mitigates composition cancellation but **does not guarantee survival of a force-stop, power loss or process kill before Room commits**. There is no durable draft journal yet. Backup/restore and all-entity FK integrity remain incomplete. Permanent delete still needs comprehensive related-row/orphan review.

## G. Export

| Format | Result / limitation |
|---|---|
| PDF | Existing native paginated exporter and three instrumentation tests retained. Magic/page-count tests do not establish visual fidelity, Bengali shaping, every oversized object or link support. |
| DOCX | Existing OOXML exporter and package tests retained; unsafe hyperlink relationships excluded. External Word/LibreOffice rendering not executed. |
| ODT | Existing OpenDocument package tests retained; unsafe links excluded. External rendering not executed. |
| RTF | Existing Unicode/escaping tests retained; full structure/fidelity not established. |
| HTML | New regression checks active URL removal and text retention; browser screenshot rendering not executed. |
| Markdown / TXT | Existing tests retained. Exact rich-layout parity is not applicable to these formats. |

## H. Security

No private provider/signing key added. AES-GCM Keystore provider storage retained with visible failure instead of silent empty-save. PIN hash compatibility retained; retry persisted and slow verification off-main. App lock is not at-rest DB encryption. Biometric/reset requirements remain open. HTML active links blocked. Bounded app-private file writes prevent name collisions and path traversal via supplied filenames; full archive import validation is not relevant until import exists. Backup exclusion rules, historical credential scan and broader dependency security review remain open. GitHub Actions secret enumeration returned HTTP 403 (integration permission limitation); no secret value was requested or read.

## I. Performance

No new startup, scrolling, export or AI benchmark measurements are claimed. Structural improvements: sampled image decode before allocation, 50/100 MiB import bounds, background JSON serialization/KDF, lifecycle-aware existing state collection preserved. The editor still holds its document blocks in memory; long-document pagination/lazy object loading and real-device profiling remain required.

## J. Executed testing evidence

Local `./gradlew :app:testDebugUnitTest --console=plain` could not start: no JAVA_HOME and no java command. Google Maven/SDK hosts are inaccessible from the sandbox. `git diff --check` is used for patch hygiene, not functional verification.

New tests: StrictDocumentReadTest (5 JVM), FileAndLinkSafetyTest (5 JVM), RepositoryIntegrityInstrumentedTest (3 Android), EditorSaveInstrumentedTest (1 Android). Existing suites retained (27 JVM + 3 Android). Configured totals are 37 JVM and 7 Android. The editor test edits a title twice through the real editor, leaves using Back, relaunches the activity and checks rich blocks/page setup remain equal. Fixture creation is through the repository; this is not the mandatory create-all-elements/process-restart UI test.

Run 49 (`bc55ed6`, https://github.com/arafatislambksmd378-lgtm/Android-My-notely/actions/runs/46096661585): compile, 37 JVM tests (0 failures, 0 skipped), lint, debug and unsigned release assembly passed. Android execution ran 7 tests, with 1 failure: editor opening triggered `java.lang.VerifyError` in the oversized EditorScreen composable. This failure was not disabled. Publication was correctly skipped. Emulator launch (home only): TotalTime 945 ms, PSS 105,986 KB; these are not editor or physical-device benchmarks.

The editor composition was split in commit `b9e24db`. Re-verification run 50: https://github.com/arafatislambksmd378-lgtm/Android-My-notely/actions/runs/46097298187 . **SUCCESS**, all three jobs completed: build 6m28s, emulator 6m15s, publication 12s. Build annotations report **37 JVM run, 0 failed, 0 skipped**. The configured **7-test Android suite passed**, including the editor regression and three repository tests; Android per-test XML/skipped totals were not separately retrieved because raw-log/artifact redirect hosts are blocked in this sandbox. No tests were ignored/disabled. Lint and both APK assemblies passed as mandatory steps. APK archive integrity passed. Earlier superseded runs were cancelled and are not counted as passes.

Build 50 device report (GitHub issue #1): API 34 x86_64 emulator, installation OK, app resumed, clean launch crash log, TotalTime **1,066 ms**, PSS **105,081 KB**, instrumented tests PASSED. These are home-launch figures only, not editor stress metrics, physical-device results or a no-crash certification of the whole application. Nonfatal workflow warnings: deprecated action runtime versions and Gradle cache restore HTTP 400; neither suppressed compilation or test failures.

## K. APK delivery

Build **50**, implementation commit **`b9e24db76a944feec61572d5631eebdb3a13d46b`**. CI publication timestamp **2026-11-28T02:23:43Z** (UTC); prerelease published 02:23:47Z. Application version remains **1.0.0 / versionCode 1**; CI run number is not the Android versionCode.

[Development prerelease and both downloads](https://github.com/arafatislambksmd378-lgtm/Android-My-notely/releases/tag/ci-build-50).

| APK | Size | SHA-256 |
|---|---:|---|
| [Debug (installable, debug-signed)](https://github.com/arafatislambksmd378-lgtm/Android-My-notely/releases/download/ci-build-50/app-debug.apk) | 62,893,565 bytes (~60.0 MiB) | `321bef3b4e3190f5ccfab43870188a4e345fb62f07c4e8486eeb17ce315d02ec` |
| [Release (UNSIGNED, not production installable)](https://github.com/arafatislambksmd378-lgtm/Android-My-notely/releases/download/ci-build-50/app-release-unsigned.apk) | 45,474,141 bytes (~43.4 MiB) | `1c5828213f11cb3751dc680251a83111520e70d753bc2252700d99cb29d55736` |

No signed production release was produced. Publication occurred only after both build and emulator jobs passed. APK archive validity is not signing certification or complete export/runtime acceptance. This final report/README update is documentation-only after the verified implementation commit.

## L–M. Remaining work and verdict

The FAIL/PARTIAL rows above are real unfinished engineering, not all external blockers. Priorities: durable draft journal and process-termination UI test; actual pinned/maintained local inference integration with verified models; local backup and non-destructive restore; structured search/index migrations; export visual verification; scanner/import/vector workflows; target-device performance; signed release and full acceptance suite. Physical hardware, absent reference images, external renderer availability and signing custody are separate verification blockers.

**NOT READY** is the only supported production verdict. Targeted repairs are useful but neither compilation nor a passing regression suite can substitute for the directive's acceptance criteria.

## Final continuation candidate: Build 59

Published version **1.0.1 (2)** from `5a99db0`: [release and downloads](https://github.com/arafatislambksmd378-lgtm/Android-My-notely/releases/tag/notely-46105415788-1), [successful workflow](https://github.com/arafatislambksmd378-lgtm/Android-My-notely/actions/runs/46105415788). **37 JVM + 15 Android tests, zero failures/skips**, lint and exact debug APK install/launch passed; separate Force Stop and SIGKILL recovery passed. Debug-signed and unsigned artifacts only; **NOT production ready**. Full hashes, limitations and acceptance matrix: [final completion report](FINAL-COMPLETION-2026-11-28.md).
