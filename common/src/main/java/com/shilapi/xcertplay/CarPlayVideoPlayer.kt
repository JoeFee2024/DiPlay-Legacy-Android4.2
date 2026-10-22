package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.VideoInCar

/** The car-side player, implemented by the Media3 activity (19+) and the ExoPlayer 2 activity (17/18). */
internal interface CarPlayVideoPlayer {
    fun state(): VideoInCar.PlayerState
    fun load()
    fun applyRate()
    fun skip(deltaMillis: Int)
    fun applySeek()
    fun close()
}
