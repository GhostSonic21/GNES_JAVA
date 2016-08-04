package com.gnes.emu;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class GNES_JAVA extends ApplicationAdapter {
	SpriteBatch batch;
    OrthographicCamera mainCamera;

    Texture frameBuffer;
    AudioDevice device;
    int[] tempAPUoutput;

	// Emulator classes
    Cartridge NESCart;
    Controller NESController;
    APU NESAPU;
    PPU_MMU NESPPUMMU;
    PPU NESPPU;
    CPU_MMU NESMMU;
    CPU NESCPU;

    // Constructors
    public GNES_JAVA(){
        // Blank constructor
    }
    public GNES_JAVA(String romPath){
        NESCart = Cartridge.getCartridge(new FileHandle(romPath));
    }

    // LIBGdx Methods
    @Override
	public void create () {
		batch = new SpriteBatch();
        mainCamera = new OrthographicCamera(256, 240);
        mainCamera.position.set(256/2, 240/2, 0);   // Set camera position to the corner
        mainCamera.update();
        batch.setProjectionMatrix(mainCamera.combined);
        device = Gdx.audio.newAudioDevice(44100, true);

        // Create Emulator Classes
        // If NESCart wasn't created, try loading some generic rom file
        if (NESCart == null) {
            NESCart = Cartridge.getCartridge(Gdx.files.internal("SuperMarioBros.nes"));
        }
        NESAPU = new APU();
        NESController = new Controller();
        NESPPUMMU = new PPU_MMU(NESCart);
        NESPPU = new PPU(NESPPUMMU);
        NESMMU = new CPU_MMU(NESCart, NESPPU, NESController, NESAPU);
        NESCPU = new CPU(NESMMU);
        NESCPU.resetNES();
	}

	@Override
	public void render () {
        // The render method runs 60 times a second for us
		Gdx.gl.glClearColor(1, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        while(!NESPPU.getNewVblank()){
            // Poll controller
            NESController.pollController();
            // Execute CPU
            NESCPU.execInst(NESPPU.NMITriggered(), NESCart.checkIRQ() || NESAPU.checkIRQ());
            // Get final CPU cycle count before triggering PPU step
            int cycles = NESCPU.getLastCycleCount() + NESMMU.getCycleAdditions();
            // Step the APU
            NESAPU.step(cycles);
            // Step the PPU
            NESPPU.step(cycles);

            if (NESAPU.getBufferFilled()){
                // Square lookup
                double[] squarelookup = new double[31];
                for (int i = 1; i < 31; i++){
                    squarelookup[i] = 95.52/((8128/i)+100);
                }
                // Test
                int[] APUBuffer = NESAPU.getOutputTemp();
                float[] testBlock = new float[204];
                //short[] testBlockShort = new short[204];
                int foo = 0;
                for (int i = 0; i < 204; i++){
                    if (APUBuffer[i*20] != 0){
                        //System.out.printf("test");
                        testBlock[i] = (float)squarelookup[APUBuffer[i*20]];
                        //testBlock[i] = ((float)(APUBuffer[i*20]))/100;
                        //testBlockShort[i] = (short)APUBuffer[i*20];
                    }
                }
                device.writeSamples(testBlock, 0, testBlock.length);
            }
        }
        // Render the framebuffer once vblank starts
        frameBuffer = NESPPU.getFrameBuffer();
        batch.begin();
        //batch.draw(frameBuffer, 0, 0, 256*2, 240*2);
        batch.draw(frameBuffer, 0, 0, 256, 240);
        batch.end();
        frameBuffer.dispose();
	}

	@Override
	public void dispose () {
		batch.dispose();
		//img.dispose();
	}
}
