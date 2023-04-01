package com.skyefractal;

import com.skyefractal.audio.AudioHelp;
import com.skyefractal.midi.MidiHelp;

import javax.sound.midi.*;
import javax.sound.sampled.*;
import java.io.*;

import com.skyefractal.midi.MidiListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 *
 *
 */
public class App 
{

    private static final Logger logger = LogManager.getLogger(App.class);

    private static String MOTU = "Express  128: Port";

    public static void main( String[] args )
    {
        App app = new App();
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            public void run()
            {
                logger.info("Shutdown Hook is running !");
                try {
                    MidiHelp.allNotesOff();
                } catch (InvalidMidiDataException e) {
                    logger.error("oops- failed to send al notes off", e);
                }
            }
        });
        logger.info("Application Terminating ...");


        String midiFileToPlay = args[0];
        // app.playMidi(midiFileToPlay);
        app.playSequence(args[0], 1, MOTU);
    }


    /**
     * "Express  128: Port"
     * @param midi
     * @param port
     * @param midiInterface
     */
    public void playSequence(String midi, int port, String midiInterface)
    {
        Sequencer sequencer = null;
        Receiver recv;
        Transmitter mitter;

        try (InputStream midiData = this.getClass().getClassLoader().getResourceAsStream(midi))
        {
            // Get a Sequencer instance
            sequencer = MidiSystem.getSequencer();
            // Open the sequencer
            sequencer.open();
            // Set the sequence for the sequencer
            Sequence sequence = MidiSystem.getSequence(midiData);
            sequencer.setSequence(sequence);

            //examine all events in the sequence
            MidiHelp.dumpSequence(sequence);
            mitter = sequencer.getTransmitter();
            // Start playing the sequence on the specified MIDI output port of the MOTU express
            recv = MidiHelp.getReceiver(port, midiInterface);
            //set to transmit on this port to device
            mitter.setReceiver(recv);
            //play a file. sequencer apparently makes own thread.
            sequencer.addMetaEventListener(new MidiListener());
            sequencer.start();

            while (sequencer.isRunning())
            {
                //noop
                Thread.sleep(200);
            }

        }
        catch (IOException | MidiUnavailableException | InvalidMidiDataException | InterruptedException e) {
            e.printStackTrace();
        }
        finally {
            sequencer.close();
        }
    }


    public void playMidi(String midiFile)
    {
        Sequencer sequencer;
        Receiver recv;
        Transmitter mitter;
        TargetDataLine line = null;
        byte[] audio = new byte[10000000];
        OutputStream audioRecord;
        AudioFileFormat.Type fileType = AudioFileFormat.Type.WAVE;
        AudioFormat format;
        ByteArrayInputStream inputStream;
        AudioInputStream audioInputStream;
        int count = 0;
        MidiDevice.Info myMidiOut;

        // Set the audio format
        format = new AudioFormat(44000.0f, 16, 2, true, false);


        try (InputStream input = this.getClass().getClassLoader().getResourceAsStream(midiFile))
        {
            AudioHelp.showMixers();
        audioRecord = new FileOutputStream(new File("./recording.wav"));
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        Mixer recordingMixer = AudioSystem.getMixer(mixers[18]);
        //Mixer: 19UFX1604 Input 13/14 (Behringer , supports TargetDataLine , Direct Audio Device: DirectSound Capture

  /*
  To record in Windows 11, you have to select the mixer for the default sound input for recording.
  Also has to be allowed, naturally, to be default in first place. Just showing up in the list of inputs
  is NOT good enough.
  If you can't find a device in the sound settings, scroll down to All Sound Devices and you may find it was disabled.
  */

        logger.info("using mixer " + recordingMixer.getMixerInfo().toString());

        Line[] lines =  recordingMixer.getSourceLines();
        logger.info("** lines = " + lines.length);
        for (int i = 0; i < lines.length; i++) {
            logger.info(lines[i].getLineInfo().toString());
        }

            inputStream = new ByteArrayInputStream(audio);
            audioInputStream = new AudioInputStream(inputStream, format, audio.length / format.getFrameSize());

            // Get the default microphone as the target data line for recording
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            line = (TargetDataLine) AudioSystem.getLine(info);

            // Open the target data line for recording
            line.open(format, 8192);
            // Start recording
            line.start();


            // Get a Sequencer instance
            sequencer = MidiSystem.getSequencer();
            // Open the sequencer
            sequencer.open();
            // Set the sequence for the sequencer
            Sequence sequence = MidiSystem.getSequence(input);
            sequencer.setSequence(sequence);

            //examine all events in the sequence
            MidiHelp.dumpSequence(sequence);
            mitter = sequencer.getTransmitter();
            // Start playing the sequence on the specified MIDI output port of the MOTU express
            recv = MidiHelp.getReceiver(1, "Express  128: Port");
            //set to transmit on this port to device
            mitter.setReceiver(recv);

            //play a file and record the audio. Should be in a Thread.
            //FIXME thread it up. garbage collection will cause blips.
            sequencer.start();

            while(sequencer.isRunning())
            {
                count = count + line.read(audio, count, line.available());
                logger.debug("read audio = " + count);
            }

            if (!sequencer.isRunning())
            {
                //close and exit
                // Close the sequencer
                sequencer.close();
                line.stop();
                line.close();

                // Write the recorded audio data to the audio file
                InputStream stream = new ByteArrayInputStream(audio, 0, count);
                audioInputStream = new AudioInputStream(stream, format, count);
                AudioSystem.write(audioInputStream, fileType, audioRecord);

            }
        }
            catch (IOException | MidiUnavailableException | InvalidMidiDataException | LineUnavailableException e) {
                e.printStackTrace();
            }
    }
}
