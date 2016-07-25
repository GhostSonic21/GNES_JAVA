package com.gnes.emu;

/**
 * Created by ghost_000 on 7/23/2016.
 */

// GxROM is a simple mapper that supports up to 4 PRG and CHR banks

public class GxROM extends Cartridge {
    // Variables and classes and whatnot
    int PRGSize;
    int CHRSize;
    byte[] PRGData;
    byte[] CHRData;
    int flags6;
    int flags7;
    int flags9;
    int PRGBank = 0;
    int CHRBank = 0;

    public GxROM(byte[] romData){
        // Define PRG and CHR size
        PRGSize = 16384 * (romData[0x4] & 0xFF);
        CHRSize = 8192 * (romData[0x5] & 0xFF);

        // Create their arrays
        PRGData = new byte[PRGSize];
        CHRData = new byte[CHRSize];

        // Copy PRG and CHR data to their respective arrays

        // PRG
        for (int i = 0; i < PRGSize; i++){
            PRGData[i] = romData[i+0x10];
        }

        // CHR
        for (int i = 0; i < CHRSize; i++){
            CHRData[i] = romData[i+0x10+PRGSize];
        }

        // Save the rest of the flags
        flags6 = romData[0x6] & 0xFF;
        flags7 = romData[0x7] & 0xFF;
        flags9 = romData[0x9] & 0xFF;
    }

    @Override
    public int PRGRead(int address) {
        int returnData = 0xff;
        if (address >= 0x8000 && address <= 0xFFFF){
            address &= 0x7FFF;
            address |= (PRGBank << 15);
            returnData = PRGData[address] & 0xFF;
        }
        return returnData;
    }

    @Override
    public void PRGWrite(int address, int data) {
        // One one register to write to
        if (address >= 0x8000 && address <= 0xFFFF){
            CHRBank = data & 0x3;
            PRGBank = (data >> 4) & 0x3;
        }
    }

    @Override
    public int CHRRead(int address) {
        int returnData = 0xFF;
        address |= (CHRBank << 13);
        returnData = CHRData[address] & 0xFF;
        return returnData;
    }

    @Override
    public void CHRWrite(int address, int data) {
        // Nothing done here
    }

    @Override
    public int readNameTable(int address, int[] VRAM) {
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
    public void writeNameTable(int address, int[] VRAM, int data) {
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
}
