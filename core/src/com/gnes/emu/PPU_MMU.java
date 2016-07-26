package com.gnes.emu;

/**
 * Created by ghost_000 on 7/11/2016.
 */

/* The NES' PPU has a different memory map for the PPU than the CPU
 * From NESDEV:
 * $0000-$0FFF 	$1000 	Pattern table 0
 * $1000-$1FFF 	$1000 	Pattern Table 1
 * $2000-$23FF 	$0400 	Nametable 0
 * $2400-$27FF 	$0400 	Nametable 1
 * $2800-$2BFF 	$0400 	Nametable 2
 * $2C00-$2FFF 	$0400 	Nametable 3
 * $3000-$3EFF 	$0F00 	Mirrors of $2000-$2EFF
 * $3F00-$3F1F 	$0020 	Palette RAM indexes
 * $3F20-$3FFF 	$00E0 	Mirrors of $3F00-$3F1F
 * Apparently the cartridge can configure this layout somehow
 * So we'll complain about that bridge when/if we cross it
 * There's also OAM data, but I think that's accessed a bit differently */

public class PPU_MMU {
    // Variables classes whatever
    int[] VRAM;
    int[] OAM;
    int[] paletteRAM;
    Cartridge cartridge;

    public PPU_MMU(Cartridge cartridge){
        this.cartridge = cartridge;
        VRAM = new int[0x800];  // VRAM only contains 2KB onboard the console, but Nametable region contains 4kb region
        OAM  = new int[0x100];
        paletteRAM = new int[0x20];
    }

    public int readByte(int address){
        address &= 0x3FFF;
        int returnByte = 0xFF;
        switch(address >> 12){
            case 0x0:
            case 0x1:{
                // Map to CHR
                returnByte = cartridge.CHRRead(address);
                break;
            }
            case 0x2:{
                // Map to VRAM
                returnByte = cartridge.readNameTable(address, VRAM);
                break;
            }
            case 0x3:{
                // Part of this is just a mirror of VRAM, rest is palette ram indexes
                if (address < 0x3F00){
                    returnByte = cartridge.readNameTable(address-0x1000, VRAM);
                }
                else{
                    // Palette Mirroring.
                    if ((address & 0x3) == 0){
                        address &= 0xFF0F;
                    }
                    returnByte = paletteRAM[address & 0x1F];
                }
                break;
            }
            default:{
                // Dunno what happens here if anything can possibly happen at all?
                break;
            }
        }
        return returnByte & 0xFF;
    }

    public void writeByte(int address, int data){
        address &= 0x3FFF;
        switch(address >> 12){
            case 0x0:
            case 0x1:{
                // Map to CHR
                cartridge.CHRWrite(address, data);
                break;
            }
            case 0x2:{
                // Map to VRAM
                cartridge.writeNameTable(address, VRAM, data & 0xFF);
                break;
            }
            case 0x3:{
                // Part of this is just a mirror of VRAM, rest is palette ram indexes
                if (address < 0x3F00){
                    cartridge.writeNameTable(address-0x1000, VRAM, data & 0xFF);
                }
                else{
                    // Palette Mirroring.
                    if ((address & 0x3) == 0){
                        address &= 0xFF0F;
                    }
                    paletteRAM[address & 0x1F] = data & 0xFF;
                }
                break;
            }
            default:{
                // Dunno what happens here if anything can possibly happen at all?
            }
        }
    }

}
