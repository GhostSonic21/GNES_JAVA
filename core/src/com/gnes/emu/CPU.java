package com.gnes.emu;

/**
 * Created by ghost_000 on 7/6/2016.
 */

// Yet Another 6502/2A03 instruction interpreter

public class CPU {
    // Classes arrays and variables
    private CPU_MMU MMU;

    // Registers
    private int reg_A = 0x00;      // Accumulator
    private int reg_X = 0x00;      // X Index
    private int reg_Y = 0x00;      // Y Index
    private int reg_SP = 0x00;     // Stack Pointer
    private int reg_PC = 0x00;     // Program Counter

    // Flags
    private boolean flag_C; // Carry
    private boolean flag_Z; // Zero
    private boolean flag_I; // Interrupt Disable
    private boolean flag_D; // Decimal Mode (Ignored on NES, but the flag is still there)
    private boolean flag_B; // Break Command
    //boolean flag_U; // Doesn't exist, always reads high?
    private boolean flag_V; // Overflow
    private boolean flag_N; // Negative

    // NMI Storage for detecting edge-triggeed NMI.
    boolean lastNMI = false;
    // Placeholder
    boolean RESET = false;  // Triggered by the Reset button. Vector 0xFFFC/D

    // Cycle count of opcode
    /* The way cycles are implemented is woefully incomplete
     * We take into account successful branches, but not page-crossing.
     * Nor do we necessarily account for possible in-between cycle behavior
     * So it's pretty inaccurate, but we need to drive PPU et al. somehow */
    private final int opcodeCycleCount[] = {
            7,6,0,8,3,3,5,5,3,2,2,2,4,4,6,6,
            2,5,0,8,4,4,6,6,2,4,2,7,4,4,7,7,
            6,6,0,8,3,3,5,5,4,2,2,2,4,4,6,6,
            2,5,0,8,4,4,6,6,2,4,2,7,4,4,7,7,
            6,6,0,8,3,3,5,5,3,2,2,2,3,4,6,6,
            2,5,0,8,4,4,6,6,2,4,2,7,4,4,7,7,
            6,6,0,8,3,3,5,5,4,2,2,2,5,4,6,6,
            2,5,0,8,4,4,6,6,2,4,2,7,4,4,7,7,
            2,6,2,6,3,3,3,3,2,2,2,2,4,4,4,4,
            2,6,0,6,4,4,4,4,2,5,2,5,5,5,5,5,
            2,6,2,6,3,3,3,3,2,2,2,2,4,4,4,4,
            2,5,0,5,4,4,4,4,2,4,2,4,4,4,4,4,
            2,6,2,8,3,3,5,5,2,2,2,2,4,4,6,6,
            2,5,0,8,4,4,6,6,2,4,2,7,4,4,7,7,
            2,6,2,8,3,3,5,5,2,2,2,2,4,4,6,6,
            2,5,0,8,4,4,6,6,2,4,2,7,4,4,7,7,
    };
    // Lazy Page-Cross table
    private final int opcodeCycleCountPageCross[] = {
            7,6,0,8,3,3,5,5,3,2,2,2,4,4,6,6,
            3,6,0,8,4,4,6,6,2,5,2,7,5,5,7,7,
            6,6,0,8,3,3,5,5,4,2,2,2,4,4,6,6,
            3,6,0,8,4,4,6,6,2,5,2,7,5,5,7,7,
            6,6,0,8,3,3,5,5,3,2,2,2,3,4,6,6,
            3,6,0,8,4,4,6,6,2,5,2,7,5,5,7,7,
            6,6,0,8,3,3,5,5,4,2,2,2,5,4,6,6,
            3,6,0,8,4,4,6,6,2,5,2,7,5,5,7,7,
            2,6,2,6,3,3,3,3,2,2,2,2,4,4,4,4,
            3,6,0,6,4,4,4,4,2,5,2,5,5,5,5,5,
            2,6,2,6,3,3,3,3,2,2,2,2,4,4,4,4,
            3,6,0,6,4,4,4,4,2,5,2,5,5,5,5,5,
            2,6,2,8,3,3,5,5,2,2,2,2,4,4,6,6,
            3,6,0,8,4,4,6,6,2,5,2,7,5,5,7,7,
            2,6,2,8,3,3,5,5,2,2,2,2,4,4,6,6,
            3,6,0,8,4,4,6,6,2,5,2,7,5,5,7,7,
    };

