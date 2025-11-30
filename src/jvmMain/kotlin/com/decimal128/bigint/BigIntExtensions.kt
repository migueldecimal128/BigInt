// SPDX-License-Identifier: MIT

package com.decimal128.bigint

import java.math.BigInteger


object BigIntExtensions {

    fun BigInt.toBigInteger(): BigInteger = BigInteger(this.toTwosComplementBigEndianByteArray())

    fun BigIntAccumulator.toBigInteger(): BigInteger = BigInteger(this.toBigInt().toTwosComplementBigEndianByteArray())

    fun BigInteger.toBigInt(): BigInt = BigInt.from(this.toString())

    fun BigInt.compareTo(bi: BigInteger) = this.compareTo(bi.toBigInt())

    infix fun BigInt.EQ(bi: BigInteger) = this.compareTo(bi) == 0

    infix fun BigInt.NE(bi: BigInteger) = this.compareTo(bi) != 0

}
