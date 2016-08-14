package com.gnes.emu;

/**
 * Created by ghost_000 on 8/13/2016.
 */
public class MMC3 extends Cartridge {
    private int PRGSize;
    private int PRGBanks;
    private int CHRSize;
    private int CHRBanks;
    private int PRGRamSize;

    private byte[] PRGData;
    private byte[] CHRData;
    private byte[] PRGRAMData;

    // iNES flags
    private int flags6;
    private int flags7;

    // MMC3 registers
    private int bankRegisterSelect;
    private boolean lowerFixed;
    private boolean lowerCHRTwoBanks;
    // Banks
    private int[] CHRBankValues = new int[6];
    private int[] PRGBankValues = new int[2];
    // Nametable Mirroring
    private boolean horizontalMirroring;

    // IRQ counter
    private int IRQLoad;
    private int IRQCounter;
    private boolean IRQCounterReload;
    private boolean IRQEnable;
    private boolean IRQ;
    // IRQ tracking stuff
    private boolean lastreadA12;    // false if last read wasn't A12, true if it was



    public MMC3(byte[] romData){
        // Constructor
        PRGSize = 16384 * (romData[0x4] & 0xFF);
        CHRSize = 8192 * (romData[0x5] & 0xFF);
        PRGBanks = (romData[0x4] & 0xFF)*2; // 8KB units
        CHRBanks = (romData[0x5] & 0xFF);
        flags6 = romData[0x6] & 0xFF;
        flags7 = romData[0x7] & 0xFF;

        // Copy PRG Data
        PRGData = new byte[PRGSize];
        for (int i = 0; i < PRGSize; i++){
            PRGData[i] = romData[i + 0x10];
        }

        // Copy CHR Data if it's CHR ROM
        if (CHRSize != 0){
            CHRData = new byte[CHRSize];
            for (int i = 0; i < CHRSize; i++){
                CHRData[i] = romData[i + 0x10 + PRGSize];
            }
        }
        else{
            System.err.printf("MMC3 shouldn't have CHR RAM(?)");
            System.exit(-1);
        }

        // Create a PRG RAM
        PRGRAMData = new byte[8192];
    }

    @Override
    public int PRGRead(int address) {
        int returnVal = 0;

        int bankNum = (address >> 13) & 0x3;
        int returnAddress = address & 0x1FFF;

        // All 8KB banks, but banks 0 or 2 could be switchable or fixed-to-2nd-to-last
        // 1 is always switchable
        if (address >= 0x6000 && address <= 0x7FFF){
            returnVal = PRGRAMData[address & 0x1FFF] & 0xFF;
        }
        else if (address >= 0x8000 && address <= 0xFFFF) {
            if (!lowerFixed) {
                switch (bankNum) {
                    case 0:
                        returnAddress |= (PRGBankValues[0] << 13);
                        break;
                    case 1:
                        returnAddress |= (PRGBankValues[1] << 13);
                        break;
                    case 2:
                        returnAddress |= ((PRGBanks - 2) << 13);
                        break;
                    case 3:
                        returnAddress |= ((PRGBanks - 1) << 13);
                        break;
                }
            } else {
                switch (bankNum) {
                    case 0:
                        returnAddress |= ((PRGBanks - 2) << 13);
                        break;
                    case 1:
                        returnAddress |= (PRGBankValues[1] << 13);
                        break;
                    case 2:
                        returnAddress |= (PRGBankValues[0] << 13);
                        break;
                    case 3:
                        returnAddress |= ((PRGBanks - 1) << 13);
                        break;
                }
            }
            returnVal = PRGData[returnAddress & (PRGSize - 1)] & 0xFF;
        }

        return returnVal;
    }

