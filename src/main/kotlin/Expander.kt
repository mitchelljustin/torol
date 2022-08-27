import Assembly.id
import Assembly.literal
import Expr.Sexp
import Token.Type.EQUAL
import Token.Type.EQUAL_GREATER
import java.io.File


class Expander {
    class MacroException(where: String, message: String, expr: Expr?) :
        CompilerException("[$where] $message: $expr")

    data class Macro(val pattern: Pattern.Definition, val substitution: Expr)

    private val global = Scope<Macro>(null)
    private var scope = global


    fun expand(expr: Expr): Expr = Expr.transform(expr) { expr ->
        when (expr) {
            is Expr.Assignment -> when (expr.operator.operator.type) {
                EQUAL_GREATER -> {
                    defineMacro(expr.target, expr.value)
                    null
                }

                EQUAL -> null

                else -> expandMacroCall(expr.terms)
            }

            is Expr.Binary -> expandMacroCall(expr.terms)

            is Expr.Phrase -> {
                if (expr.target !is Expr.Ident) return@transform null
                when (expr.target.name) {
                    "include" -> expandInclude(expr)
                    "import" -> expandImport(expr)
                    else -> expandMacroCall(expr.terms)
                }
            }

            is Expr.Ident -> {
                val pattern = Pattern.Search(expr.name)
                findMacro(pattern)?.substitution
            }

            else -> null
        }
    }

    private fun expandMacroCall(terms: List<Expr>): Expr? {
        val searchPat = Pattern.Search(terms)
        val macro = findMacro(searchPat) ?: return null
        val (pattern, substitution) = macro
        return withNewScope {
            pattern.bind(terms).forEach { (name, expr) ->
                addMacro(Pattern.Definition(name), expr)
            }
            substitute(substitution)
        }
    }

    private fun expandImport(expr: Expr.Phrase): Expr {
        val module = try {
            val (label, module) = expr.args
            if (label != Expr.Label("from"))
                throw MacroException("expandImport", "expected import from: module (func1) (func2) ..", label)
            if (module !is Expr.Ident)
                throw MacroException("expandImport", "module must be an ident", module)
            module
        } catch (_: ArrayIndexOutOfBoundsException) {
            throw MacroException("expandImport", "wrong number of arguments", expr)
        }

        fun importFunc(expr: Expr): Array<Any?> = when (expr) {
            is Expr.Grouping -> importFunc(expr.body)
            is Expr.Phrase -> {
                if (expr.terms.any { it !is Expr.Ident })
                    throw MacroException(
                        "expandImport",
                        "illegal function import, all terms must be ident",
                        expr.target
                    )
                val terms = expr.terms.map { (it as Expr.Ident).name }
                val name = terms.first()
                val pattern = Pattern.Definition(expr.terms)
                val args = terms.drop(1)
                val params = args.map { arg -> Sexp.from("param", arg.id(), "i32") }
                val func = Sexp.from(
                    "func",
                    pattern.name.id(),
                    *params.toTypedArray(),
                    Sexp.from("result", "i32"),
                )
                arrayOf(
                    name.literal(),
                    Sexp.Linebreak(),
                    func,
                )
            }

            else -> throw MacroException("expandImport", "illegal function import", expr)
        }

        //!(import "wasi_unstable" "fd_write" (func $fd_write__4 (param i32 i32 i32 i32) (result i32)))
        // import from: wasi_unstable
        //   fd_write fd iovec iovec_len num_written

        val items = expr.args.drop(2)
        return Expr.Sequence(items.map {
            Expr.Assembly(
                Sexp.from(
                    "import",
                    module.name.literal(),
                    *importFunc(it),
                )
            )
        })
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
            throw MacroException("expandInclude", "wrong number of arguments", expr)
        val arg = expr.args.first()
        if (arg !is Expr.Literal || arg.literal !is String)
            throw MacroException("expandInclude", "include arg must be a string", expr)
        val module = arg.literal.removeSuffix(".uber")
        val source = File("$module.uber").readText()
        val program = Parser.parse(source)
        return expand(program)
    }

    private fun findMacro(pattern: Pattern.Search) = scope.resolve(pattern)

    private fun defineMacro(target: Expr, value: Expr) {
        val pattern = when (target) {
            is Expr.Ident -> Pattern.Definition(target.name)
            is Expr.Phrase -> {
                if (target.target !is Expr.Ident)
                    throw MacroException("defineMacro", "target must start with Ident", target.target)
                Pattern.Definition(target.terms)
            }

            is Expr.Grouping -> {
                val terms = when (target.body) {
                    is Expr.Binary -> target.body.terms
                    is Expr.Assignment -> target.body.terms
                    else -> throw MacroException(
                        "defineOperatorMacro",
                        "operator macro must be (lhs <operator> rhs)",
                        target
                    )
                }
                Pattern.Definition(terms)
            }

            else ->
                throw MacroException("defineMacro", "target must be Phrase or Ident", target)
        }
        if (pattern in scope)
            throw MacroException("defineMacro", "macro already exists: $pattern", target)
        addMacro(pattern, value)
    }

    private fun addMacro(pattern: Pattern.Definition, value: Expr) {
        scope.define(pattern, Macro(pattern, value))
    }

    private fun substitute(expr: Expr): Expr = Expr.transform(expr) { expr ->
        when (expr) {
            is Expr.Unquote -> {
                if (expr.body !is Expr.Ident)
                    throw MacroException("subUnquotes", "unquote body must be an Ident", expr.body)
                val name = expr.body.name
                scope.resolve(name)?.substitution ?: throw MacroException(
                    "subUnquotes",
                    "no expr to substitute for '$name'",
                    expr
                )
            }

            is Sexp.Unquote -> subSexp(expr.body)

            else -> null
        }
    }

    private fun exprToSexp(expr: Expr): Sexp = when (expr) {
        is Expr.Grouping -> {
            val terms = when (expr.body) {
                is Expr.Phrase -> expr.body.terms.map(::exprToSexp)
                is Expr.Ident -> arrayListOf(exprToSexp(expr.body))
                else -> throw MacroException("exprToSexp", "illegal grouping body for sexp", expr.body)
            }
            Sexp.List(terms)
        }

        is Expr.Ident -> Sexp.Ident(expr.name)
        is Expr.Literal -> {
            when (expr.literal) {
                is String,
                is Number -> Sexp.Literal(expr.literal)

                else -> throw MacroException("exprToSexp", "illegal literal type for sexp", expr)
            }
        }

        is Sexp -> expr

        else -> throw MacroException("exprToSexp", "illegal expr for sexp", expr)
    }

    private fun subSexp(expr: Expr): Sexp = when (expr) {
        is Expr.Phrase, is Expr.Ident -> exprToSexp(expand(expr))
        is Expr.Assembly -> subSexp(expr.body)
        else -> exprToSexp(expr)
    }

}