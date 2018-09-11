package b

import a.*

fun test(c: Custom, ca: CustomAlias) {
    callCustom(c)
    callUnsigned()
    callResult(listOf(null))
    Ctor(c)
    prop
    CustomAlias(0)
}