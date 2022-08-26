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