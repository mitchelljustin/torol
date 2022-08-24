import java.io.File

class Compiler(
    private val input: File,
    private val outFile: File?,
    private val verbose: Boolean = false
) {
    class CodeGenError(where: String, message: String, extra: Any?) :
        Exception("[in $where] $message: $extra")

    private val errors = ArrayList<CodeGenError>()
    private val macroEngine = MacroEngine()
    private var out = outFile ?: File("./out/${input.nameWithoutExtension}.wat")

    fun compile() {
        var program = Parser.parse(input.readText(), verbose)
        program = macroEngine.expand(program)
        out = outFile ?: File("./out/${input.nameWithoutExtension}.wat")
        out.createNewFile()
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
            else -> error("generate", "illegal expr type", expr)
        }
    }

    private fun genSequence(expr: Expr.Sequence) {
        expr.exprs.forEach(::generate)
    }

    private fun genDirective(expr: Expr.Directive) {
        when (expr.body) {
            is Expr.Sexp -> write(expr.body.body)
            else -> error("genDirective", "illegal directive type", expr)
        }
    }
}