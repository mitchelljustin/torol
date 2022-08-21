class Token(val type: Type, val lexeme: String, val pos: Pos, val value: Any? = null) {
    data class Pos(val line: Int, val column: Int) {
        override fun toString() = "$line:$column"
    }


    @Suppress("unused")
    enum class Type(val match: String? = null) {
        NEWLINE("\n"),

        LPAREN("("), RPAREN(")"), LBRAC("{"), RBRAC("}"),

        EQUAL("="), EQUAL_GREATER("=>"),
        PIPE("|"), BANG("!"),

        OPERATOR,

        INDENT, DEDENT,

        LABEL,

        IDENT, STRING, NUMBER,

        EOF;

        companion object {
            val OPERATORS = setOf("-", "+", "/", "*")
        }
    }

    override fun toString() = "$type(${lexeme.trim()})"

}