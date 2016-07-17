package com.gnes.emu;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

/**
 * Created by ghost_000 on 7/11/2016.
 */

// Implementing the NES PPU

public class PPU {
    // Vars classes who cares
    private PPU_MMU MMU;
    private int[] OAM;  // OAM Memory
    // Registers
    // I might change how some of these are implemented to be boolean based
    private int latch = 0x00;   // PPU Has an 8-bit latch that's filled with the last byte written or read. Decays on console.

    // PPUCTRL
    private int baseNameTable = 0x0;            // Base nametable, 2 bits, 0 = $2000, 1 = $2400, 2 = $2800, 3 = $2C00
    private boolean VRAMInc = false;            // false add 1 per CPU read/write of PPUDATA, true add 32
    private boolean spriteTable = false;        // Pattern table for 8x8 sprites, ignored for 8x16. 0: $0000, 1: $1000
    private boolean backgroundTable = false;    // Background pattern table, 0: $0000, 1: $1000
    private boolean spriteSize = false;         // False: 8x8, True: 8x16
    private boolean PPUMasterSlave = false;     // This definitely does something with background colors
    private boolean NMIGenerate = false;        // Generate NMI at start of Vertical Blank?

    // PPUMASK
    // TODO: Figure out how the fuck do apply these
    private boolean greyscale = false;          // false: normal, true: produce greyscale (how does this work??)
    private boolean showBGLeft = false;         // true: show background in lefmost 8 pixels, false: don't (wat)
    private boolean showSpriteLeft = false;     // ditto but for sprites
    // If the following 2 are disabled, nothing will be rendered
    private boolean showBG = false;             // Self explanatory
    private boolean showSprites = false;        // ditto
    // This definitely does something
    private boolean emphRed = false;            // Emphasize red
    private boolean emphGreen = false;          // Emphasize green
    private boolean emphBlue = false;           // Emphasize blue

    // Status
    private boolean spriteOverflow = false;     // Set if more than 8 sprites on a line. Known to be glitchy as hell on console
    private boolean spriteZeroHit = false;      // Set if nonzero pixel of bg overlaps with nonzero pixel of sprite
    private boolean vBlank = false;             // Set if  vblank has started. Cleared with status is read and during pre-render

    // OAM Address/Data
    private int OAMADDR = 0x00;                 // OAM Address to write an OAM Byte to

    // PPUScroll
    private int scrollX = 0x00;                 // 8-Bit scroll register
    private int scrollY = 0x00;                 // Shit apparently gets weird when this is between 240 and 255
    private boolean scrollWrite = false;        // False: write to X, True: write to Y. This flips every PPUSCROLL write

    // PPUADDR and PPUDATA
    private int PPUADDR = 0x0000;               // The address of PPUData to write when PPUData recieves data
    private boolean PPUADDRToggle = false;      // false: Upper byte, true: lower. Toggles on write

    // PPUData read buffer
    int PPUDataReadBuffer = 0x00;





