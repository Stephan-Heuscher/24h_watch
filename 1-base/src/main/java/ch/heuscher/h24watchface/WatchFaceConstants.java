package ch.heuscher.h24watchface;

import android.graphics.Color;
import android.provider.CalendarContract;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class WatchFaceConstants {

    private WatchFaceConstants() {
        // private constructor to prevent instantiation
    }

    // General
    public static final Locale DE_CH_LOCALE = Locale.forLanguageTag("de-CH");
    public static final int COMPLICATION_ID = 1974;

    // Drawing & Layout
    public static final float DECENTERING_CORRECTION = -24f;
    public static final float TEXT_SIZE = 30f;
    public static final int RAND_RESERVE = 7;
    public static final float STROKE_WIDTH = 2f;
    public static final float HOUR_MARKER_RADIUS = 4f;
    public static final float EVENT_MARKER_RADIUS = 6.5f;
    public static final float EVENT_MARKER_RADIUS_MINIMAL = 1f;
    public static final int ROTATION_180_DEGREES = 180;


    // Time & Date
    public static final DateTimeFormatter MINUTES_FORMATTER = DateTimeFormatter.ofPattern("mm");
    public static final DateTimeFormatter ISO_DATE_WITH_DAYOFWEEK = DateTimeFormatter.ofPattern("E yyyy-MM-dd");
    public static final float DEGREES_PER_HOUR = 15f;
    public static final float DEGREES_PER_MINUTE = 0.25f;

    // Colors
    public static final int COLOR_6_H = Color.argb(255, 0, 255, 0);
    public static final int COLOR_12_H = Color.argb(255, 255, 255, 0);
    public static final int COLOR_18_H = Color.argb(255, 0, 0, 255);
    public static final int COLOR_24_H = Color.argb(255, 255, 0, 255);
    public static final int[] COLORS = new int[]{COLOR_24_H, COLOR_6_H, COLOR_12_H, COLOR_18_H};
    public static final float DARK_MODE_HUE = 13f;
    public static final float DARK_MODE_SATURATION = 0.04f;


    // Behavior
    public static final int LOW_BATTERY_THRESHOLD = 10;
    public static final float LOW_LIGHT_BRIGHTNESS_BOOST = 0.15f;
    public static final int MEETING_PRE_ANNOUNCE_DURATION = 50;
    public static final long CALENDAR_QUERY_WINDOW_HOURS = 18L;
    public static final long ALARM_DISPLAY_WINDOW_HOURS = 18L;

    // Text & Formatting
    public static final NumberFormat DE_CH_NUMBER = NumberFormat.getNumberInstance(DE_CH_LOCALE);
    public static final int EVENT_TITLE_MAX_LENGTH_LINE_1 = 22;
    public static final int EVENT_TITLE_MAX_LENGTH = 50;

    // Calendar Provider
    public static final String[] PROJECTION = {
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.AVAILABILITY
    };
}
