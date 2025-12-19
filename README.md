# 📘 **BigInt – Arbitrary-Precision Integer Arithmetic for Kotlin Multiplatform**
## 🚧⚠️ **Warning! Construction Zone** ⚠️🚧
`BigInt` is currently **EXPERIMENTAL**. 
Aspects of the API are still subject to change. 

`BigInt` is a high-performance arbitrary-precision
signed integer type for **Kotlin Multiplatform**, designed to
bring efficient big-integer arithmetic to 
**JVM, Native, and JavaScript** with **no external dependencies**.

It provides idiomatic Kotlin arithmetic operators, efficient
mixed-primitive arithmetic, minimal heap churn, and a clean,
understandable implementation suitable for applications
needing *hundreds of digits* without
the complexity of `java.math.BigInteger`.

---

## ✨ Features

- **Kotlin Multiplatform** (JVM / Native / JS)
- **No dependencies**
- **Arbitrary-precision signed integers**
- Arithmetic infix operators: `+ - * / %`
- Comparator operators: `< <= == != >= >`
- Integer math functions: `sqr() isqrt() pow(n) abs() gcd() factorial(n)`
- Binary bit-manipulation and boolean operations
- Accepts primitive operands (`Int`, `UInt`, `Long`, `ULong`) without boxing
- Schoolbook multiplication (O(n²))
- Knuth’s Algorithm D for division
- Sign–magnitude representation with canonical zero
- Little-endian 32-bit limbs stored in an `IntArray`
- ByteArray serialization: big/little endian of twos-complement/magnitude
- Heap-friendly mutable accumulator: `BigIntAccumulator`

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

BigInt exposes **no public constructors**.
Instances created from Kotlin primitive integer and String types
are encouraged to use the supplied extension functions.  

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

```kotlin
val x = a + 5          // Int
val y = a * 42u        // UInt
val z = a - 123456789L // Long
```

All without boxing!

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

```factorial

val f = MutableBigInt().setOne() // start at 1
for (i in 2..n)
    f *= i
val factorial = f.toBigInt()

```

Useful for statistical calculations on big data sets. 

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

- **WANTED** KMP Kotlin Multiplatform users
- Pull requests welcome
- Open issues for bugs or enhancements
- Algorithmic suggestions/improvements are especially valued  
