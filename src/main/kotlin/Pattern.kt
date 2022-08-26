data class Pattern(val terms: List<Term>) {
    constructor(vararg terms: Term) : this(terms.toList())

    val target get() = terms.first()
    val args get() = terms.drop(1)

    open class Term {
        data class Wildcard(val binding: String?) : Term() {
            override fun equals(other: Any?) = other is Wildcard
            override fun hashCode() = 0

            override fun toString() = "_"
        }

        data class Ident(val name: String) : Term() {
            override fun toString() = name
        }

        data class Label(val name: String) : Term() {
            override fun toString() = "$name:"
        }
    }

    override fun toString() = when {
        args.all { it is Term.Wildcard } -> "${target}__${args.size}"
        else -> terms.joinToString(".")
    }

    companion object {
        fun forName(name: String) = Pattern(Term.Ident(name))
        fun forDefinition(exprs: List<Expr>): Pattern = Pattern(
            exprs.mapIndexed { i, expr ->
                when (expr) {
                    is Expr.Label -> Term.Label(expr.name)
                    is Expr.Ident -> if (i == 0) Term.Ident(expr.name) else Term.Wildcard(expr.name)
                    else -> throw Exception("illegal macro definition pattern term: $expr")
                }
            }
        )

        fun forSearch(exprs: List<Expr>): Pattern = Pattern(
            exprs.mapIndexed { i, expr ->
                when (expr) {
                    is Expr.Label -> Term.Label(expr.name)
                    is Expr.Ident -> if (i == 0) Term.Ident(expr.name) else Term.Wildcard(null)
                    else -> Term.Wildcard(null)
                }
            }
        )
    }

    fun bind(exprs: List<Expr>): Map<String, Expr> =
        terms
            .zip(exprs)
            .filter { (t, _) -> t is Term.Wildcard && t.binding != null }
            .associate { (term, expr) -> Pair((term as Term.Wildcard).binding!!, expr) }

}
