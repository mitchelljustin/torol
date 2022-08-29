import Token.Type.*


class Parser(
    private val tokens: List<Token>,
) {

    class ParseException(where: String, message: String, token: Token, derivation: StringBuilder) :
        CompilerException("[$where at ${token.pos}] $message: $token\n$derivation")

    companion object {
        val AssignmentOperators = setOf(EQUAL, EQUAL_GREATER, LARROW, RARROW)
        val BinaryOperators = setOf(
            PLUS, MINUS, STAR, SLASH,
            EQUAL_EQUAL, BANG_EQUAL,
            GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,
            PLUS_EQUAL, MINUS_EQUAL, SLASH_EQUAL, STAR_EQUAL,
        )
        val Operators = AssignmentOperators + BinaryOperators
        val Literals = setOf(STRING, NUMBER)

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

    private var syntheticNewline: Boolean = false
    private var derivation = StringBuilder()
    private var stepNo = 0
    private var current = 0
    private val curToken get() = tokens[current]
    private val prevToken get() = tokens[current - 1]
    private val nextToken get() = tokens.getOrNull(current + 1)
    private val curTokVerbose get() = "$curToken:$current"

    fun parse() = program()

    private fun program(): Expr.Sequence {
        mark("program")
        val exprs = ArrayList<Expr>()
        consumeWhitespace("before program")
        while (!present(EOF)) {
            consumeWhitespace("before statement in program")
            exprs.add(statement())
            consumeWhitespace("after statement in program")
        }
        val expr = Expr.Sequence(exprs, topLevel = true)
        returning("program", expr)
        return expr
    }

    private fun statement(): Expr {
        val stmt = assignment()
        if (present(EOF))
            return stmt
        if (syntheticNewline) {
            report("consuming synthetic newline after statement")
            syntheticNewline = false
        } else {
            consume(NEWLINE, where = "after statement")
        }
        return stmt
    }

    private fun assignment(): Expr {
        mark("assignment")
        var expr = lateEval()

        if (present(AssignmentOperators)) {
            val operator = Expr.Operator(consume(where = "in assignment (operator)"))
            val value = lateEval()
            expr = Expr.Assignment(expr, operator, value)
        }

        returning("assignment", expr)

        return expr
    }

    private fun lateEval(): Expr {
        mark("lateEval")
        val exprs = arrayListOf(binary())
        while (consumeMaybe(BACKSLASH, where = "after late eval expr"))
            exprs.add(binary())
        val head = exprs.first()
        if (exprs.size == 1) {
            returning("lateEval", head)
            return head
        }
        val terms = when (head) {
            is Expr.Phrase -> head.terms
            is Expr.Ident -> listOf(head)
            else -> throw error("lateEval glue-up", "head expr must be a phrase or ident")
        }.toMutableList()
        val tail = exprs.drop(1)
        terms += tail.flatMap { expr ->
            when (expr) {
                is Expr.Sequence -> unpackSequence(expr)
                else -> listOf(expr)
            }
        }
        val gluedPhrase = Expr.Phrase(terms)
        returning("lateEval", gluedPhrase)
        return gluedPhrase
    }

    private fun binary(): Expr {
        mark("binary")
        var expr = assembly()

        while (present(BinaryOperators)) {
            val operator = Expr.Operator(consume(where = "in binary (operator)"))
            val rhs = assembly()
            expr = Expr.Binary(expr, operator, rhs)
        }

        returning("binary", expr)

        return expr
    }

    private fun assembly(): Expr {
        mark("assembly")
        val expr = when {
            consumeMaybe(BANG, where = "before assembly") -> {
                val sexps = arrayListOf(sexp())
                while (!present(NEWLINE, DEDENT, INDENT))
                    sexps.add(sexp())
                val body = when (sexps.size) {
                    1 -> sexps.first()
                    else -> Expr.Sexp.List(sexps, parens = false)
                }
                Expr.Assembly(body)
            }

            else -> phrase()
        }
        returning("assembly", expr)
        return expr
    }

    private fun phrase(): Expr {
        mark("phrase")
        val head = unary()
        val terms = arrayListOf(head)
        return when (head) {
            is Expr.Label -> {
                terms.add(
                    when {
                        startOfSequence() -> {
                            report("label phrase sequence for '$head'")
                            sequence()
                        }

                        else -> {
                            report("label phrase single assignment for '$head'")
                            assignment()
                        }
                    }
                )
                val expr = Expr.Phrase(terms)
                returning("label phrase", expr)
                expr
            }

            is Expr.Ident -> {
                while (!present(RPAREN, DEDENT, INDENT, NEWLINE, EOF, BACKSLASH, *Operators.toTypedArray())) {
                    val term = unary()
                    mark("adding term to phrase: $term")
                    terms.add(term)
                }
                if (startOfSequence()) {
                    mark("adding to phrase: final sequence")
                    val finalSequence = sequence()
                    terms += unpackSequence(finalSequence)
                }
                val expr = if (terms.size > 1) Expr.Phrase(terms) else head
                returning("phrase", expr)
                expr
            }

            else -> head
        }

    }

    private fun unpackSequence(sequence: Expr.Sequence): List<Expr> {
        report("unpacking sequence: '$sequence'")
        val terms = sequence.stmts.flatMap { stmt ->
            when {
                stmt is Expr.Phrase && stmt.target is Expr.Label -> {
                    if (stmt.terms.size != 2) throw error(
                        "label phrase",
                        "must have exactly one statement"
                    )
                    stmt.terms
                }

                else -> listOf(stmt)
            }
        }
        return terms
    }

    private fun unary(): Expr {
        if (consumeMaybe(MINUS, where = "unary")) {
            return Expr.Unary(Expr.Operator(prevToken), unary())
        }
        return primary()
    }

    private fun primary(): Expr {
        mark("primary")
        val expr = when {
            present(IDENT) -> ident()
            present(Literals) -> literal()
            present(LPAREN) -> grouping()
            present(TILDE) -> unquote()
            present(LABEL) -> label()
            present(DOT_DOT) -> multi()
            present(INDENT) -> throw error(
                "primary",
                "illegal INDENT in primary expression, probably a double indent"
            )

            startOfSequence() -> {
                mark("sequence as primary")
                sequence()
            }

            else -> throw error("primary", "illegal primary expression")
        }
        returning("primary", expr)
        return expr
    }

    private fun multi(): Expr.Multi {
        consume(DOT_DOT, where = "multi()")
        val body = primary()
        return Expr.Multi(body)
    }

    private fun unquote(): Expr.Unquote {
        mark("unquote")
        consume(TILDE, where = "to start unquote")
        val body = when {
            present(LPAREN) -> grouping()
            present(IDENT) -> ident()
            else -> throw error("unquote", "illegal unquote body")
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
            val stmt = statement()
            mark("appending statement to sequence: '$stmt'")
            stmts.add(stmt)
        }
        consume(DEDENT, where = "after sequence")
        if (syntheticNewline)
            throw error("sequence", "syntheticNewline = true")
        syntheticNewline = true
        val expr = Expr.Sequence(stmts)
        returning("sequence", expr)
        return expr
    }

    private fun label() =
        Expr.Label(consume(LABEL, where = "label()").value as String)

    private fun literal() =
        Expr.Literal(consume(Literals, where = "literal()").value!!)

    private fun ident(): Expr.Ident {
        val name = buildString {
            append(consume(IDENT, where = "ident").lexeme)
            if (consumeMaybe(DOT, where = "ident dot")) {
                append(".")
                append(consume(IDENT, where = "ident post-dot").lexeme)
            }
        }
        return Expr.Ident(name)
    }


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
                Expr.Sexp.List(terms)
            }

            consumeMaybe(DOLLAR, where = "sexp $") -> {
                val name = consume(IDENT, where = "sexp (dollar ident)").lexeme
                Expr.Sexp.Ident("$$name")
            }

            present(IDENT) -> {
                val name = ident().name
                Expr.Sexp.Ident(name)
            }

            consumeMaybe(NUMBER, where = "sexp (number)") ->
                Expr.Sexp.Literal(prevToken.value!!)

            consumeMaybe(STRING, where = "sexp (string)") ->
                Expr.Sexp.Literal(prevToken.value!!)

            present(TILDE) -> Expr.Sexp.Unquote(unquote().body)

            else -> throw error("sexp", "expected sexp")
        }
        returning(where = "sexp", sexp)
        return sexp
    }

    // --- Utility functions ---

    private fun report(msg: String) {
        derivation.append("${stepNo.toString().padStart(5, ' ')} -- $msg\n")
        stepNo++
    }

    private fun mark(where: String) {
        report("in $where: $curToken:$current at ${curToken.pos}")
    }

    private fun returning(where: String, what: Expr) {
        report("in $where: returning '$what'")
    }

    private fun consumeMaybe(vararg types: Token.Type, where: String = ""): Boolean {
        if (present(*types)) {
            consume(*types, where = where)
            return true
        }
        return false
    }


    private fun consumeWhitespace(where: String) {
        val types = arrayOf(NEWLINE, DEDENT, INDENT)
        report("consuming any ${types.joinToString("|")} $where")
        while (present(*types))
            consume(*types, where = where)
    }

    private fun consume(vararg types: Token.Type, where: String = "") = consume(types.toSet(), where)
    private fun consume(types: Set<Token.Type>, where: String = ""): Token {
        if (types.isNotEmpty() && !present(types))
            throw error(where, "expected ${types.joinToString("|")}, got $curToken")
        report("consuming $curTokVerbose $where")
        current++
        return prevToken

    }


    private fun present(vararg types: Token.Type) = present(types.toSet())

    private fun present(types: Set<Token.Type>) = curToken.type in types

    private fun error(where: String, message: String) = ParseException(where, message, curToken, derivation)
}