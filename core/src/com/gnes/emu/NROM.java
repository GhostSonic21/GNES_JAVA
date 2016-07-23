package com.gnes.emu;

import com.badlogic.gdx.files.FileHandle;

/**
 * Created by ghost_000 on 7/6/2016.
 */

// Processing and storing NES Roms

public class NROM extends Cartridge{
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
    public NROM(byte[] romFile){
        // NROM Init
        PRG_RAM = new byte[0x2000];  // Most NROM games don't use this, but some test roms do
        // Save flags
        flags6 = romFile[0x06];
        flags7 = romFile[0x07];
        flags9 = romFile[0x09];

        // Copy over data
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
    @Override
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
    @Override
    public void PRGWrite(int address, int data){
        // Since we're hard-coded to NROM for now, nothing is done on write except for ram.
        if (address >= 0x6000 && address <= 0x7FFF){
            PRG_RAM[address - 0x6000] = (byte)data;
            if (address >= 0x6004 && data != 0x00){
                System.out.printf("%c", data);
            }
        }
    }

    // CHR read/write
    // Read(s)
    @Override
    public int CHRRead(int address){
        return CHR_ROM[address];
    }

    // Write(s)
    @Override
    public void CHRWrite(int address, int data){
        // TODO: I dunno what happens here so just return
        return;
    }

    // Nametable map
    @Override
    public int readNameTable(int address, int[] VRAM){
        int VRAMAddress = 0;
        // Vertical mirroring
        if ((flags6 & 0x1) > 0){
            VRAMAddress = address & 0x7FF;
        }
        // Horizontal mirroring
        else{
            VRAMAddress = address & 0x3FF;
            if ((address & 0x800) > 0){
                VRAMAddress |= 0x400;
            }
        }
        return VRAM[VRAMAddress];
    }

    @Override
    public void writeNameTable(int address, int[] VRAM, int data){
        int VRAMAddress = 0;
        // Vertical mirroring
        if ((flags6 & 0x1) > 0){
            VRAMAddress = address & 0x7FF;
        }
        // Horizontal mirroring
        else{
            VRAMAddress = address & 0x3FF;
            if ((address & 0x800) > 0){
                VRAMAddress |= 0x400;
            }
        }
        VRAM[VRAMAddress] = data;
    }

    @Override
    public boolean checkIRQ() {
        // No IRQs
        return false;
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
