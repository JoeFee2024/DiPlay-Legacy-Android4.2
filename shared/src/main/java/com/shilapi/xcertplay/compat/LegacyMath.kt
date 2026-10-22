package com.shilapi.xcertplay.compat

/**
 * `Math.floorDiv` and `Math.floorMod` are API 24, and nothing backports them.
 *
 * Truncating division already rounds towards negative infinity for a positive dividend, so only a
 * negative dividend with a remainder needs one more step.
 */
fun floorDivBySecond(scaled: Long): Long {
    val quotient = scaled / 1_000_000_000L
    val remainder = scaled % 1_000_000_000L
    return if (scaled < 0 && remainder != 0L) quotient - 1 else quotient
}

/** `Math.floorMod(x, 1_000_000_000)`: the remainder has the sign of the dividend. */
fun floorModBySecond(scaled: Long): Long =
    scaled - floorDivBySecond(scaled) * 1_000_000_000L