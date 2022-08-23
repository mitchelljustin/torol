object InstructionSet {
    fun Word.hex(len: Int = 8) = "0x${toString(16).padStart(len, '0')}"

    fun Word.vmSize(): Int = when (this.toUInt()) {
        in 0x00u..0xffu -> 1
        in 0x01_00u..0xff_ffu -> 2
        in 0x01_00_00u..0xff_ff_ffu -> 3
        in 0x01_00_00_00u..0xff_ff_ff_ffu -> 4
        else -> throw Error("invalid int size")
    }

    fun List<Byte>.toWord(): Word = reversed().fold(0) { acc, byte ->
        (acc shl 8) + byte.toInt()
    }

    fun Word.toBytes(size: Int = 4): List<Byte> =
        (0 until size).map {
            val byte = shr(8 * it)
            byte.toByte()
        }

    enum class EnvCall(callNo: Byte) {
        Invalid(0x00),
        Exit(0x01),
        Print(0x02),
    }

    class Instruction(
        val name: String,
        val opcode: Byte,
        val execute: (Machine) -> Unit,
    ) {
        override fun toString() = name
    }

    val Instructions = ArrayList<Instruction>()
    private val OpNameToInstruction = HashMap<String, Instruction>()

    private fun define(opcode: Byte, name: String, execute: (Machine) -> Unit) {
        val instruction = Instruction(name, opcode, execute)
        Instructions.add(instruction)
        OpNameToInstruction[name] = instruction
    }

    private fun pushN(machine: Machine, size: Int, signed: Boolean) {
        val bytes = (0 until size).map {
            machine.advance()
        }.toMutableList()
        val signExtend = signed && bytes.last().toByte() < 0
        val fillByte = (if (signExtend) 0xff else 0x00).toByte()
        bytes += (size until 4).map { fillByte }
        machine.push(bytes.toWord())
    }

    init {
        define(0x00, "err") { throw it.executionError("illegal instruction") }

        define(0x01, "push1") { m -> pushN(m, 1, true) }
        define(0x02, "push2") { m -> pushN(m, 2, true) }
        define(0x03, "push3") { m -> pushN(m, 3, true) }
        define(0x04, "push4") { m -> pushN(m, 4, true) }
        define(0x05, "push1u") { m -> pushN(m, 1, false) }
        define(0x06, "push2u") { m -> pushN(m, 2, false) }
        define(0x07, "push3u") { m -> pushN(m, 3, false) }
        define(0x08, "push4u") { m -> pushN(m, 4, false) }

        define(0x09, "add") { m -> m.alterTwo(Int::plus) }
        define(0x0a, "sub") { m -> m.alterTwo(Int::minus) }
        define(0x0b, "mul") { m -> m.alterTwo(Int::times) }
        define(0x0c, "div") { m -> m.alterTwo(Int::div) }
        define(0x0d, "neg") { m -> m.alter(Int::unaryMinus) }

        define(0x0e, "bez") { m -> m.branch { it == 0 } }
        define(0x0f, "bnz") { m -> m.branch { it != 0 } }

        define(0x10, "jump") { m -> m.jump(m.pop()) }
        define(0x11, "link") { m -> m.link() }
        define(0x12, "call") { m -> val target = m.pop(); m.link(); m.jump(target) }
        define(0x13, "ecall") { m -> m.ecall() }
        define(0x14, "ret") { m -> m.jump(m.pop()) }

        define(0x15, "dup") { m -> m.duplicate() }
        define(0x16, "peek") { m -> m.push(m.stackPeek(m.pop())) }
        define(0x17, "send") { m -> m.stackSend(m.pop(), m.pop()) }
        define(0x18, "send1") { m -> m.stackSend(1, m.pop()) }

        define(0x19, "setfp") { m -> m.fp = m.sp }
        define(0x20, "pushfp") { m -> m.push(m.fp) }
        define(0x21, "popfp") { m -> m.fp = m.pop() }

        define(0x22, "loadf") { m -> m.push(m.stackLoad(m.fp + m.pop())) }
        define(0x23, "storef") { m -> m.stackStore(m.fp + m.pop(), m.pop()) }

        define(0x24, "push0") { m -> m.push(0) }
    }

    fun encode(opName: String): Instruction? = OpNameToInstruction[opName]

    fun decode(inst: Byte): Instruction =
        Instructions.getOrNull(inst.toInt()) ?: throw Exception("ISA: illegal opcode: ${inst.toInt().hex(2)}")

}