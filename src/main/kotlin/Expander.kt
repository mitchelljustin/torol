import Token.Type.EQUAL_GREATER
import java.io.File


class Expander {
    class MacroError(where: String, message: String, expr: Expr) :
        Exception("[$where] $message: $expr")

    data class Macro(val pattern: Pattern, val substitution: Expr)

    private val global = Scope<Macro>(null)
    private var scope = global


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
                when (expr.target.name) {
                    "include" -> return@transform expandInclude(expr)
//                    "string_len" -> return@transform expandStringLen(expr)
                    else -> {
                        val key = Pattern.forSearch(expr.terms)
                        val macro = findMacro(key) ?: return@transform null
                        val (pattern, substitution) = macro
                        withNewScope {
                            pattern.bind(expr.terms).forEach { (name, expr) ->
                                addMacro(Pattern.forName(name), expr)
                            }
                            substitute(substitution)
                        }
                    }
                }
            }

            is Expr.Ident -> {
                val pattern = Pattern.forName(expr.name)
                val macro = findMacro(pattern) ?: return@transform null
                macro.substitution
            }

            else -> null
        }
    }

    private fun expandStringLen(expr: Expr.Phrase): Expr {
        if (expr.args.size != 1)
            throw MacroError("expandStringLen", "len requires 1 arg", expr)
        val arg = expand(expr.args.first())
        if (arg !is Expr.Literal || arg.literal !is String)
            throw MacroError("expandStringLen", "len expects string literal", expr)
        return Expr.Literal(arg.literal.length)
    }

    private fun <T> withNewScope(func: () -> T): T {
        val originalScope = scope
        scope = Scope(scope)
        val result = func()
        scope = scope.enclosing!!
        if (scope !== originalScope)
            throw Exception("scopes dont match")
        return result
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

    private fun findMacro(pattern: Pattern) = scope.resolve(pattern)

    private fun defineMacro(target: Expr, value: Expr) {
        val pattern = when (target) {
            is Expr.Ident -> Pattern.forName(target.name)
            is Expr.Phrase -> {
                if (target.target !is Expr.Ident)
                    throw MacroError("defineMacro", "target must start with Ident", target.target)
                Pattern.forDefinition(target.terms)
            }

            else ->
                throw MacroError("defineMacro", "target must be Phrase or Ident", target)
        }
        if (pattern in scope)
            throw MacroError("defineMacro", "macro already exists: $pattern", target)
        addMacro(pattern, value)
    }

    private fun addMacro(pattern: Pattern, value: Expr) {
        scope.define(pattern, Macro(pattern, value))
    }

    private fun substitute(expr: Expr): Expr = Expr.transform(expr) { expr ->
        when (expr) {
            is Expr.Unquote -> {
                if (expr.body !is Expr.Ident)
                    throw MacroError("subUnquotes", "unquote body must be an Ident", expr.body)
                val name = expr.body.name
                scope.resolve(name)?.substitution ?: throw MacroError(
                    "subUnquotes",
                    "no expr to substitute for '$name'",
                    expr
                )
            }

            is Expr.Sexp.Unquote -> subSexp(expr.body)

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

        is Expr.Sexp -> expr

        else -> throw MacroError("exprToSexp", "illegal expr for sexp", expr)
    }

    private fun subSexp(expr: Expr): Expr.Sexp = when (expr) {
        is Expr.Phrase, is Expr.Ident -> subSexp(expand(expr))
        is Expr.Assembly -> subSexp(expr.body)
        else -> exprToSexp(expr)
    }

}