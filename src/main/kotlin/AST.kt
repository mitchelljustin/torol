abstract class AST

open class Expr : AST() {
    data class Assignment(val target: Expr, val operator: Token, val value: Expr) : Expr()

    data class Apply(val target: Expr, val values: ArrayList<Expr>) : Expr() {
        override fun toString(): String {
            val inner = (listOf(target) + values).joinToString(", ")
            return "Apply($inner)"
        }
    }

    data class Grouping(val expr: Expr) : Expr()

    class Nil : Expr() {
        override fun toString() = "Nil"
    }

    data class Sequence(val exprs: ArrayList<Expr>) : Expr() {
        override fun toString(): String {
            val inner = exprs.joinToString(", ")
            return "Sequence[$inner]"
        }
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


    data class Quote(val q: Expr) : Expr()

    data class Unquote(val u: Expr) : Expr()

    data class Instruction(val op: String) : Expr()
}

