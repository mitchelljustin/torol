import ISA.encode
import Token.Type.EQUAL

private open class Symbol {
    data class StringLiteral(val literal: String) : Symbol()
}

data class RelocSite(val addr: Int, val size: Int, val symbol: String)

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

typealias Bytecode = List<UByte>

class Compiler(
    private val program: Expr.Sequence,
) {
    class CodeGenError(where: String, message: String, expr: Expr?) :
        Exception("[in $where] $message: $expr")


    private val binary = arrayListOf<UByte>()
    private val global = Scope(null)
    private var scope = global
    private val relocations = ArrayList<RelocSite>()


    fun compile(): Bytecode {
        program.exprs.forEach(::gen)
        finish()
        return binary
    }

    private fun finish() {

    }

    private fun gen(expr: Expr) = when (expr) {
        is Expr.Directive -> genDirective(expr.body)
        is Expr.Literal -> genLiteral(expr)
        is Expr.Apply -> genApply(expr)
        is Expr.Assignment -> genAssignment(expr)
        else -> throw CodeGenError("gen", "illegal expr type", expr)
    }

    private fun genAssignment(expr: Expr.Assignment) {
        when (expr.operator.type) {
            EQUAL -> TODO()
            else -> {} // macro definition, ignore
        }
    }

    private fun genDirective(expr: Expr) {
        if (expr !is Expr.Apply)
            throw CodeGenError("genDirective", "illegal directive type", expr)
        when (expr.terms.size) {
            1 -> when (val target = expr.target) {
                is Expr.Ident ->
                    add(target.name)
                is Expr.Literal ->
                    if (target.literal is Int)
                        add(target.literal.toUByte())
                    else throw CodeGenError(
                        "genDirective",
                        "invalid literal type: ${target.literal::class.simpleName}",
                        target
                    )
                else -> throw CodeGenError("genDirective", "illegal directive", target)
            }
            else -> throw CodeGenError(
                "genDirective",
                "illegal number of terms for directive: ${expr.terms.size}",
                expr
            )
        }
    }

    private fun genApply(expr: Expr.Apply) = when (val target = expr.target) {
        is Expr.Ident -> genCall(target.name, expr.args)
        is Expr.Literal -> genLiteral(target)
        else -> throw CodeGenError("genApply", "illegal apply target", target)
    }

    private fun genCall(name: String, values: List<Expr>) {
        TODO("Not yet implemented")
    }

    private fun pushScope() {
        scope = Scope(scope)
    }

    private fun popScope() {
        if (scope === global) throw CodeGenError("popScope", "cannot pop global scope", null)
        scope = scope.enclosing!!
    }

    private fun genLiteral(expr: Expr.Literal) {
        when (val value = expr.literal) {
            is String -> {
                val label = global.addString(value)
                pushSymbol(label)
            }
            is Int -> push(value)
            else ->
                throw CodeGenError("genLiteral", "illegal literal type: ${value::class.simpleName}", expr)
        }
    }

    private fun push(value: Int) {
        val uint = value.toUInt()
        val size = when (uint) {
            in 0x00u..0xffu -> 1
            in 0x01_00u..0xff_ffu -> 2
            in 0x01_00_00u..0xff_ff_ffu -> 3
            in 0x01_00_00_00u..0xff_ff_ff_ffu -> 4
            else -> throw Error("??")
        }
        push(uint, size, signExtend = false)
    }

    private fun push(value: UInt, size: Int, signExtend: Boolean) {
        val suffix = if (signExtend) "" else "u"
        add("push$size$suffix")
        (0 until size).forEach {
            val byte = value shr (8 * it)
            add(byte.toUByte())
        }
    }

    private fun pushSymbol(symbol: String, size: Int = 4) {
        relocations.add(RelocSite(binary.size, size, symbol))
        repeat(size) { add(0x00u) }
    }

    private fun add(byte: UByte) {
        binary.add(byte)
    }

    private fun add(opName: String) {
        val instruction = encode(opName) ?: throw CodeGenError("add", "no such op: $opName", null)
        add(instruction.opcode)
    }


}