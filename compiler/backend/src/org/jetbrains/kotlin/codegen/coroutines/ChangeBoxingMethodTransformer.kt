/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.coroutines

import jdk.internal.org.objectweb.asm.Opcodes
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.codegen.optimization.boxing.isPrimitiveBoxing
import org.jetbrains.kotlin.codegen.optimization.common.ControlFlowGraph
import org.jetbrains.kotlin.codegen.optimization.common.asSequence
import org.jetbrains.kotlin.codegen.optimization.transformer.MethodTransformer
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode

object ChangeBoxingMethodTransformer : MethodTransformer() {
    override fun transform(internalClassName: String, methodNode: MethodNode) {
        val boxings = methodNode.instructions.asSequence().filter { it.isPrimitiveBoxing() }.toList()
        if (boxings.isEmpty()) return
        val cfg = ControlFlowGraph.build(methodNode)
        val list = boxings.mapNotNull { boxing ->
            assert(boxing.opcode == Opcodes.INVOKESTATIC) {
                "boxing should be INVOKESTATIC class.valueOf"
            }
            val index = methodNode.instructions.indexOf(boxing)
            val source = methodNode.instructions[index - 1]
            if (!source.isPrimitiveCreate() && !source.isPrimitiveTransformer()) return@mapNotNull null
            val succs = cfg.getSuccessorsIndices(source)
            if (succs.size != 1) return@mapNotNull null
            if (succs[0] != methodNode.instructions.indexOf(boxing)) return@mapNotNull null

            boxing to source
        }

        for ((boxing, source) in list) {
            boxing as MethodInsnNode
            methodNode.instructions.insert(boxing, withInstructionAdapter {
                val type = Type.getType("L${boxing.owner};")
                val unboxType = AsmUtil.unboxType(type)
                if (source.isPrimitiveTransformer()) {
                    store(methodNode.maxLocals, unboxType)
                }
                anew(type)
                dup()
                if (source.isPrimitiveCreate()) {
                    source.accept(this)
                } else {
                    load(methodNode.maxLocals, unboxType)
                }
                invokespecial(boxing.owner, "<init>", "($unboxType)V", false)
            })
            methodNode.instructions.remove(boxing)
            if (source.isPrimitiveCreate()) {
                methodNode.instructions.remove(source)
            }
        }
    }
}

private fun AbstractInsnNode.isPrimitiveTransformer() = when (opcode) {
    Opcodes.BALOAD,
    Opcodes.CALOAD,
    Opcodes.D2F,
    Opcodes.D2I,
    Opcodes.D2L,
    Opcodes.DADD,
    Opcodes.DALOAD,
    Opcodes.DDIV,
    Opcodes.DMUL,
    Opcodes.DNEG,
    Opcodes.DREM,
    Opcodes.DSUB,
    Opcodes.DUP,
    Opcodes.DUP_X1,
    Opcodes.DUP_X2,
    Opcodes.DUP2,
    Opcodes.DUP2_X1,
    Opcodes.DUP2_X2,
    Opcodes.F2D,
    Opcodes.F2I,
    Opcodes.F2L,
    Opcodes.FADD,
    Opcodes.FALOAD,
    Opcodes.FDIV,
    Opcodes.FMUL,
    Opcodes.FNEG,
    Opcodes.FREM,
    Opcodes.FSUB,
    Opcodes.GETFIELD,
    Opcodes.I2B,
    Opcodes.I2C,
    Opcodes.I2D,
    Opcodes.I2F,
    Opcodes.I2L,
    Opcodes.I2S,
    Opcodes.IAND,
    Opcodes.IALOAD,
    Opcodes.IADD,
    Opcodes.IDIV,
    Opcodes.IMUL,
    Opcodes.IOR,
    Opcodes.IREM,
    Opcodes.ISHL,
    Opcodes.ISHR,
    Opcodes.ISUB,
    Opcodes.IUSHR,
    Opcodes.IXOR,
    Opcodes.L2D,
    Opcodes.L2F,
    Opcodes.L2I,
    Opcodes.LADD,
    Opcodes.LALOAD,
    Opcodes.LAND,
    Opcodes.LMUL,
    Opcodes.LNEG,
    Opcodes.LOR,
    Opcodes.LSHR,
    Opcodes.LSUB,
    Opcodes.LUSHR,
    Opcodes.LXOR,
    Opcodes.SALOAD,
    Opcodes.SWAP,
    Opcodes.INVOKEDYNAMIC,
    Opcodes.INVOKEVIRTUAL,
    Opcodes.INVOKESPECIAL,
    Opcodes.INVOKEINTERFACE,
    Opcodes.INVOKESTATIC -> true
    else -> false
}

private fun AbstractInsnNode.isPrimitiveCreate() = when (opcode) {
    Opcodes.BIPUSH,
    Opcodes.DCONST_0,
    Opcodes.DCONST_1,
    Opcodes.DLOAD,
    Opcodes.FCONST_0,
    Opcodes.FCONST_1,
    Opcodes.FCONST_2,
    Opcodes.FLOAD,
    Opcodes.GETSTATIC,
    Opcodes.ICONST_M1,
    Opcodes.ICONST_0,
    Opcodes.ICONST_1,
    Opcodes.ICONST_2,
    Opcodes.ICONST_3,
    Opcodes.ICONST_4,
    Opcodes.ICONST_5,
    Opcodes.ILOAD,
    Opcodes.LCONST_0,
    Opcodes.LCONST_1,
    Opcodes.LDC,
    Opcodes.LDIV,
    Opcodes.LLOAD,
    Opcodes.SIPUSH -> true
    else -> false
}