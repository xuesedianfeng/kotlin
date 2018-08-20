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

/**
 * Returns file name and line number of current coroutine's suspension point. The coroutine can be either running coroutine, that calls
 * the function on its continuation and obtaining the information about current file and line number, or, more likely, the function is
 * called to produce debug-friendly [toString] output of suspended coroutine (i.e. where it has suspended).
 * Additionally, the function is used to obtain [StackTraceElement]s from continuations' stack in order to create more natural stack traces.
 */
@SinceKotlin("1.3")
public fun getSourceFileAndLineNumber(c: Continuation<*>): Pair<String, Int> {
    val debugMetadata = c.getDebugMetadataAnnotation()
    return debugMetadata.runtimeSourceFiles.zip(debugMetadata.runtimeLineNumbers.asList())[c.getLabel() ?: return "" to -1]
}

/**
 * The same as [getSourceFileAndLineNumber], but returns [String] instead of [Pair] to simplify data extraction on debugger's end.
 * The return string has the following pattern: "$fileName:$lineNumber".
 */
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

/**
 * Returns an array of spilled variable names and continuation's field names where the variable has been spilled.
 * The structure is the following:
 * - field names take 2*k'th indices
 * - corresponding variable names take (2*k + 1)'th indices.
 *
 * Like [getSourceFileAndLineNumberForDebugger], the function is for debugger to use, thus it returns simplest data type possible.
 * This function should only be called on suspended coroutines to get accurate mapping.
 */
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