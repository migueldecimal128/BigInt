@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.hugeint

actual inline fun unsignedMulHi(x: Long, y: Long): Long = Math.unsignedMultiplyHigh(x, y)

actual inline fun unsignedDiv(x: Long, y: Long): Long = java.lang.Long.divideUnsigned(x, y)

actual inline fun unsignedMod(x: Long, y: Long): Long = java.lang.Long.remainderUnsigned(x, y)
