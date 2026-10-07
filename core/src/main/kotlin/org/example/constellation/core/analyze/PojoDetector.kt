package org.example.constellation.core.analyze

import org.objectweb.asm.Opcodes

/**
 * Bytecode-only heuristic for "noise" data carriers: a class that has only fields,
 * getters/setters, Object overrides, and Kotlin data-class members (copy/componentN).
 */
internal object PojoDetector {

    fun isPojo(c: RawClassInfo): Boolean {
        val acc = c.access
        if (acc and (Opcodes.ACC_INTERFACE or Opcodes.ACC_ANNOTATION or Opcodes.ACC_ENUM or Opcodes.ACC_SYNTHETIC) != 0) {
            return false
        }
        val fields = c.instanceFields.toSet() + c.recordComponents.toSet()
        if (fields.isEmpty()) return false
        return c.methods.all { isAllowedPojoMethod(it, fields, c.recordComponents.toSet()) }
    }

    private fun isAllowedPojoMethod(m: RawMethod, fields: Set<String>, recordComponents: Set<String>): Boolean {
        val isStatic = m.access and Opcodes.ACC_STATIC != 0
        val synthetic = m.access and (Opcodes.ACC_SYNTHETIC or Opcodes.ACC_BRIDGE) != 0
        if (synthetic) return true
        if (m.name == "<init>" || m.name == "<clinit>") return true
        if (isStatic) return false

        // Record accessors are named after their components (`amount()`, not `getAmount()`).
        if (m.name in recordComponents && m.descriptor.startsWith("()") && m.descriptor != "()V") return true

        return when {
            m.name == "equals" -> m.descriptor == "(Ljava/lang/Object;)Z"
            m.name == "hashCode" -> m.descriptor == "()I"
            m.name == "toString" -> m.descriptor == "()Ljava/lang/String;"
            m.name == "copy" -> true // Kotlin data class
            isComponentN(m.name) -> true // Kotlin data class
            else -> isGetter(m, fields) || isSetter(m, fields)
        }
    }

    private fun isComponentN(name: String): Boolean {
        if (!name.startsWith("component")) return false
        val digits = name.removePrefix("component")
        return digits.isNotEmpty() && digits.all { it.isDigit() }
    }

    /** `getX()` / `isX()` with zero params, non-void return, matching a field. */
    private fun isGetter(m: RawMethod, fields: Set<String>): Boolean {
        val prop = propertyName(m.name, "get", "is") ?: return false
        if (!m.descriptor.startsWith("()") || m.descriptor == "()V") return false
        return matchesField(prop, m.name.startsWith("is"), fields)
    }

    /** `setX(v)` with exactly one param and void return, matching a field. */
    private fun isSetter(m: RawMethod, fields: Set<String>): Boolean {
        val prop = propertyName(m.name, "set") ?: return false
        if (!m.descriptor.endsWith(")V")) return false
        if (methodParamCount(m.descriptor) != 1) return false
        return matchesField(prop, false, fields)
    }

    private fun matchesField(prop: String, fromIsPrefix: Boolean, fields: Set<String>): Boolean {
        if (prop in fields) return true
        // `isActive()` reading Kotlin's `is`-prefixed field `isActive`.
        if (fromIsPrefix && "is" + prop.replaceFirstChar { it.uppercase() } in fields) return true
        // JavaBeans: `getURL()` reads field `URL` (two leading capitals stay as-is).
        if (prop.replaceFirstChar { it.uppercase() } in fields) return true
        return false
    }

    /** Strips [prefixes], applies JavaBeans decapitalization; null if the name is just the prefix. */
    private fun propertyName(name: String, vararg prefixes: String): String? {
        for (p in prefixes) {
            if (!name.startsWith(p)) continue
            val rest = name.removePrefix(p)
            if (rest.isEmpty()) return null
            return if (rest.length > 1 && rest[0].isUpperCase() && rest[1].isUpperCase()) rest
            else rest.replaceFirstChar { it.lowercase() }
        }
        return null
    }

    private fun methodParamCount(descriptor: String): Int {
        var count = 0
        var i = 1
        while (i < descriptor.length && descriptor[i] != ')') {
            when (descriptor[i]) {
                'L' -> { i = descriptor.indexOf(';', i); if (i < 0) return count }
                '[' -> { /* consumed with the element type below */ }
                else -> count++ // primitive or the element type closing an array run
            }
            i++
        }
        return count
    }
}
