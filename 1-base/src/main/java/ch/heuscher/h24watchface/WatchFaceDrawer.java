package ch.heuscher.h24watchface;

import static ch.heuscher.h24watchface.WatchFaceConstants.ALARM_DISPLAY_WINDOW_HOURS;
import static ch.heuscher.h24watchface.WatchFaceConstants.COLORS;
import static ch.heuscher.h24watchface.WatchFaceConstants.COMPLICATION_ID;
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
import android.support.wearable.complications.SystemProviders;

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
        Paint mBackgroundPaint = mEngine.getBackgroundPaint();
        Paint mHandPaint = mEngine.getHandPaint();
        Paint mHourPaint = mEngine.getHourPaint();
        Paint mMinutesPaint = mEngine.getMinutesPaint();
        float mHourHandLength = mEngine.getHourHandLength();
        DimmingController mDimmingController = mEngine.getDimmingController();
        boolean mMinimalMode = mEngine.isMinimalMode();
        boolean mShowMinutesDateAndMeetings = mEngine.isShowMinutesDateAndMeetings();
        LocalTime mLastCountdownTime = mEngine.getLastCountdownTime();
        long mLastReadCountdownTime = mEngine.getLastReadCountdownTime();
        String mDebug = mEngine.getDebug();
        float mRotate = mEngine.getRotate();
        Typeface mLight = mEngine.getLightTypeface();
        Typeface mNormal = mEngine.getNormalTypeface();
        Typeface mBold = mEngine.getBoldTypeface();

        canvas.rotate(mRotate, mCenterX, mCenterY);
        float hourTextDistance = mCenterX * 0.9f;
        boolean active = !(mEngine.isAmbient() || mEngine.isDarkMode());

        // Draw the background.
        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);

        int hour = mZonedDateTime.getHour();
        int minutes = mZonedDateTime.getMinute();

        // Hack to set and re-set countdown-timer
        mEngine.setActiveComplications(SystemProviders.DATE);
        mEngine.setActiveComplications(COMPLICATION_ID);

        /* These calculations reflect the rotation in degrees per unit of time, e.g., 360 / 60 = 6 and 360 / 12 = 30. */
        final float hoursRotation = getDegreesFromNorth(mZonedDateTime);

        int batteryCharge = 100;
        BatteryManager batteryManager = (BatteryManager) mContext.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null) {
            batteryCharge = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }
        // Farbe rot wenn wenig Batterie
        if (batteryCharge <= LOW_BATTERY_THRESHOLD) {
            mHandPaint.setColor(Color.RED);
            drawTextUprightFromCenter(canvas, 0, 0, "Battery: " + batteryCharge + "% !", mHandPaint, null);
        }

        int handPaintColor = Color.WHITE;
        float lightFactor = mDimmingController.getNextDimm() == null ? 1f : mDimmingController.getNextDimm();
        if (!mEngine.isAmbient() && lightFactor <= 2 * mDimmingController.getMinLuminance()
                && Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC == Settings.System.getInt(mContext.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)) {
            lightFactor += LOW_LIGHT_BRIGHTNESS_BOOST; // counteract too much automatic dimming in very low light
        }
        if (mEngine.isDarkMode()) {
            handPaintColor = Color.HSVToColor(new float[]{DARK_MODE_HUE, DARK_MODE_SATURATION, lightFactor});
        }
        mHandPaint.setColor(handPaintColor);
        mHourPaint.setColor(handPaintColor);
        mMinutesPaint.setColor(handPaintColor);

        // Light typeface if there's enough light
        boolean betterReadableInDarkMode = mEngine.isDarkMode() && lightFactor <= DimmingController.VERY_DARK;
        mHandPaint.setTypeface((!mEngine.isDarkMode() || betterReadableInDarkMode) ? mNormal : mLight);
        mHandPaint.setStrokeWidth(STROKE_WIDTH * (betterReadableInDarkMode ? 2 : 1));

        float strokeWidth = 6;
        int alphaHour = 160;
        Typeface typeface = mBold;
        if (mEngine.isDarkMode()) {
            strokeWidth = Math.max(lightFactor * 3, 1.5f);
            alphaHour = 218 - Math.min((int) (lightFactor * 200), 100);
            typeface = lightFactor < DimmingController.VERY_DARK ? mLight : mNormal;
        }
        mHourPaint.setTypeface(typeface);
        mMinutesPaint.setTypeface(typeface);
        mHourPaint.setStrokeWidth(strokeWidth);
        mMinutesPaint.setStrokeWidth(Math.min(4f, strokeWidth));

        int colorFromHour = getColorDegrees(hoursRotation);

        List<CalendarEvent> events = new ArrayList<>();

        // draw hour
        float decenter = DECENTERING_CORRECTION; // + 15;
        String hourText = "" + hour;// (int)(Math.random()*25);
        if (!mMinimalMode) {
            events = mEngine.getCalendarEvents();
            events.sort(new Comparator<CalendarEvent>() {
                public int compare(CalendarEvent event1, CalendarEvent event2) {
                    return event1.getBegin().compareTo(event2.getBegin());
                }
            });
            mHourPaint.setColor(colorFromHour);
            mHourPaint.setAlpha((int) (alphaHour * (mEngine.isDarkMode() ? lightFactor : 1f)));
            mHourPaint.setStyle(Paint.Style.FILL);
            Rect boundsText = new Rect();
            mHourPaint.getTextBounds(hourText, 0, hourText.length(), boundsText);
            float textSize = boundsText.height();
            drawTextUprightFromCenter(canvas, 0, decenter, hourText,
                    mHourPaint, null);
            mHourPaint.setColor(handPaintColor);
            // noch abzulaufende Zeit verdunkeln
            adaptBackGroundNrWithMeetings(canvas, minutes, textSize, events);

            // Anzahl Schritte schreiben (total und heute)
            float showMinutesCorrection = mShowMinutesDateAndMeetings ? 1.6f : 0.85f;
            if (!mEngine.isAmbient()) {
                drawTextUprightFromCenter(canvas, 180, mCenterY / 3 * (0.1f + showMinutesCorrection),
                        DE_CH_NUMBER.format(mEngine.getSteps()), mHandPaint, null);
                drawTextUprightFromCenter(canvas, 180, mCenterY / 3 * (0.65f + showMinutesCorrection),
                        DE_CH_NUMBER.format(mEngine.getSteps() - mEngine.getStepsAtMidnight()), mHandPaint, null);
            }
        }


        if (!mMinimalMode) {
            // nochmals den Umriss nachziehen, damit man die Zahl sieht
            mHourPaint.setStyle(Paint.Style.STROKE);
            mHourPaint.setAlpha(255);
            drawTextUprightFromCenter(canvas, 0, decenter, hourText,
                    mHourPaint, null);
        }

        // Minuten unten schreiben
        if (!mMinimalMode && mShowMinutesDateAndMeetings) {
            String minutesText = mZonedDateTime.format(MINUTES_FORMATTER);
            //minutesText = minutesText.charAt(0) + "  " + minutesText.charAt(1);
            drawTextUprightFromCenter(canvas, 180, mCenterY / 3 * 1.01f, minutesText,
                    mMinutesPaint, mEngine.isDarkMode() ? mLight : null);
        }

        // draw hand
        mHandPaint.setColor(colorFromHour);
        mHandPaint.setAlpha((int) (255 * (mEngine.isDarkMode() ? lightFactor : 1f)));
        float hourDotCenter = mHourHandLength + 2 * RAND_RESERVE;
        float hourDotRadius = RAND_RESERVE * 2f;
        float hourDotOuterRadius = RAND_RESERVE * 3.5f;
        drawCircle(canvas, hoursRotation, hourDotCenter, hourDotRadius, mHandPaint);
        mHandPaint.setColor(handPaintColor);
        drawLineFromCenter(canvas, hoursRotation, hourDotCenter - hourDotOuterRadius, mCenterX + RAND_RESERVE, mHandPaint);
        mHandPaint.setStyle(Paint.Style.STROKE);
        drawCircle(canvas, hoursRotation, hourDotCenter, hourDotOuterRadius, mHandPaint);
        if (mMinimalMode) {
            // Mitte-Orientierung
            drawCircle(canvas, hoursRotation, 0, mCenterX / 75, mHandPaint);
            drawLineFromCenter(canvas, hoursRotation, mCenterX / 75, mCenterX / 6.5f, mHandPaint);
        }
        mHandPaint.setStyle(Paint.Style.FILL);

        // buttons shown when active for switching dark mode and numbers on/off
        if (!mEngine.isAmbient()) {
            float buttonRadius = mCenterX / 3 * 2;
            if (!mEngine.isDarkMode()) {
                drawTextUprightFromCenter(canvas, 0, buttonRadius, "●", mHandPaint, mBold);
            } else {
                drawTextUprightFromCenter(canvas, 0, buttonRadius, "○", mHandPaint, mLight);
            }
            drawTextUprightFromCenter(canvas, mRotate + 90, buttonRadius, "↷", mHandPaint, mBold);
            if (!mMinimalMode && !mShowMinutesDateAndMeetings) {
                drawTextUprightFromCenter(canvas, 180, mCenterY / 3 * 2,
                        mZonedDateTime.format(MINUTES_FORMATTER), mHandPaint, null);
            }
        }


        /*if (batteryCharge <= 37 || batteryManager.getIntProperty(BatteryManager.BATTERY_STATUS_CHARGING) > 0 ) {
            // Schwarzer Punkt für Batteriestand
            drawCircle(hoursRotation, (batteryCharge * (mCenterX+RAND_RESERVE)) / 100f,
                    canvas, mHandPaint.getStrokeWidth()/2, mBackgroundPaint);
        }*/

        // DND + no Connection + "Message" + Wifi + Power anzeigen
        String specials = mEngine.getSpecials(canvas);

        // Stunden-Zahl anzeigen (genau auf Stunde) & Stunden-Punkte zeichnen
        if (!active && mMinimalMode) {
            writeHour(canvas, hourTextDistance, 12, "", false, true, false);
        }
        for (int i = 1; active && i <= 24 - Math.min(1, specials.length()); i++) {
            boolean writeNumber = i % 2 == 0 && (mMinimalMode || (i <= 21 && i >= 3));
            writeHour(canvas, hourTextDistance, i, writeNumber, !writeNumber);
        }

        float alarmDistanceFromCenter = mHourHandLength;
        AlarmManager alarm = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) {
            AlarmManager.AlarmClockInfo nextAlarmClock = alarm.getNextAlarmClock();
            if (nextAlarmClock != null && nextAlarmClock.getTriggerTime() - TimeUnit.HOURS.toMillis(ALARM_DISPLAY_WINDOW_HOURS) < mZonedDateTime.toInstant().toEpochMilli()) {
                ZonedDateTime alarmTime = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nextAlarmClock.getTriggerTime()), mZonedDateTime.getZone());
                String alarmText = "A";//String.format("%tR", time.getTime());
                drawTextUprightFromCenter(canvas, getDegreesFromNorth(alarmTime),
                        alarmDistanceFromCenter, alarmText, mHandPaint, null);
            }
        }

        boolean isCountdownActive = mLastCountdownTime != null;
        String countDownTime = "";
        if (isCountdownActive) {
            long correctedTimeMs = mLastCountdownTime.toSecondOfDay() * 1000L - (System.currentTimeMillis() - mLastReadCountdownTime);
            if (correctedTimeMs >= 0) {
                LocalTime correctedTime = LocalTime.ofSecondOfDay(correctedTimeMs / 1000);
                countDownTime = "T-";
                if (correctedTime.getHour() >= 1) {
                    countDownTime += correctedTime.getHour() + "h";
                } else if (correctedTime.getMinute() >= 1) {
                    countDownTime += correctedTime.getMinute() + "\'";
                } else {
                    countDownTime += "<" + correctedTime.getSecond() + "s";
                }
            }
        }


        // Y für textzeilen
        float currentY = mCenterY - mCenterX * 0.8f;
        if (isCountdownActive) { // always show active countdown
            drawTextUprightFromCenter(canvas, 0, mCenterY - currentY, countDownTime, mHandPaint, null);
            currentY = getNextLine(currentY);
        }

        boolean bShowMinutesDateMeetingsOrNotAmbient = mShowMinutesDateAndMeetings || !mEngine.isAmbient();
        if (bShowMinutesDateMeetingsOrNotAmbient) {
            String topText = mZonedDateTime.format(ISO_DATE_WITH_DAYOFWEEK);
            topText = mMinimalMode ? "" : topText;
            drawTextUprightFromCenter(canvas, 0, mCenterY - currentY, topText, mHandPaint, null);
            currentY = getNextLine(currentY);
            // Datum
            if (!mMinimalMode && (specials.length() > 1)) { // hide specials if displayed on top
                drawTextUprightFromCenter(canvas, 0, mCenterY - currentY, specials, mHandPaint, null);
                currentY = getNextLine(currentY);
            }
        }


        if (mShowMinutesDateAndMeetings) {
            for (CalendarEvent event : events) {
                float degreesFromNorth = getDegreesFromNorth(event.getBegin());
                mHandPaint.setStyle(Paint.Style.STROKE);
                drawCircle(canvas, degreesFromNorth, alarmDistanceFromCenter, mMinimalMode ? EVENT_MARKER_RADIUS_MINIMAL : EVENT_MARKER_RADIUS, mHandPaint);
                mHandPaint.setStyle(Paint.Style.FILL);
                long inFuture = event.getBegin().toInstant().toEpochMilli() - mZonedDateTime.toInstant().toEpochMilli();
                if (!mMinimalMode && bShowMinutesDateMeetingsOrNotAmbient && inFuture <= TimeUnit.MINUTES.toMillis(MEETING_PRE_ANNOUNCE_DURATION)) {
                    String title = event.getTitle();
                    if (title == null || title.trim().length() == 0) title = "(ohne Titel)";
                    boolean isInFuture = inFuture < 0;
                    String eventHrTitle = isInFuture ?
                            "-" + TimeUnit.MILLISECONDS.toMinutes(event.getEnd().toInstant().toEpochMilli() - mZonedDateTime.toInstant().toEpochMilli())
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
        // draw top if things to show
        String[] topNotificationValues = {"", specials, "+"};
        drawTextUprightFromCenter(canvas, 0, mCenterY - 16,
                topNotificationValues[Math.min(2, specials.length())], mHandPaint, null);

        mDimmingController.setLastDimm(lightFactor);
    }

    private void adaptBackGroundNrWithMeetings(Canvas canvas, int minutes, float textSize, List<CalendarEvent> events) {
        float minuteWidth = textSize * 1 / 60f;
        float remainingRelativeHour = 1 - minutes / 60f;
        int lastMinutes = minutes;
        for (CalendarEvent event : events) {
            ZonedDateTime begin = event.getBegin();
            long eventLengthMs = -begin.toInstant().toEpochMilli() + event.getEnd().toInstant().toEpochMilli();
            if (!event.isAllDay() && !(eventLengthMs >= TimeUnit.HOURS.toMillis(24))) {
//                    if (begin.getMinute() == 0) {
//                        hasHourMeeting = true;
//                    }
                long inFuture = begin.toInstant().toEpochMilli() - mEngine.getZonedDateTime().toInstant().toEpochMilli();
                if (inFuture > 0 && inFuture <= TimeUnit.MINUTES.toMillis(MEETING_PRE_ANNOUNCE_DURATION)) {
                    // events sind geordnet -> langsam auffüllen, und wechseln, wenn zukunft + min > 60
                    long minutesOfEvent = minutes + TimeUnit.MILLISECONDS.toMinutes(inFuture);
                    if (minutesOfEvent >= 60) {
                        float relativeMeetingHour = (minutesOfEvent - 60) / 60f;
                        float yFill = mEngine.getCenterY() - (textSize * (0.5f - relativeMeetingHour));
                        mEngine.getBackgroundPaint().setStrokeWidth(minuteWidth);
                        canvas.drawLine(mEngine.getCenterX() - textSize, yFill, mEngine.getCenterX() + textSize, yFill,
                                mEngine.getBackgroundPaint());
                    } else {
                        float relativeHourToBlank = (minutesOfEvent - lastMinutes) / 60f - 1f / 60f;
                        float blankHeight = textSize * (relativeHourToBlank);
                        float startY = mEngine.getCenterY() + textSize * (0.5f - remainingRelativeHour);
                        lastMinutes = (int) minutesOfEvent + 1;
                        canvas.drawRect(mEngine.getCenterX() - textSize, startY, mEngine.getCenterX() + textSize, startY + blankHeight, mEngine.getBackgroundPaint());
                        remainingRelativeHour = remainingRelativeHour - relativeHourToBlank - 1 / 60f;
                    }
                }
            }
        }
        mEngine.getBackgroundPaint().setStrokeWidth(textSize * remainingRelativeHour);
        float yFill = mEngine.getCenterY() + textSize / 2 * (1 - remainingRelativeHour);
        canvas.drawLine(mEngine.getCenterX() - textSize, yFill, mEngine.getCenterX() + textSize, yFill,
                mEngine.getBackgroundPaint());
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