    // Keeps track of Page Crossing processing addresss modes, as well as sucesfull branching.
    private boolean pageCross = false;
    private boolean sucessfulBranch = false;
    private boolean interrupted = false;
    private int lastCycleCount = 0;


    public CPU(CPU_MMU MMU){
        this.MMU = MMU;
    }

    public void resetNES(){
        // Setup initial states
        reg_A = 0;
        reg_X = 0;
        reg_Y = 0;
        reg_SP = 0xFD;
        set_reg_F(0x34);
        // Load PC with reset vector at 0xFFFC
        reg_PC = MMU.readWord(0xFFFC);
    }

    public void testOpcode(int opcode){
        // use this to test opcode decoder/execution
        decodeExecute(opcode);
    }

    // Getter for retrieving the amount of cycles the previous instruction took
    public int getLastCycleCount(){
        return lastCycleCount;
    }

    public void execInst(){
        // Overflow for no parameters, executes with no interrupts
        execInst(false, false);
    }

    public void execInst(boolean NMI, boolean IRQ){
        // Execute One instruction

        // Interrupts
        // NMI is handled first
        // Edge triggered, only triggered if NMI was low at first
        if (NMI && !lastNMI){
            // Vector
            interruptPush(0xFFFA);
            flag_B = false; // Clear the B flag. This is probably kind of a hack.
            //System.out.printf("NMI\n");
            interrupted = true;
        }
        // The interrupt disable flag only disables IRQ interrupts because what the hell does disable mean anyway
        else if (IRQ && !flag_I){
            // Vector
            // Certain instructions can be delay the IRQ interrupt for some odd reason.
            interruptPush(0xFFFE);
            interrupted = true;
        }
        lastNMI = NMI;  // Save the NMI state

        // Actual execution
        // Gather opcode
        int opcode = MMU.readByte(reg_PC) & 0xFF;
        // Used for debugging
        //System.out.printf("PC: 0x%04X Opcode: 0x%02x\n", reg_PC, opcode);

        reg_PC++;   // Advance PC ahead once before executing
        reg_PC &= 0xFFFF;
        decodeExecute(opcode);
        // Save time in cycles that instruction took
        if (!pageCross) {
            lastCycleCount = opcodeCycleCount[opcode];
        }
        else{
            lastCycleCount = opcodeCycleCountPageCross[opcode];
        }
        if (sucessfulBranch){
            lastCycleCount++;   // Add one to branches
        }
        if (interrupted){
            lastCycleCount += 7;
        }
        pageCross = false;
        sucessfulBranch = false;
        interrupted = false;
    }

