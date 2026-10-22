package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.compat.floorDivBySecond
import com.shilapi.xcertplay.compat.floorModBySecond
import org.junit.Assert.*
import org.junit.Test

class EchoReferenceTest {
    private fun pcm(vararg samples: Int) = ByteArray(samples.size * 2).also { out ->
        samples.forEachIndexed { i, s -> out[2 * i] = s.toByte(); out[2 * i + 1] = (s shr 8).toByte() }
    }

    @Test fun nothingIsReturnedBeforePlaybackStarts() {
        val reference = EchoReference(sampleRate = 1_000)
        reference.append(pcm(1, 2, 3, 4), 0, 8, 1, pendingFrames = null, nowNs = 0)
        val out = ShortArray(2) { 9 }
        assertFalse(reference.read(out, endNs = 10_000_000))
        assertArrayEquals(shortArrayOf(0, 0), out)
    }

    @Test fun readsTheSamplesThatWerePlayingAtTheRequestedTime() {
        // 1 kHz: one sample per millisecond. Ten samples written, four still queued at t=0,
        // so sample 6 is reaching the speaker at t=0 and sample 8 at t=2 ms.
        val reference = EchoReference(sampleRate = 1_000)
        reference.append(pcm(*IntArray(10) { it * 100 }), 0, 20, 1, pendingFrames = 4, nowNs = 0)
        val out = ShortArray(3)
        assertTrue(reference.read(out, endNs = 2_000_000))
        assertArrayEquals(shortArrayOf(500, 600, 700), out)
    }

    @Test fun samplesPastWhatWasWrittenOrAlreadyOverwrittenAreSilence() {
        val reference = EchoReference(sampleRate = 1_000, capacityMillis = 4)
        reference.append(pcm(*IntArray(6) { (it + 1) * 10 }), 0, 12, 1, pendingFrames = 0, nowNs = 0)
        val out = ShortArray(8)
        assertTrue(reference.read(out, endNs = 2_000_000))
        // Samples 0-5 written, only 2-5 kept; the window covers samples 0-7.
        assertArrayEquals(shortArrayOf(0, 0, 30, 40, 50, 60, 0, 0), out)
    }

    @Test fun stereoIsDownmixedToMono() {
        val reference = EchoReference(sampleRate = 1_000)
        reference.append(pcm(100, 300, -200, -400), 0, 8, 2, pendingFrames = 0, nowNs = 0)
        val out = ShortArray(2)
        assertTrue(reference.read(out, endNs = 0))
        assertArrayEquals(shortArrayOf(200, -300), out)
    }

    @Test fun resetForgetsThePlayedAudio() {
        val reference = EchoReference(sampleRate = 1_000)
        reference.append(pcm(1, 2), 0, 4, 1, pendingFrames = 0, nowNs = 0)
        reference.reset()
        assertFalse(reference.read(ShortArray(2), endNs = 0))
    }

    /**
     * `Math.floorDiv` is API 24. A window that ends before playback started has a negative
     * dividend, which has to round towards negative infinity rather than towards zero, or the
     * echo canceller reads from the wrong place in the ring.
     */
    @Test fun floorDivisionRoundsTowardsNegativeInfinity() {
        assertEquals(0L, floorDivBySecond(0L))
        assertEquals(0L, floorDivBySecond(999_999_999L))
        assertEquals(1L, floorDivBySecond(1_000_000_000L))
        assertEquals(1L, floorDivBySecond(1_500_000_000L))
        assertEquals(2L, floorDivBySecond(2_000_000_001L))
        assertEquals(-1L, floorDivBySecond(-1L))
        assertEquals(-1L, floorDivBySecond(-999_999_999L))
        assertEquals(-1L, floorDivBySecond(-1_000_000_000L))
        assertEquals(-2L, floorDivBySecond(-1_000_000_001L))
        assertEquals(-3L, floorDivBySecond(-2_500_000_000L))
    }

    @Test fun floorDivisionAgreesWithThePlatformMethod() {
        val samples = listOf(
            0L, 1L, -1L, 999_999_999L, 1_000_000_000L, 1_000_000_001L, -999_999_999L,
            -1_000_000_000L, -1_000_000_001L, 1_234_567_890_123L, -1_234_567_890_123L,
            Long.MAX_VALUE, Long.MIN_VALUE,
        )
        for (value in samples) {
            assertEquals("floorDiv $value", Math.floorDiv(value, 1_000_000_000L), floorDivBySecond(value))
            assertEquals("floorMod $value", Math.floorMod(value, 1_000_000_000L), floorModBySecond(value))
        }
    }

    /** `Math.floorMod` takes the sign of the dividend, unlike the `%` operator. */
    @Test fun floorModKeepsTheSignOfTheDividend() {
        assertEquals(0L, floorModBySecond(0L))
        assertEquals(1L, floorModBySecond(1L))
        assertEquals(999_999_999L, floorModBySecond(-1L))
        assertEquals(1L, floorModBySecond(1_000_000_001L))
        assertEquals(999_999_999L, floorModBySecond(-1_000_000_001L))
        assertEquals(0L, floorModBySecond(-1_000_000_000L))
        assertEquals(1L, floorModBySecond(-1_999_999_999L))
    }
}
