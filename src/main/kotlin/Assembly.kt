import Expr.Sexp
import java.io.Writer

object Assembly {
    fun String.id() = "\$$this"
    fun String.literal() = "\"$this\""


    open class Section : Sexp.Builder() {
        open fun writeTo(writer: Writer) {
            terms.forEach { term ->
                writer.write(term.toString())
                writer.write("\n")
            }
        }
    }

    data class Function(
        val name: String,
        val params: List<String> = listOf(),
        val locals: MutableList<String> = arrayListOf(),
        val returns: Boolean = false,
        val export: String? = null,
    ) : Section() {
        private fun compile(): Sexp {
            val body = terms
            return Sexp.build {
                add("func")
                add(name.id())
                if (export != null) {
                    add("export", export.literal())
                }
                params.forEach { param ->
                    add("param", param)
                }
                if (returns) {
                    add("result", "i32")
                }
                locals.forEach { local ->
                    add("local", local)
                }
                linebreak()
                body.forEach { stmt ->
                    add(stmt)
                    linebreak()
                }
            }
        }


        override fun writeTo(writer: Writer) {
            val assembly = compile().toString()
            writer.write(assembly)
        }
    }

}