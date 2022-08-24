open class Expr {
    companion object {
        fun isLeaf(expr: Expr) = when (expr) {
            is Nil, is Ident, is Label, is Literal -> true
            else -> false
        }

        fun transform(expr: Expr, func: (Expr) -> Expr?): Expr {
            fun recurse(expr: Expr) = transform(expr, func)
            fun modify(expr: Expr) = func(expr) ?: expr
            return when (expr) {
                is Nil,
                is Ident,
                is Label,
                is Literal,
                -> modify(expr)

                is Sequence -> modify(Sequence(expr.exprs.map(::recurse)))
                is Binary -> modify(
                    Binary(
                        recurse(expr.target),
                        expr.operator,
                        recurse(expr.value)
                    )
                )

                is Multi -> modify(Multi(recurse(expr.body)))
                is Directive -> modify(Directive(recurse(expr.body)))
                is Phrase -> modify(Phrase(expr.terms.map(::recurse)))
                is Grouping -> modify(Grouping(recurse(expr.body)))
                is Quote -> modify(Quote(recurse(expr.body)))
                is Unquote -> modify(Unquote(recurse(expr.body)))
                is Sexp -> modify(expr)
                else -> throw Exception("unsupported expr for transform(): $expr")
            }
        }
    }

    class Nil : Expr() {
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

    data class Sexp(val body: String) : Expr() {
        override fun toString() = "Sexp$body"
    }


    data class Sequence(val exprs: List<Expr>) : Expr() {
        override fun toString(): String {
            val inner = exprs.joinToString(", ")
            return "Sequence[$inner]"
        }
    }

    data class Binary(val target: Expr, val operator: Token, val value: Expr) : Expr()

    data class Directive(val body: Expr) : Expr()

    data class Phrase(val terms: List<Expr>) : Expr() {
        val target = terms.first()
        val args = terms.drop(1)

        override fun toString(): String {
            val inner = terms.joinToString(", ")
            return "{$inner}"
        }
    }

    data class Grouping(val body: Expr) : Expr()

    data class Quote(val body: Expr) : Expr()

    data class Unquote(val body: Expr) : Expr()

}

