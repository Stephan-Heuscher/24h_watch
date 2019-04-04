package ch.heuscher.h24watchface;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class LightEventListener implements SensorEventListener {
    private SensorManager mSensorManager;
    private Sensor mLight;
    private boolean mIsRegistered = false;

    private float mLux = 100f;
    private float mMaxLuxSinceLastRead = 0;

    public float getLux() {
        return mLux;
    }

    public float getMaxLuxSinceLastRead() {
        if(mMaxLuxSinceLastRead == 0){
            return getLux();
        }
        float maxLuxSinceLastRead = mMaxLuxSinceLastRead;
        mMaxLuxSinceLastRead = 0;
        return maxLuxSinceLastRead;
    }

    public LightEventListener(SensorManager mSensorManager) {
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
        mMaxLuxSinceLastRead = Math.max(mMaxLuxSinceLastRead, mLux);
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
}