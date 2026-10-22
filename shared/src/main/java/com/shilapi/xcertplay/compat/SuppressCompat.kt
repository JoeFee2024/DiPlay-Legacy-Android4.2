package com.shilapi.xcertplay.compat

/**
 * Attaches [secondary] to this throwable's suppressed list.
 *
 * `Throwable.addSuppressed` is API 19, so calling it directly raises NoSuchMethodError on Android
 * 4.2 and 4.3. That is a `LinkageError`, not an `Exception`, so it escapes the surrounding
 * `catch (error: Exception)` blocks and kills whichever thread was cleaning up: a Bluetooth
 * socket close, a USB bridge teardown or a VPN re-establish failure would take the session down
 * instead of reporting why it failed.
 *
 * Suppression is diagnostic only, so losing it below API 19 is preferable to losing the session.
 * This deliberately swallows the linkage failure rather than letting it propagate.
 */
fun Throwable.suppressSecondary(secondary: Throwable) {
    if (secondary === this) return
    try {
        @Suppress("TooGenericExceptionCaught")
        addSuppressed(secondary)
    } catch (_: LinkageError) {
        // No suppressed list on this platform; the primary failure is still reported.
    }
}