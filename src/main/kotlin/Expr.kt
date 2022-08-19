open class Expr {
    companion object {
        fun isLeaf(expr: Expr) = when (expr) {
            is Nil, is Ident, is LabelSub, is Label, is Literal, is Instruction -> true
            else -> false
        }
    }

    class Nil : Expr() {
        override fun toString() = "Nil"
    }

    data class Ident(val name: String) : Expr() {
        override fun toString() = "Ident($name)"
    }

    data class LabelSub(val name: String) : Expr() {
        override fun toString() = "LabelSub($name)"
    }

    data class Label(val name: String) : Expr() {
        override fun toString() = "Label($name)"
    }

    data class Literal(val x: Any) : Expr() {
        override fun toString() = "Literal($x)"
    }

    data class Instruction(val op: String) : Expr()


    data class Sequence(val exprs: List<Expr>) : Expr() {
        override fun toString(): String {
            val inner = exprs.joinToString(", ")
            return "Sequence[$inner]"
        }
    }

    data class Assignment(val target: Expr, val operator: Token, val value: Expr) : Expr()

    data class Apply(val target: Expr, val values: List<Expr>) : Expr() {
        val terms get() = listOf(target) + values

        constructor(terms: List<Expr>) : this(terms.first(), terms.drop(1))

        override fun toString(): String {
            val inner = (listOf(target) + values).joinToString(", ")
            return "{$inner}"
        }
    }

    data class Grouping(val expr: Expr) : Expr()

    data class Quote(val q: Expr) : Expr()

    data class Unquote(val u: Expr) : Expr()

}

