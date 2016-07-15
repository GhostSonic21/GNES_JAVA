package com.gnes.emu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

/**
 * Created by ghost_000 on 7/6/2016.
 */

// Reserved for testing class functions and what not

public class Testers {
    public static void main(String[] args){
        //Cartridge tester = new Cartridge(Gdx.files.internal("./instr_test-v5/official_only.nes"));
        //Cartridge tester = new Cartridge(new FileHandle("./instr_test-v5/rom_singles/01-basics.nes"));
        //Cartridge tester = new Cartridge(new FileHandle("./instr_test-v5/rom_singles/03-immediate.nes"));
        //Cartridge tester = new Cartridge(new FileHandle("./instr_test-v5/rom_singles/13-rts.nes"));
        //Cartridge tester = new Cartridge(new FileHandle("./instr_test-v5/rom_singles/15-brk.nes"));
        Cartridge tester = new Cartridge(new FileHandle("./DonkeyKong.nes"));
        PPU_MMU PPU_MMU = new PPU_MMU(tester);
        PPU PPU = new PPU(PPU_MMU);
        CPU_MMU MMU  = new CPU_MMU (tester, PPU, new Controller());
        CPU CPU = new CPU(MMU);

        CPU.resetNES();
        //CPU.testOpcode(0xD8);
        while (true){
            CPU.execInst(PPU.NMITriggered(), false);
            PPU.step(CPU.getLastCycleCount());
            //CPU.execInst(false, false);
        }
    }
}
