# ProGuard rules for the NewPipe+ desktop (release) build.
#
# jsoup (pulled in transitively by NewPipe Extractor) can optionally route regular expressions
# through the re2j engine. Those com.google.re2j / org.jsoup.helper.Re2jRegex* classes are not on
# the classpath unless re2j is added explicitly, so ProGuard reports "can't find referenced class"
# warnings and aborts the release packaging. The references are never exercised at runtime without
# re2j present, so we silence exactly those warnings instead of shipping the optional engine.

-dontwarn org.jsoup.helper.Re2jRegex**
-dontwarn com.google.re2j.**

# VLCJ/JNA keep rules to preserve reflection/ServiceLoader
-keep class uk.co.caprica.vlcj.** { *; }
-keep class com.sun.jna.** { *; }
-keep class com.sun.jna.platform.** { *; }
-keep class net.sf.cglib.** { *; }
-keep interface uk.co.caprica.vlcj.** { *; }
-keep class * implements uk.co.caprica.vlcj.binding.LibVlc { *; }
# Preserve service loaders
-keep class uk.co.caprica.vlcj.factory.discovery.** { *; }
-keep class uk.co.caprica.vlcj.support.** { *; }
-keepclasseswithmembers class * { public static void main(java.lang.String[]); }
# Don't shrink aggressively to avoid stripping providers
-dontshrink
-dontoptimize
