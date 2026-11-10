/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

/**
 * The version of the packaged desktop application.
 *
 * KEEP IN SYNC when cutting a release:
 * - `packageVersion` in `desktopApp/build.gradle.kts` (the version of the MSI)
 * - the GitHub release tag `v<APP_VERSION>` and its assets `NP-Desktop-<APP_VERSION>.msi`
 *
 * The in-app updater compares this version against the release tag to decide whether an update
 * dialog should be shown, so a stale constant would suppress the updater.
 */
object DesktopVersions {

    const val APP_VERSION = "0.25.6"
}
