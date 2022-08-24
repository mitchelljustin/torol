import Token.Type.EQUAL
import Token.Type.EQUAL_GREATER
import java.io.File

class Compiler(
    private val input: File,
    private val outFile: File?,
    private val verbose: Boolean = false
) {
    class CodeGenError(where: String, message: String, extra: Any?) :
        Exception("[in $where] $message: $extra")

    private val errors = ArrayList<CodeGenError>()
    private val expander = Expander()
    private var out = outFile ?: File("./out/${input.nameWithoutExtension}.wat")

    init {
        out.createNewFile()
    }

    fun compile() {
        var program = Parser.parse(input.readText(), verbose)
        program = expander.expand(program)
        generate(program)
        errors.forEach { err -> println("!! $err") }
    }

    private fun error(where: String, message: String, extra: Any? = null): Expr {
        errors.add(CodeGenError(where, message, extra))
        return Expr.Nil()
    }

    private fun write(text: String) {
        out.writeText(text)
    }

    private fun generate(expr: Expr) {
        when (expr) {
            is Expr.Directive -> genDirective(expr)
            is Expr.Sequence -> genSequence(expr)
            is Expr.Binary -> genBinary(expr)
            else -> error("generate", "illegal expr type", expr)
        }
    }

    private fun genBinary(expr: Expr.Binary) {
        when (expr.operator.type) {
            EQUAL_GREATER -> {}
            EQUAL -> genFunctionDef(expr)
            else -> error("genBinary", "illegal binary expr operator", expr.operator)
        }
    }

    private fun genFunctionDef(expr: Expr.Binary) {
        TODO("function def")
    }

    private fun genSequence(expr: Expr.Sequence) {
        expr.exprs.forEach(::generate)
    }

    private fun genDirective(expr: Expr.Directive) {
        when (expr.body) {
            is Expr.Sexp -> write(expr.body.toString())
            else -> error("genDirective", "illegal directive type", expr)
        }
    }
}