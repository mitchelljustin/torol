import Token.Type.*

class Parser(
    private val tokens: List<Token>,
) {

    class ParseError(where: String, message: String, token: Token) :
        Exception("[$where at ${token.pos}] $message ($token)")

    companion object {
        private val AssignOperators = setOf(EQUAL, EQUAL_GREATER)
        private val UserOperators = setOf(RARROW, LARROW, PLUS, MINUS, STAR, SLASH)
        private val Operators = AssignOperators + UserOperators
        private val Literals = setOf(STRING, NUMBER)

        fun parse(source: String, verbose: Boolean = false): Expr {
            if (verbose) println("----------\n$source\n----------")
            val tokens = Scanner(source).scan()
            if (verbose) println("## ${tokens.joinToString(" ")}")
            val parser = Parser(tokens)
            try {
                val sequence = parser.parse()
                if (verbose) println("|| $sequence")
                return sequence
            } finally {
                if (verbose) println(parser.derivation)
            }
        }
    }

    private var derivation = StringBuilder()
    private var current = 0
    private val curToken get() = tokens[current]
    private val prevToken get() = tokens[current - 1]
    private val nextToken get() = tokens.getOrNull(current + 1)

    fun parse() = program()

    private fun program(): Expr.Sequence {
        mark("program")
        val exprs = ArrayList<Expr>()
        consumeWhitespace("before program")
        while (!present(EOF)) {
            consumeWhitespace("before statement in program")
            exprs.add(binary())
            consumeWhitespace("after statement in program")
        }
        val expr = Expr.Sequence(exprs)
        returning("program", expr)
        return expr
    }


    private fun binary(): Expr {
        mark("assignment")
        var expr = directive()

        while (present(Operators)) {
            val operator = consume(where = "in binary (operator)")
            val value = directive()
            expr = Expr.Binary(expr, operator, value)
        }

        returning("assignment", expr)

        return expr
    }

    private fun directive(): Expr {
        mark("directive")
        val expr =
            if (present(BANG)) {
                consume(BANG, where = "before directive")
                val body = if (present(LPAREN)) sexp() else phrase()
                Expr.Directive(body)
            } else phrase()
        returning("directive", expr)
        return expr
    }

    private fun phrase(): Expr {
        mark("phrase")
        val terms = arrayListOf(primary())

        while (!present(RPAREN, RBRAC, DEDENT, INDENT, NEWLINE, EOF, *Operators.toTypedArray())) {
            mark("appending to phrase: primary")
            val term = primary()
            terms.add(term)
        }
        if (startOfSequence()) {
            mark("appending to phrase: final sequence")
            val finalSequence = sequence()
            val (labelStmts, valueStmts) = finalSequence.exprs.partition { stmt ->
                stmt is Expr.Phrase && stmt.target is Expr.Label && stmt.terms.size == 2
            }
            if (labelStmts.isNotEmpty()) {
                if (valueStmts.isNotEmpty())
                    throw parseError(
                        "phrase final sequence",
                        "cannot both have label statements and value statements"
                    )
                labelStmts.forEach {
                    terms += (it as Expr.Phrase).terms // adds "label: value"
                }
            } else terms.add(finalSequence)
        }

        val expr = if (terms.size > 1) Expr.Phrase(terms) else terms.first()
        returning("phrase", expr)
        return expr
    }

    private fun primary(): Expr {
        mark("primary")
        val expr = when {
            present(IDENT) -> ident()
            present(Literals) -> literal()
            present(LPAREN) -> grouping()
            present(LBRAC) -> unquote()
            present(LABEL) -> label()
            present(PIPE) -> quote()
            present(STAR) -> multi()
            startOfSequence() -> sequence()
            else -> throw parseError("primary", "illegal primary expression")
        }
        returning("primary", expr)
        return expr
    }

    private fun multi(): Expr {
        consume(STAR, where = "vararg()")
        val body = primary()
        return Expr.Multi(body)
    }

    private fun unquote(): Expr {
        mark("unquote")
        consume(LBRAC, where = "to start unquote")
        val expr = binary()
        consume(RBRAC, where = "to end unquote")
        returning("unquote", expr)
        return Expr.Unquote(expr)
    }

    private fun quote(): Expr {
        mark("quote")
        consume(PIPE, where = "before quote")
        val expr = binary()
        returning("quote", expr)
        return Expr.Quote(expr)
    }

    private fun startOfSequence() = present(NEWLINE) && nextToken?.type == INDENT

    private fun grouping(): Expr {
        mark("grouping")
        consume(LPAREN, where = "before parenthesis grouping")
        if (consumeMaybe(RPAREN))
            return Expr.Nil()
        val expr = Expr.Grouping(binary())
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
            stmts.add(binary())
            consumeMaybe(NEWLINE, where = "after statement in sequence")
        }
        consume(DEDENT, where = "after sequence")
        val expr = Expr.Sequence(stmts)
        returning("sequence", expr)
        return expr
    }

    private fun label() =
        Expr.Label(consume(LABEL, where = "label()").value as String)

    private fun literal() =
        Expr.Literal(consume(Literals, where = "literal()").value!!)

    private fun ident() =
        Expr.Ident(consume(IDENT, where = "ident()").lexeme)

    private fun sexp(): Expr {
        consume(LPAREN, where = "sexp")
        var level = 1
        val body = buildString {
            fun maybeAddSpace() {
                if (!present(RPAREN))
                    append(" ")
            }
            append("(")
            while (level > 0) {
                when {
                    consumeMaybe(LPAREN, where = "sexp") -> {
                        append("(")
                        level += 1
                    }

                    consumeMaybe(RPAREN, where = "sexp") -> {
                        append(")")
                        maybeAddSpace()
                        level -= 1
                    }

                    consumeMaybe(DOLLAR, where = "sexp") -> {
                        append("$")
                        append(consume(IDENT, where = "sexp").lexeme)
                        maybeAddSpace()
                    }

                    consumeMaybe(IDENT, where = "sexp") -> {
                        append(prevToken.lexeme)
                        if (consumeMaybe(DOT)) {
                            append(".")
                            append(consume(IDENT, where = "sexp").lexeme)
                        }
                        maybeAddSpace()
                    }

                    else -> {
                        append(consume(where = "sexp").lexeme)
                        maybeAddSpace()
                    }
                }
            }
        }

        return Expr.Sexp(body)
    }


    // --- Utility functions ---

    private fun report(msg: String) {
        derivation.append("-- $msg\n")
    }

    private fun mark(where: String) {
        report("in $where: $current:$curToken at ${curToken.pos}")
    }

    private fun returning(where: String, what: Expr) {
        report("in $where: returning $what")
    }

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

    private fun consumeWhitespace(where: String) {
        consumeAny(NEWLINE, DEDENT, INDENT, where = where)
    }


    private fun consume(vararg types: Token.Type, where: String = "") = consume(types.toSet(), where)
    private fun consume(types: Set<Token.Type>, where: String = ""): Token {
        if (types.isNotEmpty() && !present(types))
            throw parseError(where, "expected ${types.joinToString(" | ")}, got $curToken")
        report("consuming $current:$curToken $where")
        current++
        return prevToken

    }

    private fun present(vararg types: Token.Type) = present(types.toSet())

    private fun present(types: Set<Token.Type>) = curToken.type in types

    private fun parseError(where: String, message: String) = ParseError(where, message, curToken)
}