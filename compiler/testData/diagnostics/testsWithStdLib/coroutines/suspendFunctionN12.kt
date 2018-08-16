// !LANGUAGE: +Coroutines
// SKIP_TXT

import kotlin.coroutines.*

fun test() {
    suspend {} is <!UNRESOLVED_REFERENCE!>SuspendFunction0<!><*>
}
