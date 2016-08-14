package com.gnes.emu;

import com.badlogic.gdx.files.FileHandle;

/**
 * Created by ghost_000 on 7/23/2016.
 */
public abstract class Cartridge {

    // Reading from PRG Space
    public abstract int PRGRead (int address);

    // Writing to PRG Space
    public abstract void PRGWrite (int address, int data);

    // Reading form CHR space
    public abstract int CHRRead (int address);

    // Writing to CHR space
    public abstract void CHRWrite (int address, int data);

    // Returns requests for nametable data.
    // Mappers can mirror different ways and even contain extra VRAM in particular cases, so take in VRAM array.
    public abstract int readNameTable (int address, int[] VRAM);

    public abstract void writeNameTable (int address, int[] VRAM, int data);

    public abstract boolean checkIRQ();

    // Returns a Cartridge object based on iNES header
    public static Cartridge getCartridge(FileHandle romHandle){
        byte[] romData = romHandle.readBytes();

        // Check for "NES" in header
        String nes = "";
        for (int i = 0; i < 3; i++){
            nes += (char)romData[i];
        }
        if (!nes.contains("NES")){
            System.err.printf("Can't find NES Header. Probably check out why.\n");
        }

        int mapperNumber = ((romData[0x06] >> 4) & 0xF)|(romData[0x07] & 0xF0);

        Cartridge returnCart;
        switch (mapperNumber){
            case 0:
                returnCart = new NROM(romData);
                break;
            case 1:
                returnCart = new MMC1(romData);
                break;
            case 2:
                returnCart = new UxROM(romData);
                break;
            case 3:
                returnCart = new CNROM(romData);
                break;
            case 4:
                returnCart = new MMC3(romData);
                break;
            case 66:
                returnCart = new GxROM(romData);
                break;
            default:
                returnCart = null;
                break;
        }

        if (returnCart == null){
            System.err.printf("Unsupported mapper #%d\n", mapperNumber);
            System.exit(-1);
        }

        return returnCart;
    }
}
