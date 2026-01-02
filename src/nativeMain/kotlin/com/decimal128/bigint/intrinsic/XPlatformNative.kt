// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint.intrinsic

import kotlinx.cinterop.ExperimentalForeignApi
import com.decimal128.unsignedmulhi.unsigned_mul_hi

actual inline fun isJsPlatform() = false

actual inline fun platformName() = "native"

@OptIn(ExperimentalForeignApi::class)
actual inline fun unsignedMulHi(x: ULong, y: ULong): ULong =
    unsigned_mul_hi(x, y)