    // Internal stuff
    int lineCount = -1;     // Tracking what line the PPU is apparently on
    int cycleCount = 0;     // Track amount of cycles we've gone through
    /*final int pallete[] = { // RGB Color Pallete for NES
            0x5C5C5C, 0x002267, 0x131280, 0x2E067E, 0x460060, 0x530231, 0x510A02, 0x411900, 0x282900, 0x0D3700, 0x003E00, 0x003C0A, 0x00313B, 0x000000, 0x000000, 0x000000,
            0xA7A7A7, 0x1E55B7, 0x3F3DDA, 0x662BD6, 0x8822AC, 0x9A246B, 0x983225, 0x814700, 0x5D5F00, 0x367300, 0x187D00, 0x097A32, 0x0B6B79, 0x000000, 0x000000, 0x000000,
            0xFEFFFF, 0x6AA7FF, 0x8F8DFF, 0xB979FF, 0xDD6FFF, 0xF172BE, 0xEE8173, 0xD69837, 0xB0B218, 0x86C71C, 0x64D141, 0x52CE81, 0x54BECD, 0x454545, 0x000000, 0x000000,
            0xFEFFFF, 0xC0DAFF, 0xD0CFFF, 0xE2C6FF, 0xF1C2FF, 0xF9C3E4, 0xF8CAC4, 0xEED4A9, 0xDEDF9B, 0xCCE79D, 0xBDECAE, 0xB5EACA, 0xB6E4EA, 0xB0B0B0, 0x000000, 0x000000
    };*/
    // RGBA8888 Format
    final int palette[] = { // RGB Color Pallete for NES
            0x5C5C5CFF, 0x002267FF, 0x131280FF, 0x2E067EFF, 0x460060FF, 0x530231FF, 0x510A02FF, 0x411900FF, 0x282900FF, 0x0D3700FF, 0x003E00FF, 0x003C0AFF, 0x00313BFF, 0x000000FF, 0x000000FF, 0x000000FF,
            0xA7A7A7FF, 0x1E55B7FF, 0x3F3DDAFF, 0x662BD6FF, 0x8822ACFF, 0x9A246BFF, 0x983225FF, 0x814700FF, 0x5D5F00FF, 0x367300FF, 0x187D00FF, 0x097A32FF, 0x0B6B79FF, 0x000000FF, 0x000000FF, 0x000000FF,
            0xFEFFFFFF, 0x6AA7FFFF, 0x8F8DFFFF, 0xB979FFFF, 0xDD6FFFFF, 0xF172BEFF, 0xEE8173FF, 0xD69837FF, 0xB0B218FF, 0x86C71CFF, 0x64D141FF, 0x52CE81FF, 0x54BECDFF, 0x454545FF, 0x000000FF, 0x000000FF,
            0xFEFFFFFF, 0xC0DAFFFF, 0xD0CFFFFF, 0xE2C6FFFF, 0xF1C2FFFF, 0xF9C3E4FF, 0xF8CAC4FF, 0xEED4A9FF, 0xDEDF9BFF, 0xCCE79DFF, 0xBDECAEFF, 0xB5EACAFF, 0xB6E4EAFF, 0xB0B0B0FF, 0x000000FF, 0x000000FF
    };

    boolean NMI = false;        // Internal flag to check if NMI should be triggered
    boolean newVblank = false;  // Mostly using this for speed control
    Pixmap frameBuffer = new Pixmap(256, 240, Pixmap.Format.RGBA8888);    // Create RGB888 pixmap for the framebuffer
    //Pixmap frameBuffer = null;

    // Constructor
    public PPU(PPU_MMU MMU){
        this.MMU = MMU;
        OAM = new int[0x100];
    }

    // PPU Step with how many cycles to step
    public void step(int cycles){
        // 1 CPU Cycle = 3 PPU cycles
        int PPUCycles = cycles * 3;
        //int PPUCycles = cycles;
        cycleCount += PPUCycles;
        // TODO: Actual rendering, Sprite 0, Other cycle-based stuff
        // Next line cycle
        if (cycleCount > 340){
            cycleCount -= 341;
            //cycleCount = 0;
            lineCount++;

            // SUPER MARIO BROS SPRITE 0 HACK (Don't use this except for testing!!!)
            /*if (lineCount == 30){
                // Super Mario Bros. splits the status bar portion of the screen by waiting until
                // there's a Sprite 0 hit on line 30 and then changing the scroll register. This hack is necessary
                // For execution until we have proper Sprite 0 hit support. This might break some games so don't use it.
                spriteZeroHit = true;
            }*/

            // Vblank trigger
            if (lineCount == 241){
                // Trigger NMI as soon as vblank reached
                vBlank = true;
                newVblank = true;
                NMI = NMIGenerate;
                // TODO: replace this rendering method
                drawNameTable0();   // Temporary way of rendering just so we can have some output
                drawSprites();      // Temp sprite draw
            }
            // Reset line count
            if (lineCount > 260){
                lineCount = -1;
                vBlank = false;
                spriteZeroHit = false;
            }
        }


        // PPU Access safe on 240, but 241 is technically vblank
        /*else if (lineCount >= 240){
            // VBLANK stuff
        }*/
    }

