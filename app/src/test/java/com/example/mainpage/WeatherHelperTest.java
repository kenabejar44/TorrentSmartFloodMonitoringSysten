package com.example.mainpage;

import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherHelperTest {

    @Test
    public void testCalculateHeatIndex_LowTemp_ReturnsSameTemp() {
        double result = WeatherHelper.calculateHeatIndex(24.0, 70.0);
        assertEquals(24.0, result, 0.01);
    }

    @Test
    public void testCalculateHeatIndex_HighTemp_CalculatesApparentTemp() {
        double result = WeatherHelper.calculateHeatIndex(32.0, 80.0);
        assertTrue(result > 32.0);
    }

    @Test
    public void testGetHeatIndexCategory() {
        assertEquals("Normal", WeatherHelper.getHeatIndexCategory(25.0));
        assertEquals("Caution", WeatherHelper.getHeatIndexCategory(30.0));
        assertEquals("Extreme Caution", WeatherHelper.getHeatIndexCategory(35.0));
        assertEquals("Danger", WeatherHelper.getHeatIndexCategory(45.0));
        assertEquals("Extreme Danger", WeatherHelper.getHeatIndexCategory(55.0));
    }
}
