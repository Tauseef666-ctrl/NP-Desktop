/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import java.net.URI
import java.util.Locale
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
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.jetbrains.coroutines.swing)
    implementation(libs.jetbrains.compose.preview)
    implementation(libs.jetbrains.lifecycle.viewmodel)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.russhwolf.settings.core)
    implementation(libs.kotlinx.serialization.json)
}

// ---------------------------------------------------------------------------
// Bundled libVLC (Windows)
//
// Desktop playback needs libVLC. To spare every user a separate VLC install, the
// official VLC win64 zip is downloaded here and shipped inside the desktop jar
// as the `vlc-win64.zip` resource (+ `vlc-bundle.properties` carrying the
// version). `BundledVlc` in :shared extracts it to the per-user app data dir on
// first run and points vlcj discovery at it, falling back to a system VLC when
// absent (non-Windows builds, tests). Native libs cannot live in the jar
// directly — they must be files on disk to be loadable.
// ---------------------------------------------------------------------------
val vlcVersion = "3.0.21"
val vlcBundleDir = layout.buildDirectory.dir("vlc-bundle")

val downloadVlcBundle = tasks.register("downloadVlcBundle") {
    group = "build"
    description = "Download the VLC win64 runtime bundled into the desktop jar (Windows only)."
    // Snapshot the script property into a configuration-time local: referencing `vlcVersion`
    // directly from the action below would capture the build script object, which the
    // configuration cache (org.gradle.configuration-cache=true) refuses to serialize.
    val version = vlcVersion
    val zipFile = vlcBundleDir.map { it.file("vlc-win64.zip") }
    val propsFile = vlcBundleDir.map { it.file("vlc-bundle.properties") }
    outputs.files(zipFile, propsFile)
    // Other platforms keep the status quo: they use a system-installed VLC.
    onlyIf { System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("windows") }
    doLast {
        val zip = zipFile.get().asFile
        val props = propsFile.get().asFile
        zip.parentFile.mkdirs()
        // Skip the download when a previously fetched, plausibly-sized zip is present.
        val alreadyValid = zip.isFile &&
            zip.length() > 50_000_000 &&
            zip.inputStream().use { it.read() } == 'P'.code // zip magic
        if (!alreadyValid) {
            val url = URI(
                "https://download.videolan.org/pub/videolan/vlc/$version/win64/vlc-$version-win64.zip"
            ).toURL()
            logger.lifecycle("Downloading VLC $version win64 from $url ...")
            val tmp = File(zip.parentFile, ".vlc-win64.zip.part")
            tmp.delete()
            url.openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 300_000
                setRequestProperty("User-Agent", "YT-Desktop-build/1.0")
            }.getInputStream().use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            // A mirror can serve an HTML page with HTTP 200; never ship that as the "bundle".
            val downloadedLooksLikeZip = tmp.length() > 50_000_000 &&
                tmp.inputStream().use { it.read() } == 'P'.code
            if (!downloadedLooksLikeZip) {
                val size = tmp.length()
                tmp.delete()
                throw GradleException("VLC download from $url did not produce a zip (got $size bytes)")
            }
            tmp.copyTo(zip, overwrite = true)
            tmp.delete()
        }
        props.writeText("vlc.version=$version\n")
        logger.lifecycle("VLC bundle ready: $zip (${zip.length()} bytes)")
    }
}

sourceSets {
    main {
        resources {
            srcDir(vlcBundleDir)
            exclude("**/*.part")
        }
    }
}

tasks.named("processResources") {
    dependsOn(downloadVlcBundle)
}

compose.desktop {
    application {
        mainClass = "net.newpipe.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb)
            // Lowercase, dash-only: valid for the Windows exe/msi AND the Debian package name.
            packageName = "yt-desktop"
            packageVersion = NEWPIPE_VERSION_NAME
            vendor = "NewPipe e.V."
            description = "YT Desktop - a libre, ad-free video front-end for the desktop"
            copyright = "GPL-3.0-or-later"

            // The jlink runtime bundled by jpackage is minimal. The JDK HttpClient (used by the
            // NewPipe extractor) and EC TLS crypto are NOT part of it by default, which made the
            // installed app die at startup with a java/net/http/HttpClient error followed by
            // "Failed to launch JVM". Add every module the app actually needs explicitly.
            includeAllModules = true

            windows {
                iconFile.set(project.file("icon.ico"))
                menu = true
                shortcut = true
                menuGroup = "YT Desktop"
                dirChooser = true
                perUserInstall = true
                // Stable identity so future installers upgrade in place instead of stacking.
                upgradeUuid = "66F7DD50-2C8A-42CE-A996-7ECEE1A51416"
            }
        }

        // Release packaging runs ProGuard; keep it enabled but non-obfuscating (a stable, testable
        // build) and add the rules that silence jsoup's optional re2j references.
        buildTypes {
            release {
                proguard {
                    configurationFiles.from("proguard-rules.pro")
                    obfuscate.set(false)
                    optimize.set(false)
                }
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
