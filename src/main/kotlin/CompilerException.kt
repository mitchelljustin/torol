open class CompilerException(message: String) : Exception(message) {
    class Multi(exceptions: List<CompilerException>) :
        CompilerException("\n" + exceptions.joinToString("\n") { "!! $it" })
}