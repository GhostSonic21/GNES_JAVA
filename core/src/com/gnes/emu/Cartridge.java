package com.gnes.emu;

import com.badlogic.gdx.files.FileHandle;

/**
 * Created by ghost_000 on 7/6/2016.
 */

// Processing and storing NES Roms

public class Cartridge {
    // Arrays classes and variables
    private byte[] PRG_ROM;
    private byte[] PRG_RAM;
    private byte[] CHR_ROM;
    private byte[] CHR_RAM;
    private byte flags6;
    private byte flags7;
    private byte flags9;
    private int PRGSize;
    private int CHRSize;

    // Contstrctor
    public Cartridge(FileHandle romHandle){
        // Currently hard-coded to NROM
        // TODO: More mappers
        // TODO: Actually process iNES header
        PRG_RAM = new byte[0x2000];  // Most NROM games don't use this, but some test roms do
        byte[] romFile = romHandle.readBytes(); // Temporarily copy romFile contents into a simple byte array
        // Check if the "NES" header is there
        String nes = "";
        for (int i = 0; i < 3; i++){
            nes += (char)romFile[i];
        }
        if (!nes.contains("NES")){
            System.out.printf("Can't find NES Header. Probably check out why.\n");
        }
        // Save flags
        flags6 = romFile[0x06];
        flags7 = romFile[0x07];
        flags9 = romFile[0x09];
        // TODO: Process whatever the hell these flags do, part of it is nametable mirroring
        // Check mapper number
        int mapperNum = ((flags6 >> 4) & 0xF)|(flags7 & 0xF0);
        if(mapperNum != 0){
            System.out.printf("Unsupported mapper detected. Only NRom supported. Execution will likely fail.\n");
        }
        // For now, copy over data
        PRGSize = 16384 * romFile[0x04];
        CHRSize = 8192 * romFile[0x05];

        PRG_ROM = new byte[PRGSize];
        CHR_ROM = new byte[CHRSize];

        // Copy PRG
        for (int i = 0; i < PRGSize; i++){
            PRG_ROM[i] = romFile[i + 0x10];
        }

        // Copy CHR
        for (int i = 0; i < CHRSize; i++){
            CHR_ROM[i] = romFile[i + PRGSize + 0x10];
        }
    }

    // PRG read/write
    // Read(s)
    public int PRGRead(int address){
        int returnData = 0xFF;
        if (address >= 0x6000 && address <= 0x7FFF){
            returnData = PRG_RAM[address - 0x6000];
        }
        else if (address >= 0x8000 && address <= 0xFFFF){
            returnData = PRG_ROM[(address - 0x8000) & (PRGSize - 1)];
        }
        return returnData & 0xFF;
    }

    // Write(s)
    public void PRGWrite(int address, int data){
        // Since we're hard-coded to NROM for now, nothing is done on write except for ram.
        if (address >= 0x6000 && address <= 0x7FFF){
            PRG_RAM[address - 0x6000] = (byte)data;
            /*if (address == 0x6000 && ((data >= 0x00 && data <= 0x7F ) || data == 0x80)){
                outputResult();
            }*/
            if (address >= 0x6004 && data != 0x00){
                System.out.printf("%c", data);
            }
        }
    }

    // CHR read/write
    // Read(s)
    public int CHRRead(int address){
        return CHR_ROM[address];    // Cry if we somehow end up with CHR_RAM
    }

    // Write(s)
    public void CHRWrite(int address){
        // TODO: I dunno what happens here so just return
        return;
    }

    // Code for nes test suites
    private void outputResult(){
        int charNum = 0x4;  // Starts at 0x6004
        char nextChar = (char)PRG_RAM[charNum];
        while(nextChar != 0x00){
            System.out.printf("%c", nextChar);
            charNum++;
            nextChar = (char)PRG_RAM[charNum];
        }
        System.out.println();
    }
}
