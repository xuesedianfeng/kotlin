// !LANGUAGE: +ReleaseCoroutines

// IGNORE_BACKEND: JVM_IR
// TARGET_BACKEND: JVM
// WITH_RUNTIME
// WITH_COROUTINES

import helpers.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.coroutines.jvm.internal.*

suspend fun getSourceFileAndLineNumberFromContinuation() = suspendCoroutineUninterceptedOrReturn<Pair<String, Int>> {
    getSourceFileAndLineNumber(it)
}

var continuation: Continuation<*>? = null

suspend fun suspendHere() = suspendCoroutineUninterceptedOrReturn<Unit> {
    continuation = it
    COROUTINE_SUSPENDED
}

suspend fun dummy() {}

suspend fun named(): Pair<String, Int> {
    dummy()
    return getSourceFileAndLineNumberFromContinuation()
}

suspend fun suspended() {
    dummy()
    suspendHere()
}

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

fun box(): String {
    var res: Any? = null
    builder {
        res = named()
    }
    if (res != Pair("runtimeDebugMetadata.kt", 28)) {
        return "" + res
    }
    builder {
        dummy()
        res = getSourceFileAndLineNumberFromContinuation()
    }
    if (res != Pair("runtimeDebugMetadata.kt", 50)) {
        return "" + res
    }

    builder {
        suspended()
    }
    res = getSourceFileAndLineNumber(continuation!!)
    if (res != Pair("runtimeDebugMetadata.kt", 33)) {
        return "" + res
    }
    return "OK"
}