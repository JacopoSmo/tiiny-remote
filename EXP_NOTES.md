**Experimental** — installs as a separate app, "Tiiny Remote Exp" (orange "exp" ribbon on its icon), beside the normal Tiiny Remote, with its own settings. Newest features first; may still have rough edges. Download the .apk below on your phone and open it to install or update (or in the app: Settings → App updates → Update app).

## 4.0.6-exp
- **Normal app and Exp app on the same NAS:** with **NAS agent 1.5** (image `:exp`) each app keeps its own automations, connectors (calendars, email, push) and results on the NAS; saving in one no longer replaces the other's. Your earlier setup counts as the normal app's. Agents before 1.5 still keep only one setup for both (and keep working: 4.0.6-exp.4 and .5 made them answer "HTTP 404", fixed).
- **Deleting an automation asks first.**
- **Back means back:** going back (← or the back gesture) to Automate, e.g. from the log opened by a test answer, returns to the same section (e.g. Connectors) at the same place on the page. Opening Automate from the side menu starts on its list, at the top.
- **Image shapes:** only the usual ones (1:1, 4:3, 3:2, 16:10, 16:9, 21:9 and their upright versions); a model's sizes are listed under the nearest one instead of odd ratios like 75:56.
- **Fixes from a full test on the phone:**
  - **Automation time:** emptying the time field to retype it no longer switches "At a set time" off; "7:00" and "0700" are accepted too (saved as 07:00).
  - **Tables** in answers and results show as aligned columns instead of raw | lines.
  - **Light mode:** the phone's status bar icons turn dark, so they stay visible.
  - **Calendars:** pasting more than 10 links says how many were left out; ✕ on the last link empties it.
  - **Connectors:** Save says "Saved" (or "Saved and sent to the NAS agent"); email errors say plainly what's wrong (server not found, no answer, user or password refused); "1 automation" instead of "1 automation(s)".
  - **Automation results** (phone and NAS agent 1.4 and newer on `:exp`) end without questions or offers of further help.
  - Alphabetical: the automation examples and the image style chips.
- **Starting point:** the same as the normal app 4.0.5 (see its release: several calendars for the NAS with agent 1.3, set-up guides for NAS, iCal and ntfy). New experimental features will be listed here as they come.
