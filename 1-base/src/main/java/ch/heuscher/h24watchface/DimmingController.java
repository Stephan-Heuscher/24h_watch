package ch.heuscher.h24watchface;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class DimmingController implements SensorEventListener {
    public static final float VERY_DARK = 0.3f;
    public static final float DEFAULT_MIN_LUMINANCE = 0.08f;
    private SensorManager mSensorManager;
    private Sensor mLight;
    private boolean mIsRegistered = false;

    private float mLux = 100f;
    private Float mNextDimm = null;
    private Float mLastDimm = 1f;
    private boolean mChangeSignaled = false;
    private float mMinLuminance = DEFAULT_MIN_LUMINANCE;

    private AlarmManager mAmbientUpdateAlarmManager;
    private PendingIntent mAmbientUpdatePendingIntent;

    public float getLux() {
        return mLux;
    }

    public DimmingController(Context context, AlarmManager alarmManager, SensorManager mSensorManager) {
        mAmbientUpdateAlarmManager = alarmManager;
        Intent ambientUpdateIntent = new Intent(MyWatchFaceService.AMBIENT_UPDATE_ACTION);

        mAmbientUpdatePendingIntent = PendingIntent.getBroadcast(
                context, 0, ambientUpdateIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        this.mSensorManager = mSensorManager;
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
        if (needsRedraw() && !mChangeSignaled){
            mChangeSignaled = true;
            //wake up to redraw
//            mAmbientUpdateAlarmManager.setExactAndAllowWhileIdle( // not used because of long reaction times
            mAmbientUpdateAlarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 500,
                    mAmbientUpdatePendingIntent);
        }
    }

    public boolean needsRedraw() {
        float dimmChange = getNextDimm() - getLastDimm();
        return Math.abs(dimmChange) >= 0.3 || (getNextDimm() < VERY_DARK && Math.abs(dimmChange) >= 0.05 );
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
            mAmbientUpdateAlarmManager.cancel(mAmbientUpdatePendingIntent);
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
        mChangeSignaled = false;
        this.mLastDimm = mLastDimm;
    }

    public float getMinLuminance() {
        return mMinLuminance;
    }

    public void setMinLuminance(float mMinLuminance) {
        this.mMinLuminance = mMinLuminance;
    }
}