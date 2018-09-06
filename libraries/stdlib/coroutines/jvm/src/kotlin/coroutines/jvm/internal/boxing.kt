/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

@file:JvmName("Boxing")

@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "ConstantConditionIf")

package kotlin.coroutines.jvm.internal

private val USE_VALUE_OF = false

/*
 * Box primitive to Java wrapper class, using either default [valueOf] method or by allocating the wrapper object.
 *
 * The latter allows HotSpot JIT to eliminate allocations completely in coroutines code with primitives.
 * Byte and Boolean versions are missing, since their [valueOf] methods return cached objects without allocating them.
 */

@SinceKotlin("1.3")
internal fun boxShort(prim: Short): java.lang.Short =
    if (USE_VALUE_OF) java.lang.Short.valueOf(prim) as java.lang.Short
    else java.lang.Short(prim)

@SinceKotlin("1.3")
internal fun boxInt(prim: Int): java.lang.Integer =
    if (USE_VALUE_OF) java.lang.Integer.valueOf(prim) as java.lang.Integer
    else java.lang.Integer(prim)

internal fun boxLong(prim: Long): java.lang.Long =
    if (USE_VALUE_OF) java.lang.Long.valueOf(prim) as java.lang.Long
    else java.lang.Long(prim)

@SinceKotlin("1.3")
internal fun boxFloat(prim: Float): java.lang.Float =
    if (USE_VALUE_OF) java.lang.Float.valueOf(prim) as java.lang.Float
    else java.lang.Float(prim)

@SinceKotlin("1.3")
internal fun boxDouble(prim: Double): java.lang.Double =
    if (USE_VALUE_OF) java.lang.Double.valueOf(prim) as java.lang.Double
    else java.lang.Double(prim)

@SinceKotlin("1.3")
internal fun boxChar(prim: Char): java.lang.Character =
    if (USE_VALUE_OF) java.lang.Character.valueOf(prim) as java.lang.Character
    else java.lang.Character(prim)
