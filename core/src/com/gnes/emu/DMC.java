package com.gnes.emu;

/**
 * Created by ghost_000 on 8/13/2016.
 */
public class DMC implements WaveChannel {
    // DMC members
    private Cartridge cartridge;    // DMC DMA can only access cartridge space, so direct access to cart should suffice
    private boolean IRQ;

    // Registers
    private int timerLoad;
    private boolean loopFlag;
    private boolean IRQEnabled;
    private int sampleAddressLoad = 0x8000;
    private int sampleAddress = 0x8000;
    private int sampleLengthLoad;
    private int sampleLength;

    // Internal
    private int outputVol;
    private int timer;
    private boolean silenced;
    private int shiftRegister;
    private int sampleBuffer;
    private boolean sampleBufferEmpty;
    private int shiftBitsRemaining = 8;    // Bits remaining in sample buffer
    //private int bytesRemaining; // How many bytes are remaining in..?
    private int cycleAdditions;

    private final int rateTable[] = {428, 380, 340, 320, 286, 254, 226, 214, 190, 160, 142, 128, 106, 84, 72, 54};


    // Constructor
    public DMC(Cartridge cartridge){
        this.cartridge = cartridge;
    }

    @Override
    public void tick() {
        // Probably ticked every CPU cycle?
        if (timer > 0){
            timer--;
        }
        else{
            // Timer tick
            timer = timerLoad;  // Probably right?
            if (!silenced){
                if ((shiftRegister & 0x1) == 1 && outputVol <= 125) outputVol += 2;
                else if ((shiftRegister & 0x1) == 0 && outputVol >= 2) outputVol -= 2;
            }
            shiftRegister >>= 1;
            shiftBitsRemaining--;
            // If it's 0 now, we start a new cycle
            if (shiftBitsRemaining == 0){
                shiftBitsRemaining = 8;
                // If the sample buffer is empty, then the silence flag is set;
                // otherwise, the silence flag is cleared x
                // and the sample buffer is emptied into the shift register.
                if (sampleBufferEmpty){
                    silenced = true;
                }
                else{
                    silenced = false;
                    shiftRegister = sampleBuffer;
                }
                sampleBufferEmpty = true;
            }
        }

        // DMA, isn't clocked by timer, but done per tick
        if (sampleLength > 0 && sampleBufferEmpty){
            cycleAdditions += 4; // DMA takes 4 extra CPU cycles
            sampleBuffer = cartridge.PRGRead(sampleAddress);
            sampleBufferEmpty = false;  // No longer empty
            sampleAddress = ((sampleAddress + 1) & 0xFFFF)|0x8000;  // Wraps around to 0x8000
            sampleLength--;

            if (sampleLength == 0){
                if (loopFlag){
                    sampleLength = sampleLengthLoad;
                    sampleAddress = sampleAddressLoad;
                }
                else if (IRQEnabled){
                    IRQ = true; // Assert IRQ
                }
            }
        }
    }

    @Override
    public void halfFrameTick() {
        // Doesn't do anything
    }

    @Override
    public void quarterFrameTick() {
        // Doesn't do anything
    }

    @Override
    public void writeData(int address, int data) {
        int registerNum = address & 0x3;
        switch (registerNum){
            case 0:
                // Flags and rate
                timerLoad = rateTable[data & 0xF] - 1;
                loopFlag = (data & 0x40) == 0x40;
                IRQEnabled = (data & 0x80) == 0x80;
                if (!IRQEnabled){
                    IRQ = false;
                }
                break;
            case 1:
                // Direct load
                //" If the timer is outputting a clock at the same time,
                // the output level is occasionally not changed properly."
                // Probably not gonna emulate that
                outputVol = data & 0x7F;
                break;
            case 2:
                // Sample address
                sampleAddressLoad = 0xC000|(data << 6);
                break;
            case 3:
                // Sample length
                sampleLengthLoad = (data << 4) + 1;
                break;
        }
    }

    @Override
    public void enabled(boolean enabled) {
        if (!enabled){
            sampleLength = 0;
        }
        else{
            if (sampleLength == 0){
                sampleLength = sampleLengthLoad;
                sampleAddress = sampleAddressLoad;
            }
        }
        IRQ = false;
    }

    @Override
    public int getOutputVol() {
        return outputVol;
    }

    @Override
    public boolean lengthAboveZero() {
        // Return true if bytes remaining is more than 0
        return sampleLength > 0;
    }

    // Other public methods

    public int getCycleAdditions(){
        int returnVal = cycleAdditions;
        cycleAdditions = 0;
        return returnVal;
    }

    // Check for a DMC IRQ
    public boolean checkIRQ(){
        return IRQ;
    }

    public void setIRQEnabled(boolean IRQval){
        IRQEnabled = IRQval;
    }
}
