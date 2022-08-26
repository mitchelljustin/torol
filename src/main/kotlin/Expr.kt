import Assembly.literal

open class Expr {
    companion object {
        fun transform(expr: Expr, func: (Expr) -> Expr?): Expr {
            fun transform(expr: Expr) = transform(expr, func)
            fun visit(expr: Expr) = func(expr) ?: expr
            return when (expr) {
                is Nil,
                is Ident,
                is Label,
                is Literal,
                is Sexp.Unquote,
                is Sexp.Ident,
                is Sexp.Literal
                -> visit(expr)

                is Sequence -> visit(Sequence(expr.exprs.map(::transform)))
                is Binary -> visit(
                    Binary(
                        transform(expr.lhs),
                        expr.operator,
                        transform(expr.rhs)
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
                is Quote -> visit(Quote(transform(expr.body)))
                is Unquote -> visit(Unquote(transform(expr.body)))
                is Sexp.List -> visit(Sexp.List(expr.terms.map(::transform)))
                else -> throw Exception("unsupported expr for transform(): $expr")
            }
        }
    }

    open val returns = true

    class Nil : Expr() {
        override val returns = false
        override fun toString() = "Nil"
    }

    data class Multi(val body: Expr) : Expr()

    data class Ident(val name: String) : Expr() {
        override fun toString() = "Ident($name)"
    }

    data class Label(val name: String) : Expr() {
        override fun toString() = "Label($name)"
    }

    data class Literal(val literal: Any) : Expr() {
        override fun toString() = "Literal($literal)"
    }

    abstract class Sexp : Expr() {

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

            internal fun toSexp() = List(terms)
        }

        companion object {
            fun from(value: Any?): Sexp = when (value) {
                is Sexp -> value
                is Int -> Literal(value)
                is String -> Ident(value)
                is kotlin.collections.List<*> -> List(value.map(::from))
                else -> throw RuntimeException("cannot convert to sexp: $value")
            }

            fun from(vararg terms: Any?) = from(terms.toList())

            fun build(f: Builder.() -> Unit): List {
                val builder = Builder()
                builder.f()
                return builder.toSexp()
            }
        }

        data class Ident(val name: String) : Sexp() {
            override fun toString() = name
        }

        data class Literal(val value: Any) : Sexp() {
            override fun toString() = when (value) {
                is String -> value.literal()
                else -> value.toString()
            }
        }

        data class List(val terms: kotlin.collections.List<Expr>) : Sexp() {
            override fun toString() = "(${terms.joinToString(" ")})"
        }

        data class Unquote(val body: Expr) : Sexp() {
            override fun toString() = "~${body}"
        }

        class Linebreak : Sexp() {
            override fun toString() = "\n "
        }
    }

    data class Sequence(val exprs: List<Expr>, val topLevel: Boolean = false) : Expr() {
        override val returns get() = (exprs.lastOrNull()?.returns ?: false) && !topLevel
        override fun toString(): String = buildString {
            append("Sequence(")
            append(exprs.joinToString(","))
            append(")")
        }
    }

    data class Operator(val operator: Token) : Expr()

    data class Assignment(val target: Expr, val operator: Operator, val value: Expr) : Expr() {
        val terms get() = listOf(operator, target, value)
    }

    data class Binary(val lhs: Expr, val operator: Operator, val rhs: Expr) : Expr() {
        val terms get() = listOf(operator, lhs, rhs)
    }

    data class Assembly(val body: Sexp) : Expr()

    data class Phrase(val terms: List<Expr>) : Expr() {
        val target = terms.first()
        val args = terms.drop(1)

        constructor(target: Expr, args: List<Expr>) : this(listOf(target) + args)

        override fun toString(): String {
            val inner = terms.joinToString(", ")
            return "{$inner}"
        }
    }

    data class Grouping(val body: Expr) : Expr()

    data class Quote(val body: Expr) : Expr()

    data class Unquote(val body: Expr) : Expr()

}

