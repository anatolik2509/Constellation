package org.example.constellation.core.analyze

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/** Single-class ASM extraction. Only SKIP_FRAMES: SKIP_DEBUG would drop visitSource, SKIP_CODE the calls. */
internal class ClassScanner {

    fun scan(bytes: ByteArray): RawClassInfo {
        val visitor = ScannerVisitor()
        ClassReader(bytes).accept(visitor, ClassReader.SKIP_FRAMES)
        return visitor.build()
    }

    private companion object {
        const val LAMBDA_METAFACTORY = "java/lang/invoke/LambdaMetafactory"
    }

    private class ScannerVisitor : ClassVisitor(Opcodes.ASM9) {
        private var name: String = ""
        private var access: Int = 0
        private var superName: String? = null
        private var sourceFile: String? = null
        private var enclosingMethod: RawEnclosing? = null
        private var nestHost: String? = null
        private var innerOuterName: String? = null
        private var innerSelfAccess: Int = 0
        private val interfaces = mutableListOf<String>()
        private val instanceFields = mutableListOf<String>()
        private val staticFields = mutableListOf<RawField>()
        private val recordComponents = mutableListOf<String>()
        private val methods = mutableListOf<RawMethod>()
        private val calls = mutableListOf<RawCall>()

        fun build() = RawClassInfo(
            name = name,
            access = access,
            superName = superName,
            interfaces = interfaces.toList(),
            instanceFields = instanceFields.toList(),
            staticFields = staticFields.toList(),
            recordComponents = recordComponents.toList(),
            methods = methods.toList(),
            calls = calls.toList(),
            sourceFile = sourceFile,
            enclosingMethod = enclosingMethod,
            nestHost = nestHost,
            innerOuterName = innerOuterName,
            innerSelfAccess = innerSelfAccess,
        )

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>,
        ) {
            this.name = name
            this.access = access
            this.superName = superName
            this.interfaces += interfaces
        }

        override fun visitSource(source: String?, debug: String?) {
            sourceFile = source
        }

        override fun visitNestHost(nestHost: String?) {
            this.nestHost = nestHost
        }

        override fun visitOuterClass(owner: String?, name: String?, descriptor: String?) {
            if (owner != null) enclosingMethod = RawEnclosing(owner, name, descriptor)
        }

        override fun visitInnerClass(name: String?, outerName: String?, innerName: String?, access: Int) {
            if (name == this.name) {
                innerOuterName = outerName
                innerSelfAccess = access
            }
        }

        override fun visitRecordComponent(
            name: String?,
            descriptor: String?,
            signature: String?,
        ): org.objectweb.asm.RecordComponentVisitor? {
            if (name != null) recordComponents += name
            return null
        }

        override fun visitField(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            value: Any?,
        ): FieldVisitor? {
            if (name != null && descriptor != null && access and Opcodes.ACC_SYNTHETIC == 0) {
                if (access and Opcodes.ACC_STATIC == 0) {
                    instanceFields += name
                } else {
                    staticFields += RawField(name, descriptor)
                }
            }
            return null
        }

        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor? {
            if (name == null || descriptor == null) return null
            methods += RawMethod(name, descriptor, access)
            return object : MethodVisitor(Opcodes.ASM9) {
                override fun visitMethodInsn(
                    opcode: Int,
                    owner: String,
                    name: String,
                    descriptor: String,
                    isInterface: Boolean,
                ) {
                    // Array owners (`[Ljava/lang/Object;.clone`) have no class node.
                    if (!owner.startsWith("[")) {
                        calls += RawCall(owner, name, descriptor, isStatic = opcode == Opcodes.INVOKESTATIC)
                    }
                }

                override fun visitInvokeDynamicInsn(
                    name: String?,
                    descriptor: String?,
                    bootstrapMethodHandle: Handle?,
                    vararg bootstrapMethodArguments: Any?,
                ) {
                    // Lambdas / method references: the implementation method handle is the real call target.
                    if (bootstrapMethodHandle?.owner != LAMBDA_METAFACTORY) return
                    for (arg in bootstrapMethodArguments) {
                        if (arg !is Handle || !isMethodHandle(arg) || arg.owner.startsWith("[")) continue
                        calls += RawCall(arg.owner, arg.name, arg.desc, isStatic = arg.tag == Opcodes.H_INVOKESTATIC)
                    }
                }
            }
        }

        private fun isMethodHandle(h: Handle): Boolean =
            h.tag in Opcodes.H_INVOKEVIRTUAL..Opcodes.H_INVOKEINTERFACE
    }
}
