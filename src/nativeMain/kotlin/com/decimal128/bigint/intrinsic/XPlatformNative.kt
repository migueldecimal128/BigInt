// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint.intrinsic

import kotlinx.cinterop.ExperimentalForeignApi
import com.decimal128.unsignedmulhi.unsigned_mul_hi

@OptIn(ExperimentalForeignApi::class)
actual inline fun unsignedMulHi(x: ULong, y: ULong): ULong =
    unsigned_mul_hi(x, y)

actual inline fun verify(state: Boolean) = check(state)