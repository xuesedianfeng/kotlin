/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.codegen.inline

object SMAPParser {
    /*null smap means that there is no any debug info in file (e.g. sourceName)*/
    @JvmStatic
    fun parseOrCreateDefault(mappingInfo: String?, source: String?, path: String, methodStartLine: Int, methodEndLine: Int): SMAP {
        if (mappingInfo != null && mappingInfo.isNotEmpty()) {
            return parse(mappingInfo)
        }

        val mapping =
                if (source == null || source.isEmpty() || methodStartLine > methodEndLine)
                    FileMapping.SKIP
                else
                    FileMapping(source, path).apply {
                        if (methodStartLine <= methodEndLine) {
                            //one to one
                            addRangeMapping(RangeMapping(methodStartLine, methodStartLine, methodEndLine - methodStartLine + 1))
                        }
                    }

        return SMAP(listOf(mapping))
    }

    @JvmStatic
    fun parse(mappingInfo: String): SMAP {
        val fileMappings = linkedMapOf<Int, FileMapping>()

        val fileSection = mappingInfo.substringBefore(SMAP.LINE_SECTION).substringAfter(SMAP.FILE_SECTION)

        val fileLines = fileSection.lineSequence()

        val iterator = fileLines.iterator()
        while (iterator.hasNext()) {
            val fileDeclaration = iterator.next().trim()
            if (fileDeclaration.isEmpty()) continue
            if (!fileDeclaration.startsWith('+')) {
                throw AssertionError("File declaration should be in extended form, but: $fileDeclaration in $fileSection")
            }

            val indexAndFileInternalName = fileDeclaration.substringAfter("+ ")
            val indexEnd = indexAndFileInternalName.indexOf(' ')
            val fileIndex = indexAndFileInternalName.substring(0, indexEnd).toInt()
            val fileName = indexAndFileInternalName.substring(indexEnd + 1).trim()
            val path = iterator.next().trim()
            fileMappings[fileIndex] = FileMapping(fileName, path)
        }

        val lines = mappingInfo.substringAfter(SMAP.LINE_SECTION).substringBefore(SMAP.END).trim().split('\n')
        for (lineMapping in lines) {
            /*only simple mapping now*/
            val targetSplit = lineMapping.indexOf(':')
            val originalPart = lineMapping.substring(0, targetSplit)
            val rangeSeparator = originalPart.indexOf(',').let { if (it < 0) targetSplit else it }

            val fileSeparator = lineMapping.indexOf('#')
            val originalIndex = originalPart.substring(0, fileSeparator).toInt()
            val range = if (rangeSeparator == targetSplit) 1 else originalPart.substring(rangeSeparator + 1, targetSplit).toInt()

            val fileIndex = lineMapping.substring(fileSeparator + 1, rangeSeparator).toInt()
            val targetIndex = lineMapping.substring(targetSplit + 1).toInt()
            fileMappings[fileIndex]!!.addRangeMapping(RangeMapping(originalIndex, targetIndex, range))
        }

        return SMAP(fileMappings.values.toList())
    }
}
