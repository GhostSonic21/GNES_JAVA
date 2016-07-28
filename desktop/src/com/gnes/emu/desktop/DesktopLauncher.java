package com.gnes.emu.desktop;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.gnes.emu.Cartridge;
import com.gnes.emu.GNES_JAVA;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

public class DesktopLauncher {
	public static void main (String[] arg) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		//config.width = 256;
		//config.height = 240;
        config.width = 256*2;
        config.height = 240*2;

        // Handle rom path load
        String romPath = "";
        if (arg.length == 1){
            romPath = arg[0];   // This should be the rom path
        }
        // File chooser if all else
        else {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select NES ROM File");
            FileNameExtensionFilter filter = new FileNameExtensionFilter("NES Rom Files (*.nes)", "nes");
            chooser.setFileFilter(filter);
            int fileChooserReturn = chooser.showOpenDialog(null);
            if (fileChooserReturn == JFileChooser.CANCEL_OPTION) {
                System.out.printf("Cancelled\n");
                System.exit(0);
            } else if (fileChooserReturn == JFileChooser.ERROR_OPTION) {
                System.out.printf("File Error\n");
                System.exit(0);
            } else if (fileChooserReturn == JFileChooser.APPROVE_OPTION) {
                //System.out.printf("Option: %s", chooser.getSelectedFile().getPath());
                romPath = chooser.getSelectedFile().getPath();
            }
        }

        // Create cartridge object and start the LibGDX Program
        Cartridge nesCart = Cartridge.getCartridge(new FileHandle(romPath));
		new LwjglApplication(new GNES_JAVA(nesCart), config);
	}
}
