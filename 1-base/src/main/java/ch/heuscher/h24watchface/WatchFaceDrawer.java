package ch.heuscher.h24watchface;

import static ch.heuscher.h24watchface.WatchFaceConstants.ALARM_DISPLAY_WINDOW_HOURS;
import static ch.heuscher.h24watchface.WatchFaceConstants.COLORS;
import static ch.heuscher.h24watchface.WatchFaceConstants.DARK_MODE_HUE;
import static ch.heuscher.h24watchface.WatchFaceConstants.DARK_MODE_SATURATION;
import static ch.heuscher.h24watchface.WatchFaceConstants.DECENTERING_CORRECTION;
import static ch.heuscher.h24watchface.WatchFaceConstants.DEGREES_PER_HOUR;
import static ch.heuscher.h24watchface.WatchFaceConstants.DEGREES_PER_MINUTE;
import static ch.heuscher.h24watchface.WatchFaceConstants.DE_CH_NUMBER;
import static ch.heuscher.h24watchface.WatchFaceConstants.EVENT_MARKER_RADIUS;
import static ch.heuscher.h24watchface.WatchFaceConstants.EVENT_MARKER_RADIUS_MINIMAL;
import static ch.heuscher.h24watchface.WatchFaceConstants.EVENT_TITLE_MAX_LENGTH;
import static ch.heuscher.h24watchface.WatchFaceConstants.EVENT_TITLE_MAX_LENGTH_LINE_1;
import static ch.heuscher.h24watchface.WatchFaceConstants.HOUR_MARKER_RADIUS;
import static ch.heuscher.h24watchface.WatchFaceConstants.ISO_DATE_WITH_DAYOFWEEK;
import static ch.heuscher.h24watchface.WatchFaceConstants.LOW_BATTERY_THRESHOLD;
import static ch.heuscher.h24watchface.WatchFaceConstants.LOW_LIGHT_BRIGHTNESS_BOOST;
import static ch.heuscher.h24watchface.WatchFaceConstants.MEETING_PRE_ANNOUNCE_DURATION;
import static ch.heuscher.h24watchface.WatchFaceConstants.MINUTES_FORMATTER;
import static ch.heuscher.h24watchface.WatchFaceConstants.RAND_RESERVE;
import static ch.heuscher.h24watchface.WatchFaceConstants.STROKE_WIDTH;
import static ch.heuscher.h24watchface.WatchFaceConstants.TEXT_SIZE;

