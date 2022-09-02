import AST.Sexp
import Assembly.id
import Assembly.literal
import Token.Type.EQUAL
import Token.Type.EQUAL_GREATER
import java.io.File


class Expander {
    class MacroException(where: String, message: String, expr: AST?) :
        CompilerException("[$where] $message: $expr")

    data class Macro(val pattern: Pattern.Definition, val substitution: AST)

    private val context = Context<Macro>()
    val imports = ArrayList<Pattern.Definition>()


    fun expand(input: AST): AST = AST.map(input) { expr ->
        when (expr) {
            is AST.Assignment -> when (expr.operator.operator.type) {
                EQUAL_GREATER -> {
                    defineMacro(expr.target, expr.value)
                    AST.Nil()
                }

                EQUAL -> null

                else -> expandMacroCall(expr.terms)
            }

            is AST.Binary -> expandMacroCall(expr.terms)
            is AST.Unary -> expandMacroCall(expr.terms)

            is AST.Phrase -> {
                if (expr.target !is AST.Ident) return@map null
                when (expr.target.name) {
                    "include" -> expandInclude(expr)
                    "import" -> expandImport(expr)
                    else -> expandMacroCall(expr.terms)
                }
            }

            is AST.Ident -> {
                val pattern = Pattern.Search(expr.name)
                findMacro(pattern)?.substitution
            }

            else -> null
        }
    }

    private fun expandMacroCall(terms: List<AST>): AST? {
        val searchPat = Pattern.Search(terms)
        val macro = findMacro(searchPat) ?: return null
        val (pattern, substitution) = macro
        return context.withNewScope {
            pattern.bind(terms).forEach { (name, expr) ->
                addMacro(Pattern.Definition(name), expr)
            }
            substitute(substitution)
        }
    }

    private fun expandImport(expr: AST.Phrase): AST {
        val module = try {
            val (label, module) = expr.args
            if (label != AST.Label("from"))
                throw MacroException("expandImport", "expected import from: module (func1) (func2) ..", label)
            if (module !is AST.Literal || module.literal !is String)
                throw MacroException("expandImport", "module must be an string literal", module)
            module.literal
        } catch (_: ArrayIndexOutOfBoundsException) {
            throw MacroException("expandImport", "wrong number of arguments", expr)
        }

        fun importFunc(expr: AST): Array<Any?> = when (expr) {
            is AST.Grouping -> importFunc(expr.body)
            is AST.Phrase -> {
                if (expr.terms.any { it !is AST.Ident })
                    throw MacroException(
                        "expandImport",
                        "illegal function import, all terms must be ident",
                        expr.target
                    )
                val terms = expr.terms.map { (it as AST.Ident).name }
                val name = terms.first()
                val pattern = Pattern.Definition(expr.terms)
                imports.add(pattern)
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
        return AST.Sequence(items.map { item ->
            AST.Assembly(
                Sexp.from(
                    "import",
                    module.literal(),
                    *importFunc(item),
                )
            )
        })
    }


    private fun expandInclude(expr: AST.Phrase): AST {
        if (expr.args.size != 1)
            throw MacroException("expandInclude", "wrong number of arguments", expr)
        val arg = expr.args.first()
        if (arg !is AST.Literal || arg.literal !is String)
            throw MacroException("expandInclude", "include arg must be a string", expr)
        val module = arg.literal.removeSuffix(".catac")
        val source = File("$module.catac").readText()
        val program = Parser.parse(source)
        return expand(program)
    }

    private fun findMacro(pattern: Pattern.Search) = context.resolve(pattern)

    private fun defineMacro(target: AST, value: AST) {
        val pattern = when (target) {
            is AST.Ident -> Pattern.Definition(target.name)
            is AST.Phrase -> {
                if (target.target !is AST.Ident)
                    throw MacroException("defineMacro", "target must start with Ident", target.target)
                Pattern.Definition(target.terms)
            }

            is AST.Grouping -> {
                val terms = when (val body = target.body) {
                    is AST.Binary -> body.terms
                    is AST.Unary -> body.terms
                    is AST.Assignment -> body.terms
                    else -> throw MacroException(
                        "defineOperatorMacro",
                        "operator macro must be ([lhs] <operator> rhs)",
                        target
                    )
                }
                Pattern.Definition(terms)
            }

            else ->
                throw MacroException("defineMacro", "target must be Phrase or Ident", target)
        }
        if (pattern in context)
            throw MacroException("defineMacro", "macro already exists: $pattern", target)
        addMacro(pattern, value)
    }

    private fun addMacro(pattern: Pattern.Definition, value: AST) {
        context.define(pattern, Macro(pattern, value))
    }

    private fun substitute(input: AST): AST = AST.map(input) { expr ->
        when (expr) {
            is AST.Unquote -> {
                if (expr.body !is AST.Ident)
                    throw MacroException("subUnquotes", "unquote body must be an Ident", expr.body)
                val name = expr.body.name
                context.resolve(Pattern.Search(name))?.substitution ?: throw MacroException(
                    "subUnquotes",
                    "no expr to substitute for '$name'",
                    expr
                )
            }

            is Sexp.Unquote -> subSexp(expr.body)

            else -> null
        }
    }

    private fun exprToSexp(expr: AST): Sexp = when (expr) {
        is AST.Grouping -> {
            val terms = when (expr.body) {
                is AST.Phrase -> expr.body.terms.map(::exprToSexp)
                is AST.Ident -> arrayListOf(exprToSexp(expr.body))
                else -> throw MacroException("exprToSexp", "illegal grouping body for sexp", expr.body)
            }
            Sexp.List(terms, parens = true)
        }

        is AST.Ident -> Sexp.Ident(expr.name)
        is AST.Literal -> {
            when (expr.literal) {
                is String,
                is Number -> Sexp.Literal(expr.literal)

                else -> throw MacroException("exprToSexp", "illegal literal type for sexp", expr)
            }
        }

        is Sexp -> expr

        else -> throw MacroException("exprToSexp", "illegal expr for sexp", expr)
    }

    private fun subSexp(expr: AST): Sexp = when (expr) {
        is AST.Phrase, is AST.Ident -> exprToSexp(expand(expr))
        is AST.Assembly -> subSexp(expr.body)
        else -> exprToSexp(expr)
    }

}