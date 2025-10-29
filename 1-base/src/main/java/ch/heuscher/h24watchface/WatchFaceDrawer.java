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

    private final Context mContext;

    private Paint mBackgroundPaint;
    private Paint mHandPaint;
    private Paint mHourPaint;
    private Paint mMinutesPaint;

    private final Typeface mLight;
    private final Typeface mNormal;
    private final Typeface mBold;

    private float mHourHandLength;
    private float mCenterX;
    private float mCenterY;
    private int mWidth;
    private int mHeight;

    public WatchFaceDrawer(Context context) {
        this.mContext = context;

        mLight = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        mNormal = Typeface.create("sans-serif", Typeface.NORMAL);
        mBold = Typeface.create("sans-serif", Typeface.BOLD);

        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(Color.BLACK);
        mBackgroundPaint.setAntiAlias(true);

        mHandPaint = new Paint();
        mHandPaint.setStrokeWidth(STROKE_WIDTH);
        mHandPaint.setAntiAlias(true);
        mHandPaint.setStrokeCap(Paint.Cap.ROUND);
        mHandPaint.setTextSize(TEXT_SIZE);
        mHandPaint.setTypeface(mNormal);
        mHandPaint.setShadowLayer(8, 0, 0, Color.BLACK);

        mHourPaint = new Paint();
        mHourPaint.setAntiAlias(true);
        mHourPaint.setLetterSpacing(-0.065f);

        mMinutesPaint = new Paint();
        mMinutesPaint.setAntiAlias(true);
        mMinutesPaint.setShadowLayer(8, 0, 0, Color.BLACK);
        mMinutesPaint.setStyle(Paint.Style.STROKE);
    }

    public void onSurfaceChanged(int width, int height) {
        mWidth = width;
        mHeight = height;
        mCenterX = width / 2f;
        mCenterY = height / 2f;
        mHourHandLength = mCenterX - 2 * RAND_RESERVE;
        mHourPaint.setTextSize(height * 0.95f);
        mMinutesPaint.setTextSize(mCenterY / 2);
    }

    public void onDraw(Canvas canvas, MyWatchFaceService.Engine engine, ZonedDateTime zonedDateTime, DimmingController dimmingController) {
        float mRotate = engine.getRotate();

        canvas.rotate(mRotate, mCenterX, mCenterY);
        drawBackground(canvas);

        float lightFactor = updateAndGetLightFactor(engine, dimmingController);
        final float hoursRotation = getDegreesFromNorth(zonedDateTime);
        int colorFromHour = getColorDegrees(hoursRotation);
        int handPaintColor = getHandPaintColor(engine.isDarkMode(), lightFactor);

        updatePaints(engine, lightFactor, handPaintColor);

        boolean active = !(engine.isAmbient() || engine.isDarkMode());

        List<CalendarEvent> events = drawHourAndEvents(canvas, engine, zonedDateTime, colorFromHour, handPaintColor, lightFactor);

        drawWatchHand(canvas, engine, hoursRotation, colorFromHour, handPaintColor, lightFactor);

        String specials = engine.getSpecials();
        drawHourMarkers(canvas, engine, active, specials);

        drawInfoText(canvas, engine, zonedDateTime, events, specials);

        drawInteractiveElements(canvas, engine);

        dimmingController.setLastDimm(lightFactor);
    }

    private void drawBackground(Canvas canvas) {
        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);
    }

    private float updateAndGetLightFactor(MyWatchFaceService.Engine engine, DimmingController dimmingController) {
        float lightFactor = dimmingController.getNextDimm() == null ? 1f : dimmingController.getNextDimm();
        if (!engine.isAmbient() && lightFactor <= 2 * dimmingController.getMinLuminance()
                && Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC == Settings.System.getInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)) {
            lightFactor += LOW_LIGHT_BRIGHTNESS_BOOST; // counteract too much automatic dimming in very low light
        }
        return lightFactor;
    }

    private int getHandPaintColor(boolean isDarkMode, float lightFactor) {
        if (isDarkMode) {
            return Color.HSVToColor(new float[]{DARK_MODE_HUE, DARK_MODE_SATURATION, lightFactor});
        } else {
            return Color.WHITE;
        }
    }

    private void updatePaints(MyWatchFaceService.Engine engine, float lightFactor, int handPaintColor) {
        mHandPaint.setColor(handPaintColor);
        mHourPaint.setColor(handPaintColor);
        mMinutesPaint.setColor(handPaintColor);

        boolean betterReadableInDarkMode = engine.isDarkMode() && lightFactor <= DimmingController.VERY_DARK;
        mHandPaint.setTypeface((!engine.isDarkMode() || betterReadableInDarkMode) ? mNormal : mLight);
        mHandPaint.setStrokeWidth(STROKE_WIDTH * (betterReadableInDarkMode ? 2 : 1));

        float strokeWidth = 6;
        Typeface typeface = mBold;
        if (engine.isDarkMode()) {
            strokeWidth = Math.max(lightFactor * 3, 1.5f);
            typeface = lightFactor < DimmingController.VERY_DARK ? mLight : mNormal;
        }
        mHourPaint.setTypeface(typeface);
        mMinutesPaint.setTypeface(typeface);
        mHourPaint.setStrokeWidth(strokeWidth);
        mMinutesPaint.setStrokeWidth(Math.min(4f, strokeWidth));
    }

    private int calculateAlpha(boolean isDarkMode, float lightFactor) {
        return isDarkMode ? 218 - Math.min((int) (lightFactor * 200), 100) : 160;
    }

    private List<CalendarEvent> drawHourAndEvents(Canvas canvas, MyWatchFaceService.Engine engine, ZonedDateTime mZonedDateTime, int colorFromHour, int handPaintColor, float lightFactor) {
        int hour = mZonedDateTime.getHour();
        int minutes = mZonedDateTime.getMinute();

        if (engine.isMinimalMode()) {
            return new ArrayList<>();
        }

        List<CalendarEvent> events = engine.getCalendarEvents();
        events.sort(Comparator.comparing(CalendarEvent::getBegin));

        // Draw the hour text
        String hourText = "" + hour;
        float decenter = DECENTERING_CORRECTION;
        mHourPaint.setColor(colorFromHour);
        mHourPaint.setAlpha(calculateAlpha(engine.isDarkMode(), lightFactor));
        mHourPaint.setStyle(Paint.Style.FILL);
        Rect boundsText = new Rect();
        mHourPaint.getTextBounds(hourText, 0, hourText.length(), boundsText);
        drawTextUprightFromCenter(canvas, 0, decenter, hourText, mHourPaint, null);

        // Fill background based on meetings
        adaptBackGroundNrWithMeetings(canvas, engine, minutes, boundsText.height(), events);

        // Draw the outline of the hour text
        mHourPaint.setColor(handPaintColor);
        mHourPaint.setStyle(Paint.Style.STROKE);
        mHourPaint.setAlpha(255);
        drawTextUprightFromCenter(canvas, 0, decenter, hourText, mHourPaint, null);

        // Draw step count
        if (!engine.isAmbient()) {
            float showMinutesCorrection = engine.isShowMinutesDateAndMeetings() ? 1.6f : 0.85f;
            drawTextUprightFromCenter(canvas, 180, mCenterY / 3 * (0.1f + showMinutesCorrection),
                    DE_CH_NUMBER.format(engine.getSteps()), mHandPaint, null);
            drawTextUprightFromCenter(canvas, 180, mCenterY / 3 * (0.65f + showMinutesCorrection),
                    DE_CH_NUMBER.format(engine.getStepsToday()), mHandPaint, null);
        }

        return events;
    }

    private void drawWatchHand(Canvas canvas, MyWatchFaceService.Engine engine, float hoursRotation, int colorFromHour, int handPaintColor, float lightFactor) {
        mHandPaint.setColor(colorFromHour);
        mHandPaint.setAlpha(calculateAlpha(engine.isDarkMode(), lightFactor));
        float hourDotCenter = mHourHandLength + 2 * RAND_RESERVE;
        float hourDotRadius = RAND_RESERVE * 2f;
        float hourDotOuterRadius = RAND_RESERVE * 3.5f;

        drawCircle(canvas, hoursRotation, hourDotCenter, hourDotRadius, mHandPaint);
        mHandPaint.setColor(handPaintColor);
        drawLineFromCenter(canvas, hoursRotation, hourDotCenter - hourDotOuterRadius, mCenterX + RAND_RESERVE, mHandPaint);
        mHandPaint.setStyle(Paint.Style.STROKE);
        drawCircle(canvas, hoursRotation, hourDotCenter, hourDotOuterRadius, mHandPaint);

        if (engine.isMinimalMode()) {
            drawCircle(canvas, hoursRotation, 0, mCenterX / 75, mHandPaint);
            drawLineFromCenter(canvas, hoursRotation, mCenterX / 75, mCenterX / 6.5f, mHandPaint);
        }
        mHandPaint.setStyle(Paint.Style.FILL);
    }

    private void drawHourMarkers(Canvas canvas, MyWatchFaceService.Engine engine, boolean active, String specials) {
        float hourTextDistance = mCenterX * 0.9f;
        if (!active && engine.isMinimalMode()) {
            writeHour(canvas, engine, hourTextDistance, 12, "", false, true, false);
        }
        for (int i = 1; active && i <= 24 - Math.min(1, specials.length()); i++) {
            boolean writeNumber = i % 2 == 0 && (engine.isMinimalMode() || (i <= 21 && i >= 3));
            writeHour(canvas, engine, hourTextDistance, i, "" + i, writeNumber, !writeNumber, true);
        }
    }

    private void drawInfoText(Canvas canvas, MyWatchFaceService.Engine engine, ZonedDateTime mZonedDateTime, List<CalendarEvent> events, String specials) {

        drawBatteryLowWarning(canvas);

        if (!engine.isMinimalMode() && engine.isShowMinutesDateAndMeetings()) {
            drawMinutes(canvas, engine, mZonedDateTime);
        }

        drawAlarms(canvas, mZonedDateTime);

        float currentY = drawTopInfo(canvas, engine, mZonedDateTime, specials);

        drawCalendarEvents(canvas, engine, mZonedDateTime, events, currentY);
    }

    private void drawBatteryLowWarning(Canvas canvas) {
        BatteryManager batteryManager = (BatteryManager) mContext.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null && batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) <= LOW_BATTERY_THRESHOLD) {
            mHandPaint.setColor(Color.RED);
            drawTextUprightFromCenter(canvas, 0, 0, "Battery: " + batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + "% !", mHandPaint, null);
        }
    }

    private void drawMinutes(Canvas canvas, MyWatchFaceService.Engine engine, ZonedDateTime zonedDateTime) {
        String minutesText = zonedDateTime.format(MINUTES_FORMATTER);
        drawTextUprightFromCenter(canvas, 180, mCenterY / 3 * 1.01f, minutesText,
                mMinutesPaint, engine.isDarkMode() ? mLight : null);
    }

    private void drawAlarms(Canvas canvas, ZonedDateTime zonedDateTime) {
        AlarmManager alarm = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) {
            AlarmManager.AlarmClockInfo nextAlarmClock = alarm.getNextAlarmClock();
            if (nextAlarmClock != null && nextAlarmClock.getTriggerTime() - TimeUnit.HOURS.toMillis(ALARM_DISPLAY_WINDOW_HOURS) < zonedDateTime.toInstant().toEpochMilli()) {
                ZonedDateTime alarmTime = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nextAlarmClock.getTriggerTime()), zonedDateTime.getZone());
                String alarmText = "A";
                drawTextUprightFromCenter(canvas, getDegreesFromNorth(alarmTime),
                        mHourHandLength, alarmText, mHandPaint, null);
            }
        }
    }

    private float drawTopInfo(Canvas canvas, MyWatchFaceService.Engine engine, ZonedDateTime zonedDateTime, String specials) {
        float currentY = mCenterY - mCenterX * 0.8f;

        // Draw countdown timer
        if (engine.getLastCountdownTime() != null) {
            currentY = drawCountdownTimer(canvas, engine, currentY);
        }

        // Draw date and specials
        if (engine.isShowMinutesDateAndMeetings() || !engine.isAmbient()) {
            currentY = drawDateAndSpecials(canvas, engine, zonedDateTime, specials, currentY);
        }

        // Draw top notification
        String[] topNotificationValues = {"", specials, "+"};
        drawTextUprightFromCenter(canvas, 0, mCenterY - 16, topNotificationValues[Math.min(2, specials.length())], mHandPaint, null);

        return currentY;
    }

    private float drawCountdownTimer(Canvas canvas, MyWatchFaceService.Engine engine, float currentY) {
        long correctedTimeMs = engine.getLastCountdownTime().toSecondOfDay() * 1000L - (System.currentTimeMillis() - engine.getLastReadCountdownTime());
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
            drawTextUprightFromCenter(canvas, 0, mCenterY - currentY, countDownTime, mHandPaint, null);
            return getNextLine(currentY);
        }
        return currentY;
    }

    private float drawDateAndSpecials(Canvas canvas, MyWatchFaceService.Engine engine, ZonedDateTime zonedDateTime, String specials, float currentY) {
        String topText = zonedDateTime.format(ISO_DATE_WITH_DAYOFWEEK);
        topText = engine.isMinimalMode() ? "" : topText;
        drawTextUprightFromCenter(canvas, 0, mCenterY - currentY, topText, mHandPaint, null);
        currentY = getNextLine(currentY);
        if (!engine.isMinimalMode() && (specials.length() > 1)) {
            drawTextUprightFromCenter(canvas, 0, mCenterY - currentY, specials, mHandPaint, null);
            currentY = getNextLine(currentY);
        }
        return currentY;
    }

    private void drawCalendarEvents(Canvas canvas, MyWatchFaceService.Engine engine, ZonedDateTime zonedDateTime, List<CalendarEvent> events, float currentY) {
        if (engine.isShowMinutesDateAndMeetings()) {
            for (CalendarEvent event : events) {
                float degreesFromNorth = getDegreesFromNorth(event.getBegin());
                mHandPaint.setStyle(Paint.Style.STROKE);
                drawCircle(canvas, degreesFromNorth, mHourHandLength, engine.isMinimalMode() ? EVENT_MARKER_RADIUS_MINIMAL : EVENT_MARKER_RADIUS, mHandPaint);
                mHandPaint.setStyle(Paint.Style.FILL);
                long inFuture = event.getBegin().toInstant().toEpochMilli() - zonedDateTime.toInstant().toEpochMilli();
                if (!engine.isMinimalMode() && (engine.isShowMinutesDateAndMeetings() || !engine.isAmbient()) && inFuture <= TimeUnit.MINUTES.toMillis(MEETING_PRE_ANNOUNCE_DURATION)) {
                    String title = event.getTitle();
                    if (title == null || title.trim().length() == 0) title = "(ohne Titel)";
                    boolean isInFuture = inFuture < 0;
                    String eventHrTitle = isInFuture ?
                            "-" + TimeUnit.MILLISECONDS.toMinutes(event.getEnd().toInstant().toEpochMilli() - zonedDateTime.toInstant().toEpochMilli())
                            : "" + TimeUnit.MILLISECONDS.toMinutes(inFuture);
                    eventHrTitle += " " + title;
                    int minimizedLength = Math.min(EVENT_TITLE_MAX_LENGTH_LINE_1, eventHrTitle.length());
                    drawTextUprightFromCenter(canvas, 0, mCenterY - currentY,
                            eventHrTitle.substring(0, minimizedLength), mHandPaint, isInFuture ? mLight : null);
                    currentY = getNextLine(currentY);
                    if (eventHrTitle.length() > minimizedLength) {
                        drawTextUprightFromCenter(canvas, 0, mCenterY - currentY,
                                eventHrTitle.substring(minimizedLength, Math.min(EVENT_TITLE_MAX_LENGTH, eventHrTitle.length())), mHandPaint, isInFuture ? mLight : null);
                        currentY = getNextLine(currentY);
                    }
                }
            }
        }
    }


    private void drawInteractiveElements(Canvas canvas, MyWatchFaceService.Engine engine) {
        if (!engine.isAmbient()) {
            float buttonRadius = mCenterX / 3 * 2;
            if (!engine.isDarkMode()) {
                drawTextUprightFromCenter(canvas, 0, buttonRadius, "●", mHandPaint, mBold);
            } else {
                drawTextUprightFromCenter(canvas, 0, buttonRadius, "○", mHandPaint, mLight);
            }
            drawTextUprightFromCenter(canvas, engine.getRotate() + 90, buttonRadius, "↷", mHandPaint, mBold);
            if (!engine.isMinimalMode() && !engine.isShowMinutesDateAndMeetings()) {
                drawTextUprightFromCenter(canvas, 180, mCenterY / 3 * 2,
                        engine.getZonedDateTime().format(MINUTES_FORMATTER), mHandPaint, null);
            }
        }
    }

    private void adaptBackGroundNrWithMeetings(Canvas canvas, MyWatchFaceService.Engine engine, int minutes, float textSize, List<CalendarEvent> events) {
        float minuteWidth = textSize / 60f;
        float remainingRelativeHour = 1 - (minutes / 60f);
        int lastMinutes = minutes;

        for (CalendarEvent event : events) {
            if (!event.isAllDay() && isUpcomingMeeting(engine, event)) {
                long minutesOfEvent = minutes + TimeUnit.MILLISECONDS.toMinutes(event.getBegin().toInstant().toEpochMilli() - engine.getZonedDateTime().toInstant().toEpochMilli());

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

    private boolean isUpcomingMeeting(MyWatchFaceService.Engine engine, CalendarEvent event) {
        long eventLengthMs = event.getEnd().toInstant().toEpochMilli() - event.getBegin().toInstant().toEpochMilli();
        long inFuture = event.getBegin().toInstant().toEpochMilli() - engine.getZonedDateTime().toInstant().toEpochMilli();
        return eventLengthMs < TimeUnit.HOURS.toMillis(24) && inFuture > 0 && inFuture <= TimeUnit.MINUTES.toMillis(MEETING_PRE_ANNOUNCE_DURATION);
    }

    private void drawMeetingIndicatorLine(Canvas canvas, float textSize, float minuteWidth, float relativeMeetingHour) {
        float yFill = mCenterY - (textSize * (0.5f - relativeMeetingHour));
        mBackgroundPaint.setStrokeWidth(minuteWidth);
        canvas.drawLine(mCenterX - textSize, yFill, mCenterX + textSize, yFill, mBackgroundPaint);
    }

    private float drawMeetingIndicatorBlank(Canvas canvas, float textSize, float remainingRelativeHour, int lastMinutes, int minutesOfEvent) {
        float relativeHourToBlank = (minutesOfEvent - lastMinutes) / 60f - 1f / 60f;
        float blankHeight = textSize * relativeHourToBlank;
        float startY = mCenterY + textSize * (0.5f - remainingRelativeHour);
        canvas.drawRect(mCenterX - textSize, startY, mCenterX + textSize, startY + blankHeight, mBackgroundPaint);
        return relativeHourToBlank + 1 / 60f;
    }

    private void drawRemainingHourIndicator(Canvas canvas, float textSize, float remainingRelativeHour) {
        mBackgroundPaint.setStrokeWidth(textSize * remainingRelativeHour);
        float yFill = mCenterY + textSize / 2 * (1 - remainingRelativeHour);
        canvas.drawLine(mCenterX - textSize, yFill, mCenterX + textSize, yFill, mBackgroundPaint);
    }


    private void writeHour(Canvas canvas, MyWatchFaceService.Engine engine, float radiusCenter, int hour, boolean writeNumber, boolean writeMarker) {
        writeHour(canvas, engine, radiusCenter, hour, "" + hour, writeNumber, writeMarker, true);
    }

    private void writeHour(Canvas canvas, MyWatchFaceService.Engine engine, float radiusCenter, int hour, String hourText,
                           boolean writeNumber, boolean writeMarker, boolean adjustColor) {
        float degreesFromNorth = hour * DEGREES_PER_HOUR;

        int handColor = mHandPaint.getColor();
        if (adjustColor) {
            mHandPaint.setColor(getColorDegrees(degreesFromNorth));
        }

        if (writeNumber) {
            drawTextUprightFromCenter(canvas, degreesFromNorth, radiusCenter,
                    hourText, mHandPaint, null);
        }
        if (writeMarker) {
            drawCircle(canvas, degreesFromNorth, mHourHandLength, HOUR_MARKER_RADIUS, mHandPaint);
            // black dot in the middle
            Float nextDimmObject = engine.getDimmingController().getNextDimm();
            float nextDimm = nextDimmObject == null ? 1 : nextDimmObject;
            drawCircle(canvas, degreesFromNorth, mHourHandLength, engine.isDarkMode() && nextDimm < DimmingController.VERY_DARK ? 3 : 2, mBackgroundPaint
            );
        }
        mHandPaint.setColor(handColor);
    }

    private void drawCircle(Canvas canvas, float rotationFromNorth, float distanceFromCenter, float radius, Paint paint) {
        canvas.save();
        canvas.rotate(rotationFromNorth, mCenterX, mCenterY);
        canvas.drawCircle(mCenterX, mCenterY - distanceFromCenter, radius, paint);
        canvas.restore();
    }

    private void drawLineFromCenter(Canvas canvas, float degreesFromNorth, float startFromCenter, float endFromCenter, Paint paint) {
        canvas.save();
        canvas.rotate(degreesFromNorth, mCenterX, mCenterY);
        canvas.drawLine(mCenterX, mCenterY - startFromCenter, mCenterX, mCenterY - endFromCenter,
                paint);
        canvas.restore();
    }

    private void drawTextUprightFromCenter(Canvas canvas, float degreesFromNorth, float radiusCenter, String text, Paint paint, Typeface typeface) {
        float textLengthX = paint.measureText(text);
        float textLengthY = paint.getTextSize();
        //                          center text
        float x = mCenterX - textLengthX / 2 + radiusCenter *
                (float) Math.cos(Math.toRadians(degreesFromNorth - 90f));
        float y = mCenterY + textLengthY / 24 * 7 +
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

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }
}
