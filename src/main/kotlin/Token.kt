class Token(val type: Type, val lexeme: String, val pos: Pos, val value: Any? = null) {
    data class Pos(val line: Int, val column: Int) {
        override fun toString() = "$line:$column"
    }


    @Suppress("unused")
    enum class Type(val match: String? = null) {
        NEWLINE("\n"),

        // grouping operators
        LPAREN("("), RPAREN(")"),

        // meta operators
        TILDE("~"), BANG("!"),

        // assignment operators
        EQUAL("="), EQUAL_GREATER("=>"),
        RARROW("->"), LARROW("<-"),

        // binary operators
        PLUS("+"), MINUS("-"), SLASH("/"), STAR("*"),

        // WASM operators
        DOT("."), DOLLAR("$"),

        // multi operator
        DOT_DOT(".."),

        INDENT, DEDENT,

        LABEL,

        IDENT, STRING, NUMBER,

        EOF
    }

    override fun toString() = "$type(${lexeme.trim()})"

}