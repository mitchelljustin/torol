import Token.Type.*


class Parser(
    private val tokens: List<Token>,
) {
    class ParseError(where: String, message: String, token: Token) :
        Exception("[$where at ${token.pos}] $message ($token)")

    private var current = 0
    private val curToken get() = tokens[current]
    private val prevToken get() = tokens[current - 1]
    private val nextToken get() = tokens.getOrNull(current + 1)

    fun parse() = program()

    private fun program(): Expr.Sequence {
        mark("program")
        val exprs = ArrayList<Expr>()
        while (!present(EOF)) {
            consumeAny(NEWLINE, DEDENT, INDENT, where = "before statement in program")
            exprs.add(assignment())
            consumeAny(NEWLINE, DEDENT, INDENT, where = "after statement in program")
        }
        val expr = Expr.Sequence(exprs)
        returning("program", expr)
        return expr
    }

    private fun report(msg: String) = println("-- $msg")

    private fun mark(where: String) {
        report("in $where: $current:$curToken at ${curToken.pos}")
    }

    private fun returning(where: String, what: Expr) {
        report("in $where: returning $what")
    }

    private fun assignment(): Expr {
        mark("assignment")
        var expr = apply()

        if (present(EQUAL, EQUAL_GREATER)) {
            val operator = consume(where = "in assignment (operator)")
            val value = apply()
            expr = Expr.Assignment(expr, operator, value)
        }

        returning("assignment", expr)

        return expr
    }

    private fun apply(): Expr {
        mark("apply")
        val values = arrayListOf<Expr>()
        val target = primary()

        while (!present(EQUAL, EQUAL_GREATER, RPAREN, DEDENT, INDENT, NEWLINE, RBRAC, EOF)) {
            mark("appending to apply: value")
            val value = primary()
            values.add(value)
        }
        if (startOfSequence()) {
            mark("appending to apply: final sequence")
            val finalSequence = sequence()
            val (labelStmts, valueStmts) = finalSequence.exprs.partition { stmt ->
                stmt is Expr.Apply && stmt.target is Expr.Label && stmt.values.size == 1
            }
            if (labelStmts.isNotEmpty()) {
                if (valueStmts.isNotEmpty())
                    throw parseError(
                        "apply final sequence",
                        "cannot both have label statements and value statements"
                    )
                labelStmts.forEach { stmt ->
                    val (label, labelValues) = stmt as Expr.Apply
                    values.add(label)
                    values.add(labelValues.first())
                }
            } else {
                values.add(finalSequence)
            }
        }

        val expr = Expr.Apply(target, values)
        returning("apply", expr)
        return expr
    }

    private fun primary(): Expr {
        mark("primary")
        val expr = when {
            present(IDENT) -> ident()
            present(STRING, NUMBER) -> literal()
            present(DIRECTIVE) -> directive()
            present(LPAREN) -> grouping()
            present(LBRAC) -> unquote()
            present(LABEL) -> label()
            present(PIPE) -> quote()
            startOfSequence() -> sequence()
            else -> throw parseError("primary", "illegal primary expression")
        }
        returning("primary", expr)
        return expr
    }

    private fun unquote(): Expr {
        mark("unquote")
        consume(LBRAC, where = "to start unquote")
        val expr = assignment()
        consume(RBRAC, where = "to end unquote")
        returning("unquote", expr)
        return Expr.Unquote(expr)
    }

    private fun quote(): Expr {
        mark("quote")
        consume(PIPE, where = "before quote")
        val expr = assignment()
        returning("quote", expr)
        return Expr.Quote(expr)
    }

    private fun startOfSequence() = present(NEWLINE) && nextToken?.type == INDENT

    private fun grouping(): Expr {
        mark("grouping")
        consume(LPAREN, where = "before parenthesis grouping")
        if (consumeMaybe(RPAREN))
            return Expr.Nil()
        val expr = Expr.Grouping(assignment())
        consume(RPAREN, where = "after parenthesis grouping")
        returning("grouping", expr)
        return expr
    }

    private fun sequence(): Expr.Sequence {
        mark("sequence")
        consume(NEWLINE, where = "before sequence")
        consume(INDENT, where = "before sequence")
        val stmts = ArrayList<Expr>()
        while (!present(DEDENT)) {
            mark("appending statement to sequence")
            stmts.add(assignment())
            consumeMaybe(NEWLINE, where = "after statement in sequence")
        }
        consume(DEDENT, where = "after sequence")
        val expr = Expr.Sequence(stmts)
        returning("sequence", expr)
        return expr
    }

    private fun label() =
        Expr.Label(consume(where = "in label()").value as String)

    private fun literal() =
        Expr.Literal(consume(where = "in literal()").value!!)

    private fun directive() =
        Expr.Directive(consume(where = "in directive()").value as String)

    private fun ident() =
        Expr.Ident(consume(IDENT, where = "in ident()").lexeme)

    private fun consumeMaybe(vararg types: Token.Type, where: String = ""): Boolean {
        report("consuming maybe ${types.joinToString(" | ")} $where")
        if (present(*types)) {
            consume(*types, where = where)
            return true
        }
        return false
    }

    private fun consumeAny(vararg types: Token.Type, where: String = "") {
        report("consuming any ${types.joinToString(" | ")} $where")
        while (present(*types))
            consume(*types, where = where)
    }

    private fun consume(vararg types: Token.Type, where: String = ""): Token {
        if (types.isNotEmpty() && !present(*types))
            throw parseError(where, "expected ${types.joinToString(" | ")}, got $curToken")
        report("consuming $current:$curToken $where")
        current++
        return prevToken

    }

    private fun present(vararg types: Token.Type) = present(types.toSet())

    private fun present(types: Set<Token.Type>) = curToken.type in types

    private fun parseError(where: String, message: String) = ParseError(where, message, curToken)
}