    private void decodeExecute(int opcode){
        int type = opcode & 0x3;    // Separate bottom 2 bits
        switch (type) {
            // Machine instructions
            case 0x00:{
                // Addressing modes 0x1, 0x3, 0x5, 0x7 are always processed, even for unofficial opcodes
                int addressingMode = (opcode >> 2) & 0x7;
                int address = -1;
                if (addressingMode == 0x1 || addressingMode == 0x3
                        || addressingMode == 0x5 || addressingMode == 0x7){
                    address = addressFromAddressingMode(addressingMode);
                }
                // Lower opcodes use immediate addressing in a different place
                else if (opcode > 0x80 && addressingMode == 0x00){
                    address = addressFromAddressingMode(0x2);
                }
                // Addressing mode 0x4 is relative for these branching instructions
                else if (addressingMode == 0x4){
                    //address = reg_PC + (byte)MMU.readByte(reg_PC);  // Byte is signed, cast to signed byte
                    int relativeNum = MMU.readByte(reg_PC);
                    reg_PC = (reg_PC + 1) & 0xFFFF;
                    address = (reg_PC + (byte)relativeNum) & 0xFFFF;
                    pageCross = (reg_PC & 0x100) != (address & 0x100);    // If bit 8 doesn't match, page-cross occurred
                }
                // There's about 46 unique instructions in this section
                int instNum = opcode >> 2;
                switch (instNum) {
                    case 0x00:{
                        //TODO: CHECK BRK
                        flag_B = true;
                        //IRQ = true;
                        reg_PC = (reg_PC + 1) & 0xFFFF;
                        interruptPush(0xFFFE);
                        break;
                    }
                    case 0x02:{
                        // PHP
                        int temp = get_reg_F();
                        temp |= 0x30;
                        MMU.writeByte(reg_SP|0x100, temp);
                        reg_SP = (reg_SP - 1) & 0xFF;
                        break;
                    }
                    case 0x04:{
                        // BPL
                        branch(!flag_N, address);
                        break;
                    }
                    case 0x06:{
                        // CLC
                        flag_C = false;
                        break;
                    }
                    case 0x08:{
                        // JSR
                        reg_SP = (reg_SP - 1) & 0xFF;
                        MMU.writeWord(reg_SP|0x100, reg_PC + 1);
                        reg_SP = (reg_SP - 1) & 0xFF;
                        reg_PC = MMU.readWord(reg_PC);
                        break;
                    }
                    // BIT
                    case 0x09:
                    case 0x0B:{
                        int memVal = MMU.readByte(address);
                        int result = (reg_A & memVal) & 0xFF;
                        // Set V and N depending on bits 6 and 7 respectively
                        flag_Z = result == 0;
                        flag_V = (memVal & 0x40) > 0;
                        flag_N = (memVal & 0x80) > 0;
                        break;
                    }
                    case 0x0A:{
                        // PLP
                        reg_SP = (reg_SP + 1) & 0xFF;
                        set_reg_F(MMU.readByte(reg_SP|0x100));
                        break;
                    }
                    case 0x0C:{
                        // BMI
                        branch(flag_N, address);
                        break;
                    }
                    case 0x0E:{
                        flag_C = true;
                        break;
                    }
                    case 0x10:{
                        // TODO: CHECK RTI
                        //System.out.println("RTI not implemented");
                        // Pull Status flags back out
                        reg_SP = (reg_SP + 1) & 0xFF;
                        set_reg_F(MMU.readByte(reg_SP|0x100));
                        // Pull PC back out
                        reg_SP = (reg_SP + 1) & 0xFF;
                        reg_PC = (MMU.readWord(reg_SP|0x100)) & 0xFFFF;
                        reg_SP = (reg_SP + 1) & 0xFF;
                        break;
                    }
                    case 0x12:{
                        // PHA
                        MMU.writeByte(reg_SP | 0x100, reg_A);
                        reg_SP = (reg_SP - 1) & 0xFF;
                        break;
                    }
                    // JMP
                    case 0x13:{
                        // JMP
                        reg_PC = address;
                        break;
                    }
                    case 0x1B:{
                        // JMP with indirect addressing (only opcode that does this)

                        reg_PC = MMU.readWordIndirect(address);

                        break;
                    }
                    case 0x14:{
                        // BVC
                        branch(!flag_V, address);
                        break;
                    }
                    case 0x16:{
                        flag_I = false;
                        break;
                    }
                    case 0x18:{
                        // RTS
                        reg_SP = (reg_SP + 1) & 0xFF;
                        reg_PC = (MMU.readWord(reg_SP|0x100) + 1) & 0xFFFF;
                        reg_SP = (reg_SP + 1) & 0xFF;
                        break;
                    }
                    case 0x1A:{
                        // PLA
                        reg_SP = (reg_SP + 1) & 0xFF;
                        reg_A = MMU.readByte(reg_SP|0x100);
                        flag_Z = reg_A == 0;
                        flag_N = isNegative(reg_A);
                        break;
                    }
                    case 0x1C:{
                        // BVS
                        branch(flag_V, address);
                        break;
                    }
                    case 0x1E:{
                        // SEI
                        flag_I = true;
                        break;
                    }
                    case 0x21:
                    case 0x23:
                    case 0x25:{
                        // STY
                        MMU.writeByte(address, reg_Y);
                        break;
                    }
                    case 0x22:{
                        // DEY
                        reg_Y = RMW_DEC(reg_Y);
                        break;
                    }
                    case 0x24:{
                        // BCC
                        branch(!flag_C, address);
                        break;
                    }
                    case 0x26:{
                        // TYA
                        reg_A = reg_Y;
                        flag_Z = reg_A == 0;
                        flag_N = isNegative(reg_A);
                        break;
                    }
                    // LDY
                    case 0x28:
                    case 0x29:
                    case 0x2B:
                    case 0x2D:
                    case 0x2F:{
                        reg_Y = MMU.readByte(address);
                        flag_Z = reg_Y == 0;
                        flag_N = isNegative(reg_Y);
                        break;
                    }
                    case 0x2A:{
                        // TAY
                        reg_Y = reg_A;
                        flag_Z = reg_Y == 0;
                        flag_N = isNegative(reg_Y);
                        break;
                    }
                    case 0x2C:{
                        // BCS
                        branch(flag_C, address);
                        break;
                    }
                    case 0x2E:{
                        flag_V = false;
                        break;
                    }
                    // CPY
                    case 0x30:
                    case 0x31:
                    case 0x33:{
                        // CPY
                        int M = MMU.readByte(address);
                        flag_C = reg_Y >= M;
                        flag_Z = reg_Y == M;
                        flag_N = isNegative(reg_Y - M);
                        break;
                    }
                    case 0x32:{
                        // INY
                        reg_Y = (reg_Y + 1) & 0xFF;
                        flag_Z = reg_Y == 0;
                        flag_N = isNegative(reg_Y);
                        break;
                    }
                    case 0x34:{
                        // BNE
                        branch(!flag_Z, address);
                        break;
                    }
                    case 0x36:{
                        // CLD
                        flag_D = false;
                        break;
                    }
                    // CPX
                    case 0x38:
                    case 0x39:
                    case 0x3B: {
                        int M = MMU.readByte(address);
                        flag_C = reg_X >= M;
                        flag_Z = reg_X == M;
                        flag_N = isNegative(reg_X - M);
                        break;
                    }
                    case 0x3A:{
                        // INX
                        reg_X = (reg_X + 1) & 0xFF;
                        flag_Z = reg_X == 0;
                        flag_N = isNegative(reg_X);
                        break;
                    }
                    case 0x3C:{
                        // BEQ
                        branch(flag_Z, address);
                        break;
                    }
                    case 0x3E:{
                        // SED
                        flag_D = true;
                        break;
                    }


                    default:{
                        // Some kind of unofficial instruction
                        System.out.printf("Unofficial 6502 opcode 0x%02X detected at 0x%04X, treating as a NOP\n", opcode, reg_PC - 1);
                        break;
                    }
                }
                break;
            }
            case 0x01: {
                // Process ALU instructions
                // Process addressing mode
                int addressingMode = (opcode >> 2) & 0x7;
                int address = addressFromAddressingMode(addressingMode);
                //int value = MMU.readByte(address);
                int instNum = opcode >> 5;  // Upper 3 bits determines ALU operation
                switch (instNum) {
                    case 0x0:
                        ALU_ORA(MMU.readByte(address));
                        break;
                    case 0x1:
                        ALU_AND(MMU.readByte(address));
                        break;
                    case 0x2:
                        ALU_EOR(MMU.readByte(address));
                        break;
                    case 0x3:
                        ALU_ADC(MMU.readByte(address));
                        break;
                    case 0x4:
                        ALU_STA(address);
                        break;
                    case 0x5:
                        ALU_LDA(MMU.readByte(address));
                        break;
                    case 0x6:
                        ALU_CMP(MMU.readByte(address));
                        break;
                    case 0x7:
                        ALU_SBC(MMU.readByte(address));
                        break;
                }
                break;
            }
            // RMW
            case 0x02:{
                // Process RMW and other instructions
                // Instruction format is quite a bit different and contains some different addressing modes
                // Only get a value for a few particular addressing modes
                int addressingMode = (opcode >> 2) & 0x7;
                int address = -1;
                if (addressingMode == 0x1 || addressingMode == 0x3 || addressingMode == 0x5 || addressingMode == 0x7){
                    // Opcode specific addresing mode difference
                    // Hacky
                    if (opcode == 0x96 || opcode == 0xB6){
                        // Zero Page, Y, only used in this one instince
                        address = MMU.readByte(reg_PC) + reg_Y;
                        address &= 0xFF;
                        reg_PC++;
                    }
                    else if (opcode == 0xBE){
                        address = addressFromAddressingMode(0x6);
                    }
                    else{
                        address = addressFromAddressingMode(addressingMode);
                    }
                }
                // If it wasn't, -1 will indicate it's something else

                int instNum = opcode >> 5;  // Upper 3 bits determines RMW operation
                switch (instNum) {
                    case 0x0:
                        if (address != -1){
                            MMU.writeByte(address, RMW_ASL(MMU.readByte(address)));
                        }
                        else if (addressingMode == 0x2){
                            // Accumulator
                            reg_A = RMW_ASL(reg_A);
                        }
                        else{
                            // Some kind of unofficial instruction
                            System.out.printf("Detected unofficial RMW instruction, treat as nop\n");
                            break;
                        }
                        break;
                    case 0x1:
                        if (address != -1){
                            MMU.writeByte(address, RMW_ROL(MMU.readByte(address)));
                        }
                        else if (addressingMode == 0x2){
                            // Accumulator
                            reg_A = RMW_ROL(reg_A);
                        }
                        else{
                            // Some kind of unofficial instruction
                            System.out.printf("Detected unofficial RMW instruction, treat as nop\n");
                            break;
                        }
                        break;
                    case 0x2:
                        if (address != -1){
                            MMU.writeByte(address, RMW_LSR(MMU.readByte(address)));
                        }
                        else if (addressingMode == 0x2){
                            // Accumulator
                            reg_A = RMW_LSR(reg_A);
                        }
                        else{
                            // Some kind of unofficial instruction
                            System.out.printf("Detected unofficial RMW instruction, treat as nop\n");
                            break;
                        }
                        break;
                    case 0x3:
                        if (address != -1){
                            MMU.writeByte(address, RMW_ROR(MMU.readByte(address)));
                        }
                        else if (addressingMode == 0x2){
                            // Accumulator
                            reg_A = RMW_ROR(reg_A);
                        }
                        else{
                            // Some kind of unofficial instruction
                            System.out.printf("Detected unofficial RMW instruction, treat as nop\n");
                            break;
                        }
                        break;
                    // This is where things get different
                    case 0x4:
                        if (address != -1 && addressingMode != 0x7){
                            RMW_STX(address);
                        }
                        else if (addressingMode == 0x2){
                            // TXA
                            reg_A = reg_X;
                            flag_Z = reg_A == 0;
                            flag_N = isNegative(reg_A);
                        }
                        else if (addressingMode == 0x6){
                            // TXS
                            reg_SP = reg_X;
                        }

                        else{
                            // Some kind of unofficial instruction
                            System.out.printf("Detected unofficial RMW instruction, treat as nop\n");
                            break;
                        }
                        break;
                    case 0x5:
                        if (address != -1){
                            RMW_LDX(address);
                        }
                        else if (addressingMode == 0x0){
                            RMW_LDX(addressFromAddressingMode(0x2));
                        }
                        else if (addressingMode == 0x2){
                            // TAX
                            reg_X = reg_A;
                            flag_Z = reg_X == 0;
                            flag_N = isNegative(reg_X);
                        }
                        else if (addressingMode == 0x6){
                            // TSX
                            reg_X = reg_SP;
                            flag_Z = reg_X == 0;
                            flag_N = isNegative(reg_X);
                        }
                        else{
                            // Some kind of unofficial instruction
                            System.out.printf("Detected unofficial RMW instruction, treat as nop\n");
                            break;
                        }
                        break;
                    case 0x6:
                        if (address != -1){
                            MMU.writeByte(address, RMW_DEC(MMU.readByte(address)));
                        }
                        else if (addressingMode == 0x2){
                            reg_X = RMW_DEC(reg_X);
                        }
                        break;
                    case 0x7:
                        if (address != -1){
                            MMU.writeByte(address, RMW_INC(MMU.readByte(address)));
                        }
                        else if (addressingMode == 0x2) {
                            // actual nop
                            break;
                        }
                        else{
                            // Some kind of unofficial instruction
                            System.out.printf("Detected unofficial RMW instruction, treat as nop\n");
                            break;
                        }
                        break;
                }
                break;
            }
            case 0x03:{
                // These are all unofficial instructions
                // Give some type of error for now
                System.out.printf("Unofficial 6502 opcode 0x%02X detected at 0x%04X, treating as a NOP\n", opcode, reg_PC - 1);
                if (opcode == 0x7){
                    int address = addressFromAddressingMode(0x1);
                    MMU.writeByte(address, RMW_ASL(MMU.readByte(address)));
                    ALU_ORA(MMU.readByte(address));
                }
                break;
            }
        }
    }
    // ALU Functions
    private void ALU_ORA(int input){
        reg_A = (reg_A | input) & 0xFF;
        flag_Z = reg_A == 0;
        flag_N = isNegative(reg_A);
    }
    private void ALU_AND(int input){
        reg_A = (reg_A & input) & 0xFF;
        flag_Z = reg_A == 0;
        flag_N = isNegative(reg_A);
    }
    private void ALU_EOR(int input){
        reg_A = (reg_A ^ input) & 0xFF;
        flag_Z = reg_A == 0;
        flag_N = isNegative(reg_A);
    }
    private void ALU_ADC(int input){
        // This fucking overflow flag fuck
        int adding = (input + (flag_C ? 1:0));
        int result = reg_A + adding;
        int oldA = reg_A;

        reg_A = result & 0xFF;
        flag_C = result > 0xFF;
        flag_Z = reg_A == 0;
        flag_N = isNegative(reg_A);
        flag_V = (~(oldA ^ input) & (oldA ^ result) & 0x80) > 0;
    }
    private void ALU_STA(int input){
        // Stores accumulator into memory
        MMU.writeByte(input, reg_A);
    }
    private void ALU_LDA(int input){//reg_A = MMU.readByte(input);
        reg_A = input;
        flag_Z = reg_A == 0;
        flag_N = isNegative(reg_A);
    }
    private void ALU_CMP(int input){
        //int M = MMU.readByte(input);
        flag_Z = reg_A == input;
        flag_C = reg_A >= input;
        flag_N = isNegative((reg_A - input) & 0xFF);
    }
    private void ALU_SBC(int input){
        // I learned you can flip the bits and just use the adc method and it just works
        input ^= 0xff;
        ALU_ADC(input);
    }
    // OTHER opcode Functions
    private int RMW_ASL (int input){
        int result = input << 1;
        result &= 0xFF;
        flag_C = (input & 0x80) == 0x80;
        flag_Z = result == 0;
        flag_N = isNegative(result);
        return result;
    }
    private int RMW_ROL (int input){
        int result = (input << 1) | (flag_C ? 1:0);
        result &= 0xFF;
        flag_C = (input & 0x80) == 0x80;
        flag_Z = result == 0;
        flag_N = isNegative(result);
        return result;
    }
    private int RMW_LSR (int input){
        int result = input >>> 1;
        result &= 0xFF;
        flag_C = (input & 0x1) > 0;
        flag_Z = result == 0;
        flag_N = isNegative(result);
        return result;
    }
    private int RMW_ROR (int input){
        int result = (input >> 1) | (flag_C ? 0x80: 0x00);
        result &= 0xFF;
        flag_C = (input & 0x1) == 0x1;
        flag_Z = result == 0;
        flag_N = isNegative(result);
        return result;
    }
    private void RMW_STX (int input){
        MMU.writeByte(input, reg_X);
    }
    private void RMW_LDX (int input){
        reg_X = MMU.readByte(input);
        flag_Z = reg_X == 0;
        flag_N = isNegative(reg_X);
    }
    private int RMW_DEC (int input){
        int result = input - 1;
        result &= 0xFF;
        flag_Z = result == 0;
        flag_N = isNegative(result);
        return result;
    }
    private int RMW_INC (int input){
        int result = input + 1;
        result &= 0xFF;
        flag_Z = result == 0;
        flag_N = isNegative(result);
        return result;
    }

