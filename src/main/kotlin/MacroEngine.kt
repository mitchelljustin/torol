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
        fun empty() = Pattern(listOf())

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
data class Macro(val key: MacroKey, val substitution: Expr)

class MacroEngine(
    private val sequence: Expr.Sequence,
) {
    class MacroError(where: String, message: String, expr: Expr) :
        Exception("[$where] $message: $expr")

    private val macros = HashMap<MacroKey, Macro>()

    fun expandAll(): Expr.Sequence = Expr.Sequence(sequence.exprs.map(::expand))

    private fun expand(expr: Expr): Expr = when (expr) {
        is Expr.Assignment -> {
            val expr = Expr.Assignment(
                expand(expr.target),
                expr.operator,
                expand(expr.value),
            )

            if (expr.operator.type == EQUAL_GREATER) {
                defineMacro(expr)
            }
            expr
        }
        is Expr.Ident -> {
            val macro = findMacro(MacroKey(expr.name, Pattern.empty()))
            macro?.substitution ?: expr
        }
        is Expr.Directive ->
            Expr.Directive(expand(expr.body) as Expr.Apply)
        is Expr.Apply -> {
            val expr = Expr.Apply(listOf(expr.target) + expr.args.map(::expand))
            if (expr.target is Expr.Ident) {
                val key = MacroKey(expr.target.name, Pattern.forSearch(expr.args))
                val macro = findMacro(key)
                if (macro != null) {
                    val (foundKey, substitution) = macro
                    val binding = foundKey.pattern.bind(expr.args)
                    substitute(substitution, binding)
                } else expr
            } else expr
        }
        is Expr.Sequence -> Expr.Sequence(expr.exprs.map(::expand))
        else -> expr
    }

    private fun findMacro(key: MacroKey) = macros[key]

    private fun defineMacro(expr: Expr.Assignment) {
        val key = when (val target = expr.target) {
            is Expr.Apply -> {
                if (target.target !is Expr.Ident)
                    throw MacroError("defineMacro", "target must start with Ident", target.target)
                MacroKey(target.target.name, Pattern.forDefinition(target.args))
            }
            else ->
                throw MacroError("defineMacro", "target must be Apply or Ident", target)
        }
        macros[key] = Macro(key, expr.value)
    }

    private fun substitute(substitution: Expr, binding: Map<String, Expr>): Expr {
        val expr = findQuote(substitution)?.quoted ?: substitution
//            ?: throw MacroError("evalSubstitution", "could not find quote in substitution", substitution)
        return subUnquotes(expr, binding)
    }

    private fun subUnquotes(expr: Expr, binding: Map<String, Expr>): Expr {
        fun recurse(expr: Expr) = subUnquotes(expr, binding)
        return when {
            expr is Expr.Unquote
                    && expr.body is Expr.Apply
                    && expr.body.target is Expr.Ident
                    && expr.body.args.isEmpty() -> {
                val name = expr.body.target.name
                val toSub = binding[name] ?: throw MacroError("subUnquotes", "no expr to substitute for '$name'", expr)
                toSub
            }
            expr is Expr.Assignment -> Expr.Assignment(
                recurse(expr.target),
                expr.operator,
                recurse(expr.value),
            )
            expr is Expr.Apply -> Expr.Apply(expr.terms.map(::recurse))
            expr is Expr.Grouping -> Expr.Grouping(recurse(expr.body))
            expr is Expr.Sequence -> Expr.Sequence(expr.exprs.map(::recurse))
            expr is Expr.Directive -> Expr.Directive(recurse(expr.body) as Expr.Apply)
            Expr.isLeaf(expr) -> expr
            else -> throw MacroError("subUnquotes", "illegal expr", expr)
        }
    }

    private fun findQuote(expr: Expr): Expr.Quote? = when (expr) {
        is Expr.Quote -> expr
        is Expr.Apply -> findQuote(expr.target)
        is Expr.Sequence -> try {
            expr.exprs.firstNotNullOf(::findQuote)
        } catch (_: NoSuchElementException) {
            null
        }
        else -> null
    }

}