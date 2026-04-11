# NavPanchang — User Guide

A practical walkthrough for using NavPanchang day-to-day. Written for someone who wants to fast on every Ekadashi without ever having to look up a date.

---

## 1. Getting started

### First run

1. Install NavPanchang from the Play Store (or sideload the AAB for beta testers).
2. Open the app. You'll see a welcome screen in English.
3. Tap **हिन्दी** if you'd like the app in Hindi — this can be changed later in Settings.
4. Grant permissions when prompted:
   - **Location** — so sunrise calculations are accurate. You can tap *"Skip and choose a city"* if you prefer not to share precise location.
   - **Notifications** — so alarms can actually reach you.
   - **Exact alarms** — tap *"Allow exact alarms"*; this lets NavPanchang fire an alarm at the precise sunrise minute.
   - **Battery optimization** — tap *"Don't restrict"* when prompted. This is the single most important step for reliable alarms. NavPanchang shows a red dot next to **Settings** if this isn't done.
5. Choose your **Home city**. On a new account, this usually defaults to your current city.
6. The app pre-selects **Ekadashi**, **Purnima**, and **Amavasya** as suggested subscriptions. Toggle any of them off, add others, or leave as is.
7. NavPanchang computes the next 24 months of events for your Home city. This takes about 4 seconds.
8. You land on the Home screen.

---

## 2. The Home screen

The Home screen is the heart of the app. It has three parts, from top to bottom:

### Today status card
Always visible. Shows today's tithi (with Hindi name), the current paksha (Shukla/Krishna), sunrise time, and sunset time. This card lets you confirm the app is working before you trust it with your fasts.

### Preparation card
A colored status card that changes based on the nearest subscribed event:

| Color | Meaning |
|---|---|
| 🟡 **Yellow — "Preparing for Tomorrow"** | An event is within 24 hours. Example: "Preparing for Tomorrow — Shukla Ekadashi, sunrise 5:42 AM." |
| 🟢 **Green — "Observing Today"** | The event is happening right now. Shows today's sunrise and Parana info. |
| 🔵 **Cyan — "Parana window"** | For Ekadashi: the fast-breaking window is open. Shows the exact end time so you don't miss it. |
| _hidden_ | No subscribed event is nearby. |

### Subscription list
Every supported event with an ON/OFF toggle. Each row shows the date of the next occurrence.

Tap a row to open the **Event Detail** screen.

---

## 3. Subscribing to an event

From the Home screen:

1. Find the event you want in the subscription list (e.g. "Ekadashi").
2. Tap the toggle switch to turn it ON.

That's it. NavPanchang will automatically schedule:

- A **Planner alarm** the evening before each occurrence (8:00 PM by default).
- An **Observer alarm** at sunrise on the day of each occurrence.
- A **Parana alarm** the next morning (Ekadashi only) with the fast-breaking window.

If the event you want isn't listed, tap **+ Add more events** at the bottom to see the full catalog.

---

## 4. Event Detail screen

Tap any subscribed event's row to open its detail screen. You'll see:

### Next 12 occurrences
A list of the next 12 dates this event occurs, each with:
- The Gregorian date and day of the week.
- The sunrise time at your current location.
- For Ekadashi: the Parana window (e.g. `"Parana: 15 Apr, 5:44 AM – 9:18 AM"`).
- A `[!]` indicator if that occurrence is a Kshaya or shifted-due-to-viddha anomaly (tap for an explanation).

### Alarm controls
Three independent toggles:

- **Planner** — the evening-before reminder.
- **Observer** — the sunrise alarm on the day.
- **Parana** — only visible for Ekadashi. The fast-breaking reminder.

### Custom Planner time
Change the default 8:00 PM Planner time to whatever suits your routine. For example, if you usually cook at 5 PM, set it to 4:30 PM so you have time to plan.

### Sound picker
Choose from four bundled sounds for the ritual alarms:
- **Temple bell** (default)
- **Conch (sankh)**
- **Bell toll**
- **Soft Om mantra**

---

## 5. Calendar tab

Tap the **Calendar** icon in the bottom navigation.

- Each cell of the month grid shows the Gregorian date and a small tithi label (e.g. "शु ११" for Shukla Ekadashi).
- Subscribed-event days have a colored dot.
- Tap any day to open the **Day Detail** screen with the full panchang — tithi, nakshatra, yoga, karana, vara, sunrise, sunset, moonrise, moonset.
- Swipe left/right (or tap the arrows) to change months.

### Adhik Maas
If the viewed month is an intercalary (Adhik) month, the month header shows something like `"Adhik Shravan 2023 (अधिक श्रावण)"` with an `(i)` icon. Tap the icon for a short explanation: regular vrats like Ekadashi are still observed, but major annual festivals (like Mahashivratri) are performed in the following month.

### Kshaya tithi
On the rare days when a tithi is Kshaya (it begins and ends between two sunrises), the Day Detail screen draws a small timeline showing the exact tithi start and end times between the two surrounding sunrises. A caption explains how NavPanchang decided which day to observe the vrat.

---

## 6. Settings tab

Tap the **Settings** icon in the bottom navigation.

### Home City
Your home base. The 24-month Calendar view is computed for this city. Tap to change — you can search by name or drop a pin.

### Current Location
Read-only. Shows the GPS coordinates NavPanchang is currently using and when they were last updated.

