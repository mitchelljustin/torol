import Expr.Sexp
import Token.Type.EQUAL
import Token.Type.EQUAL_GREATER
import java.io.File
import java.io.PrintWriter


class Compiler(
    private val input: File,
    private var outFile: File? = null,
    private val verbose: Boolean = false
) {
    companion object {
        private fun String.name() = "\$$this"
        private fun String.literal() = "\"$this\""
    }

    class CodeGenError(where: String, message: String, extra: Any?) :
        Exception(buildString {
            append("[in ")
            append(where)
            append("] ")
            append(message)
            append(": ")
            append(extra)
            if (extra != null)
                append(" ${extra::class.simpleName}")
        })

    open class Section {
        private val stmts = ArrayList<Sexp>()

        // add("i32.const", i)
        // add("data", listOf("i32.const", loc), string)

        fun add(vararg items: Any?) {
            val sexp = Sexp.Grouping(items.map(::toSexp))
            stmts.add(sexp)
        }

        private fun toSexp(value: Any?): Sexp =
            when (value) {
                is Sexp -> value
                is Int -> Sexp.Literal(value)
                is String -> Sexp.Ident(value)
                is List<*> -> Sexp.Grouping(value.map(::toSexp))
                else -> throw RuntimeException("illegal sexp value: $value")
            }


        open fun writeTo(writer: PrintWriter) {
            stmts.forEach {
                writer.write(it.toString())
                writer.write("\n")
            }
        }
    }

    data class Function(
        val name: String,
        val params: List<String> = listOf(),
        val locals: List<String> = listOf(),
        val resultType: String? = null,
        val export: String? = null,
    ) : Section() {
        override fun writeTo(writer: PrintWriter) {

            writer.write("(func ${name.name()} ")
            if (export != null) {
                writer.write("(export ${export.literal()}) ")
            }
            writer.write(
                params.joinToString(" ") { "(param $it)" }
            )
            writer.write(
                locals.joinToString(" ") { "(local $it)" }
            )
            if (resultType != null) {
                writer.write("(result $resultType) ")
            }
            writer.write("\n")
            super.writeTo(writer)
            writer.write(")")
        }
    }

    private val errors = ArrayList<CodeGenError>()
    private val expander = Expander()

    init {
        outFile = outFile ?: File("./out/${input.nameWithoutExtension}.wat")
        outFile!!.createNewFile()
    }

    private val meta = Section()
    private val main = Function("main", export = "_start")
    private var function = main
    private val strings = HashMap<String, Int>()
    private var stringPtr = 0x1_0000


    fun compile() {
        var program = Parser.parse(input.readText(), verbose)
        program = expander.expand(program)
        meta.add("memory", listOf("export", "memory".literal()), 2)
        generate(program)
        errors.forEach { err -> println("!! $err") }
        if (errors.isEmpty())
            finish()
    }

    private fun finish() {
        outFile!!.printWriter().use { out ->
            out.write("(module\n")
            meta.writeTo(out)
            main.writeTo(out)
            out.write("\n)\n")
        }
    }

    private fun error(where: String, message: String, extra: Any? = null) {
        errors.add(CodeGenError(where, message, extra))
    }


    private fun generate(expr: Expr) {
        when (expr) {
            is Expr.Assembly -> genAssembly(expr)
            is Expr.Sequence -> genSequence(expr)
            is Expr.Binary -> genBinary(expr)
            is Expr.Literal -> genLiteral(expr)
            is Expr.Phrase -> genCall(expr)
            else -> error("generate", "illegal expr type", expr)
        }
    }

    private fun genCall(expr: Expr.Phrase) {
        if (expr.target !is Expr.Ident) {
            error("genCall", "call target must be ident", expr.target)
            return
        }
        expr.args.forEach(::generate)
        function.add("call", expr.target.name.name())
    }

    private fun genLiteral(expr: Expr.Literal) {
        when (val value = expr.literal) {
            is Int -> genI32Const(value)
            is String -> genStringLiteral(value)
            else -> error("genLiteral", "illegal literal type", expr.literal)
        }
    }

    private fun genStringLiteral(value: String) {
        val ptr = strings[value] ?: newStringLiteral(value)
        genI32Const(ptr)
        genI32Const(value.length)
    }

    private fun newStringLiteral(value: String): Int {
        val ptr = stringPtr
        meta.add("data", listOf("i32.const", ptr), value.literal())
        stringPtr += value.length
        return ptr
    }

    private fun genI32Const(value: Int) {
        function.add("i32.const", value)
    }

    private fun genBinary(expr: Expr.Binary) {
        when (expr.operator.type) {
            EQUAL_GREATER -> {}
            EQUAL -> genFunctionDef(expr.target, expr.value)
            else -> error("genBinary", "illegal binary expr operator", expr.operator)
        }
    }

    private fun genFunctionDef(expr: Expr, value: Expr) {
        TODO("function def")
    }

    private fun genSequence(expr: Expr.Sequence) {
        expr.exprs.forEach(::generate)
    }

    private fun genAssembly(expr: Expr.Assembly) {
        function.add(expr.body)
    }
}