package org.example.constellation.core.analyze

/** Everything the scanner reads off one class file — technical, analyzer-internal. */
internal class RawClassInfo(
    val name: String,
    val access: Int,
    val superName: String?,
    val interfaces: List<String>,
    /** Non-static, non-synthetic instance field names. */
    val instanceFields: List<String>,
    /** Static, non-synthetic fields (INSTANCE, Companion, constants…), with descriptors. */
    val staticFields: List<RawField>,
    val recordComponents: List<String>,
    val methods: List<RawMethod>,
    val calls: List<RawCall>,
    val sourceFile: String?,
    /** EnclosingMethod attribute — the natural owner of anonymous/local/synthetic bodies. */
    val enclosingMethod: RawEnclosing? = null,
    /** NestHost attribute. */
    val nestHost: String? = null,
    /** This class's own InnerClasses entry: declared outer class (null when anonymous/local). */
    val innerOuterName: String? = null,
    /** Access flags of this class's own InnerClasses entry (0 when not nested). */
    val innerSelfAccess: Int = 0,
)