    // Some shortcut functions
    private void branch(boolean condition, int address){
        if (condition){
            lastCycleCount++;   // Increase a cycle?
            sucessfulBranch = true;
            reg_PC = address;
        }
        else{
            // Apparently pageCross penalty doesn't occur if branch isn't successful?
            // I'm not sure how that works, but that's what the test suggests
            // My guess is that the actual relative calculation isn't done unless branch is successful.
            // However, it still retrieves the immediate byte. An accurate implementation should look different.
            pageCross = false;
        }
    }

    private int get_reg_F(){
        int returnVal = ((flag_C ? 1:0) << 0)|((flag_Z ? 1:0) << 1)|((flag_I ? 1:0) << 2)|((flag_D ? 1:0) << 3)
                |((flag_B ? 1:0) << 4)|(1 << 5)|((flag_V ? 1:0) << 6)|((flag_N ? 1:0) << 7);
        return returnVal;
    }
    private void set_reg_F(int input){
        flag_C = (input & 0x01) > 0;
        flag_Z = (input & 0x02) > 0;
        flag_I = (input & 0x04) > 0;
        flag_D = (input & 0x08) > 0;
        //flag_B = (input & 0x10) > 0;
        flag_B = false;
        //flag_U = (input & 0x20) > 0;
        flag_V = (input & 0x40) > 0;
        flag_N = (input & 0x80) > 0;
    }

