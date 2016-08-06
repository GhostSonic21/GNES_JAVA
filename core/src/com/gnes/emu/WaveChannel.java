package com.gnes.emu;

/**
 * Created by ghost_000 on 8/3/2016.
 */
public interface WaveChannel{
    void tick();
    void halfFrameTick();
    void quarterFrameTick();
    void writeData(int address, int data);
    void enabled(boolean enabled);
    int getOutputVol();
    boolean lengthAboveZero();
}