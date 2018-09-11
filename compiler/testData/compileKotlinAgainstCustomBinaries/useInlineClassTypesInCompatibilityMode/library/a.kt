package a

inline class Custom(val x: Int)

fun callCustom(c: Custom) {}
fun callUnsigned(): UInt = 42u
fun callResult(r: List<Result<Int>?>) {}

class Ctor(c: Custom)

val prop: UByte = 0u

typealias CustomAlias = Custom