# SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
# SPDX-License-Identifier: GPL-3.0-or-later
#
# The release processing must not change the behaviour of the application. The debug app image
# runs without ProGuard; the release image therefore keeps every reflection-relevant class and
# disables shrinking, optimisation and obfuscation, so the packaged jars stay equivalent to the
# verified development classes. The rules below keep the libraries that rely on reflection,
# annotation processing or service loading.

# Unresolved optional references that the libraries scan for at runtime.
-dontwarn org.graalvm.**
-dontwarn com.oracle.svm.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.re2j.**
-dontwarn libcore.io.Memory
-dontwarn org.robolectric.Robolectric
-dontwarn com.sun.awt.AWTUtilities

# Keep the behaviour identical to the unprocessed application.
-dontshrink
-dontoptimize
-dontobfuscate

# Our own code (Compose UI, Koin annotation generated modules, expect/actual services).
-keep class net.newpipe.app.** { *; }
-keep interface net.newpipe.app.** { *; }

# Koin
-keep class org.koin.** { *; }
-keep interface org.koin.** { *; }

# libVLC via JNA: the bindings map native symbols through interfaces and class names.
-keep class uk.co.caprica.vlcj.** { *; }
-keep interface uk.co.caprica.vlcj.** { *; }
-keep class * implements uk.co.caprica.vlcj.binding.LibVlc { *; }
-keep class uk.co.caprica.vlcj.factory.discovery.** { *; }
-keep class uk.co.caprica.vlcj.support.** { *; }
-keep class com.sun.jna.** { *; }
-keep interface com.sun.jna.** { *; }
-keep class net.sf.cglib.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# NewPipe extractor: services are discovered reflectively including their link handlers.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep interface org.schabi.newpipe.extractor.** { *; }
-keep class org.schabi.newpipe.extractor.services.youtube.** { *; }

# HTTP stack and libraries with dynamic platform integration.
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class org.jsoup.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class com.grack.nanojson.** { *; }

# Kermit logging and settings storage.
-keep class co.touchlab.kermit.** { *; }
-keep class com.russhwolf.settings.** { *; }

# Annotations and signatures are read at runtime.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault

# Nothing is shrunk, so every resource (including META-INF service declarations) is preserved.
