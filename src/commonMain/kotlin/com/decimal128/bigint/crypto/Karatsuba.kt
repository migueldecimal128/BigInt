// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint.crypto

object Karatsuba {

    // <<<<<<<<<< PRIMITIVES >>>>>>>>>

    /**
     * Returns the 32-bit limb `n` zero-extended to a 64-bit `ULong`.
     */
    private inline fun dw32(n: Int) = n.toUInt().toULong()


}