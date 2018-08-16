/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.jps.platforms

import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.util.JpsPathUtil
import org.jetbrains.kotlin.config.TargetPlatformKind
import org.jetbrains.kotlin.jps.model.targetPlatform
import org.jetbrains.kotlin.utils.LibraryUtils

internal class KotlinTargetBindingsLoader internal constructor(val compileContext: CompileContext) {
    private val byJpsModuleBuildTarget = mutableMapOf<ModuleBuildTarget, KotlinModuleBuildTarget<*>>()
    private val isKotlinJsStdlibJar = mutableMapOf<String, Boolean>()

    fun build() = byJpsModuleBuildTarget

    fun ensureLoaded(target: ModuleBuildTarget): KotlinModuleBuildTarget<*>? {
        return byJpsModuleBuildTarget.computeIfAbsent(target) {
            when (target.module.targetPlatform ?: detectTargetPlatform(target)) {
                is TargetPlatformKind.Common -> KotlinCommonModuleBuildTarget(compileContext, target)
                is TargetPlatformKind.JavaScript -> KotlinJsModuleBuildTarget(compileContext, target)
                is TargetPlatformKind.Jvm -> KotlinJvmModuleBuildTarget(compileContext, target)
            }
        }
    }

    /**
     * Compatibility for KT-14082
     * todo: remove when all projects migrated to facets
     */
    private fun detectTargetPlatform(target: ModuleBuildTarget): TargetPlatformKind<*> {
        if (hasJsStdLib(target)) return TargetPlatformKind.JavaScript

        return TargetPlatformKind.DEFAULT_PLATFORM
    }

    private fun hasJsStdLib(target: ModuleBuildTarget): Boolean {
        KotlinJvmModuleBuildTarget(compileContext, target).allDependencies.libraries.forEach { library ->
            for (root in library.getRoots(JpsOrderRootType.COMPILED)) {
                val url = root.url

                val isKotlinJsLib = isKotlinJsStdlibJar.computeIfAbsent(url) {
                    LibraryUtils.isKotlinJavascriptStdLibrary(JpsPathUtil.urlToFile(url))
                }

                if (isKotlinJsLib) return true
            }
        }

        return false
    }
}