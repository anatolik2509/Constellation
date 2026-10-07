package org.example.constellation.core.analyze

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

internal class RawMethod(val name: String, val descriptor: String, val access: Int)

internal class RawCall(val owner: String, val name: String, val descriptor: String)

internal class RawClassInfo(
    val name: String,
    val access: Int,
    val superName: String?,
    val interfaces: List<String>,
    /** Non-static, non-synthetic instance field names. */
    val instanceFields: List<String>,
    val recordComponents: List<String>,
    val methods: List<RawMethod>,
    val calls: List<RawCall>,
    val sourceFile: String?,
)

/** Single-class ASM extraction. Only SKIP_FRAMES: SKIP_DEBUG would drop visitSource, SKIP_CODE the calls. */
internal class ClassScanner {

    fun scan(bytes: ByteArray): RawClassInfo {
        val visitor = ScannerVisitor()
        ClassReader(bytes).accept(visitor, ClassReader.SKIP_FRAMES)
        return visitor.build()
    }

    private class ScannerVisitor : ClassVisitor(Opcodes.ASM9) {
        private var name: String = ""
        private var access: Int = 0
        private var superName: String? = null
        private var sourceFile: String? = null
        private val interfaces = mutableListOf<String>()
        private val instanceFields = mutableListOf<String>()
        private val recordComponents = mutableListOf<String>()
        private val methods = mutableListOf<RawMethod>()
        private val calls = mutableListOf<RawCall>()

        fun build() = RawClassInfo(
            name = name,
            access = access,
            superName = superName,
            interfaces = interfaces.toList(),
            instanceFields = instanceFields.toList(),
            recordComponents = recordComponents.toList(),
            methods = methods.toList(),
            calls = calls.toList(),
            sourceFile = sourceFile,
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
            if (name != null &&
                access and Opcodes.ACC_STATIC == 0 &&
                access and Opcodes.ACC_SYNTHETIC == 0
            ) {
                instanceFields += name
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
                        calls += RawCall(owner, name, descriptor)
                    }
                }

                override fun visitInvokeDynamicInsn(
                    name: String?,
                    descriptor: String?,
                    bootstrapMethodHandle: Handle?,
                    vararg bootstrapMethodArguments: Any?,
                ) {
                    // Lambdas / method references: the implementation method handle is the real call target.
                    if (bootstrapMethodHandle?.owner == "java/lang/invoke/LambdaMetafactory") {
                        for (arg in bootstrapMethodArguments) {
                            if (arg is Handle && isMethodHandle(arg)) {
                                if (!arg.owner.startsWith("[")) {
                                    calls += RawCall(arg.owner, arg.name, arg.desc)
                                }
                            }
                        }
                    }
                }
            }
        }

        private fun isMethodHandle(h: Handle): Boolean =
            h.tag in Opcodes.H_INVOKEVIRTUAL..Opcodes.H_INVOKEINTERFACE
    }
}
