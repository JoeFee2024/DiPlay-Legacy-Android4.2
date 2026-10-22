package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android 4.2 and 4.3 decisions. Robolectric cannot run API 17 or 18, so the platform level is
 * passed in explicitly and only the branches those releases take are asserted here; the level
 * itself is covered by the API 19 instrumentation run on a device.
 */
class LegacyVideoDecodeTest {
    @Test fun android42And43CarrySpsPpsInsideTheFirstFrame() {
        assertTrue(needsInBandSpsPps(VideoCodec.H264, sdkInt = 17))
        assertTrue(needsInBandSpsPps(VideoCodec.H264, sdkInt = 18))
    }

    @Test fun android44AndNewerRelyOnCodecSpecificData() {
        assertFalse(needsInBandSpsPps(VideoCodec.H264, sdkInt = 19))
        assertFalse(needsInBandSpsPps(VideoCodec.H264, sdkInt = 29))
    }

    @Test fun hevcNeverGetsH264ParameterSets() {
        assertFalse(needsInBandSpsPps(VideoCodec.H265, sdkInt = 17))
        assertFalse(needsInBandSpsPps(VideoCodec.H265, sdkInt = 18))
    }

    @Test fun android42TriesEveryVendorDecoderBeforeTheSoftwareOne() {
        // The device's own order is kept: it knows which of its decoders is the better one.
        assertEquals(
            listOf("OMX.MTK.VIDEO.DECODER.AVC", "OMX.TI.Ducati.VD.TP.H264", "OMX.google.h264.decoder"),
            fallbackDecoderOrder(
                vendorNames = listOf("OMX.MTK.VIDEO.DECODER.AVC", "OMX.TI.Ducati.VD.TP.H264"),
                softwareName = "OMX.google.h264.decoder",
                sdkInt = 17,
            ),
        )
    }

    @Test fun android43UsesTheSameVendorFirstOrder() {
        assertEquals(
            listOf("OMX.STARTUP.VIDEO.DECODER", "OMX.google.h264.decoder"),
            fallbackDecoderOrder(listOf("OMX.STARTUP.VIDEO.DECODER"), "OMX.google.h264.decoder", 18),
        )
    }

    @Test fun theSoftwareDecoderIsTriedOnceEvenWhenItIsAlsoListed() {
        assertEquals(
            listOf("OMX.ALLWINNER.TDecoder", "OMX.google.h264.decoder"),
            fallbackDecoderOrder(
                vendorNames = listOf("OMX.google.h264.decoder", "OMX.ALLWINNER.TDecoder"),
                softwareName = "OMX.google.h264.decoder",
                sdkInt = 17,
            ),
        )
    }

    @Test fun aMissingSoftwareDecoderLeavesTheVendorListIntact() {
        assertEquals(
            listOf("OMX.rockchip.video.decoder.avg"),
            fallbackDecoderOrder(listOf("OMX.rockchip.video.decoder.avg"), softwareName = null, sdkInt = 17),
        )
    }

    @Test fun newerPlatformsKeepTheirSingleSoftwareFallback() {
        assertEquals(
            listOf("OMX.google.h264.decoder"),
            fallbackDecoderOrder(
                vendorNames = listOf("OMX.TI.Ducati.VD.TP.H264", "OMX.MTK.VIDEO.DECODER.AVC"),
                softwareName = "OMX.google.h264.decoder",
                sdkInt = 19,
            ),
        )
    }

    @Test fun newerPlatformsWithNoSoftwareDecoderHaveNothingToFallBackTo() {
        assertEquals(
            emptyList<String>(),
            fallbackDecoderOrder(vendorNames = listOf("c2.android.avc.decoder"), softwareName = null, sdkInt = 29),
        )
    }

    @Test fun android42WaitsForTheFirstFrameInsteadOfPolling() {
        assertEquals(
            5_000L,
            videoDrainTimeoutUs(hasRenderedFrame = false, sdkInt = 17, legacyTimeoutUs = 5_000L),
        )
        assertEquals(
            5_000L,
            videoDrainTimeoutUs(hasRenderedFrame = false, sdkInt = 19, legacyTimeoutUs = 5_000L),
        )
    }

    @Test fun pollingResumesOnceTheFirstFrameIsOnScreen() {
        assertEquals(
            0L,
            videoDrainTimeoutUs(hasRenderedFrame = true, sdkInt = 17, legacyTimeoutUs = 5_000L),
        )
        assertEquals(
            0L,
            videoDrainTimeoutUs(hasRenderedFrame = true, sdkInt = 19, legacyTimeoutUs = 5_000L),
        )
    }

    @Test fun newerPlatformsAlwaysPoll() {
        assertEquals(
            0L,
            videoDrainTimeoutUs(hasRenderedFrame = false, sdkInt = 21, legacyTimeoutUs = 5_000L),
        )
    }

    @Test fun parkingIsRefusedBeforeAnyApi19OrApi21ClassIsTouched() {
        // A refusal that runs late raises a LinkageError instead of an Exception, which escapes
        // the detach handlers and kills the decoder worker, so the floor has to cover both.
        assertTrue(SURFACE_PARKING_MIN_SDK >= 19) // android.media.ImageReader
        assertTrue(SURFACE_PARKING_MIN_SDK >= 21) // MediaCodec.setOutputSurface
        assertTrue(SURFACE_PARKING_MIN_SDK <= 23) // Android 6 is still able to park
    }
}