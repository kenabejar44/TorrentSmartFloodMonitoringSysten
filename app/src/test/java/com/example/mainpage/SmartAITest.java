package com.example.mainpage;

import org.junit.Test;
import static org.junit.Assert.*;

public class SmartAITest {

    @Test
    public void testGetWeatherAdvice_DangerWaterLevel() {
        String advice = MainActivity.SmartAI.getWeatherAdvice("Clear", 30.0, 80.0);
        assertTrue(advice.contains("DANGER"));
        assertTrue(advice.contains("High water levels detected"));
    }

    @Test
    public void testGetWeatherAdvice_RainWithWarningWaterLevel() {
        String advice = MainActivity.SmartAI.getWeatherAdvice("Heavy Rain", 28.0, 60.0);
        assertTrue(advice.contains("WARNING"));
    }

    @Test
    public void testGetWeatherAdvice_RainWithSafeWaterLevel() {
        String advice = MainActivity.SmartAI.getWeatherAdvice("Light Rain", 29.0, 20.0);
        assertTrue(advice.contains("ADVISORY"));
    }

    @Test
    public void testGetWeatherAdvice_Safe() {
        String advice = MainActivity.SmartAI.getWeatherAdvice("Clear", 30.0, 20.0);
        assertTrue(advice.contains("SAFE"));
    }
}
