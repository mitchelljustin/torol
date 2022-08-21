open class Expr {
    companion object {
        fun isLeaf(expr: Expr) = when (expr) {
            is Nil, is Ident, is Label, is Literal -> true
            else -> false
        }

        fun transform(expr: Expr, func: (Expr) -> Expr?): Expr {
            fun modify(expr: Expr) = func(expr) ?: expr
            return when (expr) {
                is Nil, is Ident, is Label, is Literal, is Operator,
                -> modify(expr)
                is Sequence -> modify(Sequence(expr.exprs.map(::modify)))
                is Assignment -> modify(Assignment(modify(expr.target), expr.operator, modify(expr.value)))
                is Directive -> modify(Directive(modify(expr.body)))
                is Apply -> modify(Apply(expr.terms.map(::modify)))
                is Grouping -> modify(Grouping(modify(expr.body)))
                is Quote -> modify(Quote(modify(expr.quoted)))
                is Unquote -> modify(Unquote(modify(expr.body)))
                else -> throw Exception("unsupported expr for transform(): $expr")
            }
        }
    }

    class Nil : Expr() {
        override fun toString() = "Nil"
    }

    data class Operator(val operator: String) : Expr() {
        override fun toString() = "Operator($operator)"
    }

    data class Ident(val name: String) : Expr() {
        override fun toString() = "Ident($name)"
    }

    data class Label(val name: String) : Expr() {
        override fun toString() = "Label($name)"
    }

    data class Literal(val literal: Any) : Expr() {
        override fun toString() = "Literal($literal)"
    }


    data class Sequence(val exprs: List<Expr>) : Expr() {
        override fun toString(): String {
            val inner = exprs.joinToString(", ")
            return "Sequence[$inner]"
        }
    }

    data class Assignment(val target: Expr, val operator: Token, val value: Expr) : Expr()

    data class Directive(val body: Expr) : Expr()

    data class Apply(val terms: List<Expr>) : Expr() {
        val target = terms.first()
        val args = terms.drop(1)

        override fun toString(): String {
            val inner = terms.joinToString(", ")
            return "{$inner}"
        }
    }

    data class Grouping(val body: Expr) : Expr()

    data class Quote(val quoted: Expr) : Expr()

    data class Unquote(val body: Expr) : Expr()

}

