// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint.intrinsic

actual inline fun platformName() = "wasmJS"

actual inline fun isJsPlatform() = true

actual inline fun unsignedMulHi(x: ULong, y: ULong): ULong {
    val x0 = (x and 0xFFFF_FFFFu).toULong()
    val x1 = (x shr 32)         // upper 32 bits
    val y0 = (y and 0xFFFF_FFFFu).toULong()
    val y1 = (y shr 32)

    // 32×32 → 64 partials
    val p11 = x1 * y1
    val p01 = x0 * y1
    val p10 = x1 * y0
    val p00 = x0 * y0

    // Combine middle terms and carry
    val middle = (p01 and 0xFFFF_FFFFuL) + (p10 and 0xFFFF_FFFFuL) + (p00 shr 32)

    // High 64 = p11 + (p01 >> 32) + (p10 >> 32) + (middle >> 32)
    val hi = p11 +
            (p01 shr 32) +
            (p10 shr 32) +
            (middle shr 32)

    return hi
}


