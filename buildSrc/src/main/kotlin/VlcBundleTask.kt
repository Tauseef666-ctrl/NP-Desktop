import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Copies a libVLC installation into the application resources of the packaged desktop app.
 *
 * The packaged application bundles its own libVLC so playback does not depend on a separate
 * VLC installation on the user's machine. Uninstallers, browser plugins and GUI skins are not
 * needed by the application and are skipped.
 *
 * In addition to the unpacked bundle, the task writes the whole copy into a zip archive
 * (`vlc-runtime.zip`) that is shipped inside the application jar and extracted at first run
 * by `VlcNativeLocator`, because jpackage does not support adding plain data directories to
 * the Windows package.
 */
abstract class VlcBundleTask : DefaultTask() {

    /**
     * The libVLC installation to bundle. The task only re-bundles when the installation changes.
     */
    @get:InputDirectory
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val vlcRoot: DirectoryProperty

    /**
     * The directory whose contents become the extra application resources.
     */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /**
     * The zip archive of `outputDir` that ships inside the application jar.
     */
    @get:OutputFile
    abstract val runtimeZip: RegularFileProperty

    @TaskAction
    fun bundle() {
        val destination = outputDir.get().asFile.apply { mkdirs() }
        val source = vlcRoot.orNull?.asFile
        val usable = source != null && source.resolve("libvlc.dll").isFile

        if (!usable) {
            // Development runs and CI without a provisioned libVLC still succeed; the
            // application then falls back to its system lookup order.
            logger.warn("VlcBundle: no libVLC source was found; the package will not bundle VLC")
            destination.deleteRecursively()
            runtimeZip.get().asFile.delete()
            return
        }

        val excluded = setOf("msi", "skins", "locale", "hrtfs")
        source!!.copyRecursively(destination, overwrite = true) { _, _ -> OnErrorAction.SKIP }
        for (dir in destination.listFiles() ?: emptyArray()) {
            if (dir.isDirectory && dir.name in excluded) dir.deleteRecursively()
        }
        for (file in destination.listFiles() ?: emptyArray()) {
            if (file.isFile && file.name in setOf("axvlc.dll", "npvlc.dll", "vlc.exe", "uninstall.exe")) {
                file.delete()
            }
        }

        zipDirectory(destination, runtimeZip.get().asFile)

        val bundled = destination.walkTopDown().filter { it.isFile }.count()
        val sizeMb = destination.walkTopDown().filter { it.isFile }
            .sumOf { it.length() } / (1024 * 1024)
        val zipMb = runtimeZip.get().asFile.length() / (1024 * 1024)
        logger.lifecycle(
            "VlcBundle: bundled $bundled files ($sizeMb MB) from ${source.absolutePath}, " +
                "runtime zip $zipMb MB"
        )
    }

    private fun zipDirectory(source: File, target: File) {
        val parent = target.parentFile
        if (parent != null) parent.mkdirs()

        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            source.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = source.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                val entry = ZipEntry(relative)
                entry.time = file.lastModified()
                entry.size = file.length()
                zip.putNextEntry(entry)
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}


