import Token.Type.EQUAL_GREATER

open class Match {
    data class Any(val binding: String?) : Match() {
        override fun equals(other: kotlin.Any?) = other is Any
        override fun hashCode() = 0
    }

    data class Label(val name: String) : Match()
}

data class Pattern(val terms: List<Match>) {
    companion object {
        fun forDefinition(exprs: List<Expr>): Pattern = Pattern(
            exprs.map {
                when (it) {
                    is Expr.Label -> Match.Label(it.name)
                    is Expr.Ident -> Match.Any(it.name)
                    else -> throw Exception("illegal macro definition pattern term: $it")
                }
            }
        )

        fun forSearch(exprs: List<Expr>): Pattern = Pattern(
            exprs.map {
                when (it) {
                    is Expr.Label -> Match.Label(it.name)
                    else -> Match.Any(null)
                }
            }
        )
    }

    fun bind(exprs: List<Expr>): Map<String, Expr> =
        terms
            .zip(exprs)
            .filter { (t, _) -> t is Match.Any && t.binding != null }
            .associate { (term, expr) -> Pair((term as Match.Any).binding!!, expr) }

}

data class MacroKey(val name: String, val pattern: Pattern)

class MacroEngine(
    private val sequence: Expr.Sequence,
) {
    class MacroError(where: String, message: String, expr: Expr) :
        Exception("[$where] $message: $expr")

    private val macros = HashMap<MacroKey, Pair<MacroKey, Expr>>()

    fun expandAll(): Expr.Sequence = Expr.Sequence(sequence.exprs.map(::expand))

    private fun expand(expr: Expr): Expr = when (expr) {
        is Expr.Assignment -> {
            if (expr.operator.type == EQUAL_GREATER) {
                defineMacro(expr)
                expr
            } else {
                Expr.Assignment(
                    expand(expr.target),
                    expr.operator,
                    expand(expr.value),
                )
            }
        }
        is Expr.Apply -> {
            if (expr.target is Expr.Ident) {
                val key = MacroKey(expr.target.name, Pattern.forSearch(expr.values))
                val macro = findMacro(key)
                if (macro != null) {
                    val (foundKey, substitution) = macro
                    evalSubstitution(substitution, foundKey.pattern, expr.values)
                } else expr
            } else expr
        }
        else -> expr
    }

    private fun findMacro(key: MacroKey) = macros[key]

    private fun defineMacro(expr: Expr.Assignment) {
        val key = when (val target = expr.target) {
            is Expr.Apply -> {
                if (target.target !is Expr.Ident)
                    throw MacroError("defineMacro", "target must start with Ident", target.target)
                MacroKey(target.target.name, Pattern.forDefinition(target.values))
            }
            else ->
                throw MacroError("defineMacro", "target must be Apply or Ident", target)
        }
        macros[key] = Pair(key, expr.value)
    }

    private fun evalSubstitution(substitution: Expr, pattern: Pattern, values: List<Expr>): Expr {
        val quote = findQuote(substitution)
            ?: throw MacroError("evalSubstitution", "could not find quote in substitution", substitution)
        val binding = pattern.bind(values)
        return subUnquotes(quote.q, binding)
    }

    private fun subUnquotes(expr: Expr, binding: Map<String, Expr>): Expr = when {
        expr is Expr.Unquote
                && expr.u is Expr.Apply
                && expr.u.target is Expr.Ident
                && expr.u.values.isEmpty() -> {
            val name = expr.u.target.name
            val toSub = binding[name] ?: throw MacroError("subUnquotes", "no expr to substitute for '$name'", expr)
            toSub
        }
        expr is Expr.Assignment -> Expr.Assignment(
            subUnquotes(expr.target, binding),
            expr.operator,
            subUnquotes(expr.value, binding),
        )
        expr is Expr.Apply -> Expr.Apply(expr.terms.map { subUnquotes(it, binding) })
        expr is Expr.Grouping -> Expr.Grouping(subUnquotes(expr.expr, binding))
        expr is Expr.Sequence -> Expr.Sequence(expr.exprs.map { subUnquotes(it, binding) })
        Expr.isLeaf(expr) -> expr
        else -> throw MacroError("subUnquotes", "illegal expr", expr)
    }

    private fun findQuote(expr: Expr): Expr.Quote? = when (expr) {
        is Expr.Apply -> findQuote(expr.target)
        is Expr.Quote -> expr
        is Expr.Sequence -> expr.exprs.firstNotNullOf(::findQuote)
        else -> null
    }

}