// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint.intrinsic

actual inline fun platformName() = "jvm"

actual inline fun isJsPlatform() = false

actual inline fun unsignedMulHi(x: ULong, y: ULong): ULong =
    Math.unsignedMultiplyHigh(x.toLong(), y.toLong()).toULong()