import android.app.AlarmManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.provider.Settings;
import android.support.v4.graphics.ColorUtils;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class WatchFaceDrawer {

    private final MyWatchFaceService.Engine mEngine;
    private final Context mContext;

    public WatchFaceDrawer(MyWatchFaceService.Engine engine, Context context) {
        this.mEngine = engine;
        this.mContext = context;
    }

    public void onDraw(Canvas canvas, Rect bounds) {
        ZonedDateTime mZonedDateTime = mEngine.getZonedDateTime();
        float mCenterX = mEngine.getCenterX();
        float mCenterY = mEngine.getCenterY();
        float mRotate = mEngine.getRotate();

        canvas.rotate(mRotate, mCenterX, mCenterY);
        drawBackground(canvas);

        float lightFactor = updateAndGetLightFactor();
        final float hoursRotation = getDegreesFromNorth(mZonedDateTime);
        int colorFromHour = getColorDegrees(hoursRotation);
        int handPaintColor = getHandPaintColor(lightFactor);

        updatePaints(lightFactor, handPaintColor, mZonedDateTime);

        boolean active = !(mEngine.isAmbient() || mEngine.isDarkMode());

        List<CalendarEvent> events = drawHourAndEvents(canvas, mZonedDateTime, colorFromHour, handPaintColor, lightFactor);

        drawWatchHand(canvas, hoursRotation, colorFromHour, handPaintColor);

        String specials = mEngine.getSpecials();
        drawHourMarkers(canvas, active, specials);

        drawInfoText(canvas, mZonedDateTime, events, specials);

        drawInteractiveElements(canvas);

        mEngine.getDimmingController().setLastDimm(lightFactor);
    }

    private void drawBackground(Canvas canvas) {
        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mEngine.getBackgroundPaint());
    }

    private float updateAndGetLightFactor() {
        float lightFactor = mEngine.getDimmingController().getNextDimm() == null ? 1f : mEngine.getDimmingController().getNextDimm();
        if (!mEngine.isAmbient() && lightFactor <= 2 * mEngine.getDimmingController().getMinLuminance()
                && Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC == Settings.System.getInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)) {
            lightFactor += LOW_LIGHT_BRIGHTNESS_BOOST; // counteract too much automatic dimming in very low light
        }
        return lightFactor;
    }

    private int getHandPaintColor(float lightFactor) {
        if (mEngine.isDarkMode()) {
            return Color.HSVToColor(new float[]{DARK_MODE_HUE, DARK_MODE_SATURATION, lightFactor});
        } else {
            return Color.WHITE;
        }
    }

    private void updatePaints(float lightFactor, int handPaintColor, ZonedDateTime mZonedDateTime) {
        mEngine.getHandPaint().setColor(handPaintColor);
        mEngine.getHourPaint().setColor(handPaintColor);
        mEngine.getMinutesPaint().setColor(handPaintColor);

        boolean betterReadableInDarkMode = mEngine.isDarkMode() && lightFactor <= DimmingController.VERY_DARK;
        mEngine.getHandPaint().setTypeface((!mEngine.isDarkMode() || betterReadableInDarkMode) ? mEngine.getNormalTypeface() : mEngine.getLightTypeface());
        mEngine.getHandPaint().setStrokeWidth(STROKE_WIDTH * (betterReadableInDarkMode ? 2 : 1));

        float strokeWidth = 6;
        Typeface typeface = mEngine.getBoldTypeface();
        if (mEngine.isDarkMode()) {
            strokeWidth = Math.max(lightFactor * 3, 1.5f);
            typeface = lightFactor < DimmingController.VERY_DARK ? mEngine.getLightTypeface() : mEngine.getNormalTypeface();
        }
        mEngine.getHourPaint().setTypeface(typeface);
        mEngine.getMinutesPaint().setTypeface(typeface);
        mEngine.getHourPaint().setStrokeWidth(strokeWidth);
        mEngine.getHourPaint().setStrokeWidth(Math.min(4f, strokeWidth));
    }

    private List<CalendarEvent> drawHourAndEvents(Canvas canvas, ZonedDateTime mZonedDateTime, int colorFromHour, int handPaintColor, float lightFactor) {
        int hour = mZonedDateTime.getHour();
        int minutes = mZonedDateTime.getMinute();

        if (mEngine.isMinimalMode()) {
            return new ArrayList<>();
        }

        List<CalendarEvent> events = mEngine.getCalendarEvents();
        events.sort(Comparator.comparing(CalendarEvent::getBegin));

        // Draw the hour text
        String hourText = "" + hour;
        float decenter = DECENTERING_CORRECTION;
        int alphaHour = mEngine.isDarkMode() ? 218 - Math.min((int) (lightFactor * 200), 100) : 160;
        mEngine.getHourPaint().setColor(colorFromHour);
        mEngine.getHourPaint().setAlpha((int) (alphaHour * (mEngine.isDarkMode() ? lightFactor : 1f)));
        mEngine.getHourPaint().setStyle(Paint.Style.FILL);
        Rect boundsText = new Rect();
        mEngine.getHourPaint().getTextBounds(hourText, 0, hourText.length(), boundsText);
        drawTextUprightFromCenter(canvas, 0, decenter, hourText, mEngine.getHourPaint(), null);

        // Fill background based on meetings
        adaptBackGroundNrWithMeetings(canvas, minutes, boundsText.height(), events);

        // Draw the outline of the hour text
        mEngine.getHourPaint().setColor(handPaintColor);
        mEngine.getHourPaint().setStyle(Paint.Style.STROKE);
        mEngine.getHourPaint().setAlpha(255);
        drawTextUprightFromCenter(canvas, 0, decenter, hourText, mEngine.getHourPaint(), null);

        // Draw step count
        if (!mEngine.isAmbient()) {
            float showMinutesCorrection = mEngine.isShowMinutesDateAndMeetings() ? 1.6f : 0.85f;
            drawTextUprightFromCenter(canvas, 180, mEngine.getCenterY() / 3 * (0.1f + showMinutesCorrection),
                    DE_CH_NUMBER.format(mEngine.getSteps()), mEngine.getHandPaint(), null);
            drawTextUprightFromCenter(canvas, 180, mEngine.getCenterY() / 3 * (0.65f + showMinutesCorrection),
                    DE_CH_NUMBER.format(mEngine.getStepsToday()), mEngine.getHandPaint(), null);
        }

        return events;
    }

    private void drawWatchHand(Canvas canvas, float hoursRotation, int colorFromHour, int handPaintColor) {
        mEngine.getHandPaint().setColor(colorFromHour);
        mEngine.getHandPaint().setAlpha((int) (255 * (mEngine.isDarkMode() ? 1f : 1f)));
        float hourDotCenter = mEngine.getHourHandLength() + 2 * RAND_RESERVE;
        float hourDotRadius = RAND_RESERVE * 2f;
        float hourDotOuterRadius = RAND_RESERVE * 3.5f;

        drawCircle(canvas, hoursRotation, hourDotCenter, hourDotRadius, mEngine.getHandPaint());
        mEngine.getHandPaint().setColor(handPaintColor);
        drawLineFromCenter(canvas, hoursRotation, hourDotCenter - hourDotOuterRadius, mEngine.getCenterX() + RAND_RESERVE, mEngine.getHandPaint());
        mEngine.getHandPaint().setStyle(Paint.Style.STROKE);
        drawCircle(canvas, hoursRotation, hourDotCenter, hourDotOuterRadius, mEngine.getHandPaint());

        if (mEngine.isMinimalMode()) {
            drawCircle(canvas, hoursRotation, 0, mEngine.getCenterX() / 75, mEngine.getHandPaint());
            drawLineFromCenter(canvas, hoursRotation, mEngine.getCenterX() / 75, mEngine.getCenterX() / 6.5f, mEngine.getHandPaint());
        }
        mEngine.getHandPaint().setStyle(Paint.Style.FILL);
    }

    private void drawHourMarkers(Canvas canvas, boolean active, String specials) {
        float hourTextDistance = mEngine.getCenterX() * 0.9f;
        if (!active && mEngine.isMinimalMode()) {
            writeHour(canvas, hourTextDistance, 12, "", false, true, false);
        }
        for (int i = 1; active && i <= 24 - Math.min(1, specials.length()); i++) {
            boolean writeNumber = i % 2 == 0 && (mEngine.isMinimalMode() || (i <= 21 && i >= 3));
            writeHour(canvas, hourTextDistance, i, "" + i, writeNumber, !writeNumber, true);
        }
    }

    private void drawInfoText(Canvas canvas, ZonedDateTime mZonedDateTime, List<CalendarEvent> events, String specials) {

        drawBatteryLowWarning(canvas);

        if (!mEngine.isMinimalMode() && mEngine.isShowMinutesDateAndMeetings()) {
            drawMinutes(canvas, mZonedDateTime);
        }

        drawAlarms(canvas, mZonedDateTime);

        float currentY = drawTopInfo(canvas, mZonedDateTime, specials);

        drawCalendarEvents(canvas, mZonedDateTime, events, currentY);
    }

    private void drawBatteryLowWarning(Canvas canvas) {
        BatteryManager batteryManager = (BatteryManager) mContext.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null && batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) <= LOW_BATTERY_THRESHOLD) {
            mEngine.getHandPaint().setColor(Color.RED);
            drawTextUprightFromCenter(canvas, 0, 0, "Battery: " + batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + "% !", mEngine.getHandPaint(), null);
        }
    }

    private void drawMinutes(Canvas canvas, ZonedDateTime zonedDateTime) {
        String minutesText = zonedDateTime.format(MINUTES_FORMATTER);
        drawTextUprightFromCenter(canvas, 180, mEngine.getCenterY() / 3 * 1.01f, minutesText,
                mEngine.getMinutesPaint(), mEngine.isDarkMode() ? mEngine.getLightTypeface() : null);
    }

    private void drawAlarms(Canvas canvas, ZonedDateTime zonedDateTime) {
        AlarmManager alarm = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) {
            AlarmManager.AlarmClockInfo nextAlarmClock = alarm.getNextAlarmClock();
            if (nextAlarmClock != null && nextAlarmClock.getTriggerTime() - TimeUnit.HOURS.toMillis(ALARM_DISPLAY_WINDOW_HOURS) < zonedDateTime.toInstant().toEpochMilli()) {
                ZonedDateTime alarmTime = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nextAlarmClock.getTriggerTime()), zonedDateTime.getZone());
                String alarmText = "A";
                drawTextUprightFromCenter(canvas, getDegreesFromNorth(alarmTime),
                        mEngine.getHourHandLength(), alarmText, mEngine.getHandPaint(), null);
            }
        }
    }

    private float drawTopInfo(Canvas canvas, ZonedDateTime zonedDateTime, String specials) {
        float currentY = mEngine.getCenterY() - mEngine.getCenterX() * 0.8f;

        // Draw countdown timer
        if (mEngine.getLastCountdownTime() != null) {
            currentY = drawCountdownTimer(canvas, currentY);
        }

        // Draw date and specials
        if (mEngine.isShowMinutesDateAndMeetings() || !mEngine.isAmbient()) {
            currentY = drawDateAndSpecials(canvas, zonedDateTime, specials, currentY);
        }

        // Draw top notification
        String[] topNotificationValues = {"", specials, "+"};
        drawTextUprightFromCenter(canvas, 0, mEngine.getCenterY() - 16, topNotificationValues[Math.min(2, specials.length())], mEngine.getHandPaint(), null);

        return currentY;
    }

    private float drawCountdownTimer(Canvas canvas, float currentY) {
        long correctedTimeMs = mEngine.getLastCountdownTime().toSecondOfDay() * 1000L - (System.currentTimeMillis() - mEngine.getLastReadCountdownTime());
        if (correctedTimeMs >= 0) {
            LocalTime correctedTime = LocalTime.ofSecondOfDay(correctedTimeMs / 1000);
            String countDownTime = "T-";
            if (correctedTime.getHour() >= 1) {
                countDownTime += correctedTime.getHour() + "h";
            } else if (correctedTime.getMinute() >= 1) {
                countDownTime += correctedTime.getMinute() + "\'";
            } else {
                countDownTime += "<" + correctedTime.getSecond() + "s";
            }
            drawTextUprightFromCenter(canvas, 0, mEngine.getCenterY() - currentY, countDownTime, mEngine.getHandPaint(), null);
            return getNextLine(currentY);
        }
        return currentY;
    }

    private float drawDateAndSpecials(Canvas canvas, ZonedDateTime zonedDateTime, String specials, float currentY) {
        String topText = zonedDateTime.format(ISO_DATE_WITH_DAYOFWEEK);
        topText = mEngine.isMinimalMode() ? "" : topText;
        drawTextUprightFromCenter(canvas, 0, mEngine.getCenterY() - currentY, topText, mEngine.getHandPaint(), null);
        currentY = getNextLine(currentY);
        if (!mEngine.isMinimalMode() && (specials.length() > 1)) {
            drawTextUprightFromCenter(canvas, 0, mEngine.getCenterY() - currentY, specials, mEngine.getHandPaint(), null);
            currentY = getNextLine(currentY);
        }
        return currentY;
    }

    private void drawCalendarEvents(Canvas canvas, ZonedDateTime zonedDateTime, List<CalendarEvent> events, float currentY) {
        if (mEngine.isShowMinutesDateAndMeetings()) {
            for (CalendarEvent event : events) {
                float degreesFromNorth = getDegreesFromNorth(event.getBegin());
                mEngine.getHandPaint().setStyle(Paint.Style.STROKE);
                drawCircle(canvas, degreesFromNorth, mEngine.getHourHandLength(), mEngine.isMinimalMode() ? EVENT_MARKER_RADIUS_MINIMAL : EVENT_MARKER_RADIUS, mEngine.getHandPaint());
                mEngine.getHandPaint().setStyle(Paint.Style.FILL);
                long inFuture = event.getBegin().toInstant().toEpochMilli() - zonedDateTime.toInstant().toEpochMilli();
                if (!mEngine.isMinimalMode() && (mEngine.isShowMinutesDateAndMeetings() || !mEngine.isAmbient()) && inFuture <= TimeUnit.MINUTES.toMillis(MEETING_PRE_ANNOUNCE_DURATION)) {
                    String title = event.getTitle();
                    if (title == null || title.trim().length() == 0) title = "(ohne Titel)";
                    boolean isInFuture = inFuture < 0;
                    String eventHrTitle = isInFuture ?
                            "-" + TimeUnit.MILLISECONDS.toMinutes(event.getEnd().toInstant().toEpochMilli() - zonedDateTime.toInstant().toEpochMilli())
                            : "" + TimeUnit.MILLISECONDS.toMinutes(inFuture);
                    eventHrTitle += " " + title;
                    int minimizedLength = Math.min(EVENT_TITLE_MAX_LENGTH_LINE_1, eventHrTitle.length());
                    drawTextUprightFromCenter(canvas, 0, mEngine.getCenterY() - currentY,
                            eventHrTitle.substring(0, minimizedLength), mEngine.getHandPaint(), isInFuture ? mEngine.getLightTypeface() : null);
                    currentY = getNextLine(currentY);
                    if (eventHrTitle.length() > minimizedLength) {
                        drawTextUprightFromCenter(canvas, 0, mEngine.getCenterY() - currentY,
                                eventHrTitle.substring(minimizedLength, Math.min(EVENT_TITLE_MAX_LENGTH, eventHrTitle.length())), mEngine.getHandPaint(), isInFuture ? mEngine.getLightTypeface() : null);
                        currentY = getNextLine(currentY);
                    }
                }
            }
        }
    }


    private void drawInteractiveElements(Canvas canvas) {
        if (!mEngine.isAmbient()) {
            float buttonRadius = mEngine.getCenterX() / 3 * 2;
            if (!mEngine.isDarkMode()) {
                drawTextUprightFromCenter(canvas, 0, buttonRadius, "●", mEngine.getHandPaint(), mEngine.getBoldTypeface());
            } else {
                drawTextUprightFromCenter(canvas, 0, buttonRadius, "○", mEngine.getHandPaint(), mEngine.getLightTypeface());
            }
            drawTextUprightFromCenter(canvas, mEngine.getRotate() + 90, buttonRadius, "↷", mEngine.getHandPaint(), mEngine.getBoldTypeface());
            if (!mEngine.isMinimalMode() && !mEngine.isShowMinutesDateAndMeetings()) {
                drawTextUprightFromCenter(canvas, 180, mEngine.getCenterY() / 3 * 2,
                        mEngine.getZonedDateTime().format(MINUTES_FORMATTER), mEngine.getHandPaint(), null);
            }
        }
    }

    private void adaptBackGroundNrWithMeetings(Canvas canvas, int minutes, float textSize, List<CalendarEvent> events) {
        float minuteWidth = textSize / 60f;
        float remainingRelativeHour = 1 - (minutes / 60f);
        int lastMinutes = minutes;

        for (CalendarEvent event : events) {
            if (!event.isAllDay() && isUpcomingMeeting(event)) {
                long minutesOfEvent = minutes + TimeUnit.MILLISECONDS.toMinutes(event.getBegin().toInstant().toEpochMilli() - mEngine.getZonedDateTime().toInstant().toEpochMilli());

                if (minutesOfEvent >= 60) {
                    drawMeetingIndicatorLine(canvas, textSize, minuteWidth, (minutesOfEvent - 60) / 60f);
                } else {
                    remainingRelativeHour -= drawMeetingIndicatorBlank(canvas, textSize, remainingRelativeHour, lastMinutes, (int) minutesOfEvent);
                    lastMinutes = (int) minutesOfEvent + 1;
                }
            }
        }
        drawRemainingHourIndicator(canvas, textSize, remainingRelativeHour);
    }

    private boolean isUpcomingMeeting(CalendarEvent event) {
        long eventLengthMs = event.getEnd().toInstant().toEpochMilli() - event.getBegin().toInstant().toEpochMilli();
        long inFuture = event.getBegin().toInstant().toEpochMilli() - mEngine.getZonedDateTime().toInstant().toEpochMilli();
        return eventLengthMs < TimeUnit.HOURS.toMillis(24) && inFuture > 0 && inFuture <= TimeUnit.MINUTES.toMillis(MEETING_PRE_ANNOUNCE_DURATION);
    }

    private void drawMeetingIndicatorLine(Canvas canvas, float textSize, float minuteWidth, float relativeMeetingHour) {
        float yFill = mEngine.getCenterY() - (textSize * (0.5f - relativeMeetingHour));
        mEngine.getBackgroundPaint().setStrokeWidth(minuteWidth);
        canvas.drawLine(mEngine.getCenterX() - textSize, yFill, mEngine.getCenterX() + textSize, yFill, mEngine.getBackgroundPaint());
    }

    private float drawMeetingIndicatorBlank(Canvas canvas, float textSize, float remainingRelativeHour, int lastMinutes, int minutesOfEvent) {
        float relativeHourToBlank = (minutesOfEvent - lastMinutes) / 60f - 1f / 60f;
        float blankHeight = textSize * relativeHourToBlank;
        float startY = mEngine.getCenterY() + textSize * (0.5f - remainingRelativeHour);
        canvas.drawRect(mEngine.getCenterX() - textSize, startY, mEngine.getCenterX() + textSize, startY + blankHeight, mEngine.getBackgroundPaint());
        return relativeHourToBlank + 1 / 60f;
    }

    private void drawRemainingHourIndicator(Canvas canvas, float textSize, float remainingRelativeHour) {
        mEngine.getBackgroundPaint().setStrokeWidth(textSize * remainingRelativeHour);
        float yFill = mEngine.getCenterY() + textSize / 2 * (1 - remainingRelativeHour);
        canvas.drawLine(mEngine.getCenterX() - textSize, yFill, mEngine.getCenterX() + textSize, yFill, mEngine.getBackgroundPaint());
    }


    private void writeHour(Canvas canvas, float radiusCenter, int hour, boolean writeNumber, boolean writeMarker) {
        writeHour(canvas, radiusCenter, hour, "" + hour, writeNumber, writeMarker, true);
    }

    private void writeHour(Canvas canvas, float radiusCenter, int hour, String hourText,
                           boolean writeNumber, boolean writeMarker, boolean adjustColor) {
        float degreesFromNorth = hour * DEGREES_PER_HOUR;
        float dotDistance = mEngine.getHourHandLength();

        int handColor = mEngine.getHandPaint().getColor();
        if (adjustColor) {
            mEngine.getHandPaint().setColor(getColorDegrees(degreesFromNorth));
        }

        if (writeNumber) {
            drawTextUprightFromCenter(canvas, degreesFromNorth, radiusCenter,
                    hourText, mEngine.getHandPaint(), null);
        }
        if (writeMarker) {
            drawCircle(canvas, degreesFromNorth, dotDistance, HOUR_MARKER_RADIUS, mEngine.getHandPaint());
            // black dot in the middle
            Float nextDimmObject = mEngine.getDimmingController().getNextDimm();
            float nextDimm = nextDimmObject == null ? 1 : nextDimmObject;
            drawCircle(canvas, degreesFromNorth, dotDistance, mEngine.isDarkMode() && nextDimm < DimmingController.VERY_DARK ? 3 : 2, mEngine.getBackgroundPaint()
            );
        }
        mEngine.getHandPaint().setColor(handColor);
    }

    private void drawCircle(Canvas canvas, float rotationFromNorth, float distanceFromCenter, float radius, Paint paint) {
        canvas.save();
        canvas.rotate(rotationFromNorth, mEngine.getCenterX(), mEngine.getCenterY());
        canvas.drawCircle(mEngine.getCenterX(), mEngine.getCenterY() - distanceFromCenter, radius, paint);
        canvas.restore();
    }

    private void drawLineFromCenter(Canvas canvas, float degreesFromNorth, float startFromCenter, float endFromCenter, Paint paint) {
        canvas.save();
        canvas.rotate(degreesFromNorth, mEngine.getCenterX(), mEngine.getCenterY());
        canvas.drawLine(mEngine.getCenterX(), mEngine.getCenterY() - startFromCenter, mEngine.getCenterX(), mEngine.getCenterY() - endFromCenter,
                paint);
        canvas.restore();
    }

    private void drawTextUprightFromCenter(Canvas canvas, float degreesFromNorth, float radiusCenter, String text, Paint paint, Typeface typeface) {
        float textLengthX = paint.measureText(text);
        float textLengthY = paint.getTextSize();
        //                          center text
        float x = mEngine.getCenterX() - textLengthX / 2 + radiusCenter *
                (float) Math.cos(Math.toRadians(degreesFromNorth - 90f));
        float y = mEngine.getCenterY() + textLengthY / 24 * 7 +
                radiusCenter *
                (float) Math.sin(Math.toRadians(degreesFromNorth - 90f));
        if (typeface != null) {
            Typeface prevTypeface = paint.getTypeface();
            paint.setTypeface(typeface);
            canvas.drawText(text, x, y, paint);
            paint.setTypeface(prevTypeface);
        } else {
            canvas.drawText(text, x, y, paint);
        }
    }

    private int getColorDegrees(float degreesFromNorth) {
        degreesFromNorth = degreesFromNorth % 360;
        float relativeAdvance = degreesFromNorth / 360 * (COLORS.length);
        int firstColorIndex = (int) relativeAdvance;
        float amountFirstColor = relativeAdvance - firstColorIndex;
        int secondColorIndex = (firstColorIndex + 1) % (COLORS.length);
        int colorForTime = ColorUtils.blendARGB(COLORS[firstColorIndex], COLORS[secondColorIndex], amountFirstColor);
        float[] hsvVals = new float[3];
        Color.colorToHSV(colorForTime, hsvVals);
        // full luminance
        hsvVals[2] = 1;
        return Color.HSVToColor(hsvVals);
    }

    private float getNextLine(float currentY) {
        return currentY + 1.1f * TEXT_SIZE;
    }

    private float getDegreesFromNorth(ZonedDateTime time) {
        return time.getHour() * DEGREES_PER_HOUR + time.getMinute() * DEGREES_PER_MINUTE;
    }
}
