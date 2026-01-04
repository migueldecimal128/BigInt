package jitcode

import com.decimal128.bigint.BigInt
import com.decimal128.bigint.Mago

fun main() {
    val x = BigInt.randomWithMaxBitLen(4096)
    val y = BigInt.randomWithMaxBitLen(4096)

    repeat(50_000) {
        Mago.newAdd(x.magia, x.meta.normLen, y.magia, y.meta.normLen)
    }
}
