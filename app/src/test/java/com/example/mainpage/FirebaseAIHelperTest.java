package com.example.mainpage;

import org.junit.Test;
import static org.junit.Assert.*;

public class FirebaseAIHelperTest {

    @Test
    public void testStripEmojis_Null() {
        assertNull(FirebaseAIHelper.stripEmojis(null));
    }

    @Test
    public void testStripEmojis_NoEmojis() {
        String input = "Safe water level detected in Putatan";
        assertEquals(input, FirebaseAIHelper.stripEmojis(input));
    }

    @Test
    public void testStripEmojis_WithEmojis() {
        String input = "Warning! 🚨 High water level detected 🌊 in Barangay Putatan! ⚠️";
        String expected = "Warning!  High water level detected  in Barangay Putatan! ";
        assertEquals(expected, FirebaseAIHelper.stripEmojis(input));
    }
}
