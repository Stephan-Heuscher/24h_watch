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
    public static final float DEFAULT_MIN_LUMINANCE = 0.08f;
    public static final float BOOST_MINIMUM_LUX = 100f;
    private MyWatchFaceService.Engine mEngine;
    private SensorManager mSensorManager;
    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private Sensor mLight;
    private boolean mIsRegistered = false;

    private float mLux = 100f;
    private Float mNextDimm = null;
    private Float mLastDimm = 1f;
    private float mMinLuminance = DEFAULT_MIN_LUMINANCE;

    public float getLux() {
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
        // The light sensor returns a single value.
        mLux = event.values[0];
        float nextDimm = computeLightFactor(mLux);
        setNextDimm(nextDimm);
        if (mWakeLock != null && !needsBoost())
        {
            mWakeLock.release();
            mWakeLock = null;
        }
        else if (mWakeLock == null && needsBoost()) {
            mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "heuscher24h:tag");
            mWakeLock.acquire(TimeUnit.SECONDS.toMillis(5));
        }
        if (needsRedraw()){
            mEngine.postInvalidate();
        }
    }

    public boolean needsRedraw() {
        float dimmChange = getNextDimm() - getLastDimm();
        return Math.abs(dimmChange) >= 0.3 || (getNextDimm() < VERY_DARK && Math.abs(dimmChange) >= 0.05 );
    }

    public boolean needsBoost() {
        float dimmChange = getNextDimm() - getLastDimm();
        return Math.abs(dimmChange) >= 0.3f && getNextDimm() >= 0.99f && getLux() > BOOST_MINIMUM_LUX;
    }

    private float computeLightFactor(float lux) {
        return computeFactorFromLight(lux, 1f, 20f, getMinLuminance());
    }

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

    public void setMinLuminance(float mMinLuminance) {
        this.mMinLuminance = mMinLuminance;
    }
}