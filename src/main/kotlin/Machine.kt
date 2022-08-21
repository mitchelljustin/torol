import InstructionSet.EnvCall
import InstructionSet.Instruction
import InstructionSet.decode
import InstructionSet.hex
import InstructionSet.toInt

typealias Word = Int


private fun createExceptionMessage(ip: Int, inst: Instruction?, message: String): String {
    val loc = ip.hex(8)
    val inst = if (inst != null) " $inst" else ""
    return "[at $loc$inst] $message"
}


@OptIn(ExperimentalUnsignedTypes::class)
class Machine(
    private val code: List<UByte>,
) {
    class ExecutionError(message: String, inst: Instruction?, ip: Int) :
        Exception(createExceptionMessage(ip, inst, message))

    companion object {
        const val CODE_START = 0x00_000
        const val STACK_START = 0x10_000
        const val STACK_END = 0x20_000
    }

    private var exitCode: Word? = null
    private var ip = CODE_START
    private val stack = ArrayList<Word>()
    private var isRunning = false
    private val curByte get() = code.getOrNull(ip)
    private val curInst: Instruction?
        get() {
            val inst = curByte ?: return null
            return decode(inst)
        }

    fun run() {
        isRunning = true
        while (isRunning) {
            val inst = curInst ?: throw executionError("instruction is null")
            ip++
            inst.execute(this)
        }
    }

    fun executionError(message: String) = ExecutionError(message, curInst, ip)

    fun advance(): UByte {
        val inst = curByte ?: throw executionError("cannot advance, at end of code")
        ip++
        return inst
    }

    fun readInt(addr: Word): Int = code.slice(addr until addr + 4).toInt()

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
            EnvCall.Exit -> {
                exitCode = pop()
                isRunning = false
            }
            EnvCall.Print -> {
                val stringPtr = pop()
                val stringLength = readInt(stringPtr)
                val stringStart = stringPtr + 4
                val bytes = code.slice(stringStart until stringStart + stringLength)
                val string = bytes.toUByteArray().toByteArray().decodeToString()
                println(string)
            }

            null ->
                throw executionError("no such ecall: $callNo")
        }
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