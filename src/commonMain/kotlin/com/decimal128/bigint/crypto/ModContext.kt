package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.BigIntAccumulator

class ModContext(m: BigInt) {
    private val impl = Barrett(m)

    fun modMul(a: BigInt, b: BigInt, out: BigIntAccumulator) =
        impl.modMul(a, b, out)

    fun modSqr(a: BigInt, out: BigIntAccumulator) =
        impl.modSqr(a, out)

    private class Barrett(val m: BigInt,
                          val mu: BigInt
    ) {
        val mSquared = m.sqr()
        val kBits = m.magnitudeBitLen()
        val k = (kBits + 0x1F) ushr 5
        val shiftKMinus1Bits = (k - 1) * 32
        val shiftKPlus1Bits  = (k + 1) * 32
        val bPowKPlus1 = BigInt.Companion.withSetBit(shiftKPlus1Bits)

        // Initial capacities are sized by bitLen to avoid resizing in modPow hot paths
        val q = BigIntAccumulator.Companion.withInitialBitCapacity(2*kBits + 32)
        val r1 = BigIntAccumulator.Companion.withInitialBitCapacity(kBits + 32)
        val r2 = BigIntAccumulator.Companion.withInitialBitCapacity(2*kBits + 32)

        val mulTmp = BigIntAccumulator.Companion.withInitialBitCapacity(2*kBits + 32)

        companion object {
            operator fun invoke(m: BigInt): Barrett {
                if (m.isNegative() || m <= 1)
                    throw ArithmeticException("Barrett divisor must be >1")
                val mNormalized = m.normalize()
                val muLimbs = calcMuLimbs(mNormalized).normalize()
                return Barrett(mNormalized, muLimbs)
            }

            private fun calcMuLimbs(m: BigInt): BigInt {
                check (m.isNormalized())
                val mLimbLen = m.meta.normLen
                val x = BigInt.Companion.withSetBit(2 * mLimbLen * 32)
                val mu = x / m
                return mu
            }

        }

        fun reduceInto(x: BigIntAccumulator, out: BigIntAccumulator) {
            check(out !== q && out !== r1 && out !== r2 && out !== mulTmp)

            require (x >= 0)
            require (x < mSquared)
            if (x < m) {
                out.set(x)
                return
            }
            val r = out
            // q1 = floor(x / b**(k - 1))
            //val q1 = x ushr ((kLimbs - 1) * 32)
            q.set(x)
            q.mutShr(shiftKMinus1Bits)
            // q2 = q1 * mu
            //val q2 = q1 * muLimbs
            q *= mu
            // q3 = floor(q2 / b**(k + 1))
            //val q3 = q2 ushr ((kLimbs + 1) * 32)
            q.mutShr(shiftKPlus1Bits)

            // r1 = x % b**(k + 1)
            //val r1 = x and BigInt.withBitMask((kLimbs + 1) * 32)
            r1.set(x)
            r1.applyBitMask(shiftKPlus1Bits)
            // r2 = (q3 * m) % b**(k + 1)
            //val r2 = (q3 * m) and BigInt.withBitMask((kLimbs + 1) * 32)
            r2.setMul(q, m)
            r2.applyBitMask(shiftKPlus1Bits)
            //var r = r1 - r2
            r.setSub(r1, r2)
            //if (r.isNegative())
            //    r = r + BigInt.withSetBit((kLimbs + 1) * 32)
            if (r.isNegative())
                r += bPowKPlus1

            if (r >= m) r -= m
            if (r >= m) r -= m
            if (r >= m)
                throw IllegalStateException()
        }

        fun modMul(a: BigInt, b: BigInt, out: BigIntAccumulator) {
            mulTmp.setMul(a, b)
            reduceInto(mulTmp, out)
        }

        fun modSqr(a: BigInt, out: BigIntAccumulator) {
            mulTmp.setSqr(a)
            reduceInto(mulTmp, out)
        }

    }
}