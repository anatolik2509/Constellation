package org.example.constellation.core.properties

import org.example.constellation.core.analyze.RawClassInfo

/** One declared method as seen by the analyzer — technical, providers-only. */
class MethodDecl(
    val name: String,
    val descriptor: String,
    val isStatic: Boolean,
    val isSynthetic: Boolean,
)

/**
 * The only sanctioned technical surface: property providers may look at bytecode
 * facts here, but nothing they return may leak into the model.
 * Constructed only by the analyzer.
 */
class ClassContext internal constructor(raw: RawClassInfo) {
    val name: String = raw.name
    val isInterface: Boolean = raw.access and 0x0200 != 0
    val isAnnotation: Boolean = raw.access and 0x2000 != 0
    val isEnum: Boolean = raw.access and 0x4000 != 0
    val isSynthetic: Boolean = raw.access and 0x1000 != 0
    val superName: String? = raw.superName
    val interfaces: List<String> = raw.interfaces
    val instanceFields: List<String> = raw.instanceFields
    val recordComponents: List<String> = raw.recordComponents
    val methods: List<MethodDecl> = raw.methods.map {
        MethodDecl(
            name = it.name,
            descriptor = it.descriptor,
            isStatic = it.access and 0x0008 != 0,
            isSynthetic = it.access and (0x1000 or 0x0040) != 0, // ACC_SYNTHETIC | ACC_BRIDGE
        )
    }
}
