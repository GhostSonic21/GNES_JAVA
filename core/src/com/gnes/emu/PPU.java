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
    private boolean tallSprite = false;         // False: 8x8, True: 8x16
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

    // PPUData read buffer
    int PPUDataReadBuffer = 0x00;


    // Loopy's scroll registers
    int loopyV = 0;         // Current VRAM address (15-bit)
    int loopyT = 0;         // Temporary VRAM address (15-bit)
    int loopyX = 0;         // Fine X Scroll (3-bit)
    boolean loopyW = false; // Write toggle



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

    boolean NMI = false;        // NMI flag
    boolean newVblank = false;  // Mostly using this for speed control
    Pixmap frameBuffer = new Pixmap(256, 240, Pixmap.Format.RGBA8888);    // Create RGB888 pixmap for the framebuffer
    int lineBuffer[] = new int[256];
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
        // TODO: Other cycle-based stuff
        // Next line cycle
        while (PPUCycles-- > 0) {
            cycleCount++;
            if (cycleCount == 256){
                if ((showBG || showSprites) && lineCount < 240) {
                    incrementLoopyY();
                }
            }
            else if (cycleCount == 257){
                // Copy horizontal bits each scanline
                if ((showBG || showSprites) && lineCount < 240) {
                    loopyV = (loopyV & 0xFBE0) | (loopyT & 0x041F);
                }
            }
            else if (cycleCount == 304){
                // Copy vertical bits during pre-render line
                // Copying is meant to be partially done, so this is inaccurate
                if ((showBG || showSprites) && lineCount == -1) {
                    loopyV = (loopyV & 0x41F)|(loopyT & 0xFBE0);
                    //loopyV = loopyT;
                }
            }
            /* Probably don't need this for now since scanlies are rendered all at once
            else if (cycleCount == 328){
                if (showBG || showSprites) {
                    for (int i = 0; i < 32; i++) {
                        //incrementLoopyX();
                    }
                }
            }*/
            else if (cycleCount > 340) {
                cycleCount = 0;
                lineCount++;
                if (lineCount < 240) {
                    // Zero out line buffer
                    for (int i = 0; i < 256; i++) {
                        lineBuffer[i] = 0;
                    }
                    // Attempt with loopy's data
                    if (showBG) {
                        boolean baseNameTableX = (loopyV & 0x400) == 0x400;
                        boolean baseNameTableY = (loopyV & 0x800) == 0x800;
                        int loopyXscroll = (((loopyV & 0x1F) << 3)|loopyX) + ((baseNameTableX) ? 256 : 0);
                        int loopyYscroll = (((loopyV >> 12) & 0x7)|(((loopyV >> 5) & 0x7) << 3)|
                                (((loopyV >> 8) & 0x3) << 6)) + (baseNameTableY ? 240:0);
                        drawBGScanLine(loopyXscroll, loopyYscroll, lineCount);
                    }
                    if (showSprites) {
                        drawSpriteLine(lineCount);
                    }
                    renderLine(lineCount);
                }

                // Vblank trigger
                if (lineCount == 241) {
                    // Trigger NMI as soon as vblank reached
                    vBlank = true;
                    newVblank = true;
                    NMI = NMIGenerate;
                }
                // Reset line count
                if (lineCount > 260) {
                    lineCount = -1;
                    vBlank = false;
                    spriteZeroHit = false;
                    spriteOverflow = false;
                    NMI = false;
                }
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
                tallSprite = (data & 0x20) > 0;
                PPUMasterSlave = (data & 0x40) > 0;
                NMIGenerate = (data & 0x80) > 0;
                // NMI triggered on register write if vBlank is in progress during this.
                // Apparently activation here is dependent on Vblank flag that can be disabled by reading $2002
                if(NMIGenerate && vBlank){
                    NMI = true;
                }
                else if (!NMIGenerate){
                    // NMI can be regenerated by toggling the NMIGenerate flag, so we set it to false to allow that.
                    NMI = false;
                }
                // Loopy write
                loopyT = (loopyT & 0xF3FF)|(baseNameTable << 10);
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
                if (!loopyW) {
                    // Data in
                    int scrollX = data & 0xff;

                    // Loopy write
                    loopyT = (loopyT & 0xFFE0)|((scrollX >> 3)&0x1F);
                    loopyX = scrollX & 0x7;
                }
                // Y
                else{
                    // Data in
                    int scrollY = data & 0xff;

                    // Loopy write
                    // There's like 3 parts to this, so I'll split it for readability's sake
                    int part1 = scrollY & 0x7;
                    int part2 = (scrollY >> 3) & 0x7;
                    int part3 = (scrollY >> 6) & 0x3;
                    // 0x8C1F sounds random, but I'm just masking off the bits they're supposed to go
                    // Hopefully this is the right way of doing this
                    loopyT = (loopyT & 0x8C1F)|(part1 << 12)|(part2 << 5)|(part3 << 8);
                }
                // Togle scrollWrite
                loopyW = !loopyW;
                break;
            }
            // PPUADDR
            case 0x6:{
                // Write (2)
                // High byte
                if (!loopyW){
                    // Loopy write
                    int ADDRData = data & 0x3F;
                    loopyT = (loopyT & 0x00FF) | (ADDRData << 8);
                }
                // Low byte
                else{
                    // Loopy write
                    int ADDRData = data & 0xFF;
                    loopyT = (loopyT & 0xFF00)|(ADDRData);
                    loopyV = loopyT;    // Apply temp to current on this write
                }
                // Toggle
                loopyW = !loopyW;
                break;
            }
            case 0x7:{
                // Both
                MMU.writeByte(loopyV, data);

                // Loopy Increment
                loopyV = (loopyV + (VRAMInc ? 32:1)) & 0xFFFF;
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
        switch (address){
            case 0x2:{
                returnByte = latch & 0x1F;  // First 5 bytes of latch are here for some reason
                returnByte = returnByte|((spriteOverflow ? 1:0) << 5)|((spriteZeroHit ? 1:0) << 6)|
                        ((vBlank ? 1:0) << 7);
                vBlank = false;         //Cleared after a read apparently

                // Loopy read
                loopyW = false;         // Apparently cleared on write
                break;
            }
            case 0x4:{
                returnByte = OAM[OAMADDR];
                break;
            }
            case 0x7:{
                // Regions before palette return from a buffer that's only updated on reads
                if (loopyV < 0x3F00){
                    returnByte = PPUDataReadBuffer;
                    PPUDataReadBuffer = MMU.readByte(loopyV);
                }
                // Palette data comes directly however
                else {
                    returnByte = MMU.readByte(loopyV);
                    // Buffer contains mirrored nametable byte instead
                    PPUDataReadBuffer = MMU.readByte(loopyV & 0x2F1F);
                }
                // Loopy Increment
                loopyV = (loopyV + (VRAMInc ? 32:1)) & 0xFFFF;
                break;
            }
        }
        latch = returnByte;
        return returnByte;
    }

    public void OAMDMA(int[] OAMData){
        // Used specifically for handling DMA requests to PPU OAM
        for (int i = 0; i < 256; i++){
            int DMAAdress = (OAMADDR + i) & 0xFF;
            OAM[DMAAdress] = OAMData[i];
        }
    }

    public boolean NMITriggered(){
        // Return if NMI should be triggered
        return NMI;
    }

    public boolean getNewVblank(){
        boolean returnData = newVblank;
        newVblank = false;
        return returnData;
    }

    public Texture getFrameBuffer(){
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
            int tempByte2 = MMU.readByte(tileAddress + j + 8);
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

    // Testing scanline bg renderer
    public void drawBGScanLine(int x, int y, int screenY){
        //int[] bgBuffer = new int[256];
        int baseX = x;
        int baseY = y;
        int patternTable = backgroundTable ? 0x1000:0x0000;
        //int drawX = baseX;
        //int drawY = 0;
        for (int i = 0; i < 256; i++){
            int drawX = (baseX + i) & 0x1FF;
            //int drawY = baseY % 480;
            int drawY = baseY % 480;
            // Determine nametable region
            int baseNameTable2 = 0x2000;
            if (drawX >= 256){
                baseNameTable2 += 0x400;
                drawX -= 256;
            }
            if (drawY >= 240){
                baseNameTable2 += 0x800;
                drawY -= 240;
            }
            // Determine the tile to render from
            int tileNum = (drawX/8) + ((drawY/8)*32);
            // Determine which pixel of that tile to render from
            int pixelX = drawX % 8;
            int pixelY = drawY % 8;

            // Get pixel data (specifically the palette data)
            int tilePointer = MMU.readByte(baseNameTable2 + tileNum);    // Tile pointer
            // I borrowed this code from the drawnametable0 method so here's a hack to make this work
            int tileX = tileNum % 32;
            int tileY = tileNum / 32;
            int attributeByte = (baseNameTable2 + 0x3C0) + ((tileX/4) + ((tileY/4) * 8));
            int cornerNum = (((tileX/2)%2) + (((tileY/2)%2)*2))*2;
            int attribute = (MMU.readByte(attributeByte) >> cornerNum) & 0x3;

            int paletteIndex = (attribute << 2)|(MMU.readByte(patternTable + (tilePointer << 4) + pixelY) >> (7 - pixelX))&0x1|
                    ((MMU.readByte(patternTable + (tilePointer << 4) + (pixelY + 8)) >> (7 - pixelX)) & 0x1) << 1;
            if ((paletteIndex & 0x3) != 0 && !(!showBGLeft && i < 8)){
                lineBuffer[i] = paletteIndex;
            }
        }
    }

    private void drawSpriteLine(int y){
        boolean sprite0onLine = false;
        int spriteCount = 0;
        int[] secondaryOAM = new int[32];    // Secondary OAM table
        int[] spriteLineBuffer = new int[256];
        boolean[] priorityValues = new boolean[256];
        int spriteHeight = tallSprite ? 16:8;

        for (int i = 0; i < 64; i++){
            // Check if sprite is on the line
            if (OAM[i*4] + 1 <= y && OAM[i*4] + spriteHeight >= y){
                // Check if this is sprite0, used for sprite 0 hit later
                if (i == 0){
                    sprite0onLine = true;   // Sprite 0 is present
                }
                if (spriteCount < 8) {
                    for (int j = 0; j < 4; j++) {
                        secondaryOAM[(spriteCount * 4) + j] = OAM[(i * 4) + j];
                    }
                    spriteCount++;          // Increment sprite counter
                }
                else{
                    // Set overflow flag and break the loop
                    spriteOverflow = true;
                    break;
                }
            }
        }

        if (spriteCount == 0){
            // For the sake of MMC3 compatibility, do a dummy sprite read (Not accurate, but should work).
            MMU.readByte(spriteTable ? 0x1000 : 0x0000);
            return;
        }
        for (int i = 0; i < spriteCount; i++){
            int byte0 = secondaryOAM[(i * 4) + 0] + 1;   // Y-Position - 1
            int byte1 = secondaryOAM[(i * 4) + 1];   // Index for 8x8. Encoded different for 8x16
            int byte2 = secondaryOAM[(i * 4) + 2];   // Attributes
            int byte3 = secondaryOAM[(i * 4) + 3];   // X-Position

            //int pixelX = 0;
            int pixelY = ((byte2 & 0x80) > 0 ? (spriteHeight-1)-(y-byte0):y-byte0);
            int patternTable = 0;
            int tilePointer = 0;
            if (!tallSprite) {
                patternTable = spriteTable ? 0x1000 : 0x0000;
                tilePointer = patternTable + (byte1 << 4);
            }
            // Pattern table for 8x16 sprite comes from the 1st byte
            else{
                patternTable = (byte1 & 0x1) << 12;
                tilePointer = patternTable + (((byte1 & 0xFE)|(pixelY > 7 ? 1:0)) << 4);
            }
            // Bit of a hack. Masks the pixelY incase it's an 8x16, so that rendering doesn't bug
            pixelY = pixelY & 0x7;
            int tileByte0 = MMU.readByte(tilePointer + pixelY);
            int tileByte1 = MMU.readByte(tilePointer + pixelY + 8);

            for (int j = 0; j < 8; j++){
                int pixelX = ((byte2 & 0x40) > 0 ? (7-j):(j));
                int paletteIndex = ((tileByte0 >> (7-pixelX))&0x1)|(((tileByte1 >> (7-pixelX))&0x1)<<1)|
                        ((0x4|(byte2&0x3)) << 2);

                if ((paletteIndex & 0x3) != 0 && (byte3+j) < 256 && spriteLineBuffer[byte3+j] == 0
                        && !(!showSpriteLeft && (byte3+j) < 8)) {
                    spriteLineBuffer[byte3+j] = paletteIndex;
                    priorityValues[byte3+j] = (byte2 & 0x20) > 0;
                    // Sprite 0 hit check
                    if (i == 0 && sprite0onLine && lineBuffer[byte3+j] != 0 && (byte3 + j) != 255){
                        // We're only here because a sprite was, check if a line pixel is
                        spriteZeroHit = true;   // hit
                    }
                }
            }
        }
        if (spriteCount < 8 && tallSprite){
            // Dummy reads tile FF in 0x1000 if less than 8 sprites for 8x16 sprites
            // Apparently this sort of happens (although not exactly like this).
            // For MMC3 games using 8x16, we need this.
            // Example: Without this, Kirby's Adventure's hud will flicker at times
            // due to some sprites being in the other sprite region.
            MMU.readByte(0x1FF0);
        }
        // Draw to linebuffer
        for (int i = 0; i < 256; i++){
            if (spriteLineBuffer[i] != 0 && (lineBuffer[i] == 0 || !priorityValues[i])){
                lineBuffer[i] = spriteLineBuffer[i];
            }
        }
    }

    private void renderLine(int y){
        for (int i = 0; i < 256; i++) {
            frameBuffer.drawPixel(i, y, palette[MMU.readByte(0x3F00 + lineBuffer[i]) & 0x3F]);
        }
    }

    private void incrementLoopyY(){
        // Loopy increment Y
        // From the NESDev psuedocode
        if ((loopyV & 0x7000) != 0x7000){
            loopyV = (loopyV + 0x1000) & 0xFFFF;
        }
        else{
            loopyV = (loopyV & ~0x7000) & 0xFFFF;
            int y = (loopyV & 0x03E0) >> 5;
            if (y == 29){
                y = 0;
                loopyV = (loopyV ^ 0x800) & 0xFFFF;
            }
            else if (y == 31){
                y = 0;
            }
            else{
                y += 1;
            }
            loopyV = ((loopyV & ~0x03E0) & 0xFFFF)| (y << 5);
        }
    }

    private void incrementLoopyX(){
        // Coarse X Increment from nesdev psuedocode
        if ((loopyV & 0x001F) == 31){
            loopyV = (loopyV & ~0x001F) & 0xFFFF;
            loopyV = (loopyV ^ 0x0400) & 0xFFFF;
        }
        else{
            loopyV = (loopyV + 1) & 0xFFFF;
        }
    }
}
