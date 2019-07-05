24h Watchface on the Basis of Android Wear watch face codelab
=============================================================

A 24h watchface for my needs (Huawei Watch 2).  Codelab see https://watchface-codelab.appspot.com

Features
--------
   * 24h watch with 24:00h at top - no minute hand
   * Dark mode with minimized light emission (dimming controlled by light sensor)
   * Light mode with extra big numbers
   * Switch between dark and light mode by tabbing the right (with "button")
   * Minimal mode with no numbers (date or time), minimal status & notifications ("-,|,+" at top for notifications)
   * Turn minimal mode on/off by tabbing the left (with "button")
   * Display of next alarm and calendar events (+ 18h)
   * Display title of next calendar event 30 minutes before event
   * Display of time to and time during calendar events (+ 30 minutes before)
   * Display of ISO date and german day
   * Toggling display by 180° by tabbing the bottom
   * Display of status information at the top of the watch face:  ↯: Charging -- W: Wifi enabled
   -- ⚠: Unread notifications -- ¿: Active notifications -- Ø: Do not disturb -- ✈: Flight mode
   -- #: No internet connection -- ⌖: GPS enabled
   * Remaining time on countdown timer on bottom (works cleanly for timers of less than ten hours)
   * Battery fill is shown as black dot in the hour hand on battery < 50% 
   
Next ideas
--------
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
