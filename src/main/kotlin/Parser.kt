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

    private fun program(): Expr {
        mark("program")
        val exprs = ArrayList<Expr>()
        while (!present(EOF)) {
            exprs.add(assignment())
            consumeAny(NEWLINE, where = "after statement in program")
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
        val expr = Expr.Apply(primary(), arrayListOf())

        while (!present(EQUAL, EQUAL_GREATER, RPAREN, INDENT, NEWLINE, EOF)) {
            mark("appending to apply: value")
            val value = primary()
            expr.values.add(value)
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
                    val (target, values) = stmt as Expr.Apply
                    expr.values.add(target)
                    expr.values.add(values.first())
                }
            } else {
                expr.values.add(finalSequence)
            }
        }

        if (expr.values.isEmpty()) {
            returning("apply", expr.target)
            return expr.target
        }

        returning("apply", expr)
        return expr
    }

    private fun primary(): Expr {
        mark("primary")
        val expr = when {
            present(IDENT) -> ident()
            present(STRING, NUMBER, SYMBOL) -> literal()
            present(INSTRUCTION) -> instruction()
            present(LPAREN) -> grouping()
            present(LABEL) -> label()
            startOfSequence() -> sequence()
            else -> throw parseError("primary", "illegal primary expression")
        }
        returning("primary", expr)
        return expr
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
        Expr.Label(consume(where = "in label()").literal as String)

    private fun literal() =
        Expr.Literal(consume(where = "in literal()").literal!!)

    private fun instruction() =
        Expr.Instruction(consume(where = "in instruction()").lexeme.substring(1))

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