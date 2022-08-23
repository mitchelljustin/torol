import InstructionSet.encode
import InstructionSet.toBytes
import InstructionSet.vmSize
import Token.Type.EQUAL
import Token.Type.EQUAL_GREATER

typealias Bytecode = ArrayList<Byte>


class Compiler {
    class CodeGenError(where: String, message: String, extra: Any?) :
        Exception("[in $where] $message: $extra")

    data class Relocation(val addr: Int, val label: String)

    data class Function(
        val pattern: Pattern,
        val code: Bytecode = Bytecode(),
        val relocations: ArrayList<Relocation> = ArrayList(),
    )

    private val macroEngine = MacroEngine()

    private val strings = HashMap<String, String>()
    private val main = Function(Pattern.forName("_main"))
    private val functions = hashMapOf(main.pattern to main)
    private val callStack = arrayListOf(main)
    private val function get() = callStack.last()

    fun compile(source: String, verbose: Boolean = false): Bytecode {
        var program = Parser.parse(source, verbose)
        program = macroEngine.expand(program)
        generate(program)
        return finish()
    }

    private fun finish(): Bytecode {
        val binary = Bytecode()
        return binary
    }

    private fun generate(expr: Expr) = when (expr) {
        is Expr.Apply -> genApply(expr)
        is Expr.Literal -> genLiteral(expr, push = true)
        is Expr.Directive -> genDirective(expr.body)
        is Expr.Sequence -> genSequence(expr)
        is Expr.Binary -> when (expr.operator.type) {
            EQUAL_GREATER -> {} // ignore macros
            EQUAL -> when (expr.target) {
                is Expr.Apply -> defineFunction(expr.target, expr.value)
                else -> throw CodeGenError("gen", "illegal function definition pattern", expr.target)
            }
            else -> throw CodeGenError("gen", "illegal binary operator", expr)
        }
        else -> throw CodeGenError("gen", "illegal expr type ${expr::class.simpleName}", expr)
    }

    private fun defineFunction(target: Expr.Apply, body: Expr) {
        val pattern = Pattern.forDefinition(target.terms)
        val newFunction = Function(pattern)
        functions[pattern] = newFunction
        callStack.add(newFunction)
        generate(body)
        val poppedFunction = callStack.removeLast()
        if (poppedFunction !== newFunction)
            throw CodeGenError("defineFunction", "function popped off call stack is wrong", poppedFunction)
    }

    private fun genApply(expr: Expr.Apply) {
        val pattern = Pattern.forSearch(expr.terms)
        val function = functions[pattern] ?: throw CodeGenError("genApply", "no such function: $pattern", expr)
        val binding = function.pattern.bind(expr.terms)
        binding.values.forEach(::generate)
        addInst("push0")
        addReference(pattern.toString())
        addInst("call")
    }

    private fun genSequence(expr: Expr.Sequence) {
        expr.exprs.forEach(::generate)
    }

    private fun genDirective(expr: Expr) {
        when (expr) {
            is Expr.Literal -> genLiteral(expr, push = false)
            is Expr.Ident -> addInst(expr.name)
            is Expr.Apply -> genComplexDirective(expr)
            else -> throw CodeGenError("genDirective", "illegal directive type", expr)
        }
    }

    private fun genComplexDirective(expr: Expr.Apply) {
        if (expr.target !is Expr.Ident)
            throw CodeGenError("genComplexDirective", "target must be Ident", expr.target)
        when (expr.target.name) {
            "fp_offset" -> {

            }
            else -> throw CodeGenError("genComplexDirective", "illegal directive command", expr.target.name)
        }
    }

    private fun genLiteral(expr: Expr.Literal, push: Boolean) {
        when (val value = expr.literal) {
            is Int -> {
                if (push) addInst("push${value.vmSize()}")
                dump(value)
            }
            is String -> {
                if (push) addInst("push4")
                addStringConstant(value)
            }
            else -> throw CodeGenError(
                "dumpLiteral",
                "invalid literal type: ${value::class.simpleName}",
                expr
            )
        }
    }

    private fun addStringConstant(value: String) {
        val label = "string_${strings.size}"
        strings[label] = value
        addReference(label)
    }

    private fun dump(value: Int, size: Int = value.vmSize()) {
        value.toBytes(size).forEach(::addByte)
    }

    private fun addReference(label: String) {
        function.relocations.add(Relocation(function.code.size, label))
        dump(0x00000000, size = 4)
    }

    private fun addInst(opName: String) {
        val opcode = encode(opName)?.opcode ?: throw CodeGenError("addInst", "no such instruction", opName)
        addByte(opcode)
    }

    private fun addByte(byte: Byte) {
        function.code.add(byte)
    }
}