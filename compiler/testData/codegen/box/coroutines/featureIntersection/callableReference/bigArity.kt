// !LANGUAGE: +ReleaseCoroutines
// IGNORE_BACKEND: JS_IR, JS, NATIVE, JVM_IR
// WITH_REFLECT
// WITH_COROUTINES

import kotlin.reflect.full.callSuspend
import helpers.*
import kotlin.coroutines.*

class A {
    suspend fun foo(
        p00: A, p01: A, p02: A, p03: A, p04: A, p05: A, p06: A, p07: A, p08: A, p09: A,
        p10: A, p11: A, p12: A, p13: A, p14: A, p15: A, p16: A, p17: A, p18: A, p19: A,
        p20: A, p21: A, p22: A, p23: A, p24: A, p25: A, p26: A, p27: A, p28: A, p29: String
    ): String {
        return p29
    }
}

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

suspend fun expectsLambdaWithBigArity(c: suspend (A, A, A, A, A, A, A, A, A, A,
                                                  A, A, A, A, A, A, A, A, A, A,
                                                  A, A, A, A, A, A, A, A, A, String) -> String): String {
    val a = A()
    return c.invoke(a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, "OK")
}

fun box(): String {
    val a = A()
    var res = "FAIL 1"
    builder {
        res = A::foo.callSuspend(a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, a, "OK")
    }
    if (res != "OK") return res
    res = "FAIL 2"
    builder {
        res = expectsLambdaWithBigArity { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, s -> s }
    }
    return res
}
