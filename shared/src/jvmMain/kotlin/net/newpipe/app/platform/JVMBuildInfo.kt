package net.newpipe.app.platform

import org.koin.core.annotation.Singleton

/**
 * The desktop build is always a packaged release: the update category relies on that flag to
 * become visible, because its in-app updater installs the releases it checks for.
 */
@Singleton(binds = [BuildInfo::class])
class JVMBuildInfo : BuildInfo {
    override val isReleaseApk: Boolean = true
    override val isDebug: Boolean = false
}
