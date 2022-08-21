object ISA {
    fun Int.hex(len: Int = 8) = "0x${toString(16).padStart(len, '0')}"

    enum class EnvCall(callNo: UByte) {
        Invalid(0x00u),
        Exit(0x01u),
        Print(0x02u),
    }

    class Instruction(
        val name: String,
        val opcode: UByte,
        val execute: (Machine) -> Unit,
    ) {
        override fun toString() = "!$name"
    }

    private val InstructionSet = ArrayList<Instruction>()
    private val OpNameToInstruction = HashMap<String, Instruction>()

    private fun define(opName: String, execute: (Machine) -> Unit) {
        val instruction = Instruction(opName, opcode = InstructionSet.size.toUByte(), execute)
        InstructionSet.add(instruction)
        OpNameToInstruction[opName] = instruction
    }

    private fun makePush(size: Int, signExtend: Boolean): (Machine) -> Unit =
        { machine ->
            val bytes = (0 until 4).map {
                if (it < size)
                    machine.advance()
                else
                    if (signExtend) 0xffu else 0x00u
            }
            val toPush = bytes.reversed().fold(0) { acc, byte ->
                (acc shl 8) + byte.toInt()
            }
            machine.push(toPush)
        }

    init {
        define("err") { throw it.executionError("illegal instruction") }
        define("push1", makePush(1, true))
        define("push2", makePush(2, true))
        define("push3", makePush(3, true))
        define("push4", makePush(4, true))
        define("push1u", makePush(1, false))
        define("push2u", makePush(2, false))
        define("push3u", makePush(3, false))
        define("push4u", makePush(4, false))
        define("add") { it.alterTwo(Int::plus) }
        define("sub") { it.alterTwo(Int::minus) }
        define("mul") { it.alterTwo(Int::times) }
        define("div") { it.alterTwo(Int::div) }
        define("neg") { it.alter(Int::unaryMinus) }
        define("bez") { machine -> machine.branch { it == 0 } }
        define("bnz") { machine -> machine.branch { it != 0 } }
        define("jump") { machine -> machine.jump(machine.pop()) }
        define("link") { machine -> machine.link() }
        define("call") { machine -> val target = machine.pop(); machine.link(); machine.jump(target) }
        define("ecall") { it.ecall() }
    }

    fun encode(opName: String): Instruction? = OpNameToInstruction[opName]

    fun decode(inst: UByte): Instruction =
        InstructionSet.getOrNull(inst.toInt()) ?: throw Exception("ISA: illegal opcode: ${inst.toInt().hex(2)}")

}