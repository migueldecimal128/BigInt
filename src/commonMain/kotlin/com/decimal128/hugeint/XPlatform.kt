@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.hugeint

expect inline fun unsignedMulHi(x: Long, y: Long): Long

inline fun unsignedMulHi(x: ULong, y: ULong): ULong = unsignedMulHi(x.toLong(), y.toLong()).toULong()

expect inline fun unsignedDiv(x: Long, y: Long): Long

expect inline fun unsignedMod(x: Long, y: Long): Long

inline fun unsignedCmp(x: Long, y: Long) = x.toULong().compareTo(y.toULong())

inline fun unsignedCmp(x: Int, y: Int) = x.toUInt().compareTo(y.toUInt())

inline fun unsignedLT(x: Long, y: Long) = x.toULong() < y.toULong()
