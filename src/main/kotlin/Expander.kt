import Token.Type.EQUAL_GREATER
import java.io.File

data class Macro(val pattern: Pattern, val substitution: Expr)

class Expander {
    class MacroError(where: String, message: String, expr: Expr) :
        Exception("[$where] $message: $expr")

    private val macros = HashMap<Pattern, Macro>()

    companion object {
        const val Include = "include"
    }

    fun expand(expr: Expr): Expr = Expr.transform(expr) { expr ->
        when (expr) {
            is Expr.Binary -> {
                if (expr.operator.type == EQUAL_GREATER) {
                    defineMacro(expr.target, expr.value)
                }
                expr
            }

            is Expr.Phrase -> {
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
                val pattern = Pattern.forIdent(expr)
                val macro = findMacro(pattern) ?: return@transform null
                macro.substitution
            }

            else -> null
        }
    }

    private fun expandInclude(expr: Expr.Phrase): Expr {
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
            is Expr.Ident -> Pattern.forIdent(target)
            is Expr.Phrase -> {
                if (target.target !is Expr.Ident)
                    throw MacroError("defineMacro", "target must start with Ident", target.target)
                Pattern.forDefinition(target.terms)
            }

            else ->
                throw MacroError("defineMacro", "target must be Phrase or Ident", target)
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

            is Expr.Sexp.Unquote -> subSexpUnquote(expr.body, binding)

            else -> null
        }
    }

    private fun exprToSexp(expr: Expr): Expr.Sexp = when (expr) {
        is Expr.Grouping -> {
            val terms = when (expr.body) {
                is Expr.Phrase -> expr.body.terms.map(::exprToSexp)
                is Expr.Ident -> arrayListOf(exprToSexp(expr.body))
                else -> throw MacroError("exprToSexp", "illegal grouping body for sexp", expr.body)
            }
            Expr.Sexp.Grouping(terms)
        }

        is Expr.Ident -> Expr.Sexp.Ident(expr.name)
        is Expr.Literal -> {
            when (expr.literal) {
                is String,
                is Number -> Expr.Sexp.Literal(expr.literal)

                else -> throw MacroError("exprToSexp", "illegal literal type for sexp", expr)
            }
        }

        else -> throw MacroError("exprToSexp", "illegal expr for sexp", expr)
    }

    private fun subSexpUnquote(body: Expr, binding: Map<String, Expr>): Expr = when {
        body is Expr.Phrase && body.target == Expr.Ident("len") -> {
            if (body.args.size != 1)
                throw MacroError("subSexpUnquote", "len requires 1 arg", body)
            val arg = body.args.first()
            val len = when (arg) {
                is Expr.Literal -> (arg.literal as String).length
                is Expr.Ident -> {
                    val sub = binding[arg.name] ?: throw MacroError("subSexpUnquote", "undefined macro var", arg)
                    if (sub !is Expr.Literal || sub.literal !is String)
                        throw MacroError("subSexpUnquote", "len arg must be a string literal", arg)
                    sub.literal.length
                }

                else -> throw MacroError("subSexpUnquote", "illegal len arg", arg)
            }
            Expr.Sexp.Literal(len)
        }

        body is Expr.Ident -> {
            val substitution = binding[body.name] ?: throw MacroError("subSexpUnquote", "undefined macro var", body)
            exprToSexp(substitution)
        }

        else -> throw MacroError("subSexpUnquote", "illegal sexp unquote", body)
    }

}