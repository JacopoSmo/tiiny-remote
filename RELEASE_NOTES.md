Download the .apk below on your phone and open it to install or update (or in the app: Settings → App updates → Update app). It installs over 3.2 and keeps your settings and chats.

## What's new in 4.0
- **Code (new):** a coding model on your Tiiny works with you on projects kept on the phone. 💬 Ask (questions, code to copy; it may read the files), 🛠 Build (it writes and changes the files itself), 🎓 Learn (step by step, for starting out). Files to read or edit yourself, Preview for web pages, Share as .zip, and GitHub: copy a repository in, push a project in one commit, make a new private repository. Models → Types → Coding shows the models good at code.
- **Automate (new):** 🤖 agents (a goal and tools; the model chooses its steps) and 📋 processes (news, web search, calendar or email → a model → a notification), at set times or on tap. Calendar and email (IMAP, read-only) are connected separately. Runs on the phone, or around the clock on your NAS with the NAS agent (`nas-agent/` in this repository, a small Docker service).
- **Translate (new):** typed text (language detected), a photo (menu, sign, letter) or speech.
- **Create, redesigned:** 3D · Audio · Image · Text · Video, each with its choices. **Gallery** (new): everything made, searchable, by kind, grouped by day, month or year.
- **Chat:**
  - ＋ beside the message: an image (from the phone or a new photo) or a file (PDF, Word, Excel, PowerPoint, text; read on the phone). Long files are condensed after the first answer, so later questions start sooner.
  - 🎤 dictation with the Tiiny's own speech-to-text (your voice stays at home).
  - History (🕘) with each chat's last model, and "Use it" / "Load it" at the end of a reopened chat.
  - 💾 Save: green, chats are saved; red, incognito.
  - ⧉ Copy under your questions; Translate and Share under answers, bottom right.
  - What you type is kept when you leave the screen or the app.
- **Getting more out of the NPU:** no model is loaded while another one is writing (the NPU faults seen were timeouts on the link those loads use). The helper's jobs (chat names, follow-up ideas) wait until the answer is finished. Settings → Always ready keeps speech-to-text and/or OCR loaded whenever there's room.
- **NPU watch:** the log notes which models share the NPU, and an NPU fault records what was loaded at the time. A new fault makes the status strip pulse red until you tap it. The strip shows the NPU's 48 GB for the models and the CPU's own RAM separately.
- **Speed statistics:** speed tests with 1 or 3–5 runs (outliers left out), and 📊 Statistics with all tests and the speed of normal chat answers, alone and beside another model.
- **Stay connected in the background** (Settings): a silent notification, the Tiiny checked every minute and unlocked after a restart, even with the app closed.
- **Help:** a short welcome tour at the first start, and a **?** beside every page's title that walks you through that page.
- **Sidebar:** Chat, Code, Create, Gallery, Automate, Translate, then Activity, Models, Settings. ⚙ sets the start screen and the order; ⏻ shuts the app down completely (and offers to unload the models first).
- **VPN:** another app's VPN (DuckDuckGo's App Tracking Protection, say) is no longer taken for WireGuard. Android runs one VPN at a time, so switching WireGuard on stops the other one.
- **Cleaned up ("Slytherin Mode"):** "image" instead of "picture" everywhere; long menus no longer run off the screen; one meaning per pictogram; files and repositories are read with size limits; GitHub tokens never reach a shared log; and many smaller fixes.
