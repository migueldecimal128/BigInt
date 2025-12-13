package com.decimal128.bigint.crypto

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.BigIntAccumulator

object BigIntCrypto {

    fun modInv(a: BigInt, m: BigInt): BigInt {
        val mBitLen = m.magnitudeBitLen()
        if (mBitLen <= 1)
            throw IllegalArgumentException()
        var t = BigIntAccumulator.Companion
            .withInitialBitCapacity(2 * mBitLen)
        var newT = BigIntAccumulator.Companion
            .withInitialBitCapacity(2 * mBitLen)
            .set(1)
        var tmpT = BigIntAccumulator()
        var r = BigIntAccumulator().set(m)
        var newR = BigIntAccumulator().setRem(a, m)
        var tmpR = BigIntAccumulator()

        val q = BigIntAccumulator()
        val qNewT = BigIntAccumulator()
        val qNewR = BigIntAccumulator()

        while (newR.isNotZero()) {
            q.setDiv(r, newR)

            qNewT.setMul(q, newT)
            tmpT.setSub(t,qNewT)
            val swapT = t
            t = newT
            newT = tmpT
            tmpT = swapT

            qNewR.setMul(q, newR)
            tmpR.setSub(r, qNewR)
            val swapR = r
            r = newR
            newR = tmpR
            tmpR = swapR
        }
        if (! r.isOne())
            throw ArithmeticException("not invertible")
        if (t.isNegative())
            t += m
        return t.toBigInt()
    }
}