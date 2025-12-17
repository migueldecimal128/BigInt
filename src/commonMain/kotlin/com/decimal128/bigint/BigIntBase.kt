package com.decimal128.bigint

sealed class BigIntBase(
    internal var _meta: Meta,
    internal var _magia: Magia
) {
    internal val meta:Meta get() = _meta
    internal val magia:Magia get() = _magia
}