**Experimental** — installs as a separate app, "Tiiny Remote Exp" (orange "exp" ribbon on its icon), beside the normal Tiiny Remote, with its own settings. Newest features first; may still have rough edges. Download the .apk below on your phone and open it to install or update (or in the app: Settings → App updates → Update app).

## 4.0.3-exp
- **Email pictogram:** a proper coloured envelope (it was drawn as a small outline).
- **NAS agent 1.1 (Slytherin Mode):**
  - **Time zone:** the app now sends your phone's time zone, so "every day at 07:00" is 07:00 where you are; no TZ to set any more (it was Central European time for everyone). Without the app's, TZ from the setup file, else UTC.
  - **One at a time:** automations on the NAS run one after another (two at once share the Tiiny's speed, and a model load while another writes can upset the NPU).
  - **The right model:** without a chosen model, only a loaded chat model is used, never a speech-to-text or OCR model kept loaded by Always ready.
  - **Safer:** search results, pages, calendar entries and emails are marked as data for every automation, not only for agents; a malformed request size is refused; the image brings its own time-zone data.
- **Starting point:** the same as the normal app 4.0.2 (see its release: voice via the model's own port, long recordings, song style references, safer attachments, Mode, the new pictograms and more). New experimental features will be listed here as they come.
