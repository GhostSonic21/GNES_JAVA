package com.gnes.emu.desktop;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.gnes.emu.GNES_JAVA;

public class DesktopLauncher {
	public static void main (String[] arg) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		//config.width = 256;
		//config.height = 240;
        config.width = 256*2;
        config.height = 240*2;
		new LwjglApplication(new GNES_JAVA(), config);
	}
}
