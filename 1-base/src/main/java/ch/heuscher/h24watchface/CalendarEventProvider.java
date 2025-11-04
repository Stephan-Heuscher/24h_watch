package ch.heuscher.h24watchface;

import static ch.heuscher.h24watchface.WatchFaceConstants.PROJECTION;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.support.wearable.provider.WearableCalendarContract;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Provides calendar event data from the device's calendar provider.
 * Handles querying the Wearable Calendar API and converting results to CalendarEvent objects.
 */
public class CalendarEventProvider {

    private final ContentResolver contentResolver;

    /**
     * Creates a new CalendarEventProvider.
     *
     * @param contentResolver The content resolver to use for calendar queries
     */
    public CalendarEventProvider(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
    }

    /**
     * Queries upcoming calendar events within the specified time window.
     * Only returns busy (non-available) events that are not all-day events.
     *
     * @param queryWindowHours How many hours into the future to query
     * @return List of CalendarEvent objects, may be empty but never null
     */
    public List<CalendarEvent> getCalendarEvents(long queryWindowHours) {
        List<CalendarEvent> events = new ArrayList<>();
        Uri.Builder builder = WearableCalendarContract.Instances.CONTENT_URI.buildUpon();
        long begin = System.currentTimeMillis();
        ContentUris.appendId(builder, begin);
        long hourNoShow = TimeUnit.HOURS.toMillis(queryWindowHours);
        ContentUris.appendId(builder, begin + hourNoShow);
        final Cursor cursor = contentResolver.query(builder.build(), PROJECTION, null, null, null);
        if (cursor == null) {
            return events;
        }
        try {
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
        } finally {
            cursor.close();
        }
        return events;
    }
}
