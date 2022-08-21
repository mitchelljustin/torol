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
        val empty = Pattern(listOf())

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

    private fun expand(expr: Expr): Expr = Expr.transform(expr) { expr ->
        when (expr) {
            is Expr.Binary -> {
                if (expr.operator.type == EQUAL_GREATER) {
                    defineMacro(expr)
                }
                expr
            }
            is Expr.Apply -> {
                if (expr.target !is Expr.Ident) return@transform null
                val key = MacroKey(expr.target.name, Pattern.forSearch(expr.args))
                val macro = findMacro(key) ?: return@transform null
                val (foundKey, substitution) = macro
                val binding = foundKey.pattern.bind(expr.args)
                substitute(substitution, binding)
            }
            is Expr.Ident -> {
                val key = MacroKey(expr.name, Pattern.empty)
                val macro = findMacro(key) ?: return@transform null
                macro.substitution
            }
            else -> null
        }
    }

    private fun findMacro(key: MacroKey) = macros[key]

    private fun defineMacro(expr: Expr.Binary) {
        val key = when (val target = expr.target) {
            is Expr.Ident -> MacroKey(target.name, Pattern.empty)
            is Expr.Apply -> {
                if (target.target !is Expr.Ident)
                    throw MacroError("defineMacro", "target must start with Ident", target.target)
                MacroKey(target.target.name, Pattern.forDefinition(target.args))
            }
            else ->
                throw MacroError("defineMacro", "target must be Apply or Ident", target)
        }
        if (key in macros)
            throw MacroError("defineMacro", "macro with key already exists: $key", expr)
        macros[key] = Macro(key, expr.value)
    }

    private fun substitute(substitution: Expr, binding: Map<String, Expr>): Expr {
        val expr = findQuote(substitution)?.body ?: substitution
//            ?: throw MacroError("evalSubstitution", "could not find quote in substitution", substitution)
        return subUnquotes(expr, binding)
    }

    private fun subUnquotes(expr: Expr, binding: Map<String, Expr>): Expr = Expr.transform(expr) { expr ->
        when (expr) {
            is Expr.Unquote -> {
                if (expr.body !is Expr.Ident)
                    throw MacroError("subUnquotes", "unquote body must be an Ident", expr.body)
                val name = expr.body.name
                val toSub = binding[name] ?: throw MacroError("subUnquotes", "no expr to substitute for '$name'", expr)
                toSub
            }
            else -> null
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