    public void writeRegister(int address, int data){
        latch = data;
        // Address should only be coming in as a nibble, or 0x8 for oam
        switch (address){
            // PPUCTRL
            case 0x0:{
                // Writable
                baseNameTable = data & 0x3;
                VRAMInc = (data & 0x4) > 0;
                spriteTable = (data & 0x8) > 0;
                backgroundTable = (data & 0x10) > 0;
                spriteSize = (data & 0x20) > 0;
                PPUMasterSlave = (data & 0x40) > 0;
                NMIGenerate = (data & 0x80) > 0;
                // NMI triggered on register write if vBlank is in progress during this.
                if(NMIGenerate && (lineCount > 240)){
                    NMI = true;
                }
                break;
            }
            // PPUMASK
            case 0x1:{
                // Writable
                greyscale = (data & 0x1) > 0;
                showBGLeft = (data & 0x2) > 0;
                showSpriteLeft = (data & 0x4) > 0;
                showBG = (data & 0x8) > 0;
                showSprites = (data & 0x10) > 0;
                emphRed = (data & 0x20) > 0;
                emphGreen = (data & 0x40) > 0;
                emphBlue = (data & 0x80) > 0;
                break;
            }
            // PPUSTATUS
            case 0x2:{
                // Readable
                // Ignore
                break;
            }
            // OAMADDR
            case 0x3:{
                // Writable
                // Upper
                OAMADDR = data & 0xFF;
                break;
            }
            // OAMDATA
            case 0x4:{
                // Both
                OAM[OAMADDR] = data;
                OAMADDR = (OAMADDR + 1) & 0xFF;
                break;
            }
            // PPUSCROLL
            case 0x5:{
                // Write (x2)
                // X
                if (!scrollWrite) {
                    scrollX = data & 0xff;
                }
                // Y
                else{
                    scrollY = data & 0xff;
                }
                // Togle scrollWrite
                scrollWrite = !scrollWrite;
                break;
            }
            // PPUADDR
            case 0x6:{
                // Write (2)
                // High byte
                if (!PPUADDRToggle){
                    PPUADDR = (PPUADDR & 0x00FF)|(data << 8);
                }
                // Low byte
                else{
                    PPUADDR = (PPUADDR & 0xFF00)|(data);

                }
                // Toggle
                PPUADDRToggle = !PPUADDRToggle;
                break;
            }
            case 0x7:{
                // Both
                MMU.writeByte(PPUADDR, data);
                PPUADDR = (PPUADDR + (VRAMInc ? 32:1)) & 0xFFFF;
                break;
            }
            // OAM
            case 0x8:{
                // Writable
                // TODO Figure out how I'm gonna apporach this
                break;
            }
        }
    }

    public int readRegister(int address){
        int returnByte = latch;
        // TODO: Proper read implementation
        switch (address){
            case 0x2:{
                returnByte = latch & 0x1F;  // First 5 bytes of latch are here for some reason
                returnByte = returnByte|((spriteOverflow ? 1:0) << 5)|((spriteZeroHit ? 1:0) << 6)|
                        ((vBlank ? 1:0) << 7);
                vBlank = false; //Cleared after a read apparently
                break;
            }
            case 0x4:{
                returnByte = OAM[OAMADDR];
                break;
            }
            case 0x7:{
                // Regions before palette return from a buffer that's only updated on reads
                if (PPUADDR < 0x3F00){
                    returnByte = PPUDataReadBuffer;
                    PPUDataReadBuffer = MMU.readByte(PPUADDR);
                }
                // Palette data comes directly however
                else {
                    returnByte = MMU.readByte(PPUADDR);
                    // Buffer contains mirrored nametable byte instead
                    PPUDataReadBuffer = MMU.readByte(address & 0x2F1F);
                }
                // Incremented all the same
                PPUADDR = (PPUADDR + (VRAMInc ? 32:1)) & 0xFFFF;
                break;
            }
        }
        return returnByte;
    }

    public void OAMDMA(int[] OAMData){
        // Used specifically for handling DMA requests to PPU OAM
        for (int i = 0; i < 256; i++){
            OAM[i] = OAMData[i];
        }
    }

    public boolean NMITriggered(){
        // Return if NMI should be triggered
        boolean returnData = NMI;
        NMI = false;
        return returnData;
    }

    public boolean getNewVblank(){
        boolean returnData = newVblank;
        if (newVblank){
            newVblank = false;
        }
        return returnData;
    }

    public Texture getFrameBuffer(){
        // Testing pixmap feature
        /*for (int i = 0; i < 240; i++){
            for (int j = 0; j < 256; j++){
                frameBuffer.drawPixel(j, i, 0xBCDFFF);
            }
        }*/
        //int[] sevenData = {0xFE,0xC6,0x0C,0x18,0x30,0x30,0x30,0x00,0xFE,0xC6,0x0C,0x18,0x30,0x30,0x30,0x00};
        /*int[] sevenData = {0x00,0x7F,0x7F,0x78,0x73,0x73,0x73,0x7F,0x7F,0x80,0xA0,0x87,0x8F,0x8E,0x8E,0x86};
        //int[] tempPalleteData = {0xFFFFFFFF, 0xFF808080, 0xFF404040, 0xFF000000};
        //int[] tempPalleteData = {0xFF000000, 0xFF404040, 0xFF808080, 0xFFFFFFFF};
        int[] tempPalleteData = {0x000000FF, 0x404040FF, 0x808080FF, 0xFFFFFFFF};

        Pixmap seven = new Pixmap (8, 8, Pixmap.Format.RGB888);
        for (int i = 0; i < 8; i++){
            int tempByte = sevenData[i];
            int tempByte2 = sevenData[i+8];
            for (int j = 0; j < 8; j ++){
                int tempByte3 = (tempByte & 0x1)|((tempByte2 & 0x1)<<1);
                seven.drawPixel(7 - j, i, tempPalleteData[tempByte3]);
                tempByte >>= 1;
                tempByte2 >>= 1;
            }
        }
        Texture sevenTex = new Texture(seven);
        seven.dispose();
        return sevenTex;    // Return a texture created from the pixmap
        */
        return new Texture(frameBuffer);
    }

