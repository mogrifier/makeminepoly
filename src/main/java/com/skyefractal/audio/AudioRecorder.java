package com.skyefractal.audio;

import javax.sound.midi.Sequencer;
import javax.sound.sampled.*;
import java.io.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AudioRecorder implements Runnable {

    private static final Logger logger = LogManager.getLogger(AudioRecorder.class);
    private TargetDataLine line = null;
    private Sequencer sequencer = null;
    private int track = 0;
    AudioFileFormat.Type fileType = AudioFileFormat.Type.WAVE;


    public AudioRecorder (TargetDataLine line, Sequencer sequencer, int track) {
        this.line = line;
        this.sequencer = sequencer;
        this.track = track;
    }


    @Override
    public void run() {

        OutputStream audioFile;
        //stereo cd audio is 176400 bytes per second- thi sis good for one minute but will grow as needed
        ByteArrayOutputStream audio = new ByteArrayOutputStream(10000000);
        int bufferSize = 8192;
        byte[] chunk = new byte[bufferSize];
        // Open the target data line for recording
        try {
            //by opening with a certain buffer size, this should guarantee each read cycle will not exceed that amount
            line.open(AudioHelp.CD_AUDIO, bufferSize);
            // Start recording
            line.start();
            int count = 0;
            //play a file and record the audio.
            //unmute current track
            sequencer.setTrackMute(track, false);
            sequencer.setTrackSolo(track, true);

            //record before sequencer starts
            long startTime = System.currentTimeMillis();
            long currentTime = 0;

            //record two seconds
            //FIXME could just insert a number of zeroes
            while(currentTime - startTime < 2000)
            {
                currentTime = System.currentTimeMillis();
                //read into a small buffer
                line.read(chunk, 0,bufferSize);
                //append to the bytearrayoutputstream
                audio.write(chunk, 0, chunk.length);
            }

            //ensures back at the beginning for each track
            sequencer.setMicrosecondPosition(0);
            sequencer.start();

            while (sequencer.isRunning()) {
                //read into a small buffer
                //logger.debug("bytes available = " + line.available());
                count = line.read(chunk, 0, line.available());
                //append to the bytearrayoutputstream
                audio.write(chunk, 0, count);
            }

            //need to keep recording (for extra 10 seconds) to get any audio tails.
            startTime = System.currentTimeMillis();
            currentTime = 0;

            while(currentTime - startTime < 10000)
            {
                currentTime = System.currentTimeMillis();
                //read into a small buffer
                line.read(chunk, 0, bufferSize);
                //append to the bytearrayoutputstream
                audio.write(chunk, 0, chunk.length);
            }

            //finish recording and free resources to get ready for next run
            if (!sequencer.isRunning()) {
                //prepare to play next track by muting track just played
                sequencer.setTrackMute(track, true);
                sequencer.setTrackSolo(track, false);
                sequencer.stop();  //redundant I think
                //write the audio recorded
                line.stop();
                line.close();
                int size = audio.size();
                InputStream stream = new ByteArrayInputStream(audio.toByteArray(), 0, size);
                AudioInputStream audioInputStream = new AudioInputStream(stream, AudioHelp.CD_AUDIO, size);
                audioFile = new FileOutputStream(new File("./recording_" + track + ".wav"));
                AudioSystem.write(audioInputStream, fileType, audioFile);
            }
        }
        catch (LineUnavailableException | IOException e)
        {
            e.printStackTrace();
        }
    }
}
