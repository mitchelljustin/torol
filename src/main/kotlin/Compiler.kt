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
    abstract class Referent(
        val pattern: Pattern,
    ) {
        enum class Scope(val asmPrefix: String) {
            Local("local"),
            Global("global"),
        }

        class Variable(pattern: Pattern, val scope: Scope) : Referent(pattern)
        class Function(pattern: Pattern) : Referent(pattern)

    }

    class CodeGenException(where: String, message: String, extra: Any?) :
        CompilerException(buildString {
            append("[in ")
            append(where)
            append("] ")
            append(message)
            append(": ")
            if (extra != null) {
                append(extra)
                append(" (${extra::class.simpleName})")
            }

        })


    private val errors = ArrayList<CodeGenException>()
    private val expander = Expander()

    init {
        outFile = outFile ?: File("out/${input.nameWithoutExtension}.wat")
        outFile!!.createNewFile()
    }

    private val meta = Assembly.Section()
    private val main = Assembly.Function("main", export = "_start")
    private val functionSections = arrayListOf(main)
    private val functionStack = arrayListOf(main)
    private val function get() = functionStack.last()
    private val strings = HashMap<String, Int>()
    private var stringPtr = 0x1_0000
    private val context = Context<Referent>()
    private val variableScope: Referent.Scope
        get() = when {
            function === main -> Referent.Scope.Global

            else -> Referent.Scope.Local
        }

    fun compile() {
        var program = Parser.parse(input.readText(), verbose)
        program = expander.expand(program)
        expander.imports.forEach(::addFunctionDef)
        generate(program)
        if (errors.isNotEmpty())
            throw CompilerException.Multi(errors)
        finish()
    }

    private fun finish() {
        meta.add("memory", listOf("export", "memory".literal()), 2)
        outFile!!.printWriter().use { out ->
            out.write("(module\n")
            meta.writeTo(out)
            functionSections.forEach { function ->
                function.writeTo(out)
                out.write("\n\n")
            }
            out.write("\n)\n")
            out.flush()
        }
        println("Wrote assembly to ${outFile!!.absolutePath}")
    }

    private fun error(where: String, message: String, extra: Any? = null) {
        errors.add(CodeGenException(where, message, extra))
    }


    private fun generate(expr: Expr) {
        when (expr) {
            is Expr.Assembly -> genAssembly(expr)
            is Expr.Sequence -> genSequence(expr)
            is Expr.Assignment -> genAssignment(expr)
            is Expr.Binary -> errOnlyInMacros("binary expressions", expr)
            is Expr.Multi -> errOnlyInMacros("splat operators", expr)
            is Expr.Literal -> genLiteral(expr)
            is Expr.Phrase -> genCall(expr)
            is Expr.Ident -> genVarRef(expr)
            is Expr.Grouping -> genGrouping(expr)
            is Expr.Label -> error("generate", "illegal label outside of phrase", expr)
            is Expr.Nil -> genNil()
            else -> error("generate", "illegal expr type", expr)
        }
    }

    private fun errOnlyInMacros(what: String, expr: Expr) {
        error("generate", "$what may only appear in macros", expr)
    }

    private fun genNil() {
    }

    private fun genGrouping(expr: Expr.Grouping) {
        generate(expr.body)
    }

    private fun genVarRef(expr: Expr.Ident) {
        val referent = context.resolve(Pattern.Search(expr.name)) as? Referent.Variable
        if (referent == null) {
            error("genVarRef", "undefined variable", expr)
            return
        }
        function.add("${referent.scope.asmPrefix}.get", expr.name.id())
    }

    private fun genCall(expr: Expr.Phrase) {
        when (expr.target) {
            is Expr.Ident, is Expr.Path, is Expr.Access -> {}
            else -> {
                error("genCall", "call target must be ident, path or access", expr.target)
                return
            }
        }
        expr.args.forEach(::generate)
        val pattern = Pattern.Search(expr.terms)
        if (pattern !in context) {
            error("genCall", "undefined function or macro", pattern)
            return
        }
        val name = pattern.name.id()
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
        val ptr = strings[value] ?: addStringLiteral(value)
        genI32Const(ptr)
    }

    private fun addStringLiteral(value: String): Int {
        val ptr = stringPtr
        val len = value.length
        val hex32 = len.toString(16).padStart(8, '0')
        val lenData = hex32.chunked(2) { "\\$it" }.reversed().joinToString("")
        val bytes = lenData + value
        meta.add("data", listOf("i32.const", ptr), bytes.literal())
        stringPtr += len + 4
        return ptr
    }

    private fun genI32Const(value: Int) {
        function.add("i32.const", value)
    }

    private fun genAssignment(expr: Expr.Assignment) {
        when (expr.operator.operator.type) {
            EQUAL_GREATER -> {}
            EQUAL -> when (expr.target) {
                is Expr.Nil -> {
                    generate(expr.value)
                    function.add("drop")
                }

                is Expr.Ident -> genVariableDef(expr.target, expr.value)
                is Expr.Phrase -> genFunctionDef(expr.target, expr.value)
                else -> error("genAssignment", "illegal target for assignment", expr.target)
            }

            else -> error("genAssignment", "illegal assignment operator", expr.operator)
        }
    }


    private fun genVariableDef(target: Expr.Ident, value: Expr) {
        generate(value)
        val id = target.name.id()
        val pattern = Pattern.Definition(target.name)
        when (variableScope) {
            Referent.Scope.Global -> {
                if (pattern !in context.global)
                    meta.add("global", id, listOf("mut", "i32"), listOf("i32.const", "0"))
                main.add("global.set", id)
            }

            Referent.Scope.Local -> {
                if (pattern !in context)
                    function.locals.add("$id i32")
                function.add("local.set", id)
            }
        }

        addVariableDef(pattern, variableScope)
    }

    private fun addVariableDef(pattern: Pattern.Definition, scope: Referent.Scope) {
        context.define(pattern, Referent.Variable(pattern, scope))
    }


    private fun genFunctionDef(target: Expr.Phrase, body: Expr) {
        val pattern = Pattern.Definition(target.terms)
        val params = pattern.terms.filterIsInstance<Pattern.Term.Any>().filter { it.binding != null }
        val paramDefs = params.map { param ->
            val name = param.binding!!.id()
            "$name i32"
        }
        val newFunction = Assembly.Function(
            name = pattern.name,
            paramDefs,
            returns = body.returns,
        )
        addFunctionDef(pattern)
        functionStack.add(newFunction)
        functionSections.add(newFunction)
        context.withNewScope {
            params.forEach { param ->
                addVariableDef(Pattern.Definition(param.binding!!), Referent.Scope.Local)
            }
            generate(body)
        }
        functionStack.removeLast()
    }

    private fun addFunctionDef(pattern: Pattern.Definition) {
        context.define(pattern, Referent.Function(pattern))
    }

    private fun genSequence(expr: Expr.Sequence) {
        expr.stmts.forEachIndexed { i, stmt ->
            generate(stmt)
        }
    }

    private fun genAssembly(expr: Expr.Assembly) {
        val section = when (expr.body) {
            is Sexp.List -> when (expr.body.terms.first()) {
                Sexp.Ident("import") -> meta
                else -> function
            }

            else -> function
        }
        section.add(expr.body)
    }
}