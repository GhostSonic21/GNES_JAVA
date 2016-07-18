package com.gnes.emu;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class GNES_JAVA extends ApplicationAdapter {
	SpriteBatch batch;
	Texture img;
    OrthographicCamera mainCamera;

    Texture frameBuffer;
	// Emulator classes
    Cartridge NESCart;
    Controller NESController;
    PPU_MMU NESPPUMMU;
    PPU NESPPU;
    CPU_MMU NESMMU;
    CPU NESCPU;
	
	@Override
	public void create () {
		batch = new SpriteBatch();
        mainCamera = new OrthographicCamera(256, 240);
        mainCamera.position.set(256/2, 240/2, 0);   // Set camera position to the corner
        mainCamera.update();
        batch.setProjectionMatrix(mainCamera.combined);

        // Create Emulator Classes
        //NESCart = new Cartridge(Gdx.files.internal("./instr_test-v5/rom_singles/16-special.nes"));
        NESCart = new Cartridge(Gdx.files.internal("DonkeyKong.nes"));
        NESController = new Controller();
        NESPPUMMU = new PPU_MMU(NESCart);
        NESPPU = new PPU(NESPPUMMU);
        NESMMU = new CPU_MMU(NESCart, NESPPU, NESController);
        NESCPU = new CPU(NESMMU);
        NESCPU.resetNES();
        //img = new Texture("badlogic.jpg");
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
            NESCPU.execInst(NESPPU.NMITriggered(), false);
            // Get final CPU cycle count before triggering PPU step
            int cycles = NESCPU.getLastCycleCount() + NESMMU.getCycleAdditions();
            // Step the PPU
            NESPPU.step(cycles);
        }
        // Render the framebuffer once vblank starts
        frameBuffer = NESPPU.getFrameBuffer();
        batch.begin();
        //batch.draw(frameBuffer, 0, 0, 256*2, 240*2);
        batch.draw(frameBuffer, 0, 0, 256, 240);
        batch.end();
        frameBuffer.dispose();
		/*batch.begin();
		batch.draw(img, 0, 0);
		batch.end();*/
	}
	
	@Override
	public void dispose () {
		batch.dispose();
		//img.dispose();
	}
}
