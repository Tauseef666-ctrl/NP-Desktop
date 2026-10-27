/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.compose.multiplatform)
    alias(libs.plugins.about.libraries)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.jetbrains.coroutines.swing)
    implementation(libs.jetbrains.compose.preview)

    // Keep the material3 artifacts aligned with the shared module
    implementation(libs.jetbrains.compose.material3)
}

// Bundled libVLC: the packaged application ships its own copy, so playback does not depend on a
// separate VLC installation. The `vlcPrefix` Gradle property, the `VLC_HOME` environment variable
// or the Gradle `vlc-setup` plugin cache are checked, in this order.
val vlcSourceDir: String? = sequenceOf(
    findProperty("vlcPrefix")?.toString(),
    System.getenv("VLC_HOME"),
    File(System.getProperty("user.home"), ".gradle/vlcSetup/vlc").absolutePath
).firstOrNull { source ->
    source != null && File(source, "libvlc.dll").isFile
}

val vlcBundleDir = layout.buildDirectory.dir("compose/vlc-resources")
val prepareVlcBundle by tasks.registering(VlcBundleTask::class) {
    if (vlcSourceDir != null) {
        vlcRoot.set(file(vlcSourceDir))
    }
    outputDir.set(vlcBundleDir)
    // The zip is written into the application resources, so that it ends up inside the packaged
    // jar and VlcNativeLocator can extract it on first run; jpackage does not support shipping
    // additional data directories inside the Windows installer.
    runtimeZip.set(project.file("src/main/resources/vlc-runtime.zip"))
}

tasks.named("processResources") { dependsOn(prepareVlcBundle) }

compose.desktop {
    application {
        mainClass = "net.newpipe.app.MainKt"

        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "NP-Desktop"
            packageVersion = "0.25.5"
            description = "NP Desktop - Libre lightweight streaming client"
            copyright = "\u00a9 2026 NewPipe e.V. Licensed under GPL-3.0-or-later"
            vendor = "NP Desktop"

            // Bundle every JDK module in the app image. Missing crypto/network modules in the
            // minimal image are the usual cause of the "Failed to launch JVM" error on packaged
            // Windows builds.
            includeAllModules = true

            windows {
                menu = true
                shortcut = true
                iconFile.set(project.file("src/main/resources/app-icon.ico"))
                dirChooser = true
                menuGroup = "NP Desktop"
                upgradeUuid = "6943C53C-2A58-48D3-87FD-4375A2BB5854"
                perUserInstall = false
            }
        }
    }
}

aboutLibraries {
    export {
        outputFile = file("../shared/src/jvmMain/resources/aboutlibraries.json")
        prettyPrint = true
        excludeFields.addAll("organization", "scm", "funding")
    }
}

