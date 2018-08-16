fun once(p: Int): Int {
    val v = p + 1
    return v
}

fun callShadow() {
    val v = <caret>once(1)
}