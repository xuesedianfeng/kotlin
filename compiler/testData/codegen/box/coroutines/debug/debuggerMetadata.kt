// TODO:
// LANGUAGE_VERSION: 1.3

// TARGET_BACKEND: JVM
// WITH_RUNTIME
// WITH_COROUTINES

import helpers.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.coroutines.jvm.internal.*

suspend fun getVariableToSpilled() = suspendCoroutineUninterceptedOrReturn<Array<String>> {
    getVariableToSpilledMapping(it)
}

fun Array<String>.toMap(): Map<String, String> {
    val res = hashMapOf<String, String>()
    for (i in 0..(size - 1) step 2) {
        res[get(i)] = get(i + 1)
    }
    return res
}

var continuation: Continuation<*>? = null

suspend fun suspendHere() = suspendCoroutineUninterceptedOrReturn<Unit> {
    continuation = it
    COROUTINE_SUSPENDED
}

suspend fun dummy() {}

suspend fun named(): String {
    dummy()
    val s = ""
    return getVariableToSpilled().toMap()["L$0"] ?: "named fail"
}

suspend fun suspended() {
    dummy()
    val ss = ""
    suspendHere()
}

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

fun box(): String {
    var res: String = ""
    builder {
        res = named()
    }
    if (res != "s") {
        return "" + res
    }
    builder {
        dummy()
        val a = ""
        res = getVariableToSpilled().toMap()["L$0"] ?: "lambda fail"
    }
    if (res != "a") {
        return "" + res
    }

    builder {
        suspended()
    }
    res = getVariableToSpilledMapping(continuation!!).toMap()["L$0"] ?: "suspended fail"
    if (res != "ss") {
        return "" + res
    }
    return "OK"
}