    private boolean isNegative(int input){
        // Checks if 8-bit value is a negative
        return (byte)input < 0; // You could also just check bit 7 but this is sillier
    }

    private int addressFromAddressingMode (int mode) {
        //int mode = (opcode >> 2) & 0x7;
        int address = 0x0000;
        //int value = 0;  // Value that ends up being returned
        switch (mode) {
            case 0x00: {
                // Indexed indrect
                int targetAddress = (MMU.readByte(reg_PC) + reg_X) & 0xFF;
                address = MMU.readWordIndirect(targetAddress);
                reg_PC++;
                break;
            }
            case 0x01: {
                // Zero Page
                address = MMU.readByte(reg_PC);
                reg_PC++;
                break;
            }
            case 0x02: {
                // Immediate
                address = reg_PC;
                reg_PC++;
                break;
            }
            case 0x03: {
                // Absolute
                address = MMU.readWord(reg_PC);
                reg_PC += 2;
                break;
            }
            case 0x04: {
                // Indirect Indexed
                address = MMU.readWordIndirect(MMU.readByte(reg_PC)) + reg_Y;
                address &= 0xFFFF;
                pageCross = (address & 0xFF) < reg_Y;
                reg_PC++;
                break;
            }
            case 0x05: {
                // Zero Page, X
                address = MMU.readByte(reg_PC) + reg_X;
                address &= 0xFF;
                reg_PC++;
                break;
            }
            case 0x06: {
                // Absolute, Y
                address = MMU.readWord(reg_PC) + reg_Y;
                address &= 0xFFFF;
                pageCross = (address & 0xFF) < reg_Y;
                reg_PC += 2;
                break;
            }
            case 0x07: {
                // Absolute, X
                address =  MMU.readWord(reg_PC) + reg_X;
                address &= 0xFFFF;
                pageCross = (address & 0xFF) < reg_X;
                reg_PC += 2;
                break;
            }
        }
        reg_PC &= 0xFFFF;
        return address;
    }

    private void interruptPush(int vector){
        // Push PC to the stack
        reg_SP = (reg_SP - 1) & 0xFF;
        MMU.writeWord(reg_SP|0x100, reg_PC);
        reg_SP = (reg_SP - 1) & 0xFF;
        // Push status flags to the stack
        int temp = get_reg_F();
        MMU.writeByte(reg_SP|0x100, temp);
        reg_SP = (reg_SP - 1) & 0xFF;
        // Set flag I
        flag_I = true;
        // Jump to the interrupt vector
        reg_PC = MMU.readWord(vector);
    }
}
