package ch.heuscher.h24watchface;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;

import java.util.concurrent.TimeUnit;

public class DimmingController implements SensorEventListener {
    public static final float VERY_DARK = 0.3f;
    public static final float DEFAULT_MIN_LUMINANCE = 0.072f;
    public static final float BOOST_MINIMUM_LUX = 100f;
    private final MyWatchFaceService.Engine mEngine;
    private final SensorManager mSensorManager;
    private final PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private final Sensor mLight;
    private boolean mIsRegistered = false;
    private long mLastSensorChangeTime = 0;

    private float mLux = 100f;
    private Float mNextDimm = 1f;
    private Float mLastDimm = 1f;
    private float mMinLuminance = DEFAULT_MIN_LUMINANCE;

    public float getLux() {
        // Check if long time no upgrade, but should --> re-register
        if (mIsRegistered &&
                System.currentTimeMillis() - mLastSensorChangeTime > TimeUnit.SECONDS.toMillis(65)) {
            selfUnregister();
            selfRegister();
        }
        return mLux;
    }

    public DimmingController(MyWatchFaceService.Engine engine, Context context, PowerManager powerManager,  SensorManager sensorManager) {
        mEngine = engine;
        mPowerManager = powerManager;
        mSensorManager = sensorManager;
        mLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        selfRegister();
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_LIGHT)
            return;
        mLastSensorChangeTime = System.currentTimeMillis();
        // The light sensor returns a single value.
        mLux = event.values[0];
        setNextDimm(computeLightFactor(mLux));
        if (mWakeLock != null && !needsBoost()) {
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
            mWakeLock = null;
        }
        else if (mWakeLock == null && needsBoost()) {
            mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "heuscher24h:tag");
            mWakeLock.acquire(TimeUnit.SECONDS.toMillis(3));
        }
        else if (needsRedraw()){
            mEngine.invalidate();
        }
    }

    public boolean needsRedraw() {
        return System.currentTimeMillis() > mEngine.getLastDraw() + 500
                && getDimmChange() >= 0.4;
    }

    public boolean needsBoost() {
        return getDimmChange() >= 0.05f && getLux() > BOOST_MINIMUM_LUX;
    }

    private float getDimmChange() {
        return Math.abs(getNextDimm() - getLastDimm());
    }

    private float computeLightFactor(float lux) {
        return computeFactorFromLight(lux, 1f, 15f, getMinLuminance());
    }

    /**
     * Konvertiert @lux zu einem Linearen Wert zwischen @maxFactor und @minFactor mit Steigung @luxDivider (für positive Lux)
     */
    private float computeFactorFromLight(float lux, float maxFactor, float luxDivider, float minFactor) {
        return Math.min(maxFactor,  lux/ luxDivider + minFactor);
    }


    protected void selfRegister() {
        if (!mIsRegistered) {
            mSensorManager.registerListener(this, mLight, SensorManager.SENSOR_DELAY_NORMAL);
            mIsRegistered = true;
        }
    }

    protected void selfUnregister() {
        if (mIsRegistered) {
            mSensorManager.unregisterListener(this);
            mIsRegistered = false;
        }
    }

    public Float getNextDimm() {
        return mNextDimm;
    }

    public void setNextDimm(Float mNextDimm) {
        this.mNextDimm = mNextDimm;
    }

    public Float getLastDimm() {
        return mLastDimm;
    }

    public void setLastDimm(Float mLastDimm) {
        this.mLastDimm = mLastDimm;
    }

    public float getMinLuminance() {
        return mMinLuminance;
    }

}