### Reliability Check
This section is critical. It shows status pills for:

- ✅ / ❌ **Exact alarms** — tap to grant if red.
- ✅ / ❌ **Notifications** — tap to grant if red.
- ✅ / ❌ **Battery optimization** — tap to whitelist if red. Devices like Xiaomi, Samsung, OnePlus, Vivo, Oppo, and Realme can silently kill NavPanchang if this isn't done.
- ✅ / ❌ **Background location** (optional) — only needed for automatic travel detection.

A red dot on the Settings bottom-nav tab surfaces any unresolved issue so you notice from anywhere in the app.

### Default Planner time
Change the default 8:00 PM Planner time. Individual events can override this.

### Daily Morning Briefing
Optional 7:00 AM notification summarizing today's tithi and any subscribed event. Low-priority so it never wakes you — it's silent unless you unlock your phone. Off by default.

### Sunrise Time Offset
A slider for ±1 or ±2 minute manual adjustment. Use this only if NavPanchang's sunrise times consistently differ from your trusted local printed panchang. The app uses standard astronomical horizon refraction; some local traditions use slightly different values.

### Language
English or हिन्दी. Changes the app language independently of your device's system language.

### Theme
System default, Light, or Dark.

### About
Version, credits, and — importantly — a link to the **source code on GitHub**. NavPanchang is licensed under **AGPL v3** and uses the Swiss Ephemeris library by Astrodienst AG.

---

## 7. When you travel

If you've granted background location:

1. You book a flight from Lucknow to Dubai.
2. When you land and unlock your phone, Android automatically detects you've crossed the 100 km geofence.
3. NavPanchang silently recomputes the next 30–60 days of your subscribed events for Dubai's coordinates.
4. The Home screen updates: the next Ekadashi's sunrise time now shows Dubai's sunrise with a small "(Adjusted for Dubai time)" badge next to it.
5. Your Observer and Parana alarms will fire at the Dubai sunrise, not Lucknow's.

If you've **not** granted background location, the recompute happens the next time you open the app.

Your Planner alarms (the 8 PM evening-before reminder) are anchored to wall-clock time, so they automatically follow your device's timezone — 8 PM in Lucknow becomes 8 PM in Dubai.

---

## 8. What happens when you subscribe late

Say you suddenly remember at 10 PM Monday that Ekadashi is Tuesday. You enable the subscription.

- The 8 PM Planner alarm has already passed — NavPanchang will NOT fire it retroactively (that would be confusing).
- Instead, the Home screen immediately shows a green **"Observing Tomorrow — Ekadashi"** card so you know the app got the message.
- The Observer alarm will fire at sunrise Tuesday morning as expected.
- The Parana alarm will fire at sunrise Wednesday.

---

## 9. Troubleshooting

### "My alarm didn't fire"

1. Open **Settings → Reliability Check**. Is everything green?
2. If Battery Optimization is red, tap **Fix** and choose "Don't restrict". This is the #1 cause.
3. Check that the subscription's Observer (or Planner) toggle is ON in the Event Detail screen.
4. On some Xiaomi / Samsung phones, also check the manufacturer's own "Autostart" or "Protected apps" setting and allow NavPanchang.

### "The sunrise time is 1 minute different from my printed panchang"

Go to **Settings → Sunrise Time Offset** and nudge the slider to +1 or –1 minute. The app will apply the offset to all future sunrise-anchored alarms and Calendar displays.

### "I see Ekadashi on a different day than my printed panchang"

This is usually **Dashami-Viddha** at work — a tradition-specific shift that some panchangs apply and others don't. Tap the event in the Home list to open the detail screen; if the occurrence shows `"Shifted — Dashami-Viddha"`, that explains it. NavPanchang follows the standard Smarta rule (shift to Dvadashi if Dashami is present during the 96 min before sunrise). If your family tradition follows a different rule, please [open an issue on GitHub](https://github.com/) — it's a rule we can make configurable.

### "I flew somewhere and the app still shows my old city's sunrise"

1. Force-open NavPanchang after landing. If background location is denied, travel detection only runs on app launch.
2. Check **Settings → Current Location** — does it show the new city?
3. If not, grant background location in **Settings → Reliability Check**.

### "I want to completely reset the app"

On Android, go to **System Settings → Apps → NavPanchang → Storage → Clear storage**. This wipes everything (subscriptions, occurrences, alarms, home city) and forces a fresh onboarding. Your Google account backup will restore your subscription list automatically on next install if backup was enabled.

---

## 10. Privacy

NavPanchang does not send any data to any server. Everything — your subscriptions, your location, your alarm schedule — stays on your phone. There are no analytics, no ads, no crash reporting SDKs. The source code is public on GitHub so you can verify this for yourself.

The only network activity in v1 is whatever your Google account's standard backup does with NavPanchang's SharedPreferences and database files — you control that via your Google account's backup settings.

---

## 11. Getting help

- **Found a bug?** Open an issue at the GitHub repo linked from **Settings → About**.
- **Want a feature?** File an issue with the "enhancement" label.
- **Tradition-specific rule doesn't match yours?** Please open an issue — we're collecting variations so a future release can expose a rule-variant setting.

---

**Enjoy NavPanchang, and may your fasts be undisturbed.** 🙏