    public void drawSprites(){
        // Draws all sprites in the OAM Table without limit.
        // Because I'm too lazy and this is a lazy way of rendering
        // TODO: Don't do this
        // TODO: 8x16
        for (int i = 0; i < 64; i++){
            // Gather sprite data
            int byte0 = OAM[(i * 4) + 0];   // Y-Position - 1
            int byte1 = OAM[(i * 4) + 1];   // Index for 8x8. Encoded different for 8x16
            int byte2 = OAM[(i * 4) + 2];   // Attributes
            int byte3 = OAM[(i * 4) + 3];   // X-Position
            // Break if the sprite is supposed to be hidden

            if (byte0 < 0xEF || byte3 < 0xF9) {
                // TODO: Fix tile gathering (Why is this a todo what does this even mean)
                // the 0x4 or'd in indicates it's a sprite palette
                Pixmap sprite = drawTile(byte1, spriteTable ? 1:0, 0x4 | (byte2 & 0x3), (byte2 & 0x40) > 0, (byte2 & 0x80) > 0);
                // Draw to framebuffer
                frameBuffer.drawPixmap(sprite, byte3, byte0 + 1);
                // Debug draw thing
                //frameBuffer.drawPixmap(sprite, ((i%32) * 8), (i/32)*8);
                // Dispose when we're done
                sprite.dispose();
            }
        }
    }

    public void drawNameTable0(){
        // Incredibly method that draws the contents of nametable 0 to the universal framebuffer
        // No sprites or scrolling
        // Fill framebuffer with universal background color
        frameBuffer.setColor(palette[MMU.readByte(0x3F00) & 0x3F]);
        // Obnoxious debug red
        //frameBuffer.setColor(0xFF0000FF);
        frameBuffer.fill();

        // Draw tiles into framebuffer
        for (int j = 0; j < 30; j++){
            for (int i = 0; i < 32; i++){
                int attributeByte = 0x23C0 + ((i/4) + ((j/4) * 8));
                int cornerNum = (((i/2)%2) + (((j/2)%2)*2))*2;
                int attribute = (MMU.readByte(attributeByte) >> cornerNum) & 0x3;
                //int attribute = ((MMU.readByte(0x23C0 + (i/4))) >> ((i%4)*2)) & 0x3;
                Pixmap tempTile = drawTile(MMU.readByte(0x2000 + i + (j*32)), backgroundTable ? 1:0, attribute, false, false); // Hard coded pattern for now I guess
                frameBuffer.drawPixmap(tempTile, i*8, j*8);
                tempTile.dispose();
            }
        }
    }

    public Pixmap drawTile(int tileNum, int patternTableNum, int attribute, boolean xFlip, boolean yFlip){
        // return a pixmap of a tile.
        // Tilenum: Number of tile
        // patternTableNum: Which pattern table it's from
        // attribute: upper 2 bits to apply to this tile
        Pixmap tempMap = new Pixmap(8,8,Pixmap.Format.RGBA8888);
        int tileAddress = (patternTableNum << 12)|(tileNum << 4);
        for (int j = 0; j < 8; j++){
            int tempByte = MMU.readByte(tileAddress + j);
            int tempByte2 = MMU.readByte(tileAddress + j + 8);;
            for (int i = 0; i < 8; i++){
                int tempByte3 = (tempByte & 0x1)|((tempByte2 & 0x1)<<1)|(attribute << 2);
                int paletteValue = palette[MMU.readByte(0x3F00+tempByte3) & 0x3F];
                if ((tempByte3 & 0x3) != 0) {
                    //tempMap.drawPixel(7 - i, j, paletteValue);
                    tempMap.drawPixel(xFlip ? i:7-i, yFlip ? 7-j:j, paletteValue);
                }
                tempByte >>= 1;
                tempByte2 >>= 1;
            }
        }
        return tempMap;
    }
}
