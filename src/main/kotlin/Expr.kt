import Assembly.literal

open class Expr {
    companion object {
        fun transform(expr: Expr, func: (Expr) -> Expr?): Expr {
            fun transform(expr: Expr) = transform(expr, func)
            fun visit(expr: Expr) = func(expr) ?: expr
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

                is Sequence -> visit(Sequence(expr.stmts.map(::transform)))
                is Binary -> visit(
                    Binary(
                        transform(expr.lhs),
                        expr.operator,
                        transform(expr.rhs)
                    )
                )

                is Unary -> visit(
                    Unary(
                        expr.operator,
                        transform(expr.body),
                    )
                )

                is Assignment -> visit(
                    Assignment(
                        transform(expr.target),
                        expr.operator,
                        transform(expr.value)
                    )
                )

                is Multi -> visit(Multi(transform(expr.body)))
                is Assembly -> visit(Assembly(transform(expr.body) as Sexp))
                is Phrase -> visit(Phrase(expr.terms.map(::transform)))
                is Grouping -> visit(Grouping(transform(expr.body)))
                is Path -> visit(Path(expr.segments.map(::transform), prefixed = expr.prefixed))
                is Access -> visit(Access(expr.segments.map(::transform), prefixed = expr.prefixed))
                is Unquote -> visit(Unquote(transform(expr.body)))
                is Sexp.List -> visit(Sexp.List(expr.terms.map { transform(it) as Sexp }, parens = expr.parens))
                else -> throw Exception("unsupported expr for transform(): $expr")
            }
        }
    }

    open val returns = true

    class Nil : Expr() {
        override val returns = false
        override fun toString() = "()"
    }

    data class Multi(val body: Expr) : Expr() {
        override fun toString() = "..$body"
    }

    data class Ident(val name: String) : Expr() {
        override fun toString() = name
    }

    data class Nomen(val name: String) : Expr() {
        override fun toString() = name
    }

    data class Label(val name: String) : Expr() {
        override fun toString() = "$name:"
    }

    data class Literal(val literal: Any) : Expr() {
        override fun toString() = when (literal) {
            is String -> literal.literal()
            else -> literal.toString()
        }
    }


    data class Sequence(val stmts: List<Expr>, val topLevel: Boolean = false) : Expr() {
        override val returns = (stmts.lastOrNull()?.returns ?: false) && !topLevel
        override fun toString(): String = buildString {
            append("[ ")
            append(stmts.joinToString("; "))
            append(" ]")
        }
    }

    data class Operator(val operator: Token) : Expr() {
        override fun toString() = operator.lexeme
    }

    data class Assignment(val target: Expr, val operator: Operator, val value: Expr) : Expr() {
        override val returns = false
        val terms get() = listOf(operator, target, value)
        override fun toString() = "$target $operator $value"
    }

    data class Binary(val lhs: Expr, val operator: Operator, val rhs: Expr) : Expr() {
        val terms get() = listOf(operator, lhs, rhs)
        override fun toString() = "‹$lhs $operator $rhs›"

    }

    data class Unary(val operator: Operator, val body: Expr) : Expr() {
        val terms get() = listOf(operator, body)

        override fun toString() = "$operator$body"
    }

    data class Assembly(val body: Sexp) : Expr() {
        override fun toString() = "!$body"
    }

    data class Phrase(val terms: List<Expr>) : Expr() {
        val target = terms.first()
        val args = terms.drop(1)

        override fun toString(): String = "‹${terms.joinToString(" ")}›"
    }

    data class Grouping(val body: Expr) : Expr() {
        override fun toString() = "($body)"
    }

    data class Access(val segments: List<Expr>, val prefixed: Boolean) : Expr() {
        override fun toString(): String {
            val inner = segments.joinToString(".")
            return when (prefixed) {
                true -> ".$inner"
                false -> inner
            }
        }
    }

    data class Path(val segments: List<Expr>, val prefixed: Boolean) : Expr() {
        override fun toString(): String {
            val inner = segments.joinToString("::")
            return when (prefixed) {
                true -> "::$inner"
                false -> inner
            }
        }
    }

    data class Unquote(val body: Expr) : Expr() {
        override fun toString() = "~$body"
    }

    abstract class Sexp : Expr() {
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

        data class Unquote(val body: Expr) : Sexp() {
            override fun render() = "~$body"
            override fun toString() = render()

        }

        class Linebreak : Sexp() {
            override fun render() = "\n "
            override fun toString() = render()

        }
    }

}

