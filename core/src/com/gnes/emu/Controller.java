package com.gnes.emu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

/**
 * Created by ghost_000 on 7/14/2016.
 */
public class Controller {
    // Variables classes and stuff
    boolean latchP1 = false;    // Maybe don't use this?
    int buttonDataP1 = 0x00;

    // Constructor
    public Controller(){
        //
    }

    // Data being requested
    public int recieveData(int address){
        //
        int returnData = 0;

        if (address == 0x4016){
            returnData = buttonDataP1 & 0x1;
            if (!latchP1){
                buttonDataP1 = (buttonDataP1 >> 1)|0x80;
            }
        }
        else if (address == 0x4017){
            // no second controller
        }
        return returnData;
    }

    // Data being recieved
    public void sendData(int address, int data){
        // if bit 0 is high, the controller is continuously recieving first button,
        // if low latch the button states
        if (address == 0x4016){
            latchP1 = (data & 0x1) > 0;
        }
        else if (address == 0x4017){
            // No second controller
        }
    }

    // Called constantly to store controller data
    public void pollController(){
        if (latchP1){
            // gather button data
            boolean A = Gdx.input.isKeyPressed(Input.Keys.S);
            boolean B = Gdx.input.isKeyPressed(Input.Keys.A);
            boolean Select = Gdx.input.isKeyPressed(Input.Keys.BACKSPACE);
            boolean Start = Gdx.input.isKeyPressed(Input.Keys.ENTER);
            boolean Up = Gdx.input.isKeyPressed(Input.Keys.UP);
            boolean Down = Gdx.input.isKeyPressed(Input.Keys.DOWN);
            boolean Left = Gdx.input.isKeyPressed(Input.Keys.LEFT);
            boolean Right = Gdx.input.isKeyPressed(Input.Keys.RIGHT);
            buttonDataP1 = (A ? 1:0)|((B ? 1:0)<<1)|((Select ? 1:0)<<2)|((Start ? 1:0)<<3)|((Up ? 1:0)<<4)
                    |((Down ? 1:0)<<5)|((Left ? 1:0)<<6)|((Right ? 1:0)<<7);
        }
    }
}
