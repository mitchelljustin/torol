open class WasmValue {
    data class I32(val v: Int) : WasmValue()
    data class I64(val v: Long) : WasmValue()
    data class F32(val v: Float) : WasmValue()
    data class F64(val v: Double) : WasmValue()
    data class Ref(val v: Long) : WasmValue()
}

class Scope<Value>(
    val enclosing: Scope<Value>?,
) {
    private val binding = HashMap<Pattern, Value>()

    fun define(pattern: Pattern, value: Value) {
        binding[pattern] = value
    }

    fun resolve(pattern: Pattern): Value? =
        binding.getOrElse(pattern) { enclosing?.resolve(pattern) }

    fun resolve(name: String) = resolve(Pattern.forName(name))

    operator fun contains(pattern: Any?): Boolean = when (pattern) {
        is Pattern -> pattern in binding
        else -> false
    }
}