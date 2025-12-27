// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint.intrinsic

actual inline fun unsignedMulHi(x: ULong, y: ULong): ULong =
    Math.unsignedMultiplyHigh(x.toLong(), y.toLong()).toULong()

actual inline fun verify(state: Boolean) {
    if (true)
        assert(state)
}

