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

    private static final String MOTUMIDIEXPRESS = "Express  128: Port";

    public static void main( String[] args ) throws MidiUnavailableException {
        App app = new App();
        /*
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            public void run()
            {
                logger.info("Shutdown Hook is running !");
                try {
                    MidiHelp.allNotesOff();
                } catch (InvalidMidiDataException e) {
                    logger.error("oops- failed to send all notes off properly", e);
                }
            }
        });
        */

        if (args[0].equals("-s"))
        {
            //split a track
            logger.info("splitting midi file " + args[1] + " into multiple tracks and saving as " + args[2]);
            app.splitTrack(args[1], args[2]);
        }
        else if (args[0].equals("-r"))
        {
            //record a track
            logger.info("recording multitrack midi file " + args[1] + " as separate audio stems.");
            app.recordMultitrackMidi(args[1], args[2], Integer.parseInt(args[3]), args[4]);
        }
        else if (args[0].equals("-p"))
        {
            //play a track
            logger.info("playing midi file " + args[1] + " over " + args[2] + " - " + args[3]);
            app.playSequence(args[1], args[2], Integer.parseInt(args[3]));
        }

        System.exit(0);

    }


    /**
     * This expects a midi file with 1 polyphonic track. It will create a new track for each note
     * (all of the same notes). So if your original track used 5 notes, you will get five tracks. All played
     * together it will sound like the original. The point of splitting, though is to enable a monosynth to play
     * each track and record the audio from it. Merging all the audio stems will create a polyphonic version
     * from a monosynth, saving you from much tedium.
     * @param midi the midi file to read and split into multiple tracks (one per note)
     * @param multiTrackMidi the name of new multitrack midi file to write the data to
     */
    public void splitTrack(String midi, String multiTrackMidi) {
        Sequencer sequencer = null;

        try (InputStream midiData = this.getClass().getClassLoader().getResourceAsStream(midi)) {
            // Set the sequence for the sequencer
            Sequence sequence = MidiSystem.getSequence(midiData);
            //this sequence should only have one track. wrong if more.
            if (sequence.getTracks().length > 1) {
                throw new InvalidMidiDataException("midi file has more than 1 track");
            }
            Sequence multiTrack = MidiHelp.splitTrack(sequence.getTracks()[0]);
            MidiSystem.write(multiTrack, 1, new File(multiTrackMidi));
        } catch (IOException | InvalidMidiDataException e) {
            logger.error("invalid data or midi file not found", e);
        }
    }

    /**
     * Plays a midi file and sends the MIDI data out the specified interface and port.
     *
     * @param midi  midi file name
     * @param midiInterface name of interface output, i.e. "Express  128: Port"
     * @param port  port number of the interface
     */
    public void playSequence(String midi, String midiInterface, int port)
    {
        Sequencer sequencer = null;
        Receiver recv;
        Transmitter mitter;

        try (InputStream midiData = this.getClass().getClassLoader().getResourceAsStream(midi))
        {
            //try to kill default synth in the OS. Why? It keeps playing all the time.
            MidiHelp.disableDefaultSynth();
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
            // Start playing the sequence on the specified MIDI output port of the interface
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


    /**
     * This will play and record each track of a Type 1 midi file. The intent is to play each from a monosynth
     * and create a set of stems that can be merged into a nice polyphonic track.
     * @param midiFile a Type 1 multitrack midi file
     */
    public void recordMultitrackMidi(String midiFile, String midiInterface, int port, String mixerName) throws MidiUnavailableException {
        MidiHelp.disableDefaultSynth();
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
            //try to kill default synth in the OS. Why? It keeps playing all the time.
            MidiHelp.disableDefaultSynth();

            audioRecord = new FileOutputStream(new File("./recording.wav"));
            inputStream = new ByteArrayInputStream(audio);
            audioInputStream = new AudioInputStream(inputStream, format, audio.length / format.getFrameSize());


            AudioHelp.showMixers();
            Mixer.Info[] mixers = AudioSystem.getMixerInfo();
            Mixer recordingMixer = AudioSystem.getMixer(mixers[22]);
            //Mixer: 19UFX1604 Input 13/14 (Behringer , supports TargetDataLine , Direct Audio Device: DirectSound Capture

  /*
  To record in Windows 11, you have to select the mixer for the default sound input for recording.
  Also has to be allowed, naturally, to be default in first place. Just showing up in the list of inputs
  is NOT good enough.
  If you can't find a device in the sound settings, scroll down to All Sound Devices and you may find it was disabled.
  */

            logger.info("using mixer " + recordingMixer.getMixerInfo().toString());
            Line[] targetLines = recordingMixer.getTargetLines();

            for (Line current : targetLines)
            {
                logger.info(" target line from recordingMixer: " + current.getLineInfo());
            }

            // Get the default microphone as the target data line for recording-
            // very confusing but it seems I don't need the Mixer at all. Just grab the default input.
            //DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            line = AudioSystem.getTargetDataLine(format, recordingMixer.getMixerInfo());

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
            recv = MidiHelp.getReceiver(port, midiInterface);
            //set to transmit on this port to device
            mitter.setReceiver(recv);

            //play a file and record the audio. Should be in a Thread.
            sequencer.start();

            //FIXME
            /*
            need to pipe the audio to a writer. No need to allocate all at once.

             */

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
