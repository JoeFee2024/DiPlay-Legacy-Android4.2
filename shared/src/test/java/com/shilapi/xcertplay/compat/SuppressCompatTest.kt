package com.shilapi.xcertplay.compat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * `Throwable.addSuppressed` is API 19. Below that it raises NoSuchMethodError, which is a
 * LinkageError rather than an Exception, so it would escape the `catch (error: Exception)`
 * handlers around the socket, bridge and VPN teardown paths and kill the thread doing the cleanup.
 */
class SuppressCompatTest {
    @Test fun theSecondaryFailureIsRecordedWhenThePlatformSupportsIt() {
        val primary = IllegalStateException("primary")
        val secondary = IllegalArgumentException("secondary")
        primary.suppressSecondary(secondary)
        assertEquals(listOf<Throwable>(secondary), primary.suppressed.toList())
    }

    @Test fun severalSecondariesAreAllRecorded() {
        val primary = IllegalStateException("primary")
        repeat(3) { primary.suppressSecondary(RuntimeException("secondary $it")) }
        assertEquals(3, primary.suppressed.size)
    }

    /** Java rejects self-suppression, and it must not be attempted here either. */
    @Test fun selfSuppressionIsIgnoredRatherThanThrowing() {
        val primary = IllegalStateException("primary")
        primary.suppressSecondary(primary)
        assertEquals(0, primary.suppressed.size)
    }

    @Test fun thePrimaryIsStillTheOneReportedToTheCaller() {
        val primary = IllegalStateException("primary")
        primary.suppressSecondary(IllegalArgumentException("secondary"))
        assertSame(primary, primary)
        assertEquals("primary", primary.message)
    }
}