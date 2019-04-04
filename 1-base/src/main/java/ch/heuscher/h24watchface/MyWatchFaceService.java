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
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Message;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.SystemProviders;
import android.support.wearable.provider.WearableCalendarContract;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;

import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFaceService extends CanvasWatchFaceService {

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(60);
    private static final float TEXT_SIZE = 30f;
    private static final int RAND_RESERVE = 7;
    public static final SimpleDateFormat HOUR_MINUTE_SECOND_FORMAT = new SimpleDateFormat("HH:mm:ss");
    public static final SimpleDateFormat MINUTE_SECOND_FORMAT = new SimpleDateFormat("mm:ss");
    public static final SimpleDateFormat HOUR_MINUTE_FORMAT = new SimpleDateFormat("HH'h'mm");

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        public static final int VERY_DARK = 10;
        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (R.id.message_update == message.what) {
                    invalidate();
                    if (shouldTimerBeRunning()) {
                        long timeMs = System.currentTimeMillis();
                        long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                        mUpdateTimeHandler.sendEmptyMessageDelayed(R.id.message_update, delayMs);
                    }
                }
            }
        };

        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        private boolean mRegisteredTimeZoneReceiver = false;
        private static final float STROKE_WIDTH = 1f;
        private Calendar mCalendar;
        private Paint mBackgroundPaint;
        private Paint mHandPaint;
        private Paint mHourPaint;

        private Typeface mLight = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        private Typeface mNormal = Typeface.create("sans-serif", Typeface.NORMAL);
        private Typeface mBold = Typeface.create("sans-serif", Typeface.BOLD);

        private boolean mAmbient;
        private boolean mDarkMode = true;
        private float mDefaultMinLuminance = 0.07f;
        private float mMinLuminance = mDefaultMinLuminance;

        private float mHourHandLength;
        private float mMinuteHandLength;
        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        private LightEventListener mLightEventListener;
        private float mRotate = 0;
        private int mCompilationId = 1974;
        private float mLastLux;
        private long mLastReadCountdownTime;
        private LocalTime mLastCountdownTime;

        private String mDebug = null;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFaceService.this).
                    setAcceptsTapEvents(true).
                    setShowUnreadCountIndicator(true). // so dass Unread-Punkt nicht mehr sichtbar
                    setHideStatusBar(true).build());

            mLightEventListener = new LightEventListener((SensorManager) getSystemService(SENSOR_SERVICE));

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);

            mHandPaint = new Paint();
            mHandPaint.setStrokeWidth(STROKE_WIDTH);
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);
            mHandPaint.setTextSize(TEXT_SIZE);
            mHandPaint.setTypeface(mNormal);

            mHourPaint = new Paint();
            mHourPaint.setAntiAlias(true);

            mCalendar = Calendar.getInstance();

            setDefaultComplicationProvider(mCompilationId, new ComponentName("com.google.android.deskclock",
                            "com.google.android.deskclock.complications.TimerProviderService"),
                    ComplicationData.TYPE_SHORT_TEXT);
            setActiveComplications(mCompilationId);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            mLightEventListener.selfUnregister();
            super.onDestroy();
        }

        @Override
        public void onComplicationDataUpdate(int watchFaceComplicationId, ComplicationData data) {
            super.onComplicationDataUpdate(watchFaceComplicationId, data);
            String timerValue = null;
            if (watchFaceComplicationId == mCompilationId) {
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
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                invalidate();
            }
            if (mDarkMode) {
                mLightEventListener.selfRegister();
            } else {
                mLightEventListener.selfUnregister();
            }

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
            updateTimer();
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
            mHourHandLength = mCenterX - RAND_RESERVE - 7;
            mMinuteHandLength = mCenterX - RAND_RESERVE;
            mHourPaint.setTextSize(mCenterY);
        }

        @Override
        public void onTapCommand(
                @TapType int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case WatchFaceService.TAP_TYPE_TAP:
                    if (y <= mCenterY / 2 ) {
                        mDarkMode = !mDarkMode;
                    }
                    if (x <= mCenterX / 2 ) {
                        mMinLuminance -= 0.01f;
                    }
                    if (x >= mCenterX / 2 * 3 ) {
                        mMinLuminance += 0.01f;
                    }
                    if (y >= mCenterY / 2 * 3) {
                        mRotate = mRotate == 0 ? 180 : 0;
                    }
                    invalidate();
                    break;

                case WatchFaceService.TAP_TYPE_TOUCH:
                case WatchFaceService.TAP_TYPE_TOUCH_CANCEL:
                default:
                    super.onTapCommand(tapType, x, y, eventTime);
                    break;
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            canvas.rotate(mRotate, mCenterX, mCenterY);
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            // Draw the background.
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);

            int hour = mCalendar.get(Calendar.HOUR_OF_DAY);
            int minutes = mCalendar.get(Calendar.MINUTE);
            int seconds = mCalendar.get(Calendar.SECOND);

            // Hack to set and re-set countdown-timer
            setActiveComplications(SystemProviders.DATE);
            setActiveComplications(mCompilationId);


            // Darkmode autom. umschaltung
            if (minutes == 0 && hour == 19 && seconds <=1) {
                    mDarkMode = true;
            }

            /* These calculations reflect the rotation in degrees per unit of time, e.g., 360 / 60 = 6 and 360 / 12 = 30. */
            final float minutesRotation = minutes * 6f;
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

            mLastLux = mLightEventListener.getMaxLuxSinceLastRead();
            int handPaintColor = Color.WHITE;
            if (mAmbient && mDarkMode) {
                float lightFactor = Math.min(1f, mLastLux /20f+mMinLuminance);
                handPaintColor = Color.HSVToColor(new float[]{13f, 0.04f, lightFactor});
            }
            mHandPaint.setColor(handPaintColor);
            mHourPaint.setColor(handPaintColor);

            // Light typeface if there's enough light
            mHandPaint.setTypeface(mDarkMode && mLastLux > VERY_DARK ? mLight : mNormal);

            String hourText = "" + hour;
            mHourPaint.setStyle(Paint.Style.FILL);
            float strokeWidth = 6;
            int alphaHour = 160;
            Typeface typeface = mBold;
            if(mDarkMode) {
                strokeWidth = Math.min(5, mLastLux /12 + 1.5f);
                alphaHour = 228 - Math.min((int) mLastLux, 100);
                typeface = mLastLux < 8 ? mLight : mNormal;
            }
            mHourPaint.setTypeface(typeface);
            mHourPaint.setStrokeWidth(strokeWidth);
            mHourPaint.setAlpha(alphaHour);

            Rect boundsText = new Rect();
            mHourPaint.getTextBounds(hourText, 0, hourText.length(), boundsText);
            float textSize = boundsText.height();
            float decenteringCorrection = -12;
            drawTextUprightFromCenter(0, decenteringCorrection, hourText,
                    mHourPaint, canvas, null);

            // noch abzulaufende Zeit verdunkeln
            float relativeHour = 1 - minutes / 60f;
            mBackgroundPaint.setStrokeWidth(textSize * relativeHour);
            float yFill = mCenterY + textSize/2 * (1-relativeHour);
            canvas.drawLine(mCenterX - textSize, yFill, mCenterX + textSize, yFill,
                    mBackgroundPaint);
            // nochmals den Umriss nachziehen
            mHourPaint.setStyle(Paint.Style.STROKE);
            drawTextUprightFromCenter(0, decenteringCorrection, hourText,
                    mHourPaint, canvas, null);

            // Minuten-"Zeiger" aus Kreisen über der mittleren Zahl
            float minutesCircleRadius = mCenterX / 16;
            drawCircle(0, 0, canvas, minutesCircleRadius, mBackgroundPaint);
            drawCircle(minutesRotation, minutesCircleRadius, canvas, minutesCircleRadius, mBackgroundPaint);
            drawLineFromCenter(minutesRotation, 0,
                    2 * minutesCircleRadius - 6, mHandPaint, canvas);

            float startPoint = (batteryCharge / 100f) * mHourHandLength;
            // dick für restl. Batterie
            mHandPaint.setStrokeWidth(STROKE_WIDTH*4);
            drawLineFromCenter(hoursRotation, 0, startPoint, mHandPaint, canvas);
            // dünn für verbrauchte Batterie
            mHandPaint.setStrokeWidth(STROKE_WIDTH*2);
            drawLineFromCenter(hoursRotation, startPoint, mHourHandLength, mHandPaint, canvas);

            // DND + no Connection + "Message" + Wifi + Power anzeigen
            String specials = getSpecials(batteryManager, canvas);

            // Stunden-Zahl anzeigen (genau auf Stunde) & Stunden-Punkte zeichnen
            float radiusCenter = mCenterX * 0.8f;
            Date date = mCalendar.getTime();
            for (int i = 0; i <= 23; i++) {
                if(i == 0) {
                    boolean isSpecial = specials.length() != 0;
                    String topText = isSpecial ? specials : "24";
                    if (isSpecial || !mAmbient) {
                        writeHour(canvas, radiusCenter, i, topText, true);
                    }
                }
                else if (i == 6){
                    String minutesText = new SimpleDateFormat(": mm", Locale.GERMAN).format(date);
                    drawTextUprightFromCenter(90, radiusCenter - 12,
                            minutesText, mHandPaint, canvas, null);
                    if (!mAmbient) writeHour(canvas, radiusCenter, i, false);
                }
                else if (i == 12){
                    boolean isCountdownActive = mLastCountdownTime != null;
                    if (isCountdownActive) {
                        String countDownTime = "T-";
                        long correctedTimeMs = mLastCountdownTime.toSecondOfDay()*1000 - (System.currentTimeMillis() - mLastReadCountdownTime);
                        LocalTime correctedTime = LocalTime.ofSecondOfDay(correctedTimeMs / 1000);
                        if (correctedTime.getHour() >= 1){
                            countDownTime += correctedTime.getHour() + "h";
                        }
                        else if (correctedTime.getMinute() >= 1){
                            countDownTime += correctedTime.getMinute() + "'";
                        }
                        else {
                            countDownTime += "<" + correctedTime.getSecond() + "s";
                        }
                        drawTextUprightFromCenter(180, radiusCenter,
                                countDownTime, mHandPaint, canvas, null);
                    }
                    if (!mAmbient) writeHour(canvas, radiusCenter, i, !isCountdownActive);
                }
                else if (!mAmbient) {
                    writeHour(canvas, radiusCenter,i, i % 2 == 0 && ( i!=2 && i != 22));
                }
                if(i==hour) {
                    writeHour(canvas, radiusCenter,  hour, false);
                    writeHour(canvas, radiusCenter, hour+1, false);
                }
            }

            // luminanz zeigen wenn nötig
            if (Math.abs(mMinLuminance - mDefaultMinLuminance) >= 0.0001f) {
                drawTextUprightFromCenter(80,mHourHandLength-40,
                        new DecimalFormat(".##").format(mMinLuminance) , mHandPaint, canvas, null);
            }

            float alarmDistanceFromCenter = mHourHandLength;
            Calendar time = Calendar.getInstance();
            AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarm != null) {
                AlarmManager.AlarmClockInfo nextAlarmClock = alarm.getNextAlarmClock();
                if (nextAlarmClock != null && nextAlarmClock.getTriggerTime() - TimeUnit.HOURS.toMillis(18) < now) {
                    time.setTimeInMillis(nextAlarmClock.getTriggerTime());
                    String alarmText = "A";//String.format("%tR", time.getTime());
                    drawTextUprightFromCenter(getDegreesFromNorth(time),
                            alarmDistanceFromCenter, alarmText, mHandPaint, canvas, null);
                }
            }
            // Y für textzeilen
            float currentY = getNextLine(mCenterY - radiusCenter);

            // Datum
            String dateDate = new SimpleDateFormat("E YYYY-MM-dd", Locale.GERMAN).format(date);
            drawTextUprightFromCenter(0,mCenterY - currentY, dateDate, mHandPaint, canvas, null);
            currentY = getNextLine(currentY);

            List<CalendarEvent> events = getCalendarEvents();
            events.sort(new Comparator<CalendarEvent>()
            {
                public int compare(CalendarEvent event1, CalendarEvent event2){ return event1.getBegin().compareTo(event2.getBegin()); }
            });
            for (CalendarEvent event : events) {
                long eventLengthMs = - event.getBegin().getTime() + event.getEnd().getTime();
                if (!event.isAllDay() && !(eventLengthMs >= TimeUnit.HOURS.toMillis(24))) {
                    time.setTimeInMillis(event.getBegin().getTime());
                    float degreesFromNorth = getDegreesFromNorth(time);
                    mHandPaint.setStyle(Paint.Style.STROKE);
                    drawCircle(degreesFromNorth, alarmDistanceFromCenter, canvas, 6.5f, mHandPaint);
                    mHandPaint.setStyle(Paint.Style.FILL);
                    long inFuture = time.getTimeInMillis() - mCalendar.getTimeInMillis();
                    if (inFuture <= TimeUnit.MINUTES.toMillis(30)) {
                        String title = event.getTitle();
                        title = title != null && title.length() > 0 ? title.substring(0, Math.min(20, title.length())) : "(ohne Titel)";
                        boolean isInFuture = inFuture < 0;
                        String eventHrTitle = isInFuture ?
                                "-" + TimeUnit.MILLISECONDS.toMinutes(event.getEnd().getTime() - mCalendar.getTimeInMillis())
                                : "" + TimeUnit.MILLISECONDS.toMinutes(inFuture);
                        eventHrTitle +=  " " + title;
                        drawTextUprightFromCenter(0,mCenterY - currentY,
                                eventHrTitle, mHandPaint, canvas, isInFuture ? mLight : null);
                        currentY = getNextLine(currentY);
                    }
                }
            }
            // minute hand on exterior ring as last
            drawLineFromCenter(minutesRotation, mCenterX * 0.87f, mCenterX + RAND_RESERVE, mHandPaint, canvas);
        }

        private String getSpecials(BatteryManager batteryManager, Canvas canvas) {
            String specials = "" + (mDebug != null ? mDebug : "");
            try {
                if (batteryManager.getIntProperty(BatteryManager.BATTERY_STATUS_CHARGING) > 0  ) {
                    specials += "↯";
                }
                WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null && wifiManager.isWifiEnabled()) {
                    specials += "W";
                }
                if (getUnreadCount() > 0) { // entweder ungelesene
                    specials += "!";
                }
                else if (getNotificationCount() > 0) { // oder noch andere
                    specials += "¿";
                }
                if (getInterruptionFilter() == INTERRUPTION_FILTER_PRIORITY) {
                    specials += "Ø";
                }
                if (Settings.System.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON) == 1) {
                    specials += "✈";
                }
                else {
                    ConnectivityManager connectivityManager =(ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    Network activeNetwork = connectivityManager.getActiveNetwork();
                    if (activeNetwork == null) {
                        specials += "#";
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

        private void writeHour(Canvas canvas, float radiusCenter, int hour, boolean writeNumber) {
            writeHour(canvas, radiusCenter, hour, ""+hour, writeNumber);
        }

        private void writeHour(Canvas canvas, float radiusCenter, int hour, String hourText, boolean writeNumber) {
            float rotatePerHour = 15f;
            float degreesFromNorth = hour * rotatePerHour;
            float dotDistance = mHourHandLength;

            if (writeNumber) {
                drawTextUprightFromCenter(degreesFromNorth, radiusCenter,
                        hourText, mHandPaint, canvas, null);
            }
            drawCircle(degreesFromNorth, dotDistance, canvas, 4, mHandPaint);
            // black dot in the middle
            drawCircle(degreesFromNorth, dotDistance, canvas,
                    mDarkMode && mLastLux > VERY_DARK ? 3 : 2, mBackgroundPaint);
        }

        private void drawCircle(float rotationFromNorth, float distanceFromCenter, Canvas canvas, float radius, Paint paint) {
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

            /*
            * Whether the timer should be running depends on whether we're visible
            * (as well as whether we're in ambient mode),
            * so we may need to start or stop the timer.
            */
            updateTimer();
        }

        private void registerReceiver() {
            mLightEventListener.selfRegister();
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            mLightEventListener.selfUnregister();
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(R.id.message_update);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }
    }

    private float getNextLine(float currentY) {
        return currentY + 1.05f * TEXT_SIZE;
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
