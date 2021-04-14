/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.heuscher.h24watchface;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.support.v4.graphics.ColorUtils;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.SystemProviders;
import android.support.wearable.provider.WearableCalendarContract;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;

import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog 24h watch face. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFaceService extends CanvasWatchFaceService {

    private static final float TEXT_SIZE = 30f;
    private static final int RAND_RESERVE = 7;
    public static final Locale DE_CH_LOCALE = Locale.forLanguageTag("de-CH");
    public static final SimpleDateFormat MINUTES = new SimpleDateFormat("mm", DE_CH_LOCALE);
    public static final NumberFormat DE_CH_NUMBER = NumberFormat.getNumberInstance(DE_CH_LOCALE);
    public static final SimpleDateFormat ISO_DATE_WITH_DAYOFWEEK = new SimpleDateFormat("YYYY-MM-dd E", DE_CH_LOCALE);
    public static final int MEETING_PRE_ANNOUNCE_DURATION = 50;
    public static final int COLOR_6_H = Color.argb(255, 0, 255, 0);
    public static final int COLOR_12_H = Color.argb(255, 255, 255, 0);
    public static final int COLOR_18_H = Color.argb(255, 0, 0, 255);
    public static final int COLOR_24_H = Color.argb(255, 255, 0, 255);
    public static final int[] COLORS = new int[]{COLOR_24_H, COLOR_6_H, COLOR_12_H, COLOR_18_H};
    // public static final int[] COLORS = new int[]{Color.BLUE, Color.GREEN, Color.RED};  // (Previous colors)

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    public class Engine extends CanvasWatchFaceService.Engine {

        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        private int mSteps = 0; //very ugly hack (shared variables)
        private int mStepsAtMidnight = 0;
        private final SensorEventListener mStepCounterListener = new SensorEventListener() {
            private LocalDateTime lastStepDateTime = LocalDateTime.now();
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                 /* from https://github.com/android/sensors-samples/blob/master/BatchStepSensor/Application/src/main/java/com/example/android/batchstepsensor/BatchStepSensorFragment.java
                A step counter event contains the total number of steps since the listener
                was first registered. We need to keep track of this initial value to calculate the
                number of steps taken, as the first value a listener receives is undefined.
                 */
                    LocalDateTime currentStepDateTime = LocalDateTime.now();
                    // After init and if the day changes, store current step count
                    if (mStepsAtMidnight == 0 || (currentStepDateTime.getDayOfYear() - lastStepDateTime.getDayOfYear()) != 0){
                        mStepsAtMidnight = mSteps;
                    }
                    mSteps = (int) event.values[0];
                    lastStepDateTime = currentStepDateTime;
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                // ignore
            }
        };

        private boolean mRegisteredReceivers = false;
        private static final float STROKE_WIDTH = 2f;
        private Calendar mCalendar;
        private Paint mBackgroundPaint;
        private Paint mHandPaint;
        private Paint mHourPaint;
        private Paint mMinutesPaint;

        private Typeface mLight = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        private Typeface mNormal = Typeface.create("sans-serif", Typeface.NORMAL);
        private Typeface mBold = Typeface.create("sans-serif", Typeface.BOLD);

        private boolean mAmbient;
        private boolean mDarkMode = true;
        private boolean mMinimalMode = false;
        private boolean mShow24Hours = false;
        private boolean mShowMinutesDateAndMeetings = true;

        private float mHourHandLength;
        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        private DimmingController mDimmingController;
        private float mRotate = 0;
        private int mCompilationId = 1974;
        private long mLastReadCountdownTime;
        private LocalTime mLastCountdownTime;

        private SensorManager mSystemService;
        private Sensor mStepCounter;

        private String mDebug = null;

        private long mLastDraw;


        public boolean isAmbient() {
            return mAmbient;
        }

        public void setAmbient(boolean mAmbient) {
            this.mAmbient = mAmbient;
        }

        public boolean isDarkMode() {
            return mDarkMode;
        }

        public void setDarkMode(boolean mDarkMode) {
            this.mDarkMode = mDarkMode;
            if (mDarkMode){
                mDimmingController.selfRegister();
            }
            else {
                mDimmingController.selfUnregister();
            }
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFaceService.this).
                    setAcceptsTapEvents(true).
                    setShowUnreadCountIndicator(true). // so dass Unread-Punkt nicht mehr sichtbar
                    setHideStatusBar(true).build());

            mSystemService = (SensorManager) getSystemService(SENSOR_SERVICE);
            mStepCounter = mSystemService.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

            mDimmingController = new DimmingController(
                    this,
                    getBaseContext(),
                    (PowerManager) getSystemService(POWER_SERVICE),
                    mSystemService);
            setDarkMode(true);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);
            mBackgroundPaint.setAntiAlias(true);

            mHandPaint = new Paint();
            mHandPaint.setStrokeWidth(STROKE_WIDTH);
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);
            mHandPaint.setTextSize(TEXT_SIZE);
            mHandPaint.setTypeface(mNormal);
            mHandPaint.setShadowLayer(8,0,0,Color.BLACK);

            mHourPaint = new Paint();
            mHourPaint.setAntiAlias(true);
            mHourPaint.setLetterSpacing(-0.065f);

            mMinutesPaint = new Paint();
            mMinutesPaint.setAntiAlias(true);
            mMinutesPaint.setShadowLayer(8,0,0,Color.BLACK);

            mCalendar = Calendar.getInstance();

            setDefaultComplicationProvider(mCompilationId, new ComponentName("com.google.android.deskclock",
                            "com.google.android.deskclock.complications.TimerProviderService"),
                    ComplicationData.TYPE_SHORT_TEXT);
            setActiveComplications(mCompilationId);
        }

        @Override
        public void onDestroy() {
            mDimmingController.selfUnregister();
            unregisterReceiver();
            super.onDestroy();
        }

        @Override
        public void onComplicationDataUpdate(int watchFaceComplicationId, ComplicationData data) {
            super.onComplicationDataUpdate(watchFaceComplicationId, data);
            String timerValue = null;
            if (watchFaceComplicationId == mCompilationId && data.getShortText() != null) {
                // This is the timer complication
                try {
                    mLastReadCountdownTime = System.currentTimeMillis();
                    timerValue = "" + data.getShortText()
                            .getText(getBaseContext(), mLastReadCountdownTime);
                    if (timerValue != null && timerValue.contains(":"))
                    {
                        if (timerValue.length() == 5) {
                            timerValue = "00:" + timerValue;
                        }
                        if (timerValue.length() == 4) {
                            timerValue = "0" + timerValue + ":00";
                        }
                        mLastCountdownTime = LocalTime.parse(timerValue);
                    }
                    else {
                        mLastCountdownTime = null;
                    }
                }
                catch (Exception e){
                    // ignore --> I will look if I see no timer.
                    mDebug = timerValue;
                    Log.d("Heuscher24h", timerValue, e);
                }
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();

        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (isAmbient() != inAmbientMode) {
                setAmbient(inAmbientMode);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;
            /*
             * Find the coordinates of the center point on the screen.
             * Ignore the window insets so that, on round watches
             * with a "chin", the watch face is centered on the entire screen,
             * not just the usable portion.
             */
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;
            /*
             * Calculate the lengths of the watch hands and store them in member variables.
             */
            mHourHandLength = mCenterX - 2 * RAND_RESERVE;
            mHourPaint.setTextSize(mHeight);
            mMinutesPaint.setTextSize(mCenterY/3);
        }

        @Override
        public void onTapCommand(
                @TapType int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case WatchFaceService.TAP_TYPE_TAP:
                    if (y <= mHeight / 3 ) { // top
                        setDarkMode(!isDarkMode());
                    }
                    else if (x <= mWidth / 3 ) { // left
                        mShow24Hours = !mShow24Hours;
                    }
                    else if (x >= mWidth / 3 * 2 ) { // right
                        mRotate = mRotate == 0 ? 180 : 0;
                    }
                    else if (y >= mHeight / 3 * 2) { // bottom
                        mShowMinutesDateAndMeetings = !mShowMinutesDateAndMeetings;
                    }
                    else { // center
                        mMinimalMode = !mMinimalMode;
                    }
                case WatchFaceService.TAP_TYPE_TOUCH_CANCEL:
                    invalidate();
                case WatchFaceService.TAP_TYPE_TOUCH:
                default:
                    super.onTapCommand(tapType, x, y, eventTime);
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            canvas.rotate(mRotate, mCenterX, mCenterY);
            float hourTextDistance = mCenterX * 0.9f;
            boolean active = !(isAmbient() || isDarkMode());
            setLastDraw(System.currentTimeMillis());
            mCalendar.setTimeInMillis(getLastDraw());

            // Draw the background.
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);

            int hour = mCalendar.get(Calendar.HOUR_OF_DAY);
            int minutes = mCalendar.get(Calendar.MINUTE);

            // Hack to set and re-set countdown-timer
            setActiveComplications(SystemProviders.DATE);
            setActiveComplications(mCompilationId);

            /* These calculations reflect the rotation in degrees per unit of time, e.g., 360 / 60 = 6 and 360 / 12 = 30. */
            final float hoursRotation = getDegreesFromNorth(mCalendar);

            int batteryCharge = 100;
            BatteryManager batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            if (batteryManager != null) {
                batteryCharge = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            }
            // Farbe rot wenn wenig Batterie
            if (batteryCharge <= 10) {
                mHandPaint.setColor(Color.RED);
                drawTextUprightFromCenter(0, 0, "Battery: " +batteryCharge + "% !", mHandPaint, canvas, null);
            }

            int handPaintColor = Color.WHITE;
            float lightFactor = mDimmingController.getNextDimm() == null ? 1f : mDimmingController.getNextDimm();
            if (!isAmbient() && lightFactor <= 2*mDimmingController.getMinLuminance()
            && Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC == Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)) {
                lightFactor += 0.15f; // counteract too much automatic dimming in very low light
            }
            if (isDarkMode()) {
                handPaintColor = Color.HSVToColor(new float[]{13f, 0.04f, lightFactor});
            }
            mHandPaint.setColor(handPaintColor);
            mHourPaint.setColor(handPaintColor);
            mMinutesPaint.setColor(handPaintColor);

            // Light typeface if there's enough light
            boolean betterReadableInDarkMode = isDarkMode() && lightFactor <= DimmingController.VERY_DARK;
            mHandPaint.setTypeface((!isDarkMode() || betterReadableInDarkMode) ? mNormal : mLight);
            mHandPaint.setStrokeWidth(STROKE_WIDTH * (betterReadableInDarkMode ? 2 : 1));

            float strokeWidth = 6;
            int alphaHour = 160;
            Typeface typeface = mBold;
            if(isDarkMode()) {
                strokeWidth = Math.max(lightFactor*3, 1.5f);
                alphaHour = 218 - Math.min((int)(lightFactor*200), 100);
                typeface = lightFactor < DimmingController.VERY_DARK ? mLight : mNormal;
            }
            mHourPaint.setTypeface(typeface);
            mMinutesPaint.setTypeface(typeface);
            mHourPaint.setStrokeWidth(strokeWidth);
            mMinutesPaint.setStrokeWidth(strokeWidth);

            int colorFromHour = getColorDegrees(hoursRotation);

            List<CalendarEvent> events = getCalendarEvents();
            events.sort(new Comparator<CalendarEvent>()
            {
                public int compare(CalendarEvent event1, CalendarEvent event2){ return event1.getBegin().compareTo(event2.getBegin()); }
            });

            // draw hour
            float decenteringCorrection = -24;
            String hourText = "" + hour;//Math.random()*25;//
            if (!mMinimalMode) {
                mHourPaint.setColor(colorFromHour);
                mHourPaint.setAlpha((int) (alphaHour * (isDarkMode() ? lightFactor : 1f)));
                mHourPaint.setStyle(Paint.Style.FILL);
                Rect boundsText = new Rect();
                mHourPaint.getTextBounds(hourText, 0, hourText.length(), boundsText);
                float textSize = boundsText.height();
                drawTextUprightFromCenter(0, decenteringCorrection, hourText,
                        mHourPaint, canvas, null);
                mHourPaint.setColor(handPaintColor);
                // noch abzulaufende Zeit verdunkeln
                adaptBackGroundNrWithMeetings(canvas, minutes, textSize, events);

                // Minuten unten schreiben
                if (mShowMinutesDateAndMeetings) {
                    String minutesText = MINUTES.format(mCalendar.getTime());
                    drawTextUprightFromCenter(180, mCenterY/3*2, minutesText, mMinutesPaint, canvas, null);
                }

                // Anzahl Schritte schreiben (total und heute)
                if(!isAmbient()) {
                    drawTextUprightFromCenter(180, mCenterY / 3 * 0.65f,
                            DE_CH_NUMBER.format(mSteps), mHandPaint, canvas, null);
                    drawTextUprightFromCenter(180, mCenterY / 3 * 1.2f,
                            DE_CH_NUMBER.format(mSteps - mStepsAtMidnight), mHandPaint, canvas, null);
                }
            }


            if (!mMinimalMode) {
                // nochmals den Umriss nachziehen, damit man die Zahl sieht
                mHourPaint.setStyle(Paint.Style.STROKE);
                mHourPaint.setAlpha(255);
                drawTextUprightFromCenter(0, decenteringCorrection, hourText,
                        mHourPaint, canvas, null);
            }

            // draw hand
            mHandPaint.setColor(colorFromHour);
            mHandPaint.setAlpha((int) (255 * (isDarkMode() ? lightFactor : 1f)));
            float hourDotCenter = mHourHandLength + 2 * RAND_RESERVE;
            float hourDotRadius = RAND_RESERVE * 2f;
            float hourDotOuterRadius = RAND_RESERVE * 3.5f;
            drawCircle(hoursRotation, hourDotCenter, hourDotRadius, mHandPaint, canvas);
            mHandPaint.setColor(handPaintColor);
            drawLineFromCenter(hoursRotation, hourDotCenter - hourDotOuterRadius, mCenterX + RAND_RESERVE, mHandPaint, canvas);
            mHandPaint.setStyle(Paint.Style.STROKE);
            drawCircle(hoursRotation, hourDotCenter, hourDotOuterRadius, mHandPaint, canvas);
            if (mMinimalMode) {
                // Mitte-Orientierung
                drawCircle(hoursRotation, 0, mCenterX / 75, mHandPaint, canvas);
                drawLineFromCenter(hoursRotation, mCenterX / 75, mCenterX / 10, mHandPaint, canvas);
                drawLineFromCenter(hoursRotation, mCenterX /2 - mCenterX / 15, mCenterX /2 + mCenterX / 15, mHandPaint, canvas);
            }
            mHandPaint.setStyle(Paint.Style.FILL);

            // buttons shown when active for switching dark mode and numbers on/off
            if(!isAmbient()){
                float buttonRadius = mCenterX / 3 * 2;
                if (!isDarkMode()) {
                    drawTextUprightFromCenter(0, buttonRadius,"☼", mHandPaint, canvas, mBold);
                    drawCircle(0, buttonRadius, 6, mHandPaint, canvas);
                    drawTextUprightFromCenter(270 + mRotate, hourTextDistance,
                            mRotate == 0 ? "18" : "6", mHandPaint, canvas, mShow24Hours ? mBold : mLight);
                }
                else {
                    drawTextUprightFromCenter(0, buttonRadius,"○", mHandPaint, canvas, mLight);
                }
                drawTextUprightFromCenter(mRotate + 90, buttonRadius,"↷", mHandPaint, canvas, mBold);
                if (!mMinimalMode && !mShowMinutesDateAndMeetings) {
                    drawTextUprightFromCenter(180, mCenterY/3*2,
                            MINUTES.format(mCalendar.getTime()), mHandPaint, canvas, null);
                }
            }


            /*if (batteryCharge <= 37 || batteryManager.getIntProperty(BatteryManager.BATTERY_STATUS_CHARGING) > 0 ) {
                // Schwarzer Punkt für Batteriestand
                drawCircle(hoursRotation, (batteryCharge * (mCenterX+RAND_RESERVE)) / 100f,
                        canvas, mHandPaint.getStrokeWidth()/2, mBackgroundPaint);
            }*/

            // DND + no Connection + "Message" + Wifi + Power anzeigen
            String specials = getSpecials(canvas);

            // Stunden-Zahl anzeigen (genau auf Stunde) & Stunden-Punkte zeichnen
            Date date = mCalendar.getTime();
            if(!active && specials.length() == 0  && mMinimalMode){
                writeHour(canvas, hourTextDistance,24, "", false, true, false);
            }
            for (int i = 1; active && i <= 24 - 1 * Math.min(1,specials.length()); i++) {
                boolean writeNumber = mShow24Hours && i % 2 == 0 && (mMinimalMode || (i <= 21 && i >= 3));
                writeHour(canvas, hourTextDistance, i, writeNumber, !writeNumber);
            }

            float alarmDistanceFromCenter = mHourHandLength;
            Calendar time = Calendar.getInstance();
            AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarm != null) {
                AlarmManager.AlarmClockInfo nextAlarmClock = alarm.getNextAlarmClock();
                if (nextAlarmClock != null && nextAlarmClock.getTriggerTime() - TimeUnit.HOURS.toMillis(18) < getLastDraw()) {
                    time.setTimeInMillis(nextAlarmClock.getTriggerTime());
                    String alarmText = "A";//String.format("%tR", time.getTime());
                    drawTextUprightFromCenter(getDegreesFromNorth(time),
                            alarmDistanceFromCenter, alarmText, mHandPaint, canvas, null);
                }
            }

            boolean isCountdownActive = mLastCountdownTime != null;
            String countDownTime = "";
            if (isCountdownActive) {
                long correctedTimeMs = mLastCountdownTime.toSecondOfDay()*1000 - (System.currentTimeMillis() - mLastReadCountdownTime);
                if (correctedTimeMs >= 0){
                    LocalTime correctedTime = LocalTime.ofSecondOfDay(correctedTimeMs / 1000);
                    countDownTime = "T-";
                    if (correctedTime.getHour() >= 1){
                        countDownTime += correctedTime.getHour() + "h";
                    }
                    else if (correctedTime.getMinute() >= 1){
                        countDownTime += correctedTime.getMinute() + "'";
                    }
                    else {
                        countDownTime += "<" + correctedTime.getSecond() + "s";
                    }
                }
            }


            // Y für textzeilen
            float currentY = mCenterY - mCenterX * 0.8f;
            if (isCountdownActive) { // always show active countdown
                drawTextUprightFromCenter(0, mCenterY - currentY, countDownTime, mHandPaint, canvas, null);
                currentY = getNextLine(currentY);
            }

            boolean bShowMinutesDateMeetingsOrNotAmbient = mShowMinutesDateAndMeetings || !isAmbient();
            if (bShowMinutesDateMeetingsOrNotAmbient) {
                String topText = ISO_DATE_WITH_DAYOFWEEK.format(date);
                topText = topText.substring(0, topText.length() - 1);
                topText = mMinimalMode ? "" : topText;
                drawTextUprightFromCenter(0, mCenterY - currentY, topText, mHandPaint, canvas, null);
                currentY = getNextLine(currentY);
                // Datum
                if (!mMinimalMode && (specials.length() > 1)) { // hide specials if displayed on top
                    drawTextUprightFromCenter(0, mCenterY - currentY, specials, mHandPaint, canvas, null);
                    currentY = getNextLine(currentY);
                }
            }


            for (CalendarEvent event : events) {
                long eventLengthMs = - event.getBegin().getTime() + event.getEnd().getTime();
                if (!event.isAllDay() && !(eventLengthMs >= TimeUnit.HOURS.toMillis(24))) {
                    time.setTimeInMillis(event.getBegin().getTime());
                    float degreesFromNorth = getDegreesFromNorth(time);
                    mHandPaint.setStyle(Paint.Style.STROKE);
                    drawCircle(degreesFromNorth, alarmDistanceFromCenter, mMinimalMode ? 1 : 6.5f, mHandPaint, canvas);
                    mHandPaint.setStyle(Paint.Style.FILL);
                    long inFuture = time.getTimeInMillis() - mCalendar.getTimeInMillis();
                    if (!mMinimalMode && bShowMinutesDateMeetingsOrNotAmbient && inFuture <= TimeUnit.MINUTES.toMillis(MEETING_PRE_ANNOUNCE_DURATION)) {
                        String title = event.getTitle();
                        if (title == null || title.trim().length() == 0) title = "(ohne Titel)";
                        boolean isInFuture = inFuture < 0;
                        String eventHrTitle = isInFuture ?
                                "-" + TimeUnit.MILLISECONDS.toMinutes(event.getEnd().getTime() - mCalendar.getTimeInMillis())
                                : "" + TimeUnit.MILLISECONDS.toMinutes(inFuture);
                        eventHrTitle +=  " " + title;
                        int minimizedLength = Math.min(22, eventHrTitle.length());
                        drawTextUprightFromCenter(0,mCenterY - currentY,
                                eventHrTitle.substring(0, minimizedLength), mHandPaint, canvas, isInFuture ? mLight : null);
                        currentY = getNextLine(currentY);
                        if (eventHrTitle.length() > minimizedLength){
                            drawTextUprightFromCenter(0,mCenterY - currentY,
                                    eventHrTitle.substring(minimizedLength, Math.min(50, eventHrTitle.length())), mHandPaint, canvas, isInFuture ? mLight : null);
                            currentY = getNextLine(currentY);
                        }
                    }
                }

            }
            // draw top if things to show
            String[] topNotificationValues = {"", specials, "+"};
            drawTextUprightFromCenter(0,mCenterY - 16,
                    topNotificationValues[Math.min(2,specials.length())], mHandPaint, canvas, null);

            mDimmingController.setLastDimm(lightFactor);
        }

        private void adaptBackGroundNrWithMeetings(Canvas canvas, int minutes, float textSize, List<CalendarEvent> events) {
            Calendar time = Calendar.getInstance();
            float minuteWidth = textSize * 1 / 60f;
            float remainingRelativeHour = 1 - minutes / 60f;
            int lastMinutes = minutes;
            for (CalendarEvent event : events) {
                long eventLengthMs = -event.getBegin().getTime() + event.getEnd().getTime();
                if (!event.isAllDay() && !(eventLengthMs >= TimeUnit.HOURS.toMillis(24))) {
                    time.setTimeInMillis(event.getBegin().getTime());
                    long inFuture = time.getTimeInMillis() - mCalendar.getTimeInMillis();
                    if (inFuture > 0 && inFuture <= TimeUnit.MINUTES.toMillis(MEETING_PRE_ANNOUNCE_DURATION)) {
                        // events sind geordnet -> langsam auffüllen, und wechseln, wenn zukunft + min > 60
                        long minutesOfEvent = minutes + TimeUnit.MILLISECONDS.toMinutes(inFuture);
                        if(minutesOfEvent >= 60){
                            float relativeMeetingHour = (minutesOfEvent - 60) / 60f;
                            float yFill = mCenterY - (textSize * (0.5f - relativeMeetingHour));
                            mBackgroundPaint.setStrokeWidth(minuteWidth);
                            canvas.drawLine(mCenterX - textSize, yFill, mCenterX + textSize, yFill,
                                    mBackgroundPaint);
                        }
                        else {
                            float relativeHourToBlank = (minutesOfEvent - lastMinutes) / 60f - 1f/60f;
                            float blankHeight = textSize * (relativeHourToBlank);
                            float startY  = mCenterY + textSize * (0.5f - remainingRelativeHour);
                            lastMinutes = (int) minutesOfEvent + 1;
                            canvas.drawRect(mCenterX - textSize, startY, mCenterX + textSize, startY + blankHeight, mBackgroundPaint);
                            remainingRelativeHour = remainingRelativeHour - relativeHourToBlank - 1/60f;
                        }
                    }
                }
            }
            mBackgroundPaint.setStrokeWidth(textSize * remainingRelativeHour);
            float yFill = mCenterY + textSize/2 * (1-remainingRelativeHour);
            canvas.drawLine(mCenterX - textSize, yFill, mCenterX + textSize, yFill,
                    mBackgroundPaint);
        }

        private String getSpecials(Canvas canvas) {
            String specials = "" + (mDebug != null ? mDebug : "");
            try {
                WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null && wifiManager.isWifiEnabled()) {
                    specials += "W";
                }
                if (getUnreadCount() > 0) { // entweder ungelesene
                    specials += "!";
                }
                else if (getNotificationCount() > 0) { // oder noch andere
                    specials += "i";
                }
                if (getInterruptionFilter() != INTERRUPTION_FILTER_PRIORITY) {
                    specials += "<";
                }
                if (Settings.Global.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON) == 1) {
                    specials += ">";
                }
                else {
                    ConnectivityManager connectivityManager =(ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    Network activeNetwork = connectivityManager.getActiveNetwork();
                    if (activeNetwork == null) {
                        specials += "X";
                    }
                }
                LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    specials += "⌖";
                }
            }
            catch (Throwable t) {
                drawTextUprightFromCenter(0,0, t.getMessage(), mHandPaint, canvas, null);
            }
            return specials;
        }

        private void writeHour(Canvas canvas, float radiusCenter, int hour, boolean writeNumber, boolean writeMarker) {
            writeHour(canvas, radiusCenter, hour, ""+hour, writeNumber, writeMarker, true);
        }

        private void writeHour(Canvas canvas, float radiusCenter, int hour, String hourText,
                                boolean writeNumber, boolean writeMarker, boolean adjustColor) {
            float rotatePerHour = 15f;
            float degreesFromNorth = hour * rotatePerHour;
            float dotDistance = mHourHandLength;

            int handColor = mHandPaint.getColor();
            if (adjustColor) {
                mHandPaint.setColor(getColorDegrees(degreesFromNorth));
            }

            if (writeNumber) {
                drawTextUprightFromCenter(degreesFromNorth, radiusCenter,
                        hourText, mHandPaint, canvas, null);
            }
            if (writeMarker) {
                drawCircle(degreesFromNorth, dotDistance, 4, mHandPaint, canvas);
                // black dot in the middle
                Float nextDimmObject = mDimmingController.getNextDimm();
                float nextDimm = nextDimmObject == null ? 1 : nextDimmObject;
                drawCircle(degreesFromNorth, dotDistance, isDarkMode() && nextDimm < DimmingController.VERY_DARK ? 3 : 2, mBackgroundPaint, canvas
                );
            }
            mHandPaint.setColor(handColor);
        }

        private void drawCircle(float rotationFromNorth, float distanceFromCenter, float radius, Paint paint, Canvas canvas) {
            canvas.save();
            canvas.rotate(rotationFromNorth, mCenterX, mCenterY);
            canvas.drawCircle(mCenterX, mCenterY - distanceFromCenter, radius, paint);
            canvas.restore();
        }

        private void drawLineFromCenter(float degreesFromNorth, float startFromCenter, float endFromCenter, Paint paint, Canvas canvas)
        {
            canvas.save();
            canvas.rotate(degreesFromNorth, mCenterX, mCenterY);
            canvas.drawLine(mCenterX, mCenterY - startFromCenter, mCenterX, mCenterY - endFromCenter,
                    paint);
            canvas.restore();
        }

        private void drawTextUprightFromCenter(float degreesFromNorth, float radiusCenter, String text, Paint paint, Canvas canvas, Typeface typeface)
        {
            float textLengthX = paint.measureText(text);
            float textLengthY = paint.getTextSize();
            //                          center text
            float x = mCenterX - textLengthX/2 + radiusCenter *
                    (float) Math.cos(Math.toRadians(degreesFromNorth - 90f));
            float y = mCenterY + textLengthY/24*7 +
                    radiusCenter *
                    (float) Math.sin(Math.toRadians(degreesFromNorth - 90f));
            if (typeface != null) {
                Typeface prevTypeface = paint.getTypeface();
                paint.setTypeface(typeface);
                canvas.drawText(text, x, y ,paint);
                paint.setTypeface(prevTypeface);
            }
            else {
                canvas.drawText(text, x, y ,paint);
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }
        }

        private void registerReceiver() {
            if (mRegisteredReceivers) {
                return;
            }
            mRegisteredReceivers = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            mSystemService.registerListener(mStepCounterListener, mStepCounter, SensorManager.SENSOR_DELAY_NORMAL);
            MyWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            mDimmingController.selfUnregister();
            if (!mRegisteredReceivers) {
                return;
            }
            mRegisteredReceivers = false;
            mSystemService.unregisterListener(mStepCounterListener);
            MyWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        public long getLastDraw() {
            return mLastDraw;
        }

        public void setLastDraw(long mLastDraw) {
            this.mLastDraw = mLastDraw;
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

    private float getDegreesFromNorth(@NotNull Calendar time) {
        return time.get(Calendar.HOUR_OF_DAY)*15f + time.get(Calendar.MINUTE)/4f;
    }

    private final String[] PROJECTION = {
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.ALL_DAY,
    };

    private List<CalendarEvent> getCalendarEvents() {
        List<CalendarEvent> events = new ArrayList<>();
        Uri.Builder builder = WearableCalendarContract.Instances.CONTENT_URI.buildUpon();
        long begin = System.currentTimeMillis();
        ContentUris.appendId(builder, begin);
        long hourNoShow = TimeUnit.HOURS.toMillis(18);
        ContentUris.appendId(builder, begin + hourNoShow);
        final Cursor cursor = getContentResolver().query(builder.build(), PROJECTION, null, null, null);
        if (cursor == null) {
            return events;
        }
        Calendar cal = Calendar.getInstance();
        while (cursor.moveToNext()) {
            long beginVal = cursor.getLong(0);
            long endVal = cursor.getLong(1);
            String title = cursor.getString(2);
            boolean isAllDay = !cursor.getString(3).equals("0");
            CalendarEvent newEvent = new CalendarEvent();
            newEvent.setTitle(title);
            cal.setTimeInMillis(beginVal);
            newEvent.setBegin(cal.getTime());
            cal.setTimeInMillis(endVal);
            newEvent.setEnd(cal.getTime());
            newEvent.setAllDay(isAllDay);
            events.add(newEvent);
        }
        cursor.close();
        return events;
    }
}
