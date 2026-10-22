package com.shilapi.xcertplay.media

import android.os.Build
import com.shilapi.xcertplay.airplay.VideoCodec

/**
 * Android 4.2 and 4.3 (API 17/18) decoders routinely ignore the `csd-0`/`csd-1` they are
 * configured with. Without SPS/PPS inside the first access unit they decode nothing and the
 * screen stays black, so those releases get the parameter sets prepended in-band instead.
 *
 * [sdkInt] is the effective platform level, not `Build.VERSION.SDK_INT`: head units that report a
 * newer version than they run are common and would otherwise miss the branch.
 */
internal fun needsInBandSpsPps(codec: VideoCodec, sdkInt: Int): Boolean =
    codec != VideoCodec.H265 && sdkInt <= Build.VERSION_CODES.JELLY_BEAN_MR2

/**
 * Decoders to try by name once the default one will not configure, in order.
 *
 * `MediaCodec.createDecoderByType` returns whichever decoder the device lists first, which on
 * Android 4.2/4.3 can be one that configures but never renders to a Surface. Try the remaining
 * vendor decoders before the software one, because `OMX.google.*` has the weakest Surface support
 * on those releases. A newer platform keeps its historical single software fallback, where
 * enumerating the rest buys nothing.
 *
 * [vendorNames] keeps the order the device listed, which is also the order to try them in.
 * [softwareName] may repeat a name in it; that name is used once, last.
 */
internal fun fallbackDecoderOrder(
    vendorNames: List<String>,
    softwareName: String?,
    sdkInt: Int,
): List<String> {
    val software = softwareName?.let(::listOf).orEmpty()
    if (sdkInt > Build.VERSION_CODES.JELLY_BEAN_MR2) return software
    return vendorNames.filterNot { it == softwareName } + software
}

/**
 * How long a decoder output drain may wait, in microseconds. Zero means poll without blocking.
 *
 * Android 4.x decoders only report their first frame from a dequeue that can wait: polled with a
 * zero timeout, a codec that is still working looks permanently idle. Later frames keep the
 * zero-cost poll, because there the frame is already decoded and the wait would only add latency.
 */
internal fun videoDrainTimeoutUs(hasRenderedFrame: Boolean, sdkInt: Int, legacyTimeoutUs: Long): Long =
    if (!hasRenderedFrame && sdkInt < Build.VERSION_CODES.LOLLIPOP) legacyTimeoutUs else 0L

/**
 * Android 6 (API 23) is the first release that can park a decoder off screen: `ImageReader` is
 * API 19 and `MediaCodec.setOutputSurface` is API 21, so below that the request must be refused
 * before anything touches those classes. A missing class raises a `LinkageError`, which is not an
 * `Exception` and would escape the detach handlers and kill the decoder worker.
 */
internal const val SURFACE_PARKING_MIN_SDK = Build.VERSION_CODES.M