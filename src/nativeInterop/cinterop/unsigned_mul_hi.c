// SPDX-License-Identifier: MIT

#include <stdint.h>

uint64_t unsigned_mul_hi(uint64_t x, uint64_t y) {
    __uint128_t p = (__uint128_t)x * (__uint128_t)y;
    return (uint64_t)(p >> 64);
}
