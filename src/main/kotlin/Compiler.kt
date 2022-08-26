import Assembly.id
import Assembly.literal
import Expr.Sexp
import Token.Type.EQUAL
import Token.Type.EQUAL_GREATER
import java.io.File


class Compiler(
    private val input: File,
    private var outFile: File? = null,
    private val verbose: Boolean = false
) {

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

    private val errors = ArrayList<CodeGenError>()
    private val expander = Expander()

    init {
        outFile = outFile ?: File("./out/${input.nameWithoutExtension}.wat")
        outFile!!.createNewFile()
    }

    private val meta = Assembly.Section()
    private val main = Assembly.Function("main", export = "_start")
    private val functions = arrayListOf(main)
    private val functionStack = arrayListOf(main)
    private val function get() = functionStack.last()
    private val strings = HashMap<String, Int>()
    private var stringPtr = 0x1_0000


    fun compile() {
        var program = Parser.parse(input.readText(), verbose)
        program = expander.expand(program)
        generate(program)
        errors.forEach { err -> println("!! $err") }
        if (errors.isEmpty())
            finish()
    }

    private fun finish() {
        meta.add("memory", listOf("export", "memory".literal()), 2)
        outFile!!.printWriter().use { out ->
            out.write("(module\n")
            meta.writeTo(out)
            functions.forEach {
                it.writeTo(out)
                out.write("\n\n")
            }
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
            is Expr.Ident -> genVariable(expr)
            is Expr.Grouping -> genGrouping(expr)
            is Expr.Nil -> genNil()
            else -> error("generate", "illegal expr type", expr)
        }
    }

    private fun genNil() {
        genI32Const(0)
    }

    private fun genGrouping(expr: Expr.Grouping) {
        generate(expr.body)
    }

    private fun genVariable(expr: Expr.Ident) {
        function.add("local.get", expr.name.id())
    }

    private fun genCall(expr: Expr.Phrase) {
        if (expr.target !is Expr.Ident) {
            error("genCall", "call target must be ident", expr.target)
            return
        }
        expr.args.forEach(::generate)
        val pattern = Pattern.forSearch(expr.terms)
        val name = pattern.toString().id()
        function.add("call", name)
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
    }

    private fun newStringLiteral(value: String): Int {
        val ptr = stringPtr
        val len = value.length
        val hex32 = len.toString(16).padStart(8, '0')
        val lenData = hex32.chunked(2) { "\\$it" }.reversed().joinToString("")
        meta.add("data", listOf("i32.const", ptr), (lenData + value).literal())
        stringPtr += len + 4
        return ptr
    }

    private fun genI32Const(value: Int) {
        function.add("i32.const", value)
    }

    private fun genBinary(expr: Expr.Binary) {
        when (expr.operator.type) {
            EQUAL_GREATER -> {}
            EQUAL -> when (expr.target) {
                is Expr.Ident -> genVariableDef(expr.target, expr.value)
                is Expr.Phrase -> genFunctionDef(expr.target, expr.value)
                else -> error("genBinary", "illegal target for assignment", expr.target)
            }

            else -> error("genBinary", "illegal binary expr operator", expr.operator)
        }
    }

    private fun genVariableDef(target: Expr.Ident, value: Expr) {
        val name = target.name.id()
        val typeDef = "$name i32" // TODO: types
        function.locals.add(typeDef)
        generate(value)
        function.add("local.set", name)
    }

    private fun genFunctionDef(expr: Expr.Phrase, value: Expr) {
        val pattern = Pattern.forDefinition(expr.terms)
        val params = pattern.terms
            .filterIsInstance<Pattern.Term.Wildcard>()
            .map {
                val name = it.binding?.id() ?: ""
                "$name i32"
            }
        val newFunction = Assembly.Function(
            name = pattern.toString(),
            params,
            resultType = "i32",
        )
        functionStack.add(newFunction)
        functions.add(newFunction)
        generate(value)
        functionStack.removeLast()
    }

    private fun genSequence(expr: Expr.Sequence) {
        expr.exprs.forEach(::generate)
    }

    private fun genAssembly(expr: Expr.Assembly) {
        val target = when (expr.body) {
            is Sexp.List -> when (expr.body.terms.first()) {
                Sexp.Ident("import") -> meta
                else -> function
            }

            else -> function
        }
        target.add(expr.body)
    }
}