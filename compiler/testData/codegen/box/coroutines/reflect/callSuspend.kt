// !LANGUAGE: +ReleaseCoroutines
// WITH_COROUTINES
// WITH_REFLECT

import helpers.*
import kotlin.coroutines.*
import kotlin.reflect.full.callSuspend

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

class A {
    suspend fun noArgs() = "OK"

    suspend fun twoArgs(a: String, b: String) = "$a$b"
}

suspend fun twoArgs(a: String, b: String) = "$a$b"

fun ordinary() = "OK"

fun box(): String {
    var res: String? = ""
    builder {
        res = A::class.members.find { it.name == "noArgs" }?.callSuspend(A()) as String?
    }
    if (res != "OK") return res ?: "FAIL 1"
    builder {
        res = A::class.members.find { it.name == "twoArgs" }?.callSuspend(A(), "O", "K") as String?
    }
    if (res != "OK") return res ?: "FAIL 2"
    builder {
        res = ::twoArgs.callSuspend("O", "K") as String?
    }
    if (res != "OK") return res ?: "FAIL 3"
    builder {
        res = ::ordinary.callSuspend() as String?
    }
    return res ?: "FAIL 4"
}