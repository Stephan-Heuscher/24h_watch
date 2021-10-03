24h Watchface on the Basis of Android Wear watch face codelab
=============================================================

A 24h watchface for my needs (Huawei Watch 2).  Codelab see https://watchface-codelab.appspot.com

Features
--------
   * 24h watch with 24:00h at top - no minute hand, 24h "hand" as dot
   * Dark mode with minimized light emission (dimming controlled by light sensor of the watch)
   * Light mode with extra big numbers (hour-number filled relative to minutes in hour passed)
   * Switch between dark and light mode by tabbing on the top (with "button")
   * Minimal mode with no numbers (date or time; except number of steps per day), minimal status & notifications (status at top if only one status info, else "+" for more)
   * Turn minimal mode on/off by tabbing the middle (no "button" shown)
   * Display of next alarm and calendar events (18h in future)
   * Display title of next calendar event 50 minutes before event
   * Display of time to and time during calendar events (50 minutes before)
   * Display of ISO date and german day
   * Toggling display by 180° by tabbing the right
   * Toggling minutes, date and meeting title display by tabbing the bottom
   * Display of status information at the top of the watch face (if more than one info):  W: Wifi enabled -- !: Unread notifications -- i: Active notifications -- <: Not silenced -- >: Flight mode  -- X: No internet connection -- ⌖: GPS enabled
   * Remaining time on countdown timer on top (works cleanly for timers of less than ten hours)
   * Turn on/off numbers of watch by tabbing right (numbers as buttons), numbers are never shown in dark mode)
   * Battery fill is shown as black dot in the hour hand if battery < 37% 
   * Color-changes according to hour
   * Upcoming meetings in the next 50 minutes are shown either as black (on color) or color (on black) lines in the bit hour numbers (when they are displayed)
   
Next ideas
--------
   * Why does the watchface restart occasionally? --> no reports found in log
   * Use "🔆 Bright Button" and "🔅 Dim Button" instead of sun in the UI
   * Show calendar events only if they have a notification or if status is "busy"
   * Reserve the 24h space for notifications (move dot to 12h / bottom)  
   * Handle display state and style in separate class

License
-------

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.
