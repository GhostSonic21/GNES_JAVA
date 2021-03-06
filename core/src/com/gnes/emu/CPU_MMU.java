package com.gnes.emu;

/**
 * Created by ghost_000 on 7/6/2016.
 */

/* Memory map for the 2A03
 *
 * Address range 	Size 	Device
 * $0000-$07FF 	    $0800 	2KB internal RAM
 * $0800-$0FFF  	$0800 	Mirrors of $0000-$07FF
 * $1000-$17FF 	    $0800   Mirrors of $0000-$07FF
 * $1800-$1FFF  	$0800   Mirrors of $0000-$07FF
 * $2000-$2007  	$0008 	NES PPU registers
 * $2008-$3FFF  	$1FF8 	Mirrors of $2000-2007 (repeats every 8 bytes)
 * $4000-$401F  	$0020 	NES APU and I/O registers
 * $4020-$FFFF  	$BFE0 	Cartridge space: PRG ROM, PRG RAM, and mapper registers (See Note)
 */

public class CPU_MMU {
    // Variables and objects
    int[] RAM;  // Internal memory
    Cartridge cartridge;
    PPU PPU;
    APU APU;
    Controller controller;
    int openBus;    // Contains the last value placed on the bus

    int cycleAdditions;

    // Constructors
    // Add objects as needed
    public CPU_MMU(Cartridge cartridge, PPU PPU, Controller controller, APU APU){
        this.cartridge = cartridge;
        RAM = new int[0x800];   // Initalize 2KB internal ram
        this.PPU = PPU;
        this.controller = controller;
        this.APU = APU;
    }

    // Read methods
    public int readByte(int address){
        int returnData = openBus;  // Data to return. Openbus usually returned on unmapped areas (disable if issues).
        
        if (address >= 0x0000 && address <= 0x1FFF){
            returnData = RAM[address & 0x7FF];
        }
        else if (address >= 0x2000 && address <= 0x3FFF){
            // PPU Registers
            // Do this when we actually do the ppu
            returnData = PPU.readRegister(address & 0x7);
        }
        else if (address >= 0x4000 && address <= 0x401F){
            // APU and I/O Registers
            // Implement as needed

            // APU
            // Only 0x4015 is readable
            if (address == 0x4015){
                returnData = APU.receiveData(address);
            }

            // Controller
            else if (address == 0x4016 || address == 0x4017){
                // 3 most significant bits are open bus. Paperboy relies on this for some odd reason.
                returnData = controller.recieveData(address)|(openBus & 0xE0);
            }
        }
        else if (address >= 0x4020 && address <= 0x5FFF){
            // Cartridge expansion rom, not implemented
        }
        else if (address >= 0x6000 && address <= 0xFFFF){
            returnData = cartridge.PRGRead(address);
        }
        openBus = returnData;   // Should openbus be updated on writes? Probably not?
        return returnData;
    }

    public int readWord(int address){
        int returnData = (readByte(address))|(readByte(address+1) << 8);
        returnData &= 0xFFFF;
        return returnData;
    }

    public int readWordIndirect(int address){
        // Indirect addressing on the 6502 is stupid
        // The target address will overflow if the upper byte is in 0XFF
        // Except only the lower byte of the target address overflows
        // So if you target 0x6FF, it will get the high byte from 0x6FF and low from 0x600

        int returnData = (readByte(address));
        address = (address & 0xFF00) | ((address + 1) & 0xFF);
        returnData = returnData|(readByte(address) << 8);
        returnData &= 0xFFFF;
        return returnData;
    }

    // Write methods
    public void writeByte(int address, int data) {
        openBus = data;
        if (address >= 0x0000 && address <= 0x1FFF) {
            RAM[address & 0x7FF] = data;
        }
        else if (address >= 0x2000 && address <= 0x3FFF) {
            // PPU Registers
            // Do this when we actually do the ppu
            PPU.writeRegister(address & 0x7, data);
        }
        else if (address >= 0x4000 && address <= 0x401F) {
            // APU and I/O Registers
            // Implement as needed

            // APU
            if (address <= 0x4013 || address == 0x4015 || address == 0x4017){
                APU.writeData(address, data);
            }

            // OAM DMA
            else if (address == 0x4014){
                int OAMDMAaddress = (data << 8);
                int[] OAMData = new int[256];
                for (int i = 0; i < 256; i++){
                    OAMData[i] = readByte(OAMDMAaddress + i);
                }
                PPU.OAMDMA(OAMData);
                cycleAdditions += 513;  // 513 additional cpu cycles for a DMA
            }

            // Controller
            // Only 4016 is used for the controller on write, 4017 writes to an APU register.
            else if (address == 0x4016){
                controller.sendData(address, data);
            }
        }
        else if (address >= 0x4020 && address <= 0x5FFF) {
            // Cartridge expansion rom, not implemented
        }
        else if (address >= 0x6000 && address <= 0xFFFF) {
            cartridge.PRGWrite(address, data);
        }
    }
    public void writeWord(int address, int data){
        writeByte(address, data & 0xFF);
        data >>= 8;
        writeByte(address + 1, data & 0xFF);
    }

    // Getter for cycle additions
    public int getCycleAdditions(){
        int returnData = cycleAdditions;
        cycleAdditions = 0;
        return returnData;
    }
}
