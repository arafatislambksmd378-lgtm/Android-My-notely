# Local backup and editor recovery

## Recovery contract

Room schema 3 adds `recovery_drafts` with a note-ID primary/foreign key (cascade on note deletion), title, JSON, SHA-256, base note timestamp and staged timestamp. Migration 2→3 only creates this table; migration 1→2 is retained. Existing notes and block IDs are not regenerated.

Every editor change schedules an application-owned journal transaction immediately, even if normal auto-save is disabled. The existing 700 ms debounce still controls promotion into the main note row. Writes are serialized per note, superseded queued writes are cancelled, and old editor sessions are rejected before they can cancel newer writes. Serialization and SQLite work run off-main. A manual/navigation save atomically writes the main row and the recovery snapshot. The recovery snapshot is retained as a separate last-valid copy.

Opening an editor validates a matching journal snapshot's digest, structure and base generation. A stale/invalid journal is not replayed. The main row remains available as fallback; a structurally invalid main document still fails closed rather than becoming an empty editable document. Direct repository updates/version restores invalidate the journal in the same transaction. This is not protection against an unreadable SQLite database or a broken storage device.

**Durability boundary:** an edit is recoverable after the journal transaction commits. `Recovery copy saved` acknowledges this. Edits still in memory or pending I/O can be lost on an immediate kill, low-storage error or device power loss. There is no claim of zero lost keystrokes, power-loss hardware guarantees, or full database-corruption recovery. Interrupted private-file writes retain no advertised final file; a hard kill can leave an unreferenced temporary file needing later cleanup.

Activity recreation, Force Stop and process death are different tests. The CI host harness keeps the editor open with ordinary autosave disabled, waits for a valid journal, verifies the main row is still unchanged, then performs `am force-stop`. A fresh instrumentation process reopens the real editor and verifies all fixture blocks, edits again, waits for the next journal acknowledgment, and is terminated with app-UID `kill -9`. A third process verifies/reopens the second state. The emulator PID is checked absent after each kill. This is controlled SIGKILL, not a simulated Android low-memory-killer event or an actual hardware crash. All tests target a disposable emulator/debug app, never a user's physical device.

## Backup v1 format

A ZIP (`.notely.zip`) contains exactly:

- `manifest.json`: format `notely-local-backup`, version `1`, databaseVersion `3`, timestamp, entry path/size/SHA-256 list, and original media-path→archive-path mapping.
- `database.json`: logical rows from notebooks, notes, tags, note_tags, comments, bookmarks, footnotes, versions, templates, reminders and recovery_drafts. All entity columns are preserved. Page setup/styles/block order reside in documentJson.
- `media/<sha256>.<extension>`: files referenced by image/drawing fields and attachment/audio/video blocks across notes, versions, templates and recovery copies. Identical content+extension is deduplicated.

No database file/WAL is copied. Row export uses a Room transaction. Only referenced binaries are included; missing/out-of-private-root media causes a visible failure rather than an incomplete 'successful' backup. Preferences, PIN verifiers, API keys, Android Keystore material, caches and optional AI models are excluded.

**Backups are unencrypted.** The UI warns users to choose private storage. SHA-256 detects accidental corruption; it does not prove authenticity against an attacker who can rewrite the manifest. Android app lock is not backup encryption or document-at-rest encryption.

## Safety and limits

- Storage Access Framework picker controls read/write locations. No broad external-storage permission is added.
- ZIP entries are allowlisted, unique and never extracted to supplied paths. Extraction uses internal sequential temporary names; symlink metadata and arbitrary paths are not honored.
- Every payload's actual decompressed bytes, size and SHA-256 are checked before database mutation. Unlisted/missing entries, unknown tables/columns, incompatible versions, duplicate IDs and broken relationships are rejected.
- Limits: 512 MiB compressed input and aggregate expanded payload; 32 MiB database JSON; 2 MiB manifest; fewer than 10,000 entries. Free storage checks reserve at least 16 MiB. Large libraries beyond these limits fail explicitly and require a future streaming/incremental format, not silent truncation.
- Restore always imports copies with new notebook/note/metadata IDs; original block IDs are retained within each new document. Existing rows are never REPLACEd. Tags with existing names are reused. Duplicate names are otherwise allowed as in the normal app.
- Binary paths are rewritten only in known path fields, never by replacing matching prose. Files are copied to generated per-asset subdirectories under a fresh private `attachments/restored_<uuid>/` directory, preserving original basenames (and thus attachment display names) without collisions before a single remapping/insert transaction. Failure/cancellation before commit removes that directory. Cancellation is checked before commit; commit and its completion marker are non-cancellable, so cancellation cannot delete binaries after a committed restore.
- An abrupt app/process/device termination before the restore transaction commits may leave an unreferenced restore directory. It cannot intentionally replace existing documents. Automatic orphan discovery/cleanup remains incomplete.
- Reminder records and timestamps are preserved; the existing reminder scheduler may rearm future reminders after restart. No new alarm is scheduled by the import routine itself.
- Ordinary imports/exports do not execute content. Untrusted backup content remains subject to editor/export link validation. Full signed/encrypted backups are not implemented.

## Test scope

`BackupRestoreInstrumentedTest` uses an isolated in-memory Room database (not the application's real database), two notebooks and rich-document fixtures with genuine PNG/drawing/attachment files, comments/bookmarks/footnotes/tags/templates/versions/reminders/recovery copies. It exports, deletes only fixture rows/files, restores, checks document structure and binary content, then restores again to test non-overwriting conflicts. Separate malformed archive cases test checksum failure, traversal, incompatible version and duplicate IDs with no database changes. A disk-backed migration test upgrades simulated schema 2 while preserving a note.

These are service/Room integration tests, not a claim that every document element was inserted using its UI toolbar. The final completion report records actual run results. Supported model fields are covered; section breaks, cell rich text and editable stroke data are still unsupported and cannot be certified by fixtures.
