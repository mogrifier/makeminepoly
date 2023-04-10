package com.skyefractal.audio;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * Little bytes to make code reuse possible.
 */
public class AudioHelp {

    private static final Logger logger = LogManager.getLogger(AudioHelp.class);


    public static Mixer getMixer(String mixerName)
    {
        Line.Info targetDLInfo = new Line.Info(TargetDataLine.class);
        Mixer.Info[] info = AudioSystem.getMixerInfo();
        Mixer mixer = null;
        for (int i = 0; i < info.length; i++) {
            logger.debug(info[i]);
            //scan for interface name and the port. String form is unknown so look for both. not great.
            if (info[i].toString().contains(mixerName))
            {
                mixer = AudioSystem.getMixer(info[i]);
                if (mixer.isLineSupported(targetDLInfo))
                {
                    //found mixer that is an audio input
                    logger.info("found mixer input: " + info[i]);
                    break;
                }
            }
        }
        return mixer;
    }

    public static void showMixers() {
        ArrayList<Mixer.Info> mixInfos = new ArrayList<Mixer.Info>( Arrays.asList( AudioSystem.getMixerInfo( )));
        Line.Info sourceDLInfo =new Line.Info( SourceDataLine.class);
        Line.Info targetDLInfo = new Line.Info(TargetDataLine.class);
        Line.Info clipInfo = new Line.Info(Clip.class);
        Line.Info portInfo =new Line.Info(Port.class);
        String support;
        int index = 0;

        for (Mixer.Info mixInfo :
                mixInfos) {
            Mixer mixer = AudioSystem.getMixer( mixInfo);
            support = ", supports ";
            if (mixer.isLineSupported( sourceDLInfo))
                support += "SourceDataLine ";
            if (mixer.isLineSupported( clipInfo))
                support += "Clip ";
            if (mixer.isLineSupported(targetDLInfo))
                support += "TargetDataLine ";
            if (mixer.isLineSupported( portInfo))
                support += "Port ";

            logger.info("Mixer: " + index
                    + mixInfo.getName() +
                    support + ", " +
                    mixInfo.getDescription());

            index++;
        }
    }
}
