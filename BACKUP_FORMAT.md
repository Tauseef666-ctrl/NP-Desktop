# Backup archive format

This documents the contents of a NewPipe+ **desktop** backup so that the format can be inspected,
validated, and (in future) migrated. It is intentionally concrete: every entry name and validation
rule below is enforced by code.

Implementation: `shared/src/commonMain/kotlin/net/newpipe/app/backup/`
(`BackupManifest`, `BackupEnvelope`, `BackupContent`, `BackupOps`) and
`shared/src/jvmMain/kotlin/net/newpipe/app/backup/`
(`JVMBackupRepository`, `JVMBackupCipher`).

## File naming

```
npp-backup-yyyy-MM-dd-HH-mm-ss.nppbackup
```

UTC timestamp, zero-padded so plain string ordering equals chronological ordering
(`BackupOps.backupFileName`). The `.nppbackup` suffix is both the extension and the folder-listing
filter, so a backup directory can be listed without opening files.

## Plain archive (no passphrase)

A `.nppbackup` file is a standard ZIP (`java.util.zip`) whose **first** entry is the manifest,
followed by one entry per data domain that actually holds data. Domains with no data are omitted
entirely — an empty app produces a manifest with an empty `entries` list.

```
npp-backup-2026-10-17-09-00-00.nppbackup
├── manifest.json          (always first)
├── queue.json             PlayQueueSnapshot
├── playlists.json         PlaylistSnapshot
├── resume.json            BackupResumeSnapshot
├── recently_played.json   RecentlyPlayedSnapshot
├── downloads.json         DownloadSnapshot
└── download_order.txt     download ids, newline-separated
```

### `manifest.json`

```json
{
  "appName": "NewPipe+",
  "schemaVersion": 1,
  "createdAtEpochMs": 1760700000000,
  "entries": ["queue.json", "playlists.json", "..."]
}
```

* `appName` must equal `BackupOps.APP_NAME` (`"NewPipe+"`).
* `schemaVersion` must equal `BackupOps.SCHEMA_VERSION` (`1`).
* `entries` lists the payload entries in write order; the manifest itself is never listed as payload.

A reader parses and validates the manifest **before** exposing any blob. Malformed JSON, a foreign
`appName`, or a future `schemaVersion` all resolve to "not a backup this build understands" — no
payload is touched.

### Payload entries

| Entry | Source | Shape |
| --- | --- | --- |
| `queue.json` | `PlayQueueStore` | `PlayQueueSnapshot` |
| `playlists.json` | `PlaylistStore` | `PlaylistSnapshot` |
| `resume.json` | `PlaybackResumeStore` | `BackupResumeSnapshot` (`BackupResumeEntry[]`) |
| `recently_played.json` | `RecentlyPlayedStore` | `RecentlyPlayedSnapshot` |
| `downloads.json` | `DownloadStore` | `DownloadSnapshot` |
| `download_order.txt` | `DownloadStore` | newline-separated download ids |

All blobs are UTF-8 JSON decoded with `ignoreUnknownKeys = true`. This is exactly the set of data the
desktop app persists itself — the backup layer never fabricates a parallel copy.

## Encrypted archive (passphrase)

When a passphrase is supplied the entire ZIP is encrypted and then framed. The framing is a
self-describing prefix so a reader can detect encryption and request a passphrase **without any key
material**:

```
MAGIC ("NPPENC1", 7 bytes) | headerLength (4 bytes, big-endian) | header JSON | AES-GCM ciphertext
```

### Encryption header

```json
{
  "appName": "NewPipe+",
  "schemaVersion": 1,
  "algorithm": "AES-GCM",
  "kdf": "PBKDF2WithHmacSHA256",
  "iterations": 120000,
  "saltBase64": "...",
  "nonceBase64": "..."
}
```

* Cipher: **AES-256-GCM**, 128-bit authentication tag, `AES/GCM/NoPadding`.
* KDF: **PBKDF2WithHmacSHA256**, 120 000 iterations, fresh 16-byte salt per archive.
* Nonce: fresh 96-bit (12-byte) GCM nonce per archive.
* Only standard, named JCE primitives are used — no custom cryptography.

The salt and nonce are regenerated on every encryption, so two backups of identical data produce
different ciphertext.

## Validation & rejection rules

An input is refused (empty result, never a partial restore) when **any** of these hold:

* the file does not exist, or is not a ZIP;
* an encrypted archive's magic/header is malformed, truncated, or unparseable;
* the encryption header's `appName`, `schemaVersion`, `algorithm`, `kdf`, or `iterations` are not the
  expected values;
* the passphrase is wrong or the ciphertext was tampered with (surface as a GCM tag failure);
* the manifest is missing, malformed, or declares a foreign app / future schema.

## Restore contract

Restore is **replace**, and it is safe against a half-applied state:

1. Every present blob is decoded and validated up front.
2. If any present blob is malformed, nothing is written and the call returns `false`.
3. Only then are the local domains replaced. Domains absent from the archive are cleared, matching
   the confirm dialog's "current local data will be replaced" wording.

## Relationship to the upstream Android backup (ZIP: `newpipe.db` + prefs)

Upstream NewPipe's Android export is a ZIP containing the Room database `newpipe.db` plus a
serialized/JSON preferences file (`newpipe.settings`, superseded by `preferences.json`); see
`app/src/main/java/org/schabi/newpipe/settings/export/BackupFileLocator.kt`.

The desktop archive is deliberately a **different** format, for concrete reasons:

* The desktop has no Room database; its data lives in JSON stores, so there is no `newpipe.db` to
  ship. The payload is therefore the *actual* desktop stores, not a translated database.
* Shipping the same extension would invite users to feed a desktop archive to Android (or vice
  versa), which cannot work and could be mistaken for data loss.

**Feasibility of preserving the upstream format:** a future converter could map
`queue.json`/`playlists.json`/`resume.json`/`recently_played.json`/`downloads.json` into the Room
schema and emit `newpipe.db`, but the two schemas differ (Room tables vs JSON snapshots) and the
mapping is lossy in both directions. Until such a converter exists and is tested, cross-world restore
is unsupported and this document records the exact contents so the mapping can be written later
without guessing. This supersedes the earlier "preserve the ZIP format" aspiration with an honest
statement of what the desktop format actually is.
