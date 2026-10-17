# Migration plan (schema & preference changes)

NewPipe+ stores user data in two independent worlds:

| World | Backend | Where it lives |
| --- | --- | --- |
| Android (`:app`) | Room database + `SharedPreferences` | `newpipe.db` + the default preference file |
| Desktop (`:shared` + `:desktopApp`) | JSON blobs + `java.util.prefs.Preferences` | `Preferences.userRoot()` (registry `HKCU\Software\JavaSoft\Prefs` on Windows, `~/.java/.userPrefs` on Linux) |

Both expose preferences through the multiplatform `Settings` abstraction
(`SharedPreferencesSettings` on Android, `PreferencesSettings` on the JVM), so **Android and desktop
share one key namespace**. That is what makes the rules below necessary.

## The contract

1. **Keys are permanent.** Never rename, re-case, or repurpose an existing key. A key that once
   meant "default resolution" must keep meaning that forever.
2. **Changes are additive.** New behaviour gets a new key. Old keys are only ever *read*, with a
   sensible default when absent.
3. **Missing keys are valid.** Every read must supply a default (`Settings.getXOrNull(key)` or
   `getX(key, default)`). A blank or corrupt value is healed to the default and re-persisted, not
   crashed on.
4. **Unknown keys are ignored.** Backups and JSON blobs are parsed with
   `Json { ignoreUnknownKeys = true }`, so a newer archive read by an older build degrades instead
   of failing.
5. **No silent loss.** A migration must never delete data it does not understand. If a change cannot
   be applied safely, leave the old value in place and surface an honest state.

## Key catalogue

Upstream-compatible keys are reused verbatim so semantics stay identical across platforms, e.g.
`theme`, `night_theme`, `default_resolution`, `default_audio_format`, `default_video_format`,
`default_popup_resolution`, `seek_duration`, `enable_playback_resume`, `autoplay_key`,
`preferred_open_action_key`, `start_main_player_fullscreen_key`, `popup_remember_size_pos_key`,
`main_tabs_position`, `limit_mobile_data_usage`, `prefer_descriptive_audio`, `prefer_original_audio`.

Desktop-only keys are namespaced with the `npp_` prefix so they can never collide with an upstream
key, e.g. `npp_default_download_directory`, `npp_download_tasks`, `npp_download_order`,
`npp_log_level`, `npp_notifications_enabled`, `npp_default_playback_speed`,
`npp_watch_history_enabled`, `npp_search_history`, `npp_subscriptions`, `npp_recently_played`,
`npp_video_settings`, `npp_persisted_queue`, `npp_saved_playlists`, `npp_workspace`, and
`library_indexed_folders`.

Structured desktop stores are JSON strings under a single key (queue, playlists, resume, recently
played, downloads, subscriptions, search history, video settings, workspace). Their shape is versioned
by the classes in `net.newpipe.app.player` / `net.newpipe.app.download` / `net.newpipe.app.platform`.

## Procedure for any schema or preference change

1. **Add, don't change.** Introduce a new key/shape; keep reading the old one.
2. **Pick a default** that reproduces the previous behaviour for users who never touch the setting.
3. **Write the migration once, on read.** On first access, if only the old key is present, derive the
   new value in memory; persist it only when the user actually changes something.
4. **Bump the version marker** when the *backup archive* shape changes
   (`BackupOps.SCHEMA_VERSION` / `BackupOps.ENCRYPTED_SCHEMA_VERSION`), never the preference namespace.
5. **Add tests** that construct the old value, read it through the new code path, and assert the
   outcome (see the `*PreferencesTest` suites in `shared/src/commonTest`).
6. **Never migrate the Android Room DB into the desktop stores** (or back) implicitly. The two worlds
   have different schemas; cross-world movement is only ever an explicit user action.

## Versioning

* App version: `NEWPIPE_VERSION_NAME` (`buildSrc/src/main/kotlin/ProjectConfig.kt`); Android also
  carries `NEWPIPE_VERSION_CODE`.
* Network/content: controlled by the NewPipe Extractor dependency, independent of this document.
* Backup archive: `BackupOps.SCHEMA_VERSION` (plain) and `BackupOps.ENCRYPTED_SCHEMA_VERSION`
  (encrypted). A reader refuses any archive whose version it does not understand — see
  [`BACKUP_FORMAT.md`](BACKUP_FORMAT.md).

## Rollback

Because old keys are never removed, downgrading the app after an upgrade is safe: the older build
simply ignores keys it does not know. The only irreversible action is an explicit user-triggered
restore, which replaces local data by design (documented in [`BACKUP_FORMAT.md`](BACKUP_FORMAT.md)).

## Known limitations

* Desktop preference storage is `java.util.prefs`, which has no atomic transactions; a crash mid-write
  can leave a single key unwritten (never a corrupted database, because there is no database).
* The Android Room schema and the desktop JSON stores are **not** interchangeable today. Preserving
  the upstream `newpipe.db` archive format is discussed in [`BACKUP_FORMAT.md`](BACKUP_FORMAT.md).
