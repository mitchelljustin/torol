open class CompilerException(message: String) : Exception(message) {
    class Multi(exceptions: List<CompilerException>) :
        CompilerException(exceptions.joinToString("\n") { "!! $it" })
}