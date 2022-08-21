import Token.Type.EQUAL_GREATER

typealias Bytecode = List<UByte>

fun Int.vmSize(): Int = when (this.toUInt()) {
    in 0x00u..0xffu -> 1
    in 0x01_00u..0xff_ffu -> 2
    in 0x01_00_00u..0xff_ff_ffu -> 3
    in 0x01_00_00_00u..0xff_ff_ff_ffu -> 4
    else -> throw Error("invalid int size")
}

class Compiler(
    private val program: Expr.Sequence,
) {
    class CodeGenError(where: String, message: String, expr: Expr?) :
        Exception("[in $where] $message: $expr")


    private val binary = arrayListOf<UByte>()

    fun compile(): Bytecode {
        program.exprs.forEach(::gen)
        finish()
        return binary
    }

    private fun finish() {

    }

    private fun gen(expr: Expr) = when (expr) {
        is Expr.Directive -> genDirective(expr.body)
        is Expr.Sequence -> genSequence(expr)
        is Expr.Binary -> when (expr.operator.type) {
            EQUAL_GREATER -> {} // ignore macros
            else -> throw CodeGenError("gen", "illegal binary operator", expr)
        }
        else -> throw CodeGenError("gen", "illegal expr type ${expr::class.simpleName}", expr)
    }

    private fun genSequence(expr: Expr.Sequence) {
        expr.exprs.forEach(::gen)
    }

    private fun genDirective(expr: Expr) {
        when (expr) {
            is Expr.Literal -> when (expr.literal) {
                is Int -> dump(expr.literal)
                else -> throw CodeGenError(
                    "genDirective",
                    "invalid literal type: ${expr.literal::class.simpleName}",
                    expr
                )
            }
            else -> throw CodeGenError("genDirective", "illegal directive type", expr)
        }
    }

    private fun dump(value: Int, size: Int = value.vmSize()) {
        val uint = value.toUInt()
        (0 until size).forEach {
            val byte = uint shr (8 * it)
            addByte(byte.toUByte())
        }
    }

    private fun addByte(byte: UByte) {
        binary.add(byte)
    }
}