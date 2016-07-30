package com.gnes.emu;

/**
 * Created by ghost_000 on 7/29/2016.
 */

// Essentially NROM with a bankable CHR ROM
public class CNROM extends Cartridge{
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

    // One CHR Bank register
    private int CHRBank = 0;

    // Contstrctor
    public CNROM(byte[] romFile){
        // NROM Init
        // Save flags
        flags6 = romFile[0x06];
        flags7 = romFile[0x07];
        flags9 = romFile[0x09];

        // Copy over data
        PRGSize = 16384 * (romFile[0x04] & 0xFF);
        CHRSize = 8192 * (romFile[0x05] & 0xFF);

        // Copy PRG
        PRG_ROM = new byte[PRGSize];
        for (int i = 0; i < PRGSize; i++){
            PRG_ROM[i] = romFile[i + 0x10];
        }

        // Copy CHR
        if (CHRSize > 0) {
            CHR_ROM = new byte[CHRSize];
            for (int i = 0; i < CHRSize; i++) {
                CHR_ROM[i] = romFile[i + PRGSize + 0x10];
            }
        }
        else{
            // CHR RAM
            CHRSize = 0x2000;
            CHR_RAM = new byte[CHRSize];
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
        if (address >= 0x8000){
            CHRBank = data & 0xFF;
        }
    }

    // CHR read/write
    // Read(s)
    @Override
    public int CHRRead(int address){
        int returnData = 0xFF;
        int CHRAddress = address|(CHRBank << 13);
        if (CHR_RAM == null) {
            returnData = CHR_ROM[CHRAddress];
        }
        else{
            returnData = CHR_RAM[CHRAddress];
        }
        return returnData;
    }

    // Write(s)
    @Override
    public void CHRWrite(int address, int data){
        if (CHR_RAM != null){
            CHR_RAM[address] = (byte)data;
        }
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

