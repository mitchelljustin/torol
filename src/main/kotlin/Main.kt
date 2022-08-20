import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import java.io.File

class CLI : CliktCommand() {
    private val file by option("-f", "--file", help = ".uber file to execute")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)
    private val verbose by option("-v", "--verbose", help = "print tokens and AST")
        .flag("verbose")

    override fun run() = when (file) {
        null -> runPrompt()
        else -> runFile(file!!)
    }

    private fun runPrompt() {
        while (true) {
            print(">> ")
            val line = readLine() ?: break
            interpret(line)
        }
    }

    private fun runFile(file: File) {
        val source = file.readText()
        interpret(source)
    }

    private fun interpret(source: String) = try {
        if (verbose) println("----------\n$source\n----------")
        val tokens = Scanner(source).scan()
        if (verbose) println("## ${tokens.joinToString(" ")}")
        val sequence = Parser(tokens).parse()
        if (verbose) println("|| $sequence")
        val expanded = MacroEngine(sequence).expandAll()
        if (verbose) println("~~ $expanded")
        val code = Compiler(expanded).compile()
        val machine = Machine(code)
        machine.run()
    } catch (exc: Exception) {
        println("!! ${exc::class.simpleName} ${exc.message}")
    }
}

fun main(args: Array<String>) = CLI().main(args)