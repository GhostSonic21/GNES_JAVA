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
    private boolean sweepOverflow;

    // Internal
    private int[] output = new int[4096];
    //private int[] output = new int[1024];
    private int someOutputCounter = 0;
    private boolean bufferFilled;

    // Silly lookup table for handling duty cycles easier
    private final boolean[][] squareDutyLookup =
                    {{false, true, false, false, false, false, false, false},   // 0
                    {false, true, true, false, false, false, false, false},     // 1
                    {false, true, true, true, true, false, false, false},       // 2
                    {true, false, false, true, true, true, true, true}};        // 3

    private final int[] lengthTable = {
            10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60, 10, 14, 12, 26, 14,
            12, 16, 24, 18, 48, 20, 96, 22, 192, 24, 72, 26, 16, 28, 32, 30};

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
            if (timer != 0){
                timer = timer + 1;
            }
            sequencerPointer = (sequencerPointer + 1) & 0x7;
        }


        outputVol = envelopeOutput;
        // 3 Gates
        // Sweep?
        // I dunno how to handle this so placeholder
        if (sweepForcingSilence()){
            //sweepOverflow = false;
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

        output[someOutputCounter] = outputVol;
        someOutputCounter++;
        if (someOutputCounter == 4096){
            bufferFilled = true;
            someOutputCounter = 0;
        }
    }

    @Override
    public void envelopeTick(){
        // Tick envelope out into the mixer
        // ???
        // I'm not in the mood for this
        if (!envelopeStart) {
            // Clock divider
            if (envDivider > 0) {
                envDivider--;
            }
            else {
                envDivider = volume + 1;
                // Clock decay
                if (envDecayVal > 0){
                    envDecayVal--;
                }
                else if (lengthHalt){   // lengthHalt = envelop loop flag
                    envDecayVal = 15;
                }
            }
        }
        else {
            envelopeStart = false;
            envDecayVal = 15;
            envDivider = volume + 1;
        }
        // Check constant volume
        if (constantVol){
            envelopeOutput = volume;
        }
        else {
            envelopeOutput = envDecayVal;
        }
    }

    @Override
    public void sweepTick(){
        // TODO: Probably the sweep unit lol
        if (sweepReload){
            /*if (sweepDividerCounter == 0 && sweepEnable){
                adjustTimer(sweepShiftCount);
            }*/
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
        // TODO: I don't think is implemented right?

        int tempAdder = timerLoad >> amount;
        if (sweepNegate){
            tempAdder = -(tempAdder + 1); // +1 should only be pulse 1 but eh
        }
        int targetPeriod = timerLoad + tempAdder;
        targetPeriod &= 0xFFF;
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

    @Override
    public void lengthTick(){
        if (lengthCounter > 0 && !lengthHalt){
            lengthCounter--;
        }
    }

    @Override
    public void writeData(int address, int data){
        int registerNum = address & 0x3;
        // TODO: Various side effects
        switch (registerNum){
            case 0:
                volume = data & 0xF;
                constantVol = (data & 0x10) == 0x10;
                lengthHalt = (data & 0x20) == 0x20;
                duty = (data >> 6) & 0x3;
                break;
            case 1:
                // TODO
                // Sweep unit shit
                sweepShiftCount = data & 0x7;
                sweepNegate = (data & 0x8) == 0x8;
                sweepDividerPeriod = ((data >> 4) & 0x7);
                sweepEnable = (data & 0x80) == 0x80;
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
                timerLoad = timerLoad | (data & 0x7) << 8;
                int lengthLoad = (data >> 3) & 0x1F;
                if (enabled) {
                    lengthCounter = lengthTable[lengthLoad];
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
    public int[] getOutput(){
        return output;
    }

    @Override
    public boolean getBufferFilled(){
        boolean returnData = bufferFilled;
        bufferFilled = false;
        return returnData;
    }

    @Override
    public void enabled(boolean enabled){
        this.enabled = enabled;
        if (!enabled){
            lengthCounter = 0;
        }
    }
}
