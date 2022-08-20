object ISA {
    fun Int.hex(len: Int = 8) = "0x${toString(16).padStart(len, '0')}"

    enum class EnvCall(callNo: UByte) {
        Invalid(0x00u),
        Exit(0x01u),
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

    private fun makePush(size: Int): (Machine) -> Unit =
        { machine ->
            val UBytes = (0 until size).map { machine.advance() }
            val toPush = UBytes.reversed().fold(0) { acc, UByte ->
                (acc shl 8) + UByte.toInt()
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
        define("call") { machine -> val target = machine.pop(); machine.link(); machine.jump(target) }
        define("ecall") { it.ecall() }
    }

    fun encode(opName: String): Instruction? = OpNameToInstruction[opName]

    fun decode(inst: UByte): Instruction =
        InstructionSet.getOrNull(inst.toInt()) ?: throw Exception("ISA: illegal opcode: ${inst.toInt().hex(2)}")

}