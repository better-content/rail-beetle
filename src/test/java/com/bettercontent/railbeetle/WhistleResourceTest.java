package com.bettercontent.railbeetle;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WhistleResourceTest {
    private static final String WHISTLE = "/assets/rail_beetle/sounds/whistle.ogg";

    @Test
    void whistleIsShortMonoVorbis() throws IOException {
        byte[] ogg = resource(WHISTLE);
        assertTrue(ogg.length > 1_000, "whistle must contain encoded audio");
        assertEquals("OggS", new String(ogg, 0, 4, StandardCharsets.US_ASCII));

        int vorbis = find(ogg, new byte[] { 1, 'v', 'o', 'r', 'b', 'i', 's' });
        assertTrue(vorbis >= 0, "whistle must contain a Vorbis identification header");
        assertEquals(1, Byte.toUnsignedInt(ogg[vorbis + 11]), "whistle must be mono");

        long sampleRate = littleEndian(ogg, vorbis + 12, 4);
        assertEquals(44_100, sampleRate, "whistle must use the documented sample rate");

        long finalGranule = finalGranule(ogg);
        double durationSeconds = (double) finalGranule / sampleRate;
        assertTrue(durationSeconds >= 1.20 && durationSeconds <= 1.30,
                "whistle must have a sub-second body and only a short tail; duration=" + durationSeconds);
    }

    @Test
    void soundsJsonPublishesWhistleEvent() throws IOException {
        String sounds = new String(resource("/assets/rail_beetle/sounds.json"), StandardCharsets.UTF_8);
        assertTrue(sounds.contains("\"whistle\""));
        assertTrue(sounds.contains("\"rail_beetle:whistle\""));
        assertTrue(sounds.contains("\"subtitles.rail_beetle.whistle\""));
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream stream = WhistleResourceTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "missing resource " + path);
            return stream.readAllBytes();
        }
    }

    private static int find(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static long finalGranule(byte[] ogg) {
        long last = -1;
        for (int offset = 0; offset + 27 <= ogg.length;) {
            assertEquals("OggS", new String(ogg, offset, 4, StandardCharsets.US_ASCII),
                    "invalid Ogg page at byte " + offset);
            last = littleEndian(ogg, offset + 6, 8);
            int segments = Byte.toUnsignedInt(ogg[offset + 26]);
            int bodyLength = 0;
            for (int i = 0; i < segments; i++) {
                bodyLength += Byte.toUnsignedInt(ogg[offset + 27 + i]);
            }
            offset += 27 + segments + bodyLength;
        }
        assertTrue(last >= 0, "whistle must contain at least one Ogg page");
        return last;
    }

    private static long littleEndian(byte[] bytes, int offset, int length) {
        long value = 0;
        for (int i = 0; i < length; i++) {
            value |= (long) Byte.toUnsignedInt(bytes[offset + i]) << (i * 8);
        }
        return value;
    }
}
