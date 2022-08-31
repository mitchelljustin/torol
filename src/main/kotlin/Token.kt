class Token(val type: Type, val lexeme: String, val pos: Pos, val value: Any? = null) {
    data class Pos(val line: Int, val column: Int) {
        override fun toString() = "$line:$column"
    }


    @Suppress("unused")
    enum class Type(val match: String? = null) {
        NEWLINE("\n"),

        // grouping operators
        LPAREN("("), RPAREN(")"),

        // special operators
        TILDE("~"), BANG("!"), BACKSLASH("\\"), DOT_DOT(".."),

        // assignment operators
        EQUAL("="), EQUAL_GREATER("=>"),
        RARROW("->"), LARROW("<-"),

        // binary (/unary) operators
        PLUS("+"), MINUS("-"), SLASH("/"), STAR("*"),
        PLUS_EQUAL("+="), MINUS_EQUAL("-="), SLASH_EQUAL("/="), STAR_EQUAL("*="),
        EQUAL_EQUAL("=="), BANG_EQUAL("!="),
        GREATER(">"), GREATER_EQUAL(">="), LESS("<"), LESS_EQUAL("<="),

        // unary operators
        AMPERSAND("&"),

        // WASM operators
        DOLLAR("$"),

        // access operators
        DOT("."), COLON_COLON("::"),

        INDENT, DEDENT,

        LABEL,

        IDENT, STRING, NUMBER, NOMEN,

        EOF
    }

    override fun toString() = "$type(${lexeme.trim()})"

}