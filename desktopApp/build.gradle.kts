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
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.jetbrains.coroutines.swing)
    implementation(libs.jetbrains.compose.preview)
    implementation(libs.jetbrains.lifecycle.viewmodel)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.russhwolf.settings.core)
    implementation(libs.kotlinx.serialization.json)
}

compose.desktop {
    application {
        mainClass = "net.newpipe.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb)
            packageName = NEWPIPE_APPLICATION_ID_NEW
            packageVersion = NEWPIPE_VERSION_NAME
            vendor = "NewPipe e.V."
            description = "NewPipe+ desktop client"
            copyright = "GPL-3.0-or-later"

            windows {
                iconFile.set(project.file("icon.ico"))
                menu = true
                shortcut = true
                menuGroup = "NewPipe+"
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
