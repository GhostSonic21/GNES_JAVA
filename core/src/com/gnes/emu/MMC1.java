package com.gnes.emu;

/**
 * Created by ghost_000 on 7/27/2016.
 */
public class MMC1 extends Cartridge {
    // Variables classes etc.
    int PRGSize;
    int PRGBanks;
    int PRGRAMSize;
    int CHRSize;
    byte[] PRGData;
    byte[] CHRData;
    byte[] PRGRAMData;
    int flags6;
    int flags7;
    boolean CHRRAM;     // Set if cart uses CHR RAM
    boolean PRGLarge;   // Set if PRG is above 512KiB (CHR Bank registers act different)
    boolean CHRLarge;   // Set if CHR is above 256KiB (CHR Bank registers act different)

    // MMC1 registers
    int control;
    int CHRBank0;
    int CHRBank1;
    int PRGBank;
    int serialClocks = 0; // Serial data tracker
    int serialData = 0;

    public MMC1(byte[] romData){
        PRGSize = 16384 * (romData[0x4] & 0xFF);
        PRGLarge = PRGSize > 0x40000;
        CHRSize = 8192 * (romData[0x5] & 0xFF);
        CHRLarge = CHRSize > 0x2000;
        flags6 = romData[0x6] & 0xFF;
        flags7 = romData[0x7] & 0xFF;
        // Copy PRG Data
        PRGData = new byte[PRGSize];
        for (int i = 0; i < PRGSize; i++){
            PRGData[i] = romData[i + 0x10];
        }
        PRGBanks = romData[0x4] & 0xFF;

        // Copy CHR Data if it's CHR ROM
        if (romData[0x5] != 0){
            CHRRAM = false;
            CHRData = new byte[CHRSize];
            for (int i = 0; i < CHRSize; i++){
                CHRData[i] = romData[i + 0x10 + PRGSize];
            }
        }
        // If it's CHR RAM
        else {
            CHRRAM = true;
            CHRSize = 0x2000;
            CHRData = new byte[0x2000];
        }

        // PRGRam handling
        /*if ((flags6 & 0x2) > 0){
            // PRGRam exists, how big is it?
            if (romData[0x8] == 0){
                romData[0x8] = 1;   // Should be 1 if RAM exists and this states 0
            }
            PRGRAMSize = 8192 * (romData[0x8] & 0xFF);
            PRGRAMData = new byte[PRGRAMSize];
        }
        else{
            PRGRAMSize = 0;
            // PRG RAM Might exist anyways?
            //PRGRAMSize = 8192;
            //PRGRAMData = new byte[PRGRAMSize];
        }*/
        // Despite what I may believe, it seems like PRG RAM could always exist despite bit 2 on flag 6?
        if (romData[0x8] == 0){
            romData[0x8] = 1;   // Should be 1 if RAM exists and this states 0
        }
        PRGRAMSize = 8192 * (romData[0x8] & 0xFF);
        PRGRAMData = new byte[PRGRAMSize];

        // Unsupported modes, exit for now until I figure it out
        if (PRGLarge){
            System.out.printf("ERROR: >256k PRG not supported");
            System.exit(-1);
        }
        if (PRGRAMSize > 0x2000){
            // Since I'm not sure how larger PRG RAM works right now
            System.out.printf("ERROR: >8K PRG RAM not currently supported.");
            System.exit(-1);
        }
        control |= 0xC;
    }


    @Override
    public int PRGRead(int address) {
        int returnData = 0;
        if (address >= 0x6000 & address <= 0x7FFF){
            // TODO: PRG RAM Handling
            //System.out.printf("Attempt to read PRG RAM address 0x%x\n", address);
            if (PRGRAMSize >= (address & 0x1FFF)) {
                returnData = PRGRAMData[address & 0x1FFF] & 0xFF;
            }
        }
        else if (address >= 0x8000 && address <= 0xFFFF){
            int bankNum = 0;
            int PRGAddress = 0;
            int PRGRomBankMode = (control >>> 2) & 0x3;
            // Modes, 0,1: 32KB switch, 2: Fixed 1st bank, 3: Fixed last bank
            switch (PRGRomBankMode){
                case 0:
                case 1:
                    // 32KB
                    bankNum = PRGBank & 0xE;
                    bankNum |= ((address >>> 14) & 0x1);
                    break;
                case 2:
                    if (address < 0xC000){
                        bankNum = 0;
                    }
                    else {
                        bankNum = PRGBank & 0xF;
                    }
                    break;
                case 3:
                    if (address >= 0xC000){
                        bankNum = PRGBanks -1;
                    }
                    else{
                        bankNum = PRGBank & 0xF;
                    }
                    break;
            }
            PRGAddress = (address & 0x3FFF) | (bankNum << 14);
            if (PRGLarge){
                // Weird 256KB mode
            }
            returnData = (PRGData[PRGAddress]) & 0xFF;
        }

        return returnData;
    }

