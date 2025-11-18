# 📘 **HugeInt – Arbitrary-Precision Integer Arithmetic for Kotlin Multiplatform**
## 🚧⚠️ **Warning! Construction Zone** ⚠️🚧
`HugeInt` is currently **EXPERIMENTAL**. 
Aspects of the API are still subject to change. 

`HugeInt` is a lightweight, high-performance arbitrary-precision
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
- Heap-friendly mutable accumulator: **HugeIntAccumulator**

---

## 🔧 Installation

### Gradle

```kotlin
dependencies {
    implementation("com.decimal128:hugeint:<version-coming-soon>")
}
```

HugeInt is written in Kotlin and has no dependencies. 

---

## 🚀 Quick Start

### Creating values

HugeInt exposes **no public constructors**.  
All instances must be created through `HugeInt.from()` factory methods:

```kotlin
val zero  = HugeInt.ZERO
val small = HugeInt.from(123456789L)
val dec   = HugeInt.from("123456789012345678901234567890")
val nines = HugeInt.from("-999_999_999_999_999")
val hex   = HugeInt.from("0xCAFE_BABE_FACE_DEAD_BEEF_CEDE_FEED_BEAD_FADE")
```

### Basic arithmetic

```kotlin
val a = HugeInt.from("123456789012345678901234567890")
val b = HugeInt.from(987654321)

val sum  = a + b
val diff = a - 42
val prod = a * b
val quot = a / b
val rem  = a % b
```

### Mixed primitive operations

```kotlin
val x = a + 5          // Int
val y = a * 42u        // UInt
val z = a - 123456789L // Long
```

All without boxing.

---

## 🧱 Internal Representation

- **Sign–magnitude**
- **Little-endian 32-bit limbs** stored in an `IntArray`

---

## 🧮 HugeIntAccumulator

`HugeIntAccumulator` is a mutable companion type for
**efficient in-place accumulation**, dramatically reducing 
temporary allocations during summation-heavy workloads.

### Basic usage

```kotlin
val s = HugeIntAccumulator()
val s2 = HugeIntAccumulator()
for (x in myBigData) {
    s += x 
    s2.addSquareOf(x)
}
val sum = HugeInt.from(s)
val sumOfSquares = HugeInt.from(s2)

```

```factorial

val f = HugeIntAccumulator().set(1) // start at 1
for (i in 2..n)
    f *= i
val factorial = HugeInt.from(f)

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
