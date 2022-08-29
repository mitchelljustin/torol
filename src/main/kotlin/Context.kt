interface Binding<Value> {
    fun define(pattern: Pattern.Definition, value: Value)
    fun resolve(pattern: Pattern): Value?

    operator fun contains(pattern: Pattern): Boolean = resolve(pattern) != null
}

class Context<Value> : Binding<Value> {

    class Scope<Value> : Binding<Value> {
        private val binding = Pattern.Store<Value>()

        override fun define(pattern: Pattern.Definition, value: Value) {
            binding[pattern] = value
        }

        override fun resolve(pattern: Pattern): Value? = binding[pattern]
    }

    val global = Scope<Value>()
    private val scopeStack = arrayListOf(global)
    private val scope get() = scopeStack.first()

    private fun pushScope() {
        scopeStack.add(0, Scope())
    }

    private fun popScope() {
        scopeStack.removeFirst()
    }

    override fun define(pattern: Pattern.Definition, value: Value) {
        scope.define(pattern, value)
    }

    override fun resolve(pattern: Pattern): Value? {
        for (scope in scopeStack) {
            return scope.resolve(pattern) ?: continue
        }
        return null
    }

    fun <T> withNewScope(func: () -> T): T {
        pushScope()
        val result = func()
        popScope()
        return result
    }
}