import InstructionSet.toUBytes
import InstructionSet.vmSize
import Token.Type.EQUAL_GREATER

typealias Bytecode = List<UByte>


class Compiler(
    private val program: Expr.Sequence,
) {
    class CodeGenError(where: String, message: String, expr: Expr?) :
        Exception("[in $where] $message: $expr")


    private val readonlySymbols = HashMap<Int, Int>()
    private val binary = ArrayList<UByte>()
    private val readonly = ArrayList<UByte>()

    fun compile(): Bytecode {
        program.exprs.forEach(::gen)
        finish()
        return binary
    }

    private fun finish() {
        val readonlyStart = binary.size
        binary += readonly
        readonlySymbols.entries.forEach { (codeAddr, inReadonly) ->
            val target = readonlyStart + inReadonly
            (codeAddr until codeAddr + 4)
                .zip(target.toUBytes())
                .forEach { (addr, byte) -> binary[addr] = byte }
        }
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
                is String -> {
                    val string = expr.literal.encodeToByteArray().map(Byte::toUByte).toMutableList()
                    val length = string.size
                    val bytes = length.toUBytes() + string
                    val ptr = readonly.size
                    readonlySymbols[binary.size] = ptr
                    readonly.addAll(bytes)
                    dump(0x00, size = 4)
                }
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