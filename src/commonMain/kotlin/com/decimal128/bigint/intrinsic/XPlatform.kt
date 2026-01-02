// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint.intrinsic


expect inline fun unsignedMulHi(x: ULong, y: ULong): ULong

expect inline fun platformName() : String

expect inline fun isJsPlatform(): Boolean

