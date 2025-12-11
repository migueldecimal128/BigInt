package com.decimal128.bigint

internal sealed interface MagianCloak

internal interface Magian : MagianCloak {
    val meta: Meta
    val magia: Magia
}

internal val MagianCloak.meta get() = (this as Magian).meta
internal val MagianCloak.magia get() = (this as Magian).magia