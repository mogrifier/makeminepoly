package com.skyefractal.midi;

import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;

import com.skyefractal.App;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MidiListener implements MetaEventListener {

    private static final Logger logger = LogManager.getLogger(MidiListener.class);

    @Override
    public void meta(MetaMessage meta) {

        logger.info("received metamessage; type = " + meta.getType());
        byte[] data = meta.getData();
        for (int i = 0; i < data.length; i++)
        {
            logger.info("byte " + i + " = " + data[i]);
        }

    }
}
