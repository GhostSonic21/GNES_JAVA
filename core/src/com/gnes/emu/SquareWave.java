package com.gnes.emu;

/**
 * Created by ghost_000 on 8/3/2016.
 */
public class SquareWave implements WaveChannel{
    // Registers
    private boolean enabled;
    private int duty;
    private boolean lengthHalt;
    private boolean constantVol;
    private int volume;
    private int timer;  // AKA the period
    private int timerLoad;  // Loaded into the timer
    private int lengthCounter;
    private int sequencerPointer;
    private int outputVol;

    // Envelope stuff?
    private boolean envelopeStart;  // Envelope start flag
    private int envDivider;
    private int envDecayVal;
    private int envelopeOutput;

    // Sweep bs
    private boolean sweepEnable;
    private int sweepDividerPeriod;
    private boolean sweepNegate;
    private int sweepShiftCount;
    private boolean sweepReload;
    private int sweepDividerCounter;

    // Internal
    // Silly lookup table for handling duty cycles easier
    private final boolean[][] squareDutyLookup =
                    {{false, true, false, false, false, false, false, false},   // 0 12.5%
                    {false, true, true, false, false, false, false, false},     // 1 25%
                    {false, true, true, true, true, false, false, false},       // 2 50%
                    {true, false, false, true, true, true, true, true}};        // 3 75% (same sound as 25%)


    // Constructor
    public SquareWave(){
        //
    }

    // General tick
    @Override
    public void tick(){
        if (timer > 0){
            timer--;
        }
        else{
            timer = timerLoad;
            sequencerPointer = (sequencerPointer + 1) & 0x7;
        }

        // Check constant volume here
        if (constantVol){
            outputVol = volume;
        }
        else {
            outputVol = envelopeOutput;
        }
        // 3 Gates
        // Sweep
        if (sweepForcingSilence()){
            outputVol = 0;
        }
        // Sequencer
        if (!squareDutyLookup[duty][sequencerPointer]){
            outputVol = 0;
        }
        // Length Counter
        if (lengthCounter == 0) {
            outputVol = 0;
        }
    }

    @Override
    public void halfFrameTick(){
        lengthTick();
        sweepTick();
    }

    @Override
    public void quarterFrameTick(){
        envelopeTick();
    }

    private void envelopeTick(){
        // Tick envelope
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

    private void sweepTick(){
        if (sweepReload){
            if (sweepDividerCounter == 0 && sweepEnable){
                adjustTimer(sweepShiftCount);
            }
            sweepDividerCounter = sweepDividerPeriod;
            sweepReload = false;
        }
        else if (sweepDividerCounter > 0){
            sweepDividerCounter--;
        }
        else {
            sweepDividerCounter = sweepDividerPeriod;
            if (sweepEnable) {
                adjustTimer(sweepShiftCount);
            }
        }
    }

    private void adjustTimer(int amount){
        int tempAdder = timerLoad >> amount;
        if (sweepNegate){
            tempAdder = -(tempAdder + 1); // +1 should only be pulse 1 but eh
        }
        int targetPeriod = timerLoad + tempAdder;
        targetPeriod &= 0x7FF;
        if (sweepEnable && !sweepForcingSilence()){
            timerLoad = targetPeriod;
        }
    }

    // Checks if a conidition is causing sweep to force a silent output
    private boolean sweepForcingSilence(){
        boolean returnVal;
        if (timerLoad < 8){
            returnVal = true;
        }
        else if(!sweepNegate && timerLoad + (timerLoad >> sweepShiftCount) > 0x7FF){
            returnVal = true;
        }
        else{
            returnVal = false;
        }

        return returnVal;
    }

    private void lengthTick(){
        if (lengthCounter > 0 && !lengthHalt){
            lengthCounter--;
        }
    }

    @Override
    public void writeData(int address, int data){
        int registerNum = address & 0x3;
        switch (registerNum){
            case 0:
                volume = data & 0xF;
                constantVol = (data & 0x10) == 0x10;
                lengthHalt = (data & 0x20) == 0x20;
                duty = (data >> 6) & 0x3;
                break;
            case 1:
                // Sweep unit values
                sweepShiftCount = data & 0x7;
                sweepNegate = (data & 0x8) == 0x8;
                sweepDividerPeriod = ((data >> 4) & 0x7);
                sweepEnable = (data & 0x80) == 0x80 && sweepShiftCount != 0;
                sweepReload = true;
                break;
            case 2:
                // timer low
                timerLoad = timerLoad & 0x700;
                timerLoad |= data;
                break;
            case 3:
                // length load(?), timer high
                timerLoad = timerLoad & 0xFF;
                timerLoad = timerLoad | ((data & 0x7) << 8);
                int lengthLoad = (data >> 3) & 0x1F;
                if (enabled) {
                    lengthCounter = APU.lengthTable[lengthLoad];
                }
                // Other?
                sequencerPointer = 0;
                timer = timerLoad;
                //lengthCounter = lengthLoad;
                envelopeStart = true;   // sets start on envelope
                break;
        }
    }

    @Override
    public void enabled(boolean enabled){
        this.enabled = enabled;
        if (!enabled){
            lengthCounter = 0;
        }
    }

    @Override
    public int getOutputVol(){
        return outputVol;
    }

    @Override
    public boolean lengthAboveZero(){
        return lengthCounter > 0;
    }
}
