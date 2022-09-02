import Token.Type.*


class Parser(
    private val tokens: List<Token>,
) {

    class ParseException(where: String, message: String, token: Token, derivation: StringBuilder) :
        CompilerException("$derivation\n[$where at ${token.pos}] $message: $token")

    companion object {
        val AssignmentOperators = setOf(EQUAL, EQUAL_GREATER, LARROW, RARROW)
        val BinaryOperators = setOf(
            PLUS, SLASH, MINUS, STAR,
            EQUAL_EQUAL, BANG_EQUAL,
            GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,
            PLUS_EQUAL, MINUS_EQUAL, SLASH_EQUAL, STAR_EQUAL,
        )
        val PhraseTerminators = setOf(
            RPAREN, DEDENT, INDENT, NEWLINE, EOF, BACKSLASH,
        ) + BinaryOperators + AssignmentOperators
        val Literals = setOf(STRING, NUMBER)
        val LineTerminators = setOf(NEWLINE, INDENT, DEDENT)

        fun parse(source: String, verbose: Boolean = false): AST {
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

    private fun program(): AST.Sequence {
        entering("program")
        val exprs = ArrayList<AST>()
        consumeLineTerminators("before program")
        while (!present(EOF)) {
            consumeLineTerminators("before statement in program")
            exprs.add(statement())
            consumeLineTerminators("after statement in program")
        }
        val expr = AST.Sequence(exprs, topLevel = true)
        returning("program", expr)
        return expr
    }

    private fun statement(): AST {
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

    private fun assignment(): AST {
        entering("assignment")
        var expr = lateEval()

        if (present(AssignmentOperators)) {
            val operator = AST.Operator(consume(where = "in assignment (operator)"))
            val value = lateEval()
            expr = AST.Assignment(expr, operator, value)
        }

        returning("assignment", expr)

        return expr
    }

    private fun lateEval(): AST {
        entering("lateEval")
        val exprs = arrayListOf(unaryLow())
        while (consumeMaybe(BACKSLASH, where = "after late eval expr"))
            exprs.add(unaryLow())
        val head = exprs.first()
        if (exprs.size == 1) {
            returning("lateEval", head)
            return head
        }
        val terms = when (head) {
            is AST.Phrase -> head.terms
            is AST.Ident -> listOf(head)
            else -> throw error("lateEval glue-up", "head expr must be a phrase or ident")
        }.toMutableList()
        val tail = exprs.drop(1)
        terms += tail.flatMap { expr ->
            when (expr) {
                is AST.Sequence -> unpackSequence(expr)
                else -> listOf(expr)
            }
        }
        val gluedPhrase = AST.Phrase(terms)
        returning("lateEval", gluedPhrase)
        return gluedPhrase
    }

    private fun unaryLow(): AST {
        if (consumeMaybe(MINUS, where = "in unaryMinus")) {
            return AST.Unary(AST.Operator(prevToken), unaryLow())
        }
        return binary()
    }

    private fun binary(): AST {
        entering("binary")
        var expr = assembly()

        while (present(BinaryOperators)) {
            val operator = AST.Operator(consume(where = "in binary (operator)"))
            val rhs = assembly()
            expr = AST.Binary(expr, operator, rhs)
        }

        returning("binary", expr)

        return expr
    }

    private fun assembly(): AST {
        entering("assembly")
        val expr = when {
            consumeMaybe(BANG, where = "before assembly") -> {
                val sexps = arrayListOf(sexp())
                while (!present(LineTerminators))
                    sexps.add(sexp())
                val body = when (sexps.size) {
                    1 -> sexps.first()
                    else -> AST.Sexp.List(sexps, parens = false)
                }
                AST.Assembly(body)
            }

            else -> phrase()
        }
        returning("assembly", expr)
        return expr
    }

    private fun phrase(): AST {
        entering("phrase")
        val head = splat()
        val terms = arrayListOf(head)
        return when (head) {
            is AST.Label -> {
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
                val expr = AST.Phrase(terms)
                returning("label phrase", expr)
                expr
            }

            is AST.Ident, is AST.Path, is AST.Access -> {
                while (!present(PhraseTerminators)) {
                    val term = splat()
                    entering("adding term to phrase: '$term'")
                    terms.add(term)
                }
                if (startOfSequence()) {
                    entering("adding to phrase: final sequence")
                    val finalSequence = sequence()
                    terms += unpackSequence(finalSequence)
                }
                val expr = if (terms.size > 1) AST.Phrase(terms) else head
                returning("phrase", expr)
                expr
            }

            else -> head
        }

    }

    private fun unpackSequence(sequence: AST.Sequence): List<AST> {
        report("unpacking sequence: '$sequence'")
        val terms = sequence.stmts.flatMap { stmt ->
            when {
                stmt is AST.Phrase && stmt.target is AST.Label -> {
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

    private fun splat(): AST {
        entering("splat")
        var expr = unaryHigh()
        if (consumeMaybe(DOT_DOT))
            expr = AST.Splat(expr)
        returning("splat", expr)
        return expr
    }

    private fun unaryHigh(): AST {
        entering("unaryHigh")
        val expr = when {
            present(AMPERSAND) -> unaryRef()
            present(TILDE) -> unquote()
            else -> primary()
        }
        returning("unaryHigh", expr)
        return expr
    }

    private fun primary(): AST {
        entering("primary")
        val expr = when {
            present(IDENT) || present(DOT) -> access()
            present(NOMEN) || present(COLON_COLON) -> path()
            present(Literals) -> literal()
            present(LPAREN) -> grouping()
            present(LABEL) -> label()
            present(INDENT) -> throw error(
                "primary",
                "illegal INDENT in primary expression, probably a double indent"
            )

            startOfSequence() -> {
                entering("sequence as primary")
                sequence()
            }

            else -> throw error("primary", "illegal primary expression")
        }
        returning("primary", expr)
        return expr
    }

    private fun unaryRef(): AST {
        entering("unaryRef")
        consume(AMPERSAND, where = "in unaryRef")
        val expr = AST.Unary(AST.Operator(prevToken), primary())
        returning("unaryRef", expr)
        return expr
    }

    private fun path(): AST {
        val path = segmented(::pathSegment, AST::Path, COLON_COLON)
        if (path is AST.Nomen)
            return path
        if (path !is AST.Path)
            throw error("nomenOrPath", "huh??")
        val illegalSegments = path.segments.dropLast(1).filterIsInstance<AST.Ident>()
        if (illegalSegments.isNotEmpty())
            throw error(
                "nomenOrPath",
                "illegal ident path segments, only last may be ident: ${illegalSegments.joinToString(", ")}"
            )
        return path
    }

    private fun access() = segmented(::ident, AST::Access, DOT)

    private fun segmented(segment: () -> AST, combinator: (List<AST>, Boolean) -> AST, separator: Token.Type): AST {
        entering("segmented")
        val prefixed = consumeMaybe(separator, where = "segmented (prefixed separator)")
        val segments = arrayListOf(segment())
        while (consumeMaybe(separator, where = "segmented $segment"))
            segments.add(segment())
        val expr = when {
            segments.size > 1 || prefixed -> combinator(segments, prefixed)
            else -> segments.first()
        }
        returning("segmented", expr)
        return expr
    }

    private fun unquote(): AST.Unquote {
        entering("unquote")
        consume(TILDE, where = "to start unquote")
        val body = when {
            present(LPAREN) -> grouping()
            present(IDENT) -> ident()
            else -> throw error("unquote", "illegal unquote body")
        }
        returning("unquote", body)
        return AST.Unquote(body)
    }

    @Suppress("SameParameterValue")
    private fun twoPresent(first: Token.Type, second: Token.Type) = curToken.type == first && nextToken?.type == second
    private fun startOfSequence() = twoPresent(NEWLINE, INDENT)

    private fun grouping(): AST {
        entering("grouping")
        consume(LPAREN, where = "before parenthesis grouping")
        if (consumeMaybe(RPAREN))
            return AST.Nil()
        val expr = AST.Grouping(assignment())
        consume(RPAREN, where = "after parenthesis grouping")
        returning("grouping", expr)
        return expr
    }

    private fun sequence(): AST.Sequence {
        entering("sequence")
        consume(NEWLINE, where = "before sequence")
        consume(INDENT, where = "before sequence")
        var level = 1
        while (present(INDENT)) {
            level += 1
            consume(INDENT, where = "level $level before sequence (extra)")
        }
        val stmts = ArrayList<AST>()
        while (!present(DEDENT)) {
            val stmt = statement()
            entering("appending statement to sequence: '$stmt'")
            stmts.add(stmt)
        }
        repeat(level) { i ->
            consume(DEDENT, where = "level $i after sequence")
        }
        if (syntheticNewline)
            throw error("sequence", "syntheticNewline = true")
        syntheticNewline = true
        val expr = AST.Sequence(stmts)
        returning("sequence", expr)
        return expr
    }

    private fun label() =
        AST.Label(consume(LABEL, where = "in label").value as String)

    private fun literal() =
        AST.Literal(consume(Literals, where = "in literal").value!!)

    private fun ident(): AST.Ident {
        return AST.Ident(consume(IDENT, where = "in ident").lexeme)
    }

    private fun pathSegment(): AST {
        entering("pathSegment")
        val segment = when {
            present(IDENT) -> AST.Ident(consume(IDENT, where = "pathComponent").lexeme)
            present(NOMEN) -> AST.Nomen(consume(NOMEN, where = "pathComponent").lexeme)
            present(TILDE) -> unquote()
            else -> throw error(where = "pathComponent", "path component may either be an ident or a nomen")
        }
        returning("pathSegment", segment)
        return segment
    }

    private var groupingNo = 0
    private fun sexp(): AST.Sexp {
        entering("sexp")
        val sexp = when {
            consumeMaybe(LPAREN, where = "sexp grouping (start $groupingNo)") -> {
                val thisGrouping = groupingNo
                groupingNo += 1
                val terms = ArrayList<AST.Sexp>()
                while (!present(RPAREN)) {
                    consumeLineTerminators("sexp grouping")
                    terms.add(sexp())
                    consumeLineTerminators("sexp grouping")
                }
                consume(RPAREN, where = "sexp grouping (end $thisGrouping)")
                AST.Sexp.List(terms, parens = true)
            }

            consumeMaybe(DOLLAR, where = "sexp $") -> {
                val name = consume(IDENT, where = "sexp (dollar ident)").lexeme
                AST.Sexp.Ident("$$name")
            }

            present(IDENT) -> when (val expr = access()) {
                is AST.Ident -> AST.Sexp.Ident(expr.name)
                is AST.Access -> {
                    if (expr.prefixed)
                        throw error(where = "in sexp", "sexp access cannot be dot-prefixed")
                    AST.Sexp.Access(expr.segments.map { AST.Sexp.Ident((it as AST.Ident).name) })
                }

                else -> throw error(where = "in sexp", "illegal access: '$expr'")
            }

            consumeMaybe(NUMBER, where = "sexp (number)") ->
                AST.Sexp.Literal(prevToken.value!!)

            consumeMaybe(STRING, where = "sexp (string)") ->
                AST.Sexp.Literal(prevToken.value!!)

            present(TILDE) -> AST.Sexp.Unquote(unquote().body)

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

    private fun entering(where: String) {
        report("in $where: $curToken:$current at ${curToken.pos}")
    }

    private fun returning(where: String, what: AST) {
        report("in $where: returning '$what'")
    }

    private fun consumeMaybe(vararg types: Token.Type, where: String = "") = consumeMaybe(types.toSet(), where)
    private fun consumeMaybe(types: Set<Token.Type>, where: String = ""): Boolean {
        if (present(types)) {
            consume(types, where = where)
            return true
        }
        return false
    }

    private fun consumeLineTerminators(where: String) {
        report("consuming any line terminators $where")
        while (present(LineTerminators))
            consume(LineTerminators, where = where)
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