typealias Word = Int

private fun createExceptionMessage(ip: Int, inst: Machine.Instruction?, message: String): String {
    val loc = ip.toString(16).padStart(8, '0')
    val inst = if (inst != null) " $inst" else ""
    return "[at 0x$loc$inst] $message"
}

class Machine(
    private val code: Array<UByte>,
) {
    class Instruction(
        val name: String,
        val opcode: UByte,
        val execute: (Machine) -> Unit,
    ) {
        override fun toString() = "!$name"
    }


    class ExecutionError(message: String, inst: Instruction?, ip: Int) :
        Exception(createExceptionMessage(ip, inst, message))

    private var ip = 0
    private val stack = ArrayList<Word>()
    private val curInst: Instruction?
        get() {
            val inst = code.getOrNull(ip) ?: return null
            return decode(inst)
        }

    companion object {
        val ISA = ArrayList<Instruction>()

        private fun define(name: String, execute: (Machine) -> Unit) {
            ISA.add(
                Instruction(name, opcode = ISA.size.toUByte(), execute)
            )
        }

        private fun makePush(size: Int): (Machine) -> Unit =
            { machine ->
                val bytes = (0 until size).map { machine.advance() }
                val toPush = bytes.reversed().fold(0) { acc, byte ->
                    (acc shl 8) + byte.toInt()
                }
                machine.push(toPush)
            }

        init {
            define("err") { throw it.executionError("illegal instruction") }
            define("push1", makePush(1))
            define("push2", makePush(2))
            define("push3", makePush(3))
            define("push4", makePush(4))
            define("add") { it.alterTwo(Int::plus) }
            define("sub") { it.alterTwo(Int::minus) }
            define("mul") { it.alterTwo(Int::times) }
            define("div") { it.alterTwo(Int::div) }
            define("bez") { machine -> machine.branch { it == 0 } }
            define("bnz") { machine -> machine.branch { it != 0 } }
            define("jump") { machine -> machine.jump(machine.pop()) }
            define("link") { machine -> machine.link() }
            define("jal") { machine -> val target = machine.pop(); machine.link(); machine.jump(target) }
        }

        fun decode(inst: UByte): Instruction = ISA[inst.toInt()]
    }

    fun executionError(message: String) = ExecutionError(message, curInst, ip)

    fun advance(): UByte {
        val inst = curInst ?: throw executionError("cannot advance, at end of code")
        ip++
        return inst.opcode
    }

    fun branch(cond: (Word) -> Boolean) {
        if (cond(pop()))
            jump(pop())
    }

    fun link() = push(ip)

    fun jump(target: Int) {
        ip = target
    }

    fun push(x: Word) = stack.add(x)
    fun pop(): Word = stack.removeLastOrNull() ?: throw executionError("popped from empty stack")
    fun alter(transform: (Word) -> Word) {
        if (stack.size == 0) throw executionError("altering empty stack")
        stack[stack.lastIndex] = transform(stack.last())
    }

    fun alterTwo(transform: (Word, Word) -> Word) {
        if (stack.size < 2) throw executionError("altering two on stack of less than two")
        val top = pop()
        alter { sec -> transform(top, sec) }
    }
}