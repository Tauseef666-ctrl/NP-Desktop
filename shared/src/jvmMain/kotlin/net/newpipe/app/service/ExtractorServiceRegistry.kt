/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.service

import java.util.Locale
import net.newpipe.app.model.ServiceInfo
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/**
 * Initialises the extraction engine exactly once per process.
 *
 * The engine keeps its HTTP client in a static field, so it has to be initialised before the first
 * extraction happens. On Android the legacy application already performs that initialisation with
 * its own downloader; in that case the existing downloader is kept.
 */
object ExtractorInitializer {

    private val lock = Any()

    @Volatile
    private var downloader: JvmDownloader? = null

    /**
     * Ensures the extraction engine is ready to use and returns the downloader in use.
     */
    fun ensureInitialized(): JvmDownloader = synchronized(lock) {
        downloader?.let { return it }

        val existing = runCatching { NewPipe.getDownloader() }.getOrNull()
        val own = existing as? JvmDownloader ?: JvmDownloader()
        if (existing == null) {
            // The user interface is English, so the extraction engine requests English texts
            // as well: YouTube serves view counts, relative dates ("3 months ago") and list
            // labels in the requested language, and a non-English system locale made those
            // texts appear in that language inside the English interface. The content country
            // still follows the system, so the regional lists stay local.
            val locale = Locale.getDefault()
            val localization = Localization.fromLocale(Locale.forLanguageTag("en"))
            val contentCountry = ContentCountry(locale.country.ifBlank { "US" })
            NewPipe.init(own, localization, contentCountry)
        }
        // The engine's parallel player calls share a five second budget, but its remote JS
        // fallback fetches can take longer than that on heavy pages (made-for-kids in
        // particular), turning a normal extraction into a premature "requests timed out".
        // Give the engine enough room to finish its recovery ladders.
        ServiceList.all().forEach { it.loadingTimeout = 20 }
        downloader = own
        own
    }

    /**
     * The downloader used by the extraction engine, initialising the engine when needed.
     */
    fun downloader(): JvmDownloader = ensureInitialized()
}

/**
 * A [ServiceRegistry] backed by the extraction engine.
 */
class ExtractorServiceRegistry : ServiceRegistry {

    init {
        ExtractorInitializer.ensureInitialized()
    }

    override fun services(): List<ServiceInfo> {
        ExtractorInitializer.ensureInitialized()
        return ServiceList.all()
            .sortedBy { it.serviceId }
            .map { ServiceInfo(it.serviceId, it.serviceInfo.name) }
    }

    override fun serviceById(id: Int): ServiceInfo? = services().firstOrNull { it.id == id }

    override fun serviceByName(name: String): ServiceInfo? = services().firstOrNull { it.name == name }
}

/**
 * Resolves a service id to the extraction engine service.
 */
internal fun serviceForId(serviceId: Int): StreamingService {
    ExtractorInitializer.ensureInitialized()
    return NewPipe.getService(serviceId)
}

actual fun createServiceRegistry(): ServiceRegistry = ExtractorServiceRegistry()
