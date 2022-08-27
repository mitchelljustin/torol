class Scope<Value>(
    val enclosing: Scope<Value>?,
) {
    private val binding = Pattern.Store<Value>()

    fun define(pattern: Pattern.Definition, value: Value) {
        binding[pattern] = value
    }

    fun resolve(pattern: Pattern.Search): Value? =
        binding[pattern] ?: enclosing?.resolve(pattern)

    fun resolve(name: String): Value? = resolve(Pattern.Search(name))

    operator fun contains(pattern: Any?): Boolean = when (pattern) {
        is Pattern.Search -> binding[pattern] != null
        else -> false
    }
}