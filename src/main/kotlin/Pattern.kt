open class Pattern(
    val terms: List<Term>
) {
    data class PatternException(override val message: String) : CompilerException(message)

    val lead = (terms.first() as Term.Exact).value
    internal val args = terms.drop(1)
    internal val nonVarargs = args.takeWhile { it !is Term.Vararg }
    internal val hasVararg = args.lastOrNull() is Term.Vararg
    val name
        get() = when {
            args.all { it is Term.Any } -> "${lead}//${args.size}"
            else -> terms.joinToString("/")
        }

    override fun toString() = name


    open class Term {
        data class Any(val binding: String?) : Term() {
            override fun equals(other: kotlin.Any?) = other is Any
            override fun hashCode() = 0

            override fun toString() = binding ?: "_"
        }

        data class Exact(val value: String) : Term() {
            override fun toString() = value
        }

        data class Label(val name: String) : Term() {
            override fun toString() = "$name:"
        }

        data class Vararg(val binding: String) : Term() {
            override fun toString() = "$binding.."
        }
    }


    class Definition(terms: List<AST>) : Pattern(terms.mapIndexed { i, term ->
        when (term) {
            is AST.Splat -> {
                if (term.body !is AST.Ident)
                    throw PatternException("vararg must be an ident")
                if (i != terms.lastIndex)
                    throw PatternException("vararg may only be the last term")
                Term.Vararg(term.body.name)
            }

            is AST.Operator -> Term.Exact(term.operator.lexeme)
            is AST.Label -> Term.Label(term.name)
            is AST.Ident, is AST.Path -> if (i == 0) Term.Exact(term.toString()) else Term.Any(term.toString())
            else -> throw PatternException("illegal definition pattern term: $term")
        }
    }) {
        constructor(name: String) : this(listOf(AST.Ident(name)))

        fun matchArgs(search: Search): Boolean = when {
            hasVararg -> search.args.isNotEmpty() && nonVarargs == search.args.take(nonVarargs.size)
            else -> args == search.args
        }

        fun bind(exprs: List<AST>): Map<String, AST> {
            val realArgs = exprs.drop(1)
            val binding = nonVarargs
                .zip(realArgs)
                .filter { (term, _) -> term is Term.Any && term.binding != null }
                .associate { (term, expr) -> Pair((term as Term.Any).binding!!, expr) }
                .toMutableMap()
            if (hasVararg) {
                val vararg = args.last() as Term.Vararg
                val realVarargs = realArgs.drop(nonVarargs.size)
                binding[vararg.binding] = AST.Sequence(realVarargs)
            }

            return binding
        }


    }

    class Search(terms: List<AST>) : Pattern(terms.mapIndexed { i, term ->
        when (term) {
            is AST.Operator -> Term.Exact(term.operator.lexeme)
            is AST.Label -> Term.Label(term.name)
            is AST.Ident, is AST.Path -> if (i == 0) Term.Exact(term.toString()) else Term.Any(null)
            else -> Term.Any(null)
        }
    }) {
        constructor(name: String) : this(listOf(AST.Ident(name)))
    }


    class Store<Value> {
        data class Entry<Value>(val pattern: Definition, val value: Value)

        private val store = HashMap<String, ArrayList<Entry<Value>>>()

        private fun entries(lead: String) = store.getOrPut(lead, ::arrayListOf)

        operator fun get(pattern: Pattern): Value? {
            val entries = entries(pattern.lead)
            val entry = entries.find { entry ->
                when (pattern) {
                    is Search -> entry.pattern.matchArgs(pattern)
                    is Definition -> entry.pattern.terms == pattern.terms
                    else -> throw Error()
                }
            }
                ?: return null
            return entry.value
        }

        operator fun set(definition: Definition, value: Value) {
            val entries = entries(definition.lead)
            entries.add(Entry(definition, value))
        }
    }


}
