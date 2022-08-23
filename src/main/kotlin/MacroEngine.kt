import Token.Type.EQUAL_GREATER
import java.io.File

data class Macro(val pattern: Pattern, val substitution: Expr)

class MacroEngine {
    class MacroError(where: String, message: String, expr: Expr) :
        Exception("[$where] $message: $expr")

    private val macros = HashMap<Pattern, Macro>()

    companion object {
        const val Include = "include"
    }

    init {
        InstructionSet.EnvCall.values().forEach {
            addMacro(
                Pattern.forName("callno_${it.name}"),
                Expr.Literal(it.ordinal),
            )
        }
    }

    fun expand(expr: Expr): Expr = Expr.transform(expr) { expr ->
        when (expr) {
            is Expr.Binary -> {
                if (expr.operator.type == EQUAL_GREATER) {
                    defineMacro(expr.target, expr.value)
                }
                expr
            }
            is Expr.Apply -> {
                if (expr.target !is Expr.Ident) return@transform null
                if (expr.target.name == Include) {
                    return@transform expandInclude(expr)
                }
                val key = Pattern.forSearch(expr.terms)
                val macro = findMacro(key) ?: return@transform null
                val (pattern, substitution) = macro
                val binding = pattern.bind(expr.terms)
                substitute(substitution, binding)
            }
            is Expr.Ident -> {
                val pattern = Pattern.forName(expr.name)
                val macro = findMacro(pattern) ?: return@transform null
                macro.substitution
            }
            else -> null
        }
    }

    private fun expandInclude(expr: Expr.Apply): Expr {
        if (expr.args.size != 1)
            throw MacroError("expandInclude", "wrong number of arguments", expr)
        val arg = expr.args.first()
        if (arg !is Expr.Literal || arg.literal !is String)
            throw MacroError("expandInclude", "include arg must be a string", expr)
        val module = arg.literal.removeSuffix(".uber")
        val source = File("$module.uber").readText()
        val program = Parser.parse(source)
        return expand(program)
    }

    private fun findMacro(key: Pattern) = macros[key]

    private fun defineMacro(target: Expr, value: Expr) {
        val pattern = when (target) {
            is Expr.Ident -> Pattern.forName(target.name)
            is Expr.Apply -> {
                if (target.target !is Expr.Ident)
                    throw MacroError("defineMacro", "target must start with Ident", target.target)
                Pattern.forDefinition(target.terms)
            }
            else ->
                throw MacroError("defineMacro", "target must be Apply or Ident", target)
        }
        if (pattern in macros)
            throw MacroError("defineMacro", "macro with key already exists: $pattern", target)
        addMacro(pattern, value)
    }

    private fun addMacro(pattern: Pattern, value: Expr) {
        macros[pattern] = Macro(pattern, value)
    }

    private fun substitute(substitution: Expr, binding: Map<String, Expr>): Expr = subUnquotes(substitution, binding)

    private fun subUnquotes(expr: Expr, binding: Map<String, Expr>): Expr = Expr.transform(expr) { expr ->
        when (expr) {
            is Expr.Unquote -> {
                if (expr.body !is Expr.Ident)
                    throw MacroError("subUnquotes", "unquote body must be an Ident", expr.body)
                val name = expr.body.name
                binding[name] ?: throw MacroError("subUnquotes", "no expr to substitute for '$name'", expr)
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