    @Override
    public void PRGWrite(int address, int data) {
        if (address >= 0x6000 && address <= 0x7FFF){
            // TODO: PRG RAM Handling
            //System.out.printf("PRGRAM Write: 0x%x @ 0x%x\n", data, address);
            if (PRGRAMSize >= (address & 0x1FFF)) {
                PRGRAMData[address & 0x1FFF] = (byte)data;
            }
        }
        else if (address >= 0x8000 && address <= 0xFFFF){
            // Load register
            if ((data & 0x80) > 0){
                // Reset serial
                serialClocks = 0;
                serialData = 0;
                control |= 0x0C;    // Apparently this happens too
            }
            else if (serialClocks < 4){
                serialData |= (data & 0x1) << 4;
                serialData >>>= 1;
                serialClocks++;
            }
            // On 5th write, apply to register
            else if (serialClocks == 4){
                // Take in last bit and apply to register, reset
                serialData |= (data & 0x1) << 4;
                serialClocks = 0;
                // Bit 13 & 14 determine which register serial data is applied to
                int registerNum = (address >>> 13) & 0x3;
                switch (registerNum){
                    case 0:
                        control = serialData;
                        break;
                    case 1:
                        CHRBank0 = serialData;
                        break;
                    case 2:
                        CHRBank1 = serialData;
                        break;
                    case 3:
                        PRGBank = serialData;
                        break;
                }
                // Reset serialData afterwards
                serialData = 0;
            }
        }
    }

    @Override
    public int CHRRead(int address) {
        // This is stupidly variant-dependent
        // CHR RAM Handling
        int CHRAddress = 0;

        boolean CHRBankSwitch = (control & 0x10) > 0;
        int CHRBank;
        if (CHRBankSwitch) {
            // Lower
            if (address < 0x1000) {
                if (!CHRLarge) {
                    CHRBank = CHRBank0 & 0x1;
                } else {
                    CHRBank = CHRBank0 & 0x1F;
                }
            }
            // Upper
            else {
                if (!CHRLarge) {
                    CHRBank = CHRBank1 & 0x1;
                } else {
                    CHRBank = CHRBank1 & 0x1F;
                }
            }
            CHRAddress = (address & 0xFFF) | (CHRBank << 12);
        }
        else{
            if (CHRLarge){
                CHRBank = (CHRBank0 >> 1) & 0x7;
            }
            else{
                CHRBank = 0;
            }
            CHRAddress = (address & 0x1FFF) | (CHRBank << 13);
        }


        return CHRData[CHRAddress & (CHRSize-1)] & 0xFF;
    }

    @Override
    public void CHRWrite(int address, int data) {
        // Only works for CHR RAM;
        if (CHRRAM){
            int CHRAddress = 0;
            boolean CHRBankSwitch = (control & 0x10) > 0;
            if (CHRBankSwitch){
                int CHRBank;
                if (address < 0x1000) {
                    CHRBank = CHRBank0 & 0x1;
                }
                // Upper
                else {
                    CHRBank = CHRBank1 & 0x1;
                }
                CHRAddress = (address & 0xFFF) | (CHRBank << 12);
            }
            else{
                CHRAddress = address & 0x1FFF;
            }
            CHRData[CHRAddress & (CHRSize-1)] = (byte)data;
        }
    }

    @Override
    public int readNameTable(int address, int[] VRAM) {
        int VRAMAddress = 0;
        int mirrorMode = control & 0x3;
        switch (mirrorMode){
            case 0:
                // Single screen, lower bank
                VRAMAddress = address & 0x3FF;
                break;
            case 1:
                // Single screen, upper bank
                VRAMAddress = (address & 0x3FF) | 0x400;
                break;
            case 2:
                // Vertical
                VRAMAddress = address & 0x7FF;
                break;
            case 3:
                // Horizontal
                VRAMAddress = address & 0x3FF;
                if ((address & 0x800) > 0){
                    VRAMAddress |= 0x400;
                }
                break;
        }
        return VRAM[VRAMAddress];
    }

    @Override
    public void writeNameTable(int address, int[] VRAM, int data) {
        int VRAMAddress = 0;
        int mirrorMode = control & 0x3;
        switch (mirrorMode){
            case 0:
                // Single screen, lower bank
                VRAMAddress = address & 0x3FF;
                break;
            case 1:
                // Single screen, upper bank
                VRAMAddress = (address & 0x3FF) | 0x400;
                break;
            case 2:
                // Vertical
                VRAMAddress = address & 0x7FF;
                break;
            case 3:
                // Horizontal
                VRAMAddress = address & 0x3FF;
                if ((address & 0x800) > 0){
                    VRAMAddress |= 0x400;
                }
                break;
        }
        VRAM[VRAMAddress] = data;

    }

    @Override
    public boolean checkIRQ() {
        return false;
    }
}
