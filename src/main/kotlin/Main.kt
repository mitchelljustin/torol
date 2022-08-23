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

    private val compiler = Compiler()

    override fun run() = when (file) {
        null -> runPrompt()
        else -> runFile(file!!)
    }

    private fun runPrompt() {
        while (true) {
            print(">> ")
            val line = readLine() ?: break
            compileAndRun(line)
        }
    }

    private fun runFile(file: File) {
        val source = file.readText()
        compileAndRun(source)
    }

    private fun compileAndRun(source: String) = try {
        val code = compiler.compile(source, verbose)
        Machine(code).run()
    } catch (exc: Exception) {
        println("!! ${exc::class.simpleName} ${exc.message}\n${exc.stackTrace}")
    }
}

fun main(args: Array<String>) = CLI().main(args)