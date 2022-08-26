import Expr.Sexp
import java.io.Writer

object Assembly {
    fun String.id() = "\$$this"
    fun String.literal() = "\"$this\""


    open class Section {
        internal val stmts = ArrayList<Sexp>()

        // add("i32.const", i)
        // add("data", listOf("i32.const", loc), string)

        fun add(sexp: Sexp) {
            stmts.add(sexp)
        }

        fun add(vararg items: Any?) {
            val sexp = Sexp.List(items.map(Sexp::from))
            add(sexp)
        }


        open fun writeTo(writer: Writer) {
            stmts.forEach {
                writer.write(it.toString())
                writer.write("\n")
            }
        }
    }

    data class Function(
        val name: String,
        val params: List<String> = listOf(),
        val locals: MutableList<String> = arrayListOf(),
        val resultType: String? = null,
        val export: String? = null,
    ) : Section() {
        override fun writeTo(writer: Writer) {
            writer.write("(func ${name.id()}")
            writer.write(" ")
            if (export != null) {
                writer.write("(export ${export.literal()})")
                writer.write(" ")
            }
            writer.write(
                params.joinToString(" ") { "(param $it)" }
            )
            writer.write(" ")
            if (resultType != null) {
                writer.write("(result $resultType)")
                writer.write(" ")
            }
            writer.write(
                locals.joinToString(" ") { "(local $it)" }
            )
            writer.write(" ")
            writer.write("\n")
            stmts.forEach {
                writer.write("  $it\n")
            }
            writer.write(")")
        }
    }

}