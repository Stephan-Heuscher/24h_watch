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

import static ch.heuscher.h24watchface.WatchFaceConstants.COMPLICATION_ID;
import static ch.heuscher.h24watchface.WatchFaceConstants.PROJECTION;
import static ch.heuscher.h24watchface.WatchFaceConstants.ROTATION_180_DEGREES;

import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.provider.WearableCalendarContract;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Analog 24h watch face. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFaceService extends CanvasWatchFaceService {

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    public class Engine extends CanvasWatchFaceService.Engine {

        private ZonedDateTime mZonedDateTime;
        private WatchFaceDrawer mWatchFaceDrawer;
        private StepCounterManager mStepCounterManager;

        private boolean mAmbient;
        private boolean mDarkMode = true;
        private boolean mMinimalMode = false;
        private boolean mShowMinutesDateAndMeetings = true;

        private DimmingController mDimmingController;
        private float mRotate = 0;
        private long mLastReadCountdownTime;
        private LocalTime mLastCountdownTime;

        private String mDebug = null;


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
            if (mDarkMode) {
                mDimmingController.selfRegister();
            } else {
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

            SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            mStepCounterManager = new StepCounterManager(sensorManager);

            mDimmingController = new DimmingController(
                    this,
                    getBaseContext(),
                    sensorManager);
            setDarkMode(true);

            mZonedDateTime = ZonedDateTime.now();
            mWatchFaceDrawer = new WatchFaceDrawer(getBaseContext());

            setDefaultComplicationProvider(COMPLICATION_ID, new ComponentName("com.google.android.deskclock",
                            "com.google.android.deskclock.complications.TimerProviderService"),
                    ComplicationData.TYPE_SHORT_TEXT);
            setActiveComplications(COMPLICATION_ID);
        }

        @Override
        public void onDestroy() {
            mDimmingController.selfUnregister();
            mStepCounterManager.unregister();
            super.onDestroy();
        }

        @Override
        public void onComplicationDataUpdate(int watchFaceComplicationId, ComplicationData data) {
            super.onComplicationDataUpdate(watchFaceComplicationId, data);
            String timerValue = null;
            if (watchFaceComplicationId == COMPLICATION_ID && data.getShortText() != null) {
                // This is the timer complication
                try {
                    mLastReadCountdownTime = System.currentTimeMillis();
                    timerValue = "" + data.getShortText()
                            .getText(getBaseContext(), mLastReadCountdownTime);
                    if (timerValue.contains(":")) {
                        if (timerValue.length() == 5) {
                            timerValue = "00:" + timerValue;
                        }
                        if (timerValue.length() == 4) {
                            timerValue = "0" + timerValue + ":00";
                        }
                        mLastCountdownTime = LocalTime.parse(timerValue);
                    } else {
                        mLastCountdownTime = null;
                    }
                } catch (Exception e) {
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
            mWatchFaceDrawer.onSurfaceChanged(width, height);
        }

        @Override
        public void onTapCommand(
                @TapType int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case WatchFaceService.TAP_TYPE_TAP:
                    int width = mWatchFaceDrawer.getWidth();
                    int height = mWatchFaceDrawer.getHeight();

                    // Define the boundaries for the center square (middle third of the screen)
                    float oneThirdWidth = width / 3f;
                    float twoThirdsWidth = width * 2 / 3f;
                    float oneThirdHeight = height / 3f;
                    float twoThirdsHeight = height * 2 / 3f;

                    // Check for center tap first, in the middle third of the screen
                    if (x > oneThirdWidth && x < twoThirdsWidth && y > oneThirdHeight && y < twoThirdsHeight) {
                        mMinimalMode = !mMinimalMode;
                    }
                    // Then, check the outer regions
                    else if (y <= oneThirdHeight) {
                        setDarkMode(!isDarkMode());
                    } else if (y >= twoThirdsHeight) {
                        mShowMinutesDateAndMeetings = !mShowMinutesDateAndMeetings;
                    } else if (x <= oneThirdWidth) {
                        // Left region is currently unused but reserved
                    } else if (x >= twoThirdsWidth) {
                        mRotate = mRotate == 0 ? ROTATION_180_DEGREES : 0;
                    }
                    invalidate();
                    break;

                case WatchFaceService.TAP_TYPE_TOUCH_CANCEL:
                    invalidate();
                    break;

                case WatchFaceService.TAP_TYPE_TOUCH:
                    // Let superclass handle touch events, which is important for complications
                default:
                    super.onTapCommand(tapType, x, y, eventTime);
                    break;
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mZonedDateTime = ZonedDateTime.now();
            mWatchFaceDrawer.onDraw(canvas, this, mZonedDateTime, mDimmingController);
        }

        public String getSpecials() {
            String specials = "" + (mDebug != null ? mDebug : "");
            try {
                WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null && wifiManager.isWifiEnabled()) {
                    specials += "W";
                }
                if (getUnreadCount() > 0) { // entweder ungelesene
                    specials += "i";
                }
                // 2022-07-03 Nur noch ungelesene anzeigen
                //else if (getNotificationCount() > 0) { // oder noch andere
                //    specials += "i";
                //}
                if (getInterruptionFilter() != INTERRUPTION_FILTER_PRIORITY) {
                    specials += "<";
                }
                if (Settings.Global.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON) == 1) {
                    specials += ">";
                } else {
                    ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    Network activeNetwork = connectivityManager.getActiveNetwork();
                    if (activeNetwork == null) {
                        specials += "X";
                    }
                }
                LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    specials += "⌖";
                }
            } catch (Throwable t) {
                // No longer able to draw on canvas from here, so logging the error is the best we can do
                Log.e("MyWatchFaceService", "Error getting specials", t);
            }
            return specials;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mStepCounterManager.register();
                invalidate();
            } else {
                mStepCounterManager.unregister();
            }
        }

        public long getLastDraw() {
            return mZonedDateTime.toInstant().toEpochMilli();
        }

        public List<CalendarEvent> getCalendarEvents() {
            List<CalendarEvent> events = new ArrayList<>();
            Uri.Builder builder = WearableCalendarContract.Instances.CONTENT_URI.buildUpon();
            long begin = System.currentTimeMillis();
            ContentUris.appendId(builder, begin);
            long hourNoShow = TimeUnit.HOURS.toMillis(WatchFaceConstants.CALENDAR_QUERY_WINDOW_HOURS);
            ContentUris.appendId(builder, begin + hourNoShow);
            final Cursor cursor = getContentResolver().query(builder.build(), PROJECTION, null, null, null);
            if (cursor == null) {
                return events;
            }
            while (cursor.moveToNext()) {
                long beginVal = cursor.getLong(0);
                long endVal = cursor.getLong(1);
                String title = cursor.getString(2);
                boolean isAllDay = !cursor.getString(3).equals("0")
                        || endVal - beginVal >= TimeUnit.HOURS.toMillis(24) - TimeUnit.MINUTES.toMillis(1);
                CalendarEvent newEvent = new CalendarEvent();
                newEvent.setTitle(title);
                ZonedDateTime beginTime = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(beginVal), java.time.ZoneId.systemDefault());
                newEvent.setBegin(beginTime);
                ZonedDateTime endTime = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(endVal), java.time.ZoneId.systemDefault());
                newEvent.setEnd(endTime);
                newEvent.setAllDay(isAllDay);
                // todo: why does it not filter out non-available meetings?
                boolean isBusy = cursor.getInt(4) == CalendarContract.Instances.AVAILABILITY_BUSY;
                if (isBusy && !isAllDay) {
                    events.add(newEvent);
                }
            }
            cursor.close();
            return events;
        }

        public ZonedDateTime getZonedDateTime() {
            return mZonedDateTime;
        }

        public int getSteps() {
            return mStepCounterManager.getSteps();
        }

        public int getStepsToday() {
            return mStepCounterManager.getStepsToday();
        }

        public boolean isMinimalMode() {
            return mMinimalMode;
        }

        public boolean isShowMinutesDateAndMeetings() {
            return mShowMinutesDateAndMeetings;
        }

        public LocalTime getLastCountdownTime() {
            return mLastCountdownTime;
        }

        public long getLastReadCountdownTime() {
            return mLastReadCountdownTime;
        }

        public String getDebug() {
            return mDebug;
        }

        public float getRotate() {
            return mRotate;
        }

        public DimmingController getDimmingController() {
            return mDimmingController;
        }
    }
}
