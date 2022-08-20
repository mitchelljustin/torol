import ISA.encode

private open class Symbol {
    data class StringLiteral(val literal: String) : Symbol()
}

private class Scope(
    val enclosing: Scope?,
) {
    private val symbols = HashMap<String, Symbol>()
    private var stringId = 0

    fun addString(value: String): String {
        val id = stringId++
        val label = "string_$id"
        define(label, Symbol.StringLiteral(value))
        return label
    }

    fun define(label: String, symbol: Symbol) {
        symbols[label] = symbol
    }

    fun resolve(label: String): Symbol? =
        symbols[label] ?: enclosing?.resolve(label)

}


typealias UBytecode = List<UByte>

class Compiler(
    private val program: Expr.Sequence,
) {
    class GenerateError(where: String, message: String, expr: Expr?) :
        Exception("[in $where] $message: $expr")


    private val binary = arrayListOf<UByte>()
    private val global = Scope(null)
    private var scope = global


    fun compile(): UBytecode {
        program.exprs.forEach(::gen)
        finish()
        return binary
    }

    private fun finish() {
        TODO("Not yet implemented")
    }

    private fun gen(expr: Expr) = when (expr) {
        is Expr.Literal -> genLiteral(expr)
        is Expr.Apply -> genApply(expr)
        else -> throw GenerateError("gen", "illegal expr type", expr)
    }

    private fun genApply(expr: Expr.Apply) = when (val target = expr.target) {
        is Expr.Directive -> genDirective(target.name, expr.values)
        is Expr.Ident -> genCall(target.name, expr.values)
        else -> throw GenerateError("genApply", "illegal apply target", target)
    }

    private fun genCall(name: String, values: List<Expr>) {
        TODO("Not yet implemented")
    }

    private fun genDirective(name: String, values: List<Expr>) {

    }

    private fun pushScope() {
        scope = Scope(scope)
    }

    private fun popScope() {
        if (scope === global) throw GenerateError("popScope", "cannot pop global scope", null)
        scope = scope.enclosing!!
    }

    private fun genLiteral(expr: Expr.Literal) {
        when (val value = expr.literal) {
            is String -> {
                val label = global.addString(value)

            }
            else ->
                throw GenerateError("genLiteral", "illegal literal type: ${value::class.simpleName}", expr)
        }
    }

    private fun push(value: Int) {
        val uint = value.toUInt()
        when (value) {
            in 0..0xff -> {}
            in 0x1_00..0xffff -> {}
            in 0x1_0000..0xffffff -> {}

        }
    }

    private fun add(byte: UByte) {
        binary.add(byte)
    }

    private fun add(opName: String) {
        val instruction = encode(opName) ?: throw GenerateError("add", "no such op: $opName", null)
        add(instruction.opcode)
    }


}