package com.gnes.emu;

/**
 * Created by ghost_000 on 8/5/2016.
 */
public class TriangleWave implements WaveChannel {
    // Registers
    private boolean linearCounterControlFlag;   // Also length counter halt(!)
    private int linearCounterReload;
    private int timerLoad;  // AKA period
    private int lengthCounter;
    private boolean enabled;

    // Invisible reigsters/flag (not exposed to CPU directly)
    private int linearCounter;
    private boolean linearCounterReloadFlag;
    private int outputVol;
    private int timer;
    private int sequencerPointer;

    // Sequence lookup table
    private final int[] sequenceTable = {
        15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0,
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
    };


    public TriangleWave(){
        // Constructor
    }

    @Override
    public void tick() {
        // TODO: Do 0-reading gates 0 the timer out? Or does it just prevent the sequencer from clocking?
        // TODO: edge cases regarding output?

        // Covers weird behavior at super low frequency (Mega Man 2 makes whining sound if this isn't emulated)
        // Is this actually behaving right?
        boolean ultrasonic = false;
        if (timerLoad < 2 && timer == 0){
            ultrasonic = true;
        }

        // Clock timer
        if (timer > 0){
            timer--;
        }
        else{
            timer = timerLoad;
            if (linearCounter != 0 && lengthCounter != 0){
                sequencerPointer = (sequencerPointer + 1) & 0x1F;
            }
        }

        if (!ultrasonic) {
            outputVol = sequenceTable[sequencerPointer];
        }
        else{
            outputVol = 7;  // Technically should be 7.5, so this isn't very
        }
    }

    @Override
    public void halfFrameTick() {
        lengthTick();
    }

    private void lengthTick(){
        // Frame Counter tick
        if (lengthCounter > 0 && !linearCounterControlFlag){    // LinearCounter flag is length halt on triangle
            lengthCounter--;
        }
    }

    @Override
    public void quarterFrameTick() {
        tickLinearCounter();
    }

    private void tickLinearCounter(){
        // Linear Counter tick
        if (linearCounterReloadFlag){
            linearCounter = linearCounterReload;
        }
        else if (linearCounter != 0){
            linearCounter--;
        }
        if (!linearCounterControlFlag){
            linearCounterReloadFlag = false;
        }
    }

    @Override
    public void writeData(int address, int data) {
        int registerNum = address & 0x3;
        switch (registerNum){
            case 0:
                // Linear counter reload value and control flag
                linearCounterReload = data & 0x7F;
                linearCounterControlFlag = (data & 0x80) == 0x80;
                break;
            case 1:
                // Not used, apparently
                break;
            case 2:
                // Timer low
                timerLoad = (timerLoad & 0x700)|(data & 0xFF);
                break;
            case 3:
                // Length counter load, timer high
                timerLoad = (timerLoad & 0xFF)|((data & 0x7)<<8);
                if (enabled){
                    int lengthLoad = (data >> 3) & 0x1F;
                    lengthCounter = APU.lengthTable[lengthLoad];
                }
                linearCounterReloadFlag = true;
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
