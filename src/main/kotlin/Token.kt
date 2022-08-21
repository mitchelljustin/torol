class Token(val type: Type, val lexeme: String, val pos: Pos, val value: Any? = null) {
    data class Pos(val line: Int, val column: Int) {
        override fun toString() = "$line:$column"
    }


    @Suppress("unused")
    enum class Type(val match: String? = null) {
        NEWLINE("\n"),

        // grouping operators
        LPAREN("("), RPAREN(")"), LBRAC("{"), RBRAC("}"), LSQUARE("["), RSQUARE("]"),

        // special operators
        PIPE("|"), BANG("!"), STAR("*"),

        // assignment operators
        EQUAL("="), EQUAL_GREATER("=>"),

        // user operators
        RARROW("->"), LARROW("<-"),
        PLUS("+"), MINUS("-"), SLASH("/"),

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