import Token.Type.*

class Scanner(
    private val source: String,
) {
    class ScanException(where: String, expected: String, actual: Char?, pos: Token.Pos) :
        CompilerException("[$where at $pos] expected $expected, got '${actual ?: ""}'")

    private val tokens = ArrayList<Token>()

    private var start = 0
    private var current = 0
    private var line = 1
    private var column = 1
    private var currentIndent = 0

    private val isAtEnd get() = current >= source.length
    private val curChar get() = source.getOrNull(current)
    private val prevChar get() = source.getOrNull(current - 1)
    private val curPos get() = Token.Pos(line, column)
    private val lexeme get() = source.slice(start until current)

    companion object {
        val LOWERCASE = ('a'..'z').toSet() + setOf('_')
        val UPPERCASE = ('A'..'Z').toSet()
        private val LETTERS = LOWERCASE + UPPERCASE
        val DIGITS = ('0'..'9').toSet()
        val ALPHANUMERICS = LETTERS + DIGITS
        val PUNCT_DOUBLE = Token.Type.values()
            .filter { it.match?.length == 2 }
            .groupBy { it.match!!.first() }
            .mapValues { (_, tokens) -> tokens.associateBy { it.match!![1] } }

        val PUNCT_SINGLE = Token.Type.values()
            .filter { it.match?.length == 1 }
            .associateBy { it.match!!.first() }


        const val SPACES_PER_INDENT = 2
    }

    fun scan(): ArrayList<Token> {
        while (!isAtEnd) {
            start = current
            scanToken()
        }
        if (tokens.lastOrNull()?.type != NEWLINE)
            tokens.add(Token(NEWLINE, "\n", curPos))
        setIndentLevel(0)
        tokens.add(Token(EOF, "", curPos))
        return tokens
    }

    private fun scanToken() {
        when (val char = advance()) {
            '#' -> {
                while (curChar != '\n') advance()
            }

            '"' -> string()
            '\n' -> newline()
            ' ' -> {}
            in PUNCT_DOUBLE -> doublePunct(char)
            in PUNCT_SINGLE -> addToken(PUNCT_SINGLE[char]!!)
            in LOWERCASE -> identOrLabel()
            in UPPERCASE -> nomen()
            in DIGITS -> number()
            else -> throw scanError("scanToken()", "valid character")
        }
    }

    private fun nomen() {
        consumeAll(ALPHANUMERICS)
        addToken(NOMEN)
    }


    private fun doublePunct(firstChar: Char) {
        val type = PUNCT_DOUBLE[firstChar]!![curChar]
        when {
            type != null -> {
                increment()
                addToken(type)
            }

            firstChar in PUNCT_SINGLE -> addToken(PUNCT_SINGLE[firstChar]!!)
            tokens.lastOrNull()?.type == LABEL && firstChar == ':' -> {
                val label = tokens.removeLast()
                tokens.add(Token(IDENT, label.value as String, curPos))
                tokens.add(Token(COLON_COLON, COLON_COLON.match!!, curPos))
            }

            else -> throw scanError("doublePunct()", "a valid one or two character punctuation")
        }
    }

    private fun newline() {
        addToken(NEWLINE)
        line++
        column = 1
        var spaces = 0
        while (checkAndConsume(' ')) spaces++
        val indentLevel = spaces / SPACES_PER_INDENT
        setIndentLevel(indentLevel)
    }

    private fun setIndentLevel(indentLevel: Int) {
        val diff = indentLevel - currentIndent
        fun addWhitespace(type: Token.Type) = tokens.add(Token(type, "", curPos))
        when {
            diff < 0 -> repeat(-diff) { addWhitespace(DEDENT) }
            diff > 0 -> repeat(diff) { addWhitespace(INDENT) }
            else -> {}
        }
        currentIndent = indentLevel
    }

    private fun string() {
        while (!isAtEnd && curChar != '"') advance()
        if (!checkAndConsume('"'))
            throw scanError("string()", "'\"' to terminate string")
        val literal = lexeme.slice(1 until lexeme.length - 1)
        addToken(STRING, literal)
    }

    private fun number() {
        val value = when {
            prevChar == '0' && checkAndConsume('x') -> {
                consumeAll(DIGITS)
                lexeme.substring(2).toInt(16)
            }

            else -> {
                consumeAll(DIGITS)
                lexeme.toInt()
            }
        }

        addToken(NUMBER, value)
    }

    private fun identOrLabel() {
        consumeAll(ALPHANUMERICS)
        when {
            checkAndConsume(':') ->
                addToken(LABEL, value = lexeme.dropLast(1))

            else ->
                addToken(IDENT)
        }
    }

    private fun consumeAll(chars: Set<Char>) {
        while (curChar in chars) advance()
    }

    private fun checkAndConsume(char: Char): Boolean = when (curChar) {
        char -> {
            increment()
            true
        }

        else -> false
    }

    private fun advance(): Char {
        increment()
        return prevChar!!
    }

    private fun increment() {
        column++
        current++
    }

    private fun addToken(type: Token.Type, value: Any? = null) {
        tokens.add(Token(type, lexeme, curPos, value))
    }

    private fun scanError(where: String, expected: String) =
        ScanException(where, expected, prevChar, curPos)
}