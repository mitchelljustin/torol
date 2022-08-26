import Token.Type.*


class Parser(
    private val tokens: List<Token>,
) {

    class ParseError(where: String, message: String, token: Token) :
        Exception("[$where at ${token.pos}] $message: $token")

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
        var expr = assembly()

        while (present(Operators)) {
            val operator = consume(where = "in binary (operator)")
            val value = assembly()
            expr = Expr.Binary(expr, operator, value)
        }

        returning("assignment", expr)

        return expr
    }

    private fun assembly(): Expr {
        mark("assembly")
        val expr =
            if (present(BANG)) {
                consume(BANG, where = "before assembly")
                Expr.Assembly(sexp())
            } else phrase()
        returning("assembly", expr)
        return expr
    }

    private fun phrase(): Expr {
        mark("phrase")
        val terms = arrayListOf(primary())

        while (!present(RPAREN, DEDENT, INDENT, NEWLINE, EOF, *Operators.toTypedArray())) {
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
            present(TILDE) -> unquote()
            present(LABEL) -> label()
            present(STAR) -> multi()
            startOfSequence() -> sequence()
            else -> throw parseError("primary", "illegal primary expression")
        }
        returning("primary", expr)
        return expr
    }

    private fun multi(): Expr.Multi {
        consume(STAR, where = "vararg()")
        val body = primary()
        return Expr.Multi(body)
    }

    private fun unquote(): Expr.Unquote {
        mark("unquote")
        consume(TILDE, where = "to start unquote")
        val body = when {
            present(LPAREN) -> grouping()
            present(IDENT) -> ident()
            else -> throw parseError("unquote", "illegal unquote body")
        }
        returning("unquote", body)
        return Expr.Unquote(body)
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


    private var groupingNo = 0
    private fun sexp(): Expr.Sexp {
        mark("sexp")
        val sexp = when {
            consumeMaybe(LPAREN, where = "sexp grouping (start $groupingNo)") -> {
                val thisGrouping = groupingNo
                groupingNo += 1
                val terms = ArrayList<Expr.Sexp>()
                while (!present(RPAREN)) {
                    consumeWhitespace("sexp grouping")
                    terms.add(sexp())
                    consumeWhitespace("sexp grouping")
                }
                consume(RPAREN, where = "sexp grouping (end $thisGrouping)")
                Expr.Sexp.Grouping(terms)
            }

            consumeMaybe(DOLLAR, where = "sexp $") -> {
                val name = consume(IDENT, where = "sexp (dollar ident)").lexeme
                Expr.Sexp.Ident("$$name")
            }

            consumeMaybe(IDENT, where = "sexp (ident)") -> {
                val name = buildString {
                    append(prevToken.lexeme)
                    if (consumeMaybe(DOT, where = "sexp (.)")) {
                        append(".")
                        append(consume(IDENT, where = "sexp (post-dot ident)").lexeme)
                    }
                }
                Expr.Sexp.Ident(name)
            }

            consumeMaybe(NUMBER, where = "sexp (number)") ->
                Expr.Sexp.Literal(prevToken.value!!)

            consumeMaybe(STRING, where = "sexp (string)") ->
                Expr.Sexp.Literal(prevToken.value!!)

            present(TILDE) -> Expr.Sexp.Unquote(unquote().body)

            else -> throw parseError("sexp", "expected sexp")
        }
        returning(where = "sexp", sexp)
        return sexp
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
        if (present(*types)) {
            consume(*types, where = where)
            return true
        }
        return false
    }


    private fun consumeAny(vararg types: Token.Type, where: String = "") {
        report("consuming any ${types.joinToString("|")} $where")
        while (present(*types))
            consume(*types, where = where)
    }

    private fun consumeWhitespace(where: String) {
        consumeAny(NEWLINE, DEDENT, INDENT, where = where)
    }

    private fun consume(vararg types: Token.Type, where: String = "") = consume(types.toSet(), where)
    private fun consume(types: Set<Token.Type>, where: String = ""): Token {
        if (types.isNotEmpty() && !present(types))
            throw parseError(where, "expected ${types.joinToString("|")}, got $curToken")
        report("consuming $current:$curToken $where")
        current++
        return prevToken

    }

    private fun present(vararg types: Token.Type) = present(types.toSet())

    private fun present(types: Set<Token.Type>) = curToken.type in types

    private fun parseError(where: String, message: String) = ParseError(where, message, curToken)
}