    @Override
    public void PRGWrite(int address, int data) {
        if (address >= 0x6000 && address <= 0x7FFF){
            PRGRAMData[address & 0x1FFF] = (byte)data;
        }
        // 4 pairs of registers (8 in total). One even and one odd.
        else if (address >= 0x8000 && address <= 0x9FFF){
            // Even
            // Bank Select
            if ((address & 0x1) == 0x0){
                bankRegisterSelect = data & 0x7;
                lowerFixed = (data & 0x40) == 0x40;
                lowerCHRTwoBanks = (data & 0x80) == 0x80;
            }
            // Odd
            // Bank data
            else{
                if (bankRegisterSelect < 6){
                    CHRBankValues[bankRegisterSelect] = data;
                }
                else{
                    PRGBankValues[bankRegisterSelect - 6] = data;
                }
            }
        }
        else if (address >= 0xA000 && address <= 0xBFFF){
            // Even
            // Mirroring
            if ((address & 0x1) == 0x0){
                horizontalMirroring = (data & 0x1) == 0x1;
            }
            // Odd
            // PRG Ram Select
            else{
                // Not implemented, maybe implement later (behavior is a little funny).
            }
        }
        else if (address >= 0xC000 && address <= 0xDFFF){
            // Even
            // IRQ Latch (sets IRQLoad)
            if ((address & 0x1) == 0x0){
                IRQLoad = data;
            }
            // Odd
            // IRQ Reload (sets reload flag)
            else{
                IRQCounter = 0;
                IRQCounterReload = true;
            }
        }
        else if (address >= 0xE000 && address <= 0xFFFF){
            // Even
            // IRQ disable
            if ((address & 0x1) == 0x0){
                IRQEnable = false;
                IRQ = false;
            }
            // Odd
            // IRQ Enable
            else{
                IRQEnable = true;
            }
        }
    }

    @Override
    public int CHRRead(int address) {
        handleIRQCounter(address);

        int returnVal = 0;
        // 0 and 1 are the 2 2kb regions, the rest are the 1KB regions
        int bankNum = (address >> 10) & 0x7;
        int returnAddress = 0;

        // The 2KB banks are a little weird to understand. Lower bit isn't accounted it seems.
        if (!lowerCHRTwoBanks){
            switch (bankNum){
                // 2 2KB
                case 0:
                case 1:
                    returnAddress = (address & 0x7FF)|((CHRBankValues[0] & 0xFE) << 10);
                    break;
                case 2:
                case 3:
                    returnAddress = (address & 0x7FF)|((CHRBankValues[1] & 0xFE) << 10);
                    break;
                // 4 1KB
                case 4:
                    returnAddress = (address & 0x3FF)|(CHRBankValues[2] << 10);
                    break;
                case 5:
                    returnAddress = (address & 0x3FF)|(CHRBankValues[3] << 10);
                    break;
                case 6:
                    returnAddress = (address & 0x3FF)|(CHRBankValues[4] << 10);
                    break;
                case 7:
                    returnAddress = (address & 0x3FF)|(CHRBankValues[5] << 10);
                    break;
            }
        }
        else{
            // Hopefully this is how it's supposed to work?
            switch (bankNum){
                // 4 1KB
                case 0:
                    returnAddress = (address & 0x3FF)|(CHRBankValues[2] << 10);
                    break;
                case 1:
                    returnAddress = (address & 0x3FF)|(CHRBankValues[3] << 10);
                    break;
                case 2:
                    returnAddress = (address & 0x3FF)|(CHRBankValues[4] << 10);
                    break;
                case 3:
                    returnAddress = (address & 0x3FF)|(CHRBankValues[5] << 10);
                    break;
                // 2 2KB
                case 4:
                case 5:
                    returnAddress = (address & 0x7FF)|((CHRBankValues[0] & 0xFE) << 10);
                    break;
                case 6:
                case 7:
                    returnAddress = (address & 0x7FF)|((CHRBankValues[1] & 0xFE) << 10);
                    break;
            }
        }

        returnVal = CHRData[returnAddress & (CHRSize - 1)] & 0xFF;
        return returnVal;
    }

    @Override
    public void CHRWrite(int address, int data) {
        // Probably shouldn't do anything
        handleIRQCounter(address);  // IRQ counter still affected
    }

    @Override
    public int readNameTable(int address, int[] VRAM) {
        // TODO: 4-screen games
        int VRAMAddress = 0;
        // Vertical mirroring
        if (!horizontalMirroring){
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
        // TODO: 4-screen games
        int VRAMAddress = 0;
        // Vertical mirroring
        if (!horizontalMirroring){
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
        return IRQ;
    }

    private void handleIRQCounter(int address){
        // IRQ Counter check
        if (((address & 0x1000) == 0x1000)){
            if (!lastreadA12){
                // Triggers the IRQ counter
                // If reload flag is set, or counter is 0
                if (IRQCounter <= 0 || IRQCounterReload){
                    IRQCounter = IRQLoad;
                    IRQCounterReload = false;
                }
                else{
                    IRQCounter--;
                }
                if (IRQCounter <= 0 && IRQEnable){
                    IRQ = true;
                }
            }
            lastreadA12 = true;
        }
        else{
            lastreadA12 = false;
        }
    }
}
