package com.gnes.emu;

/**
 * Created by ghost_000 on 7/24/2016.
 */
public class UxROM extends Cartridge {
    // Variables classes etc.
    int PRGSize;
    int PRGBanks;   // Keeps track of amount of banks
    int bankSelect; // Keeps track of current PRG bank
    int CHRSize;
    byte[] PRGData;
    byte[] CHRData;
    int flags6;
    int flags7;
    int flags9;

    // Constuctor
    public UxROM(byte[] romData){
        PRGSize = 16384 * (romData[0x4] & 0xFF);
        CHRSize = 8192 * (romData[0x5] & 0xFF);
        flags6 = romData[0x6] & 0xFF;
        flags7 = romData[0x7] & 0xFF;
        flags9 = romData[0x9] & 0xFF;

        // Copy PRG Data
        PRGData = new byte[PRGSize];
        for (int i = 0; i < PRGSize; i++){
            PRGData[i] = romData[i + 0x10];
        }
        PRGBanks = romData[0x4] & 0xFF;

        // CHR Setup
        if (CHRSize > 0){
            System.out.printf("Error: UxROM should not have CHR Size specified");
            System.exit(-1);
        }
        CHRSize = 0x2000;   // UxROM specieis 8KB of CHR RAM
        CHRData = new byte[CHRSize];
    }

    @Override
    public int PRGRead(int address) {
        int returnData = 0xFF;
        if (address >= 0x8000 && address <= 0xFFFF) {
            int readBank;
            // Read from selected bank
            if (address < 0xC000) {
                readBank = bankSelect;
            }
            // Always uses last bank
            else {
                readBank = PRGBanks - 1;
            }
            address &= 0x3FFF;
            address |= (readBank << 14);
            returnData = PRGData[address] & 0xFF;
        }
        return returnData;
    }

    @Override
    public void PRGWrite(int address, int data) {
        if (address > 0x8000 && address <= 0xFFFF){
            bankSelect = data & 0xFF;
        }
    }

    @Override
    public int CHRRead(int address) {
        int returnData = 0xFF;
        returnData = CHRData[address] & 0xFF;  // 8KB maps pretty straight
        return returnData;
    }

    @Override
    public void CHRWrite(int address, int data) {
        // CHR Ram should map straight if I'm right
        CHRData[address] = (byte)data;
    }

    @Override
    public int readNameTable(int address, int[] VRAM) {
        // Same as NROM
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
        // Same as NROM
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
