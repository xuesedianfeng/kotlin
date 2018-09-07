/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

@file:JvmName("Boxing")

@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "ConstantConditionIf")

package kotlin.coroutines.jvm.internal

/*
 * A flag to switch between boxing strategies for coroutines. We use two different strategies for boxing primitives in coroutine code:
 * 1) Use standard [valueOf] method.
 * 2) Allocate primitive wrapper object every time.
 *
 * The latter is default.
 *
 * This is a static final boolean field for HotSpot to optimize it away.
 */
private val USE_VALUE_OF = false

/*
 * Box primitive to Java wrapper class, using either standard [valueOf] method or by allocating the wrapper object, depending on value of
 * [USE_VALUE_OF] flag.
 *
 * The latter allows HotSpot JIT to eliminate allocations completely in coroutines code with primitives.
 */

@SinceKotlin("1.3")
internal fun boxBoolean(primitive: Boolean): java.lang.Boolean = java.lang.Boolean.valueOf(primitive) as java.lang.Boolean

@SinceKotlin("1.3")
internal fun boxByte(primitive: Byte): java.lang.Byte = java.lang.Byte.valueOf(primitive) as java.lang.Byte

@SinceKotlin("1.3")
internal fun boxShort(primitive: Short): java.lang.Short =
    if (USE_VALUE_OF) java.lang.Short.valueOf(primitive) as java.lang.Short
    else java.lang.Short(primitive)

@SinceKotlin("1.3")
internal fun boxInt(primitive: Int): java.lang.Integer =
    if (USE_VALUE_OF) java.lang.Integer.valueOf(primitive) as java.lang.Integer
    else java.lang.Integer(primitive)

@SinceKotlin("1.3")
internal fun boxLong(primitive: Long): java.lang.Long =
    if (USE_VALUE_OF) java.lang.Long.valueOf(primitive) as java.lang.Long
    else java.lang.Long(primitive)

@SinceKotlin("1.3")
internal fun boxFloat(primitive: Float): java.lang.Float = java.lang.Float(primitive)

@SinceKotlin("1.3")
internal fun boxDouble(primitive: Double): java.lang.Double = java.lang.Double(primitive)

@SinceKotlin("1.3")
internal fun boxChar(primitive: Char): java.lang.Character =
    if (USE_VALUE_OF) java.lang.Character.valueOf(primitive) as java.lang.Character
    else java.lang.Character(primitive)
