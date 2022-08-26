data class Pattern(val terms: List<Term>) {
    constructor(vararg terms: Term) : this(terms.toList())

    private val target get() = terms.first()
    private val args get() = terms.drop(1)

    open class Term {
        data class Any(val binding: String?) : Term() {
            override fun equals(other: kotlin.Any?) = other is Any
            override fun hashCode() = 0

            override fun toString() = "_"
        }

        data class Exact(val value: String) : Term() {
            override fun toString() = value
        }

        data class Label(val name: String) : Term() {
            override fun toString() = "$name:"
        }
    }

    fun name() = when {
        args.all { it is Term.Any } -> "${target}__${args.size}"
        else -> terms.joinToString(".")
    }

    companion object {
        fun forName(name: String) = Pattern(Term.Exact(name))
        fun forDefinition(terms: List<Expr>): Pattern = Pattern(
            terms.mapIndexed { i, term ->
                when (term) {
                    is Expr.Operator -> Term.Exact(term.operator.lexeme)
                    is Expr.Label -> Term.Label(term.name)
                    is Expr.Ident -> if (i == 0) Term.Exact(term.name) else Term.Any(term.name)
                    else -> throw Exception("illegal macro definition pattern term: $term")
                }
            }
        )

        fun forSearch(terms: List<Expr>): Pattern = Pattern(
            terms.mapIndexed { i, term ->
                when (term) {
                    is Expr.Operator -> Term.Exact(term.operator.lexeme)
                    is Expr.Label -> Term.Label(term.name)
                    is Expr.Ident -> if (i == 0) Term.Exact(term.name) else Term.Any(null)
                    else -> Term.Any(null)
                }
            }
        )
    }

    fun bind(exprs: List<Expr>): Map<String, Expr> =
        terms
            .zip(exprs)
            .filter { (t, _) -> t is Term.Any && t.binding != null }
            .associate { (term, expr) -> Pair((term as Term.Any).binding!!, expr) }

}
