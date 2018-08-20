/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package kotlin.coroutines.jvm.internal

import kotlin.coroutines.Continuation

// TODO: Uncomment when KT-25372 is fixed
@Target(AnnotationTarget.CLASS)
@SinceKotlin("1.3")
internal annotation class DebugMetadata(
    // @JvmName("a")
    val runtimeSourceFiles: Array<String>,
    // @JvmName("b")
    val runtimeLineNumbers: IntArray,
    // @JvmName("c")
    val debugLocalNames: Array<String>,
    // @JvmName("d")
    val debugSpilled: Array<String>,
    // @JvmName("e")
    val debugIndexToLabel: IntArray
)

@SinceKotlin("1.3")
public fun getSourceFileAndLineNumber(c: Continuation<*>): Pair<String, Int> {
    val debugMetadata = c.getDebugMetadataAnnotation()
    return debugMetadata.runtimeSourceFiles.zip(debugMetadata.runtimeLineNumbers.asList())[c.getLabel() ?: return "" to -1]
}

@SinceKotlin("1.3")
public fun getSourceFileAndLineNumberForDebugger(c: Continuation<*>): String {
    val pair = getSourceFileAndLineNumber(c)
    return if (pair.second < 0) "" else "${pair.first}:${pair.second}"
}

private fun Continuation<*>.getDebugMetadataAnnotation(): DebugMetadata {
    this as BaseContinuationImpl
    return javaClass.annotations.filterIsInstance<DebugMetadata>()[0]
}

private fun Continuation<*>.getLabel(): Int? {
    val field = javaClass.getDeclaredField("label") ?: return null
    field.isAccessible = true
    return field.get(this) as Int - 1
}

@SinceKotlin("1.3")
public fun getVariableToSpilledMapping(c: Continuation<*>): Array<String> {
    val debugMetadata = c.getDebugMetadataAnnotation()
    val res = arrayListOf<String>()
    val label = c.getLabel()
    for ((i, labelOfIndex) in debugMetadata.debugIndexToLabel.withIndex()) {
        if (labelOfIndex == label) {
            res.add(debugMetadata.debugSpilled[i])
            res.add(debugMetadata.debugLocalNames[i])
        }
    }
    return res.toTypedArray()
}