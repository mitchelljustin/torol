import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file

class CLI : CliktCommand() {
    private val file by argument("in-file", help = ".uber file to compile")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)
    private val verbose by option("-v", "--verbose", help = "print tokens and AST")
        .flag("verbose")
    private val outFile by option("-o", "--out-file", help = "output file, defaults to <input-name>.wat")
        .file(canBeDir = false)

    override fun run() {
        try {
            Compiler(file, outFile, verbose).compile()
        } catch (exc: Exception) {
            println("!! ${exc::class.simpleName} ${exc.message}\n${exc.stackTrace}")
        }
    }
}

fun main(args: Array<String>) = CLI().main(args)