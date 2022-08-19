class Token(val type: Type, val lexeme: String, val pos: Pos, val literal: Any? = null) {
    data class Pos(val line: Int, val column: Int) {
        override fun toString() = "$line:$column"
    }

    @Suppress("unused")
    enum class Type(val match: String? = null) {
        NEWLINE("\n"),

        LPAREN("("), RPAREN(")"), LBRAC("{"), RBRAC("}"),

        EQUAL("="), EQUAL_GREATER("=>"),
        PIPE("|"),

        INDENT, DEDENT,

        LABEL,

        IDENT, INSTRUCTION, STRING, NUMBER, SYMBOL,

        EOF;
    }

    override fun toString() = "$type(${lexeme.trim()})"

}