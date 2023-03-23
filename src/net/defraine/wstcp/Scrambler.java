package net.defraine.wstcp;

import java.util.Random;

public class Scrambler {

    protected final Random rand;
    protected final byte[] key;
    protected int keyPos;

    public Scrambler(long seed) {
        rand = new Random(seed);
        key = new byte[256];
        renewKey();
    }

    protected void renewKey() {
        rand.nextBytes(key);
        keyPos = 0;
    }

    // scramble is both encrypt and decrypt
    public void scramble(byte[] buf, int offset, int length) {
        for (int i = offset; i < offset+length; ++i) {
            if (keyPos == key.length)
                renewKey();
            buf[i] ^= key[keyPos++];
        }
    }
}
