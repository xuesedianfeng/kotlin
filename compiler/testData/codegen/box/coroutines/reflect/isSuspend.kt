// !LANGUAGE: +ReleaseCoroutines
// WITH_REFLECT

import kotlin.reflect.KCallable
import kotlin.reflect.full.isSuspend

suspend fun dummy() {}
fun ordinary() {}
val property = ""

@Suppress("WRONG_MODIFIER_TARGET")
suspend val coroutineContext = Unit

fun box(): String {
    if (!(::dummy as KCallable<*>).isSuspend) return "FAIL 1"
    if ((::ordinary as KCallable<*>).isSuspend) return "FAIL 2"
    if (::property.isSuspend) return "FAIL 3"
    if (::coroutineContext.isSuspend) return "FAIL 4"
    return "OK"
}
