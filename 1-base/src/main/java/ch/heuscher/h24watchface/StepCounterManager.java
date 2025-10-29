package ch.heuscher.h24watchface;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.time.LocalDateTime;

public class StepCounterManager implements SensorEventListener {

    private final SensorManager mSensorManager;
    private final Sensor mStepCounter;

    private int mSteps = 0;
    private int mStepsAtMidnight = 0;
    private LocalDateTime lastStepDateTime = LocalDateTime.now();
    private boolean mIsRegistered = false;

    public StepCounterManager(SensorManager sensorManager) {
        mSensorManager = sensorManager;
        mStepCounter = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    }

    public void register() {
        if (!mIsRegistered && mStepCounter != null) {
            mSensorManager.registerListener(this, mStepCounter, SensorManager.SENSOR_DELAY_NORMAL);
            mIsRegistered = true;
        }
    }

    public void unregister() {
        if (mIsRegistered && mStepCounter != null) {
            mSensorManager.unregisterListener(this);
            mIsRegistered = false;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            LocalDateTime currentStepDateTime = LocalDateTime.now();
            if (mStepsAtMidnight == 0 || (currentStepDateTime.getDayOfYear() - lastStepDateTime.getDayOfYear()) != 0) {
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

    public int getSteps() {
        return mSteps;
    }

    public int getStepsToday() {
        return mSteps - mStepsAtMidnight;
    }
}
