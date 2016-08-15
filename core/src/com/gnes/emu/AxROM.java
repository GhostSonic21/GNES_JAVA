package com.gnes.emu;

/**
 * Created by ghost_000 on 8/14/2016.
 */
public class AxROM extends Cartridge {
    // Mapper 7
    // Contains CHR RAM
    private int PRGSize;
    private int CHRSize;
    private byte[] PRGData;
    private byte[] PRGRAMData;
    private byte[] CHRData;
    private byte flags6;
    private byte flags7;
    private byte flags9;

    // Bank register
    private int PRGRomBankNum;
    private boolean secondVRAMPage;

    public AxROM(byte[] romFile){
        // Constructor
        PRGRAMData = new byte[0x2000];  // Maybe

        // Save flags
        flags6 = romFile[0x06];
        flags7 = romFile[0x07];
        flags9 = romFile[0x09];

        // Copy over data
        PRGSize = 16384 * (romFile[0x04] & 0xFF);
        CHRSize = 8192 * (romFile[0x05] & 0xFF);

        PRGData = new byte[PRGSize];
        for (int i = 0; i < PRGSize; i++){
            PRGData[i] = romFile[i + 0x10];
        }

        if (CHRSize == 0){
            CHRSize = 0x2000;
            CHRData = new byte[CHRSize];
        }
        else{
            System.err.printf("AxROM only uses CHRRAM according to spec. (Fix if there's an exception)\n");
            System.exit(-1);
        }
    }


    @Override
    public int PRGRead(int address) {
        int returnVal = 0;

        if (address >= 0x6000 && address <= 0x7FFF){
            returnVal = PRGRAMData[address & 0x1FFF] & 0xFF;
        }
        else if (address >= 0x8000 && address <= 0xFFFF){
            int returnAddress = address & 0x7FFF;
            returnAddress |= PRGRomBankNum << 15;
            returnVal = PRGData[returnAddress & (PRGSize - 1)] & 0xFF;
        }

        return returnVal;
    }

    @Override
    public void PRGWrite(int address, int data) {
        if (address >= 0x6000 && address <= 0x7FFF){
            PRGRAMData[address & 0x1FFF] = (byte)data;
        }
        else if (address >= 0x8000 && address <= 0xFFFF){
            PRGRomBankNum = data & 0x7;
            secondVRAMPage = (data & 0x10) == 0x10;
        }
    }

    @Override
    public int CHRRead(int address) {
        int returnVal = CHRData[address & 0x1FFF] & 0xFF;
        return returnVal;
    }

    @Override
    public void CHRWrite(int address, int data) {
        CHRData[address & 0x1FFF] = (byte)data;
    }

    @Override
    public int readNameTable(int address, int[] VRAM) {
        int returnAddress = address & 0x3FF;
        returnAddress |= (secondVRAMPage ? 1:0) << 10;
        int returnVal = VRAM[returnAddress];

        return returnVal;
    }

    @Override
    public void writeNameTable(int address, int[] VRAM, int data) {
        int returnAddress = address & 0x3FF;
        returnAddress |= (secondVRAMPage ? 1:0) << 10;
        VRAM[returnAddress] = data;
    }

    @Override
    public boolean checkIRQ() {
        return false;
    }
}
