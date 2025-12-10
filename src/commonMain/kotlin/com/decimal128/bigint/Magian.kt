@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

/**
 * These are operations that are layered above Magia and
 * below [BigInt] and [BigIntAccumulator].
 *
 * Magus ... an ancient Latin magician who works with Magia
 * Magus works his magic on magia.
 */
object Magian {


    /**
     * Standard signum function.
     *
     * @return -1 if negative, 0 if zero, 1 if positive
     */
    inline fun signum(meta: Meta) = (meta.meta shr 31) or ((-meta.meta) ushr 31)

    /**
     * Returns `true` if this value is exactly representable as a 32-bit
     * signed integer (`Int.MIN_VALUE .. Int.MAX_VALUE`).
     *
     * Only values whose magnitude fits in one 32-bit limb (or zero) pass
     * this check.
     */
    fun fitsInt(meta: Meta, magia: Magia): Boolean {
        if (meta.isZero)
            return true
        if (meta.normLen > 1)
            return false
        val limb = magia[0]
        if (limb >= 0)
            return true
        return meta.isNegative && limb == Int.MIN_VALUE
    }
}