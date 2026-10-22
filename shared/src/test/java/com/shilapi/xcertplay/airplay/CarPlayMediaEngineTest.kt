package com.shilapi.xcertplay.airplay

import java.io.Closeable
import java.net.Socket
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPlayMediaEngineTest {
    @Test
    fun streamConnectionIdUsesUnsignedDecimalForHkdfSalt() {
        assertEquals("18446744073709551615", unsignedPlistDecimal(-1L))
        assertEquals(
            BigInteger("18446744073709551615"),
            unsignedPlistInteger(-1L),
        )
    }

    /**
     * A plist integer arrives as a signed Long, and an iPhone's 64-bit seed has the high bit set
     * about half the time. `Long.toUnsignedString` is API 26, so that case has to render the same
     * value without it or the AirPlay video SETUP dies on Android 4.2/4.3.
     */
    @Test
    fun everyLongBitPatternRendersAsItsUnsignedDecimal() {
        assertEquals("0", unsignedPlistDecimal(0L))
        assertEquals("9223372036854775807", unsignedPlistDecimal(Long.MAX_VALUE))
        assertEquals("9223372036854775808", unsignedPlistDecimal(Long.MIN_VALUE))
        assertEquals("18446744073709551615", unsignedPlistDecimal(-1L))
        assertEquals("18446744073709551614", unsignedPlistDecimal(-2L))
        for (shift in 0 until 64) {
            val value = 1L shl shift
            assertEquals("2^$shift", java.lang.Long.toUnsignedString(value), value.unsignedDecimal())
        }
    }

    @Test
    fun everyIntBitPatternRendersAsItsUnsignedDecimal() {
        assertEquals("0", unsignedPlistDecimal(0))
        assertEquals("2147483647", unsignedPlistDecimal(Int.MAX_VALUE))
        assertEquals("2147483648", unsignedPlistDecimal(Int.MIN_VALUE))
        assertEquals("4294967295", unsignedPlistDecimal(-1))
        assertEquals("4294967294", unsignedPlistDecimal(-2))
        for (shift in 0 until 32) {
            val value = 1 shl shift
            assertEquals("2^$shift", java.lang.Integer.toUnsignedString(value), value.unsignedDecimal())
        }
    }

    /**
     * The rendering must agree with the platform method on every value it can be handed, not only
     * on the samples above: a disagreement changes the HKDF salt and silently breaks pairing.
     */
    @Test
    fun theRenderingMatchesThePlatformMethodOnEveryNegativeInt() {
        for (value in Int.MIN_VALUE until 0) {
            assertEquals(
                "int $value",
                java.lang.Integer.toUnsignedString(value),
                value.unsignedDecimal(),
            )
        }
        val longSamples = listOf(
            Long.MIN_VALUE, Long.MIN_VALUE + 1, -4_294_967_296L, -1_000_000_000L, -65_536L, -1L,
            0L, 1L, 65_536L, 1_000_000_000L, 4_294_967_296L, Long.MAX_VALUE - 1, Long.MAX_VALUE,
        )
        for (value in longSamples) {
            assertEquals("long $value", java.lang.Long.toUnsignedString(value), value.unsignedDecimal())
        }
    }

    @Test
    fun screenStreamTeardownReportsInactive() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val sink = object : MediaSink {
            override fun onScreenStreamActive(type: Int, active: Boolean) {
                events += type to active
            }
        }
        val session = testSession()

        try {
            val engine = CarPlayMediaEngine(sink)
            engine.onTeardown(session, 110)
            engine.onTeardown(session, 100)
        } finally {
            session.close()
        }

        assertEquals(listOf(110 to false), events)
    }

    @Test
    fun sessionCloseReportsAllScreenStreamsInactive() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val sink = object : MediaSink {
            override fun onScreenStreamActive(type: Int, active: Boolean) {
                events += type to active
            }
        }
        val session = testSession()
        val engine = CarPlayMediaEngine(sink)
        val streamsField = CarPlayMediaEngine::class.java.getDeclaredField("streams").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val streams = streamsField.get(engine) as
            MutableMap<CarPlayMediaEngine.StreamKey, Closeable>
        streams[CarPlayMediaEngine.StreamKey(session, 110)] = Closeable {}
        streams[CarPlayMediaEngine.StreamKey(session, 111)] = Closeable {}
        streams[CarPlayMediaEngine.StreamKey(session, 100)] = Closeable {}

        engine.onSessionClosed(session)
        session.close()

        assertEquals(setOf(110 to false, 111 to false), events.toSet())
        assertTrue(streams.isEmpty())
    }

    @Test
    fun videoRemoteControlSessionsAreAcceptedOnlyWithVideoInCar() {
        val stream = mapOf("type" to 130L, "clientTypeUUID" to "A6B27562-B43A-4F2D-B75F-82391E250194", "controlType" to 1L)
        val engine = CarPlayMediaEngine(object : MediaSink {})
        val plain = testSession()
        val video = testSession(videoInCar = true)
        try {
            assertNull(engine.onDataStream(plain, stream))
            val first = engine.onDataStream(video, stream)
            assertEquals(130, first?.get("type"))
            assertEquals(3L, first?.get("streamID"))
            assertEquals(4L, engine.onDataStream(video, stream)?.get("streamID"))
        } finally {
            plain.close()
            video.close()
        }
    }

    private fun testSession(videoInCar: Boolean = false): AirPlaySession = AirPlaySession(
        socket = Socket(),
        config = AirPlayConfig(
            deviceName = "test",
            deviceId = "02:00:00:00:00:02",
            btMac = "02:00:00:00:00:01",
            sourceVersion = "1.0",
            main = AirPlayDisplayConfig(widthPixels = 800, heightPixels = 480),
            videoInCar = videoInCar,
        ),
        identity = AirPlayIdentity.generate(),
        pairings = PairingStore(),
        mfi = null,
        listener = object : AirPlaySessionListener {},
        media = object : AirPlayMediaHandler {},
    )
}
