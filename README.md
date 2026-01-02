# 📘 **BigInt – Arbitrary-Precision Integer Arithmetic for Kotlin Multiplatform**
## 🚧⚠️ **Warning! Construction Zone** ⚠️🚧
`BigInt` is currently **EXPERIMENTAL**. 
The implementation is quite stable, but aspects of the user-facing API
are still subject to change. 

`BigInt` is a high-performance arbitrary-precision
signed integer type for **Kotlin Multiplatform**, designed to
bring efficient big-integer arithmetic to
**JVM, Native, and JavaScript** with **no external dependencies**.

It provides idiomatic Kotlin arithmetic operators, efficient
mixed-primitive arithmetic, **reduced heap allocation overhead**,
and a clean, understandable implementation suitable for applications
needing *hundreds or thousands of digits*.

---

## ✨ Features

- **Kotlin Multiplatform** (JVM / Native / JS)
- **No dependencies**
- **Arbitrary-precision signed integers**
- Arithmetic infix operators: `+ - * / % mod`
- Comparator operators: `< <= == != >= >`
- Integer math functions: `sqr() isqrt() pow(n) abs() gcd() factorial(n)` 
- An extensive toolbox of binary bit-manipulation and boolean operations
- Accepts primitive operands (`Int`, `UInt`, `Long`, `ULong`) without boxing
- Schoolbook multiplication (O(n²))
- Karatsuba squaring
- Knuth’s Algorithm D for division
- Modular arithmetic `modAdd modSub modMul modPow` for cryptography
- Sign–magnitude representation with canonical zero
- Little-endian 32-bit limbs stored in an `IntArray`
- ByteArray serialization: big/little endian of twos-complement/magnitude
- Heap-friendly `MutableBigInt` class for cryptography and other computationally intensive algorithms. 

## 💡 Common Use Cases

- **Statistical computing**: Large dataset aggregations with `MutableBigInt`
- **Number theory**: Prime testing, GCD, LCM, factorials
- **Cryptographic primitives**: modular exponentiation with Montgomery and Barrett reduction
- **Multiplatform apps**: Consistent big-integer behavior across JVM/Native/JS
- **Learning**: Readable implementation for understanding arbitrary-precision arithmetic
- 
---

## 🔧 Installation

### Gradle

```kotlin
dependencies {
    implementation("com.decimal128:bigint:<version-coming-soon>")
}
```

BigInt is written in Kotlin and has no dependencies. 

---

## 🚀 Quick Start

### Creating values

BigInt values should be initialized from Kotlin primitive integer
and String types using the supplied extension functions:

```kotlin
val zero  = 0.toBigInt()
val small = 123456789L.toBigInt()
val dec   = "123456789012345678901234567890".toBigInt()
val nines = "-999_999_999_999_999".toBigInt()
val hex   = "0xCAFE_BABE_FACE_DEAD_BEEF_CEDE_FEED_BEAD_FADE".toBigInt()
```

### Basic arithmetic

```kotlin
val a = "123456789012345678901234567890".toBigInt()
val b = "9999999999888888888877777777776666666666".toBigInt()

val sum  = a + 123
val diff = a - 678
val prod = a * b
val quot = a / b
val rem  = a % 1_000_000_000_000_000_000uL
```

### Mixed primitive operations

Mixing with primitive integer operands allows clean infix expressions: 

```kotlin
val x = a + 5          // Int
val y = a * 42u        // UInt
val z = a - 123456789L // Long
```
No boxing means reduced heap pressure!

---

## 🧱 Internal Representation

- **Sign–magnitude**
- **Little-endian 32-bit limbs** stored in an `IntArray`

---

## 🧮 MutableBigInt

`MutableBigInt` is a mutable companion type for
**efficient in-place accumulation**, dramatically reducing 
temporary heap allocations during large summation-heavy
workloads and intense crypto calculations. 

### Basic usage

```kotlin
val s = MutableBigInt()
val s2 = MutableBigInt()
for (x in myBigData) {
    s += x 
    s2.addSquareOf(x)
}
val sum = BigInt.from(s)
val sumOfSquares = BigInt.from(s2)

```

```kotlin
// simple factorial
val f = MutableBigInt(1) // start at 1
for (i in 2..n)
    f *= i
val factorial = f.toBigInt()

```

Useful for statistical calculations on big data sets. 

## 🔒 Cryptography Support

BigInt provides primitives for cryptographic applications:

- **Prime testing**: Baillie-PSW (Miller-Rabin + Lucas-Selfridge)
- **Modular arithmetic**: `ModContext` with Montgomery reduction
- **Efficient operations**: Optimized for RSA-2048/4096, DH/DSA-3072 key sizes

⚠️ **Security Notice**: This library has not undergone formal security audit.
Cryptographic protocols (RSA, DH, DSA) require proper padding, key generation,
and implementation of security best practices beyond raw modular arithmetic.
For production cryptography, use established, audited libraries.

## 🎯 Design Philosophy

BigInt prioritizes:

- **Heap efficiency**: Mutable operations avoid temporary allocations
- **Kotlin-first API**: Idiomatic operators and multiplatform from day one
- **Transparency**: Readable implementation you can understand and audit
- **No dependencies**: Works everywhere Kotlin does

### vs java.math.BigInteger (JVM only)

`java.math.BigInteger` is mature and battle-tested, but:
- **No mutable variant**: Every operation allocates new objects
- **JVM-only**: Not available on Native or JS targets

BigInt does not implement some advanced algorithms (like Toom-Cook
and FFT multiplication) and has not been performance tested for 
operations involving tens-of-thousand of digits. 

BigInt's combination of optimized schoolbook multiplication, schoolbook
squaring, and Karatsuba squaring, low heap pressure, and mutable 
operations provides practical performance benefits. 

*Benchmarks coming soon.*

---

## 🏗️ Building

```bash
./gradlew build
```

Run tests:

```bash
./gradlew test
```

---

## 📄 License

This project is licensed under the MIT License — see the `LICENSE` file for details.

---

## 🙋 Contributing

We're especially interested in:
- **Platform testing**: Native/JS edge cases and platform-specific issues
- **Real-world use cases**: Share how you're using BigInt
- **Benchmarks**: Performance comparisons and measurements
- **Algorithm improvements**: Toom-Cook, optimizations, etc. 
