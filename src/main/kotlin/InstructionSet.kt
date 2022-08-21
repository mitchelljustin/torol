object InstructionSet {
    fun Int.hex(len: Int = 8) = "0x${toString(16).padStart(len, '0')}"

    fun Int.vmSize(): Int = when (this.toUInt()) {
        in 0x00u..0xffu -> 1
        in 0x01_00u..0xff_ffu -> 2
        in 0x01_00_00u..0xff_ff_ffu -> 3
        in 0x01_00_00_00u..0xff_ff_ff_ffu -> 4
        else -> throw Error("invalid int size")
    }

    fun List<UByte>.toInt(): Int = reversed().fold(0) { acc, byte ->
        (acc shl 8) + byte.toInt()
    }

    fun Int.toUBytes(size: Int = 4): List<UByte> =
        (0 until size).map {
            val byte = shr(8 * it)
            byte.toUByte()
        }

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
        override fun toString() = name
    }

    private val Instructions = ArrayList<Instruction>()
    private val OpNameToInstruction = HashMap<String, Instruction>()

    private fun define(opName: String, opcode: UByte, execute: (Machine) -> Unit) {
        val instruction = Instruction(opName, opcode, execute)
        Instructions.add(instruction)
        OpNameToInstruction[opName] = instruction
    }

    private fun pushN(machine: Machine, size: Int, signed: Boolean) {
        val bytes = (0 until size).map {
            machine.advance()
        }.toMutableList()
        val signExtend = signed && bytes.last().toByte() < 0
        val fillByte = (if (signExtend) 0xff else 0x00).toUByte()
        bytes += (size until 4).map { fillByte }
        machine.push(bytes.toInt())
    }

    init {
        define("err", 0x00u) { throw it.executionError("illegal instruction") }

        define("push1", 0x01u) { m -> pushN(m, 1, true) }
        define("push2", 0x02u) { m -> pushN(m, 2, true) }
        define("push3", 0x03u) { m -> pushN(m, 3, true) }
        define("push4", 0x04u) { m -> pushN(m, 4, true) }
        define("push1u", 0x05u) { m -> pushN(m, 1, false) }
        define("push2u", 0x06u) { m -> pushN(m, 2, false) }
        define("push3u", 0x07u) { m -> pushN(m, 3, false) }
        define("push4u", 0x08u) { m -> pushN(m, 4, false) }

        define("add", 0x09u) { m -> m.alterTwo(Int::plus) }
        define("sub", 0x0au) { m -> m.alterTwo(Int::minus) }
        define("mul", 0x0bu) { m -> m.alterTwo(Int::times) }
        define("div", 0x0cu) { m -> m.alterTwo(Int::div) }
        define("neg", 0x0du) { m -> m.alter(Int::unaryMinus) }

        define("bez", 0x0eu) { m -> m.branch { it == 0 } }
        define("bnz", 0x0fu) { m -> m.branch { it != 0 } }

        define("jump", 0x10u) { m -> m.jump(m.pop()) }
        define("link", 0x11u) { m -> m.link() }
        define("call", 0x12u) { m -> val target = m.pop(); m.link(); m.jump(target) }
        define("ecall", 0x13u) { m -> m.ecall() }
    }

    fun encode(opName: String): Instruction? = OpNameToInstruction[opName]

    fun decode(inst: UByte): Instruction =
        Instructions.getOrNull(inst.toInt()) ?: throw Exception("ISA: illegal opcode: ${inst.toInt().hex(2)}")

}