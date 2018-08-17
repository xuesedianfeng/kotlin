// !LANGUAGE: +ReleaseCoroutines
// WITH_COROUTINES
// WITH_REFLECT

import helpers.*
import kotlin.coroutines.*
import kotlin.reflect.full.callSuspendBy

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

class A {
    suspend fun noArgs() = "OK"

    suspend fun twoArgs(a: String, b: String) = "$a$b"
}

suspend fun twoArgs(a: String, b: String) = "$a$b"

fun ordinary() {}

suspend fun withDefault(s: String = "OK") = s

fun box(): String {
    var res: String? = ""
    builder {
        val callable = A::class.members.find { it.name == "noArgs" }!!
        res = callable.callSuspendBy(mapOf(callable.parameters.first() to A())) as String?
    }
    if (res != "OK") return res ?: "FAIL 1"
    builder {
        val callable = A::class.members.find { it.name == "twoArgs" }!!
        res = callable.callSuspendBy(mapOf(callable.parameters[0] to A(), callable.parameters[1] to "O", callable.parameters[2] to "K")) as String?
    }
    if (res != "OK") return res ?: "FAIL 2"
    builder {
        res = ::twoArgs.callSuspendBy(mapOf(::twoArgs.parameters[0] to "O", ::twoArgs.parameters[1] to "K")) as String?
    }
    if (res != "OK") return res ?: "FAIL 3"
    builder {
        try {
            res = ::ordinary.callSuspendBy(emptyMap()) as String?
        } catch (expected: IllegalArgumentException) {}
    }
    if (res != "OK") return res ?: "FAIL 4"
    builder {
        res = ::withDefault.callSuspendBy(emptyMap()) as String?
    }
    return res ?: "FAIL 5"
}