package com.example.mainpage;

import org.junit.Test;
import static org.junit.Assert.*;

public class FloodStatusHelperTest {

    @Test
    public void testGetStatus_Safe() {
        assertEquals(FloodStatusHelper.STATUS_SAFE, FloodStatusHelper.getStatus(0.0));
        assertEquals(FloodStatusHelper.STATUS_SAFE, FloodStatusHelper.getStatus(30.0));
        assertEquals(FloodStatusHelper.STATUS_SAFE, FloodStatusHelper.getStatus(50.0));
    }

    @Test
    public void testGetStatus_Warning() {
        assertEquals(FloodStatusHelper.STATUS_WARNING, FloodStatusHelper.getStatus(50.1));
        assertEquals(FloodStatusHelper.STATUS_WARNING, FloodStatusHelper.getStatus(60.0));
        assertEquals(FloodStatusHelper.STATUS_WARNING, FloodStatusHelper.getStatus(70.0));
    }

    @Test
    public void testGetStatus_Danger() {
        assertEquals(FloodStatusHelper.STATUS_DANGER, FloodStatusHelper.getStatus(70.1));
        assertEquals(FloodStatusHelper.STATUS_DANGER, FloodStatusHelper.getStatus(80.0));
        assertEquals(FloodStatusHelper.STATUS_DANGER, FloodStatusHelper.getStatus(120.0));
        assertTrue(FloodStatusHelper.isDangerOrCritical(FloodStatusHelper.getStatus(80.0)));
    }

    @Test
    public void testGetTrend_Rising() {
        assertEquals(FloodStatusHelper.TREND_RISING, FloodStatusHelper.getTrend(50.0, 45.0));
    }

    @Test
    public void testGetTrend_Falling() {
        assertEquals(FloodStatusHelper.TREND_FALLING, FloodStatusHelper.getTrend(40.0, 45.0));
    }

    @Test
    public void testGetTrend_Stable() {
        assertEquals(FloodStatusHelper.TREND_STABLE, FloodStatusHelper.getTrend(45.0, 44.5));
        assertEquals(FloodStatusHelper.TREND_STABLE, FloodStatusHelper.getTrend(45.0, -1.0));
    }

    @Test
    public void testGetStationDisplayName() {
        assertEquals("Putatan", FloodStatusHelper.getStationDisplayName("ST001"));
        assertEquals("Alabang", FloodStatusHelper.getStationDisplayName("ST002"));
        assertEquals("All Stations", FloodStatusHelper.getStationDisplayName("MUN_ALL"));
        assertEquals("ST999", FloodStatusHelper.getStationDisplayName("ST999"));
    }

    @Test
    public void testFormatFreshness() {
        assertEquals("Last Updated: Jul 27, 2024 10:00 AM", FloodStatusHelper.formatFreshness("Jul 27, 2024 10:00 AM"));
        assertEquals("Data Freshness: Unknown", FloodStatusHelper.formatFreshness(""));
        assertEquals("Data Freshness: Unknown", FloodStatusHelper.formatFreshness(null));
    }
}
