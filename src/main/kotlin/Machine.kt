import ISA.EnvCall
import ISA.Instruction
import ISA.decode
import ISA.hex

typealias Word = Int


private fun createExceptionMessage(ip: Int, inst: Instruction?, message: String): String {
    val loc = ip.hex(8)
    val inst = if (inst != null) " $inst" else ""
    return "[at $loc$inst] $message"
}


class Machine(
    private val code: List<UByte>,
) {
    class ExecutionError(message: String, inst: Instruction?, ip: Int) :
        Exception(createExceptionMessage(ip, inst, message))

    @OptIn(ExperimentalUnsignedTypes::class)
    class Memory(
        private val code: List<UByte>,
    ) {
        class MemoryException(message: String) : Exception(message)

        companion object {
            const val HEAP_SIZE = 1024 * 1024

            const val CODE_START = 0x10_0000
            const val STACK_START = 0x20_0000
            const val HEAP_START = 0x30_0000
            const val HEAP_END = HEAP_START + HEAP_SIZE
        }

        val heap = ArrayList<UByte>(HEAP_SIZE)
        val stack = ArrayList<Word>(STACK_START)
    }

    private var ip = 0
    private var isRunning = false
    private val mem = Memory(code)
    private val curInst: Instruction?
        get() {
            val inst = code.getOrNull(ip) ?: return null
            return decode(inst)
        }

    fun run() {
        isRunning = true
        while (isRunning) {
            val inst = curInst ?: throw executionError("instruction is null")
            inst.execute(this)
        }
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

    fun ecall() {
        val callNo = pop()
        when (EnvCall.values().getOrNull(callNo)) {
            EnvCall.Invalid ->
                throw executionError("invalid ecall")
            EnvCall.Exit ->
                isRunning = false
            null ->
                throw executionError("no such ecall: $callNo")
        }
    }

    fun push(x: Word) = mem.stack.add(x)
    fun pop(): Word = mem.stack.removeLastOrNull() ?: throw executionError("popped from empty stack")
    fun alter(transform: (Word) -> Word) {
        if (mem.stack.size == 0) throw executionError("altering empty stack")
        mem.stack[mem.stack.lastIndex] = transform(mem.stack.last())
    }

    fun alterTwo(transform: (Word, Word) -> Word) {
        if (mem.stack.size < 2) throw executionError("altering two on stack of less than two")
        val top = pop()
        alter { sec -> transform(top, sec) }
    }
}