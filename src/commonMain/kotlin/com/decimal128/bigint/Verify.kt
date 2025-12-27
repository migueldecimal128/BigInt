// SPDX-License-Identifier: MIT

@file:Suppress("NOTHING_TO_INLINE")

package com.decimal128.bigint

internal const val VERIFY_ENABLED: Boolean = true

internal inline fun verify(condition: Boolean) {
    if (VERIFY_ENABLED)
        check(condition)
}