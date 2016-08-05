package com.gnes.emu;

/**
 * Created by ghost_000 on 8/1/2016.
 */
public class APU {
    // Classes, variables, etc.
    // Universal registers
    private int frameCounter;
    private int frameStep;
    // Flags
    private boolean[] channelEnable = new boolean[5];    // 0: Pulse1, 1: Pulse2, 2: Triangle, 3: Noise, 4: DMC
    private boolean DMCActive;
    private boolean frameInterrupt;
    private boolean DMCInterrupt;
    private boolean fcMode;             // False: 4-step, True: 5-step
    private boolean IRQInhibit;

    // Internal
    private int APUCycleCount;
    private boolean oddCPUcycle;
    private boolean IRQInterrupt;
    private WaveChannel[] channels;
    private boolean bufferFilled;



    public APU(){
        // Constructor
        // Init waveChannels
        channels = new WaveChannel[5];
        channels[0] = new SquareWave();
        channels[1] = new SquareWave();
        // TODO: rest
    }

    public int recieveData(int address){
        int returnData = 0;
        if (address == 0x4015){
            // TODO
            // Return status bits
        }
        else {
            System.err.printf("Invalid APU address 0x%x. Figure out why.\n", address);
        }

        return returnData;
    }

    public void writeData(int address, int data){
        if (address < 0x4015){
            int channelNum = (address >> 2) & 0xF;
            if (channels[channelNum] != null){
                channels[channelNum].writeData(address, data);
            }
        }
        else if (address == 0x4015){
            // Enable bits
            // Serially
            for (int i = 0; i < 5; i++){
                channelEnable[i] = (data & 0x1) == 1;
                if (channels[i] != null){
                    channels[i].enabled(channelEnable[i]);
                }
                data >>= 1;
            }
        }
        else if (address == 0x4017){
            // Frame counter registers
            IRQInhibit = (data & 0x40) == 0x40;
            fcMode = (data & 0x80) == 0x80;
            frameStep = 0;
            APUCycleCount = 0;
            if (fcMode){
                quarterTick();
                halfTick();
            }
        }
        else {
            System.err.printf("Invalid APU write address 0x%x. Figure out why.\n", address);
        }
        return;
    }

    public void step(int CPUcycles){
        // Step
        // 2 CPU Cycles = 1 APU cycle
        // TODO: frame IRQ
        while (CPUcycles-- > 0){
            oddCPUcycle = !oddCPUcycle;
            if (oddCPUcycle){
                // This is the second CPU Cycle, so perform APU functions
                // Increment counter
                APUCycleCount++;
                // Tick all channels
                for (int i = 0; i < 5; i++){
                    if (channels[i] != null){
                        channels[i].tick();
                    }
                }
                // Stupid sequencer shit
                if ((APUCycleCount % 3728) == 0){
                    // Step
                    frameStep++;
                    // 4-step
                    if (fcMode == false) {
                        // 120 Hz stuff
                        if ((frameStep % 2) == 0) {
                            halfTick();
                        }
                        // 240 hz stuff
                        // TODO: Triangle linear counter
                        quarterTick();

                        // 60 Hz reset
                        // TODO: IRQ
                        if (frameStep == 4) {
                            frameStep = 0;
                            APUCycleCount = 0;
                        }
                    }

                    // 5-step
                    else{
                        // TODO: Improve this
                        switch (frameStep){
                            case 1:
                                quarterTick();
                                break;
                            case 2:
                                quarterTick();
                                halfTick();
                                break;
                            case 3:
                                quarterTick();
                                break;
                            case 4:
                                break;
                            case 5:
                                quarterTick();
                                halfTick();
                                frameStep = 0;
                                APUCycleCount = 0;
                                break;

                        }
                    }
                }
            }
        }

        return;
    }

    public boolean checkIRQ(){
        // TODO Frame Counter IRQ
        boolean returnVal = IRQInterrupt;
        IRQInterrupt = false;
        return returnVal;
    }

    private void quarterTick(){
        channels[0].envelopeTick();
        channels[1].envelopeTick();
    }

    private void halfTick(){
        for (int i = 0; i < 4; i++) {
            if (channels[i] != null) {
                channels[i].lengthTick();
                channels[i].sweepTick();
            }
        }
    }

    public boolean getBufferFilled(){
        boolean returnData = channels[0].getBufferFilled();
        //bufferFilled = false;
        return returnData;
    }

    public int[] getOutputTemp(){
        int[] returnArray = new int[channels[0].getOutput().length];
        for (int i = 0; i < returnArray.length; i++){
            returnArray[i] = channels[0].getOutput()[i] + channels[1].getOutput()[i];
        }
        return returnArray;
    }
}