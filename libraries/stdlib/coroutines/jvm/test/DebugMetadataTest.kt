/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")

import org.junit.Test
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.jvm.internal.*
import kotlin.test.assertEquals

/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

class DebugMetadataTest {
    @Test
    fun testRuntimeDebugMetadata() {
        val myContinuation = @DebugMetadata(
            runtimeSourceFiles = ["test.kt", "test1.kt", "test.kt"],
            runtimeLineNumbers = [10, 2, 11],
            debugIndexToLabel = [0, 0, 1, 1, 2],
            debugLocalNames = ["a", "b", "b", "c", "c"],
            debugSpilled = ["L$1", "L$2", "L$1", "L$2", "L$1"]
        ) object : BaseContinuationImpl(null) {
            override val context: CoroutineContext
                get() = EmptyCoroutineContext

            var label = 0

            override fun invokeSuspend(result: SuccessOrFailure<Any?>): Any? = null
        }

        myContinuation.label = 1
        assertEquals("test.kt" to 10, getSourceFileAndLineNumber(myContinuation))
        assertEquals("test.kt:10", getSourceFileAndLineNumberForDebugger(myContinuation))
        assertEquals(listOf("L$1", "a", "L$2", "b"), getVariableToSpilledMapping(myContinuation).toList())
        myContinuation.label = 2
        assertEquals("test1.kt" to 2, getSourceFileAndLineNumber(myContinuation))
        assertEquals("test1.kt:2", getSourceFileAndLineNumberForDebugger(myContinuation))
        assertEquals(listOf("L$1", "b", "L$2", "c"), getVariableToSpilledMapping(myContinuation).toList())
        myContinuation.label = 3
        assertEquals("test.kt" to 11, getSourceFileAndLineNumber(myContinuation))
        assertEquals("test.kt:11", getSourceFileAndLineNumberForDebugger(myContinuation))
        assertEquals(listOf("L$1", "c"), getVariableToSpilledMapping(myContinuation).toList())
    }
}
