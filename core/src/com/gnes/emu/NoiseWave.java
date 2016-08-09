package com.gnes.emu;

/**
 * Created by ghost_000 on 8/7/2016.
 */
public class NoiseWave implements WaveChannel {
    // Registers
    private boolean enabled;
    private boolean lengthHalt;
    private boolean mode;
    private int timer;
    private int timerLoad;
    private int lengthCounter;
    private int shiftRegister = 1;  // Must be initialized or you won't have any sound


    // Envelope
    // TODO: Probably seperate envelope into separate class of some sort, instead of copy-pasting this dumb code.
    private boolean constantVol;
    private boolean envelopeStart;  // Envelope start flag
    private int volume;
    private int envDivider;
    private int envDecayVal;
    private int envelopeOutput;

    // Other invisible stuff
    private int outputVol;

    // Timer load works on a lookup table in the noise channel
    private final int[] timerTable = {
        4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068,
    };

    public NoiseWave(){
        //
    }

    @Override
    public void tick() {
        // More of the same
        if (timer > 0){
            timer--;
        }
        else{
            timer = timerLoad;
            // Clock shift register thing
            int feedback = (shiftRegister & 0x1) ^ (mode ? ((shiftRegister >> 6) & 0x1):((shiftRegister >> 1) & 0x1));
            shiftRegister >>= 1;
            shiftRegister = (shiftRegister) | (feedback << 14);
        }
        if ((shiftRegister & 0x1) != 0 || lengthCounter == 0){
            outputVol = 0;
        }
        else{
            // check constant volume here
            if (constantVol){
                outputVol = volume;
            }
            else {
                outputVol = envelopeOutput;
            }
        }
    }

    @Override
    public void halfFrameTick() {
        lengthTick();

    }

    private void lengthTick(){
        if (lengthCounter >0 && !lengthHalt){
            lengthCounter--;
        }
    }

    @Override
    public void quarterFrameTick() {
        envelopeTick();
    }

    private void envelopeTick(){
        // Tick envelope out into the mixer
        // ???
        // I'm not in the mood for this
        if (!envelopeStart) {
            // Clock divider
            if (envDivider > 0) {
                envDivider--;
            }
            else {
                envDivider = volume;
                // Clock decay
                if (envDecayVal > 0){
                    envDecayVal--;
                }
                else if (lengthHalt){   // lengthHalt = envelop loop flag
                    envDecayVal = 15;
                }
            }
        }
        // if started
        else {
            envelopeStart = false;
            envDecayVal = 15;
            envDivider = volume; // NESDev makes a weird comment about (the period becomes V + 1). Doesn't look right
        }

        // Set the output
        envelopeOutput = envDecayVal;
    }

    @Override
    public void writeData(int address, int data) {
        int registerNum = address & 0x3;
        switch (registerNum){
            case 0:
                // Envelope stuff (volume, constantvol and lengthHalt flags)
                volume = data & 0xF;
                constantVol = (data & 0x10) == 0x10;
                lengthHalt = (data & 0x20) == 0x20;
                break;
            case 1:
                // Nothing
                break;
            case 2:
                // Mode and timerLoad (from a table)
                mode = (data & 0x80) == 0x80;
                timerLoad = timerTable[data & 0xF];
                break;
            case 3:
                // Length counter load, restarts envelope as well
                envelopeStart = true;
                if (enabled){
                    int lengthLoad = (data >> 3) & 0x1F;
                    lengthCounter = APU.lengthTable[lengthLoad];
                }
                break;
        }
    }

    @Override
    public void enabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled){
            lengthCounter = 0;
        }
    }

    @Override
    public int getOutputVol() {
        return outputVol;
    }

    @Override
    public boolean lengthAboveZero() {
        return lengthCounter > 0;
    }
}
