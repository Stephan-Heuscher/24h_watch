package ch.heuscher.h24watchface;

import static ch.heuscher.h24watchface.WatchFaceConstants.COLORS;
import static ch.heuscher.h24watchface.WatchFaceConstants.DARK_MODE_HUE;
import static ch.heuscher.h24watchface.WatchFaceConstants.DARK_MODE_SATURATION;

import android.graphics.Color;
import android.support.v4.graphics.ColorUtils;

/**
 * Handles all color calculations for the watch face, including:
 * - 24-hour color gradient calculations
 * - Dark mode color adjustments
 * - Alpha/transparency calculations based on lighting conditions
 */
public class ColorCalculator {

    /**
     * Calculates the color for a given position on the 24-hour color wheel.
     * Colors are blended smoothly between the predefined color points.
     *
     * @param degreesFromNorth The angle in degrees (0-360) from north
     * @return The calculated RGB color with full luminance
     */
    public int getColorDegrees(float degreesFromNorth) {
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

    /**
     * Calculates the appropriate color for the watch hand and text based on mode and lighting.
     *
     * @param isDarkMode Whether dark mode is enabled
     * @param lightFactor The current light level factor (1.0-15.0 typically)
     * @return The calculated color (dark beige in dark mode, white otherwise)
     */
    public int getHandPaintColor(boolean isDarkMode, float lightFactor) {
        if (isDarkMode) {
            // Ensure outline remains visible by maintaining minimum brightness of VERY_DARK threshold
            float brightness = Math.max(lightFactor, DimmingController.VERY_DARK);
            return Color.HSVToColor(new float[]{DARK_MODE_HUE, DARK_MODE_SATURATION, brightness});
        } else {
            return Color.WHITE;
        }
    }

    /**
     * Calculates the alpha (transparency) value for elements that need dimming.
     * Used for the hour number and watch hand dot to ensure consistent visibility.
     *
     * @param isDarkMode Whether dark mode is enabled
     * @param lightFactor The current light level factor
     * @return Alpha value (0-255)
     */
    public int calculateAlpha(boolean isDarkMode, float lightFactor) {
        return isDarkMode ? 218 - Math.min((int) (lightFactor * 200), 100) : 160;
    }
}
