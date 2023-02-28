import Assembly.literal

open class AST {
    companion object {
        fun map(expr: AST, transform: (AST) -> AST?): AST {
            fun recurse(expr: AST) = map(expr, transform)
            fun visit(expr: AST) = transform(expr) ?: expr
            return when (expr) {
                is Nil,
                is Ident,
                is Nomen,
                is Label,
                is Literal,
                is Sexp.Unquote,
                is Sexp.Ident,
                is Sexp.Literal
                -> visit(expr)

                is Sequence -> visit(Sequence(expr.stmts.map(::recurse)))
                is Binary -> visit(
                    Binary(
                        recurse(expr.lhs),
                        expr.operator,
                        recurse(expr.rhs)
                    )
                )

                is Unary -> visit(
                    Unary(
                        expr.operator,
                        recurse(expr.body),
                    )
                )

                is Assignment -> visit(
                    Assignment(
                        recurse(expr.target),
                        expr.operator,
                        recurse(expr.value)
                    )
                )

                is Splat -> visit(Splat(recurse(expr.body)))
                is Assembly -> visit(Assembly(recurse(expr.body) as Sexp))
                is Phrase -> visit(Phrase(expr.terms.map(::recurse)))
                is Grouping -> visit(Grouping(recurse(expr.body)))
                is Path -> visit(Path(expr.segments.map(::recurse), prefixed = expr.prefixed))
                is Access -> visit(Access(recurse(expr.target), recurse(expr.member)))
                is Unquote -> visit(Unquote(recurse(expr.body)))
                is Sexp.List -> visit(Sexp.List(expr.terms.map { recurse(it) as Sexp }, parens = expr.parens))
                is Sexp.Access -> visit(
                    Sexp.Access(
                        recurse(expr.target) as Sexp.Ident,
                        recurse(expr.member) as Sexp.Ident
                    )
                )

                else -> throw Exception("unsupported expr for transform(): '$expr'")
            }
        }
    }

    open val returns = true

    class Nil : AST() {
        override val returns = false
        override fun toString() = "()"
    }

    data class Splat(val body: AST) : AST() {
        override fun toString() = "$body.."
    }

    data class Reference(val body: AST) : AST() {
        override fun toString() = "&$body"
    }

    data class Ident(val name: String) : AST() {
        override fun toString() = name

        fun toSexp() = Sexp.Ident(name)
    }

    data class Nomen(val name: String) : AST() {
        override fun toString() = name
    }

    data class Label(val name: String) : AST() {
        override fun toString() = "$name:"
    }

    data class Literal(val literal: Any) : AST() {
        override fun toString() = when (literal) {
            is String -> literal.literal()
            else -> literal.toString()
        }
    }


    data class Sequence(val stmts: List<AST>, val topLevel: Boolean = false) : AST() {
        override val returns = (stmts.lastOrNull()?.returns ?: false) && !topLevel
        override fun toString(): String = buildString {
            append("[ ")
            append(stmts.joinToString("; "))
            append(" ]")
        }
    }

    data class Operator(val operator: Token) : AST() {
        override fun toString() = operator.lexeme
    }

    data class Assignment(val target: AST, val operator: Operator, val value: AST) : AST() {
        override val returns = false
        val terms get() = listOf(operator, target, value)
        override fun toString() = "‹$target $operator $value›"
    }

    data class Binary(val lhs: AST, val operator: Operator, val rhs: AST) : AST() {
        val terms get() = listOf(operator, lhs, rhs)
        override fun toString() = "‹$lhs $operator $rhs›"

    }

    data class Unary(val operator: Operator, val body: AST) : AST() {
        val terms get() = listOf(operator, body)

        override fun toString() = "$operator$body"
    }

    data class Assembly(val body: Sexp) : AST() {
        override fun toString() = "!$body"
    }

    data class Phrase(val terms: List<AST>) : AST() {
        val target = terms.first()
        val args = terms.drop(1)

        override fun toString(): String = "‹${terms.joinToString(" ")}›"
    }

    data class Grouping(val body: AST) : AST() {
        override fun toString() = "($body)"
    }

    data class Access(val target: AST, val member: AST) : AST() {
        override fun toString() = "‹$target.$member›"
    }

    data class Path(val segments: List<AST>, val prefixed: Boolean) : AST() {
        override fun toString(): String {
            val inner = segments.joinToString("::")
            return when (prefixed) {
                true -> "::$inner"
                false -> inner
            }
        }
    }

    data class Unquote(val body: AST) : AST() {
        override fun toString() = "~$body"
    }

    abstract class Sexp : AST() {
        abstract fun render(): String

        open class Builder {
            internal val terms = arrayListOf<Sexp>()

            fun add(value: Any?) {
                terms.add(from(value))
            }

            fun add(vararg values: Any?) {
                add(values.toList())
            }

            fun linebreak() {
                add(Linebreak())
            }

            internal open fun compile(): Sexp = List(terms, parens = false)
        }

        companion object {
            fun from(value: Any?): Sexp = when (value) {
                is Sexp -> value
                is Int -> Literal(value)
                is String -> Ident(value)
                is kotlin.collections.List<*> -> List(value.map(::from), parens = true)
                else -> throw RuntimeException("cannot convert to sexp: $value")
            }

            fun from(vararg terms: Any?) = from(terms.toList())
        }

        data class Ident(val name: String) : Sexp() {
            override fun render() = name
            override fun toString() = render()
        }

        data class Literal(val value: Any) : Sexp() {
            override fun render() = when (value) {
                is String -> value.literal()
                else -> value.toString()
            }

            override fun toString() = render()

        }

        data class List(val terms: kotlin.collections.List<Sexp>, val parens: Boolean) : Sexp() {
            override fun render(): String {
                val inner = terms.joinToString(" ") { it.render() }
                return when (parens) {
                    true -> "($inner)"
                    false -> inner
                }
            }

            override fun toString() = render()

        }

        data class Access(val target: Ident, val member: Ident) : Sexp() {
            override fun render() = "$target.$member"

            override fun toString() = render()
        }

        data class Unquote(val body: AST) : Sexp() {
            override fun render() = "~$body"
            override fun toString() = render()
        }

        class Linebreak : Sexp() {
            override fun render() = "\n "
            override fun toString() = render()

        }
    }

}

