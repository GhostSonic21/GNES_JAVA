package com.gnes.emu;

/**
 * Created by ghost_000 on 8/3/2016.
 */
public interface WaveChannel{
    void tick();
    void envelopeTick();
    void sweepTick();
    void lengthTick();
    void writeData(int address, int data);
    int[] getOutput();
    boolean getBufferFilled(); // Probably change this
    void enabled(boolean enabled);
}