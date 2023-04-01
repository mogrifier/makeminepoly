package com.skyefractal.midi;
import javax.sound.midi.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Midi utilities for me.
 */
public class MidiHelp {

    private static final Logger logger = LogManager.getLogger(MidiHelp.class);

    /**
     * Get the MIDI Receiver for a given interface name and port number.
     * @param port
     * @param midiInterfaceName
     * @return javax.sound.midi.Receiver (from perspective of interface). If effectively represents the MIDI input
     * on a synthesizer (that is the receiver). On your MIDI interface, it would be a MIDI output channel
     * and is used by a javax.sound.midi.Transmitter.
     * @throws MidiUnavailableException
     */
    public static Receiver getReceiver(int port, String midiInterfaceName) throws MidiUnavailableException {

        Receiver receiver = null;
        MidiDevice.Info[] info = MidiSystem.getMidiDeviceInfo();

        if (info.length == 0)
        {
            throw new MidiUnavailableException("no MIDI receivers found");
        }

        for (int i = 0; i < info.length; i++) {
            logger.info(info[i]);
            //scan for interface name and the port. String form is unknown so look for both. not great.
            if (info[i].toString().contains(Integer.toString(port))
                    && info[i].toString().contains(midiInterfaceName))
            {
                //found receiver
                logger.info("found requested device: " + info[i]);
                MidiDevice device = MidiSystem.getMidiDevice(info[i]);
                device.open();
                receiver = device.getReceiver();
                break;
            }
        }
        return receiver;
    }



    /**
     * Simply prints out the content of a midi sequence to the console. This supports printing the data from all tracks
     * in the sequence. Track 0 is some type of metadata. The other tracks from regular MIDI data.
     * @param seq the sequence to dump
     */
    public static void dumpSequence(Sequence seq) {

        //first track just had special commands. track 1 contains the actual data. SMF Type 1.
  /*
  144 = note on
  128 = note off
  176 = chan1 control/mode change. 64 = sustain pedal.

  negative byte values just need 256 added to them.

  */
        int trackCount = seq.getTracks().length;
        for (int j = 0; j < trackCount; j++) {

            Track track = seq.getTracks()[j];
            MidiEvent event = null;
            long tick = 0;
            System.out.println("************  TRACK "+ j);
            for (int i = 0; i < track.size(); i++) {
                event = track.get(i);
                logger.info("status = " + event.getMessage().getStatus() + "  ");
                byte[] msg = event.getMessage().getMessage();
                for (int k = 0; k < msg.length; k++) {
                    logger.info(" byte " + k + "= " + msg[k]);
                }
                tick = event.getTick();
                logger.info(" tick= " + tick);

            }
        }
    }


    /**
     * Demonstrates how to write a MIDI file using code.
     */
    public static void writeMidiFile() {
        try (OutputStream stream =  new FileOutputStream(new File("./sample.mid"))) {
            // Create a new sequence with 96 tick per quarter note and 1 track
            Sequence sequence = new Sequence(Sequence.PPQ, 960, 1);

            // Create a new track
            Track track = sequence.createTrack();

            // Set the tempo of the track to 120 BPM (beats per minute)
            int tempo = 500000; // microseconds per quarter note. OMG. last three bytes are 500,000 in hex bytes
            byte[] data = new byte[]{0x51, 0x03, 0x07, (byte)160, 0x20};
            //byte wtf = 0xa6 is not LEGAL- some UTF problem I think. so (byte)160.
            MidiMessage tempoMsg = new MetaMessage(0x51, data, data.length);
            //0x51 = set tempo. see https://mido.readthedocs.io/en/latest/meta_message_types.html
            MidiEvent tempoEvent = new MidiEvent(tempoMsg, 0);
            //adds event at time tick = 0
            track.add(tempoEvent);

            //add a sequence of on/off data in chromatic scale
            for (int i = 0; i < 100; i++) {
                // Add a Note On event to the track for C4 (MIDI note number 60) with velocity 64 at tick 0
                ShortMessage noteOn = new ShortMessage();
                noteOn.setMessage(ShortMessage.NOTE_ON, 0, 60 + i % 24, 64);
                MidiEvent noteOnEvent = new MidiEvent(noteOn, i * 960);
                track.add(noteOnEvent);

                // Add a Note Off event to the track for the same note with velocity 0 at tick 96 (equivalent to a quarter note)
                ShortMessage noteOff = new ShortMessage();
                noteOff.setMessage(ShortMessage.NOTE_OFF, 0, 60  + i % 24, 0);
                MidiEvent noteOffEvent = new MidiEvent(noteOff, i * 960 + 960);
                track.add(noteOffEvent);
            }

            logger.info("track event count = " + track.size());
            logger.info("track ticks = " + track.ticks());

            //write the data to the console as a check.
            MidiSystem.write(sequence, 1, System.out);


            // Write the sequence to a MIDI file named "output.mid". type 0 is not allowed by javax MIDI. Using type 1.
            MidiSystem.write(sequence, 1, stream);
            stream.close();
        }
        catch (IOException | InvalidMidiDataException e)
        {
            logger.error("failed to create sample.mid", e);
        }
    }


    /**
     * Wonder how to do this...
     *
     * c = 123, v = 0 channel mode message. Looks like I can use low level messaging.
     */
    public static void allNotesOff() throws InvalidMidiDataException {

        byte[] data = new byte[]{0};
        MetaMessage msg = new MetaMessage(123, data, 1); //
        // int type, byte[] data, int length)

        //no shortcut. got to write the code to put this in a track and send it with a sequencer??


    }
}
