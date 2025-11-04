package ch.heuscher.h24watchface;

import android.content.Context;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.support.wearable.watchface.WatchFaceService;
import android.util.Log;

/**
 * Provides system status information for display on the watch face.
 * Gathers data about WiFi, notifications, interruption filter, airplane mode, network, and GPS status.
 */
public class SystemStatusProvider {

    private static final String TAG = "SystemStatusProvider";
    private static final int INTERRUPTION_FILTER_PRIORITY = WatchFaceService.INTERRUPTION_FILTER_PRIORITY;

    private final Context context;

    /**
     * Creates a new SystemStatusProvider.
     *
     * @param context The context to use for system service access
     */
    public SystemStatusProvider(Context context) {
        this.context = context;
    }

    /**
     * Gathers all system status indicators and returns them as a string.
     * Each status is represented by a single character:
     * - W: WiFi enabled
     * - i: Unread notifications
     * - &lt;: Interruption filter not set to priority
     * - &gt;: Airplane mode enabled
     * - X: No active network (when not in airplane mode)
     * - ⌖: GPS enabled
     *
     * @param debugInfo Optional debug information to prepend
     * @param unreadCount Number of unread notifications
     * @param interruptionFilter Current interruption filter mode
     * @return String containing all active status indicators
     */
    public String getSystemStatus(String debugInfo, int unreadCount, int interruptionFilter) {
        String specials = "" + (debugInfo != null ? debugInfo : "");
        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                specials += "W";
            }
            if (unreadCount > 0) {
                specials += "i";
            }
            if (interruptionFilter != INTERRUPTION_FILTER_PRIORITY) {
                specials += "<";
            }
            if (Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON) == 1) {
                specials += ">";
            } else {
                ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                Network activeNetwork = connectivityManager.getActiveNetwork();
                if (activeNetwork == null) {
                    specials += "X";
                }
            }
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                specials += "⌖";
            }
        } catch (Throwable t) {
            // No longer able to draw on canvas from here, so logging the error is the best we can do
            Log.e(TAG, "Error getting system status", t);
        }
        return specials;
    }
}
