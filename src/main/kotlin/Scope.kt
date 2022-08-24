open class WasmValue {
    data class I32(val v: Int) : WasmValue()
    data class I64(val v: Long) : WasmValue()
    data class F32(val v: Float) : WasmValue()
    data class F64(val v: Double) : WasmValue()
    data class Ref(val v: Long) : WasmValue()
}

class Scope(
    private val enclosing: Scope?,
) {
    private val binding = HashMap<Pattern, WasmValue>()

    fun define(pattern: Pattern, value: WasmValue) {
        binding[pattern] = value
    }

    fun resolve(pattern: Pattern): WasmValue? =
        binding.getOrElse(pattern) { enclosing?.resolve(pattern) }
}