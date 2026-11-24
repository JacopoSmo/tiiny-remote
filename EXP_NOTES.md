**Experimental** — installs as a separate app, "Tiiny Remote Exp" (orange "exp" ribbon on its icon), beside the normal Tiiny Remote, with its own settings. Newest features first; may still have rough edges. Download the .apk below on your phone and open it to install or update (or in the app: Settings → App updates → Update app).

## 4.0.4-exp
- **Connector tests you can follow:** Test (NAS agent, email) shows "Testing…" until the answer comes, and every NAS agent request and test result is in the log (Automate). A refused token now says so plainly (also where Android reports it as an error of its own), spaces or line breaks copied with the address or token are ignored, and an address without http:// is pointed out.
- **NAS agent 1.2:** the app sends the Tiiny's address the way it uses it itself (http:// added, a trailing /v1 taken off): an address without http:// made the Tiiny "not reachable from the NAS". The agent also accepts such addresses, and Test now says why the NAS can't reach the Tiiny.
- **Lost the token?** The terminal guide has a last step that shows it again from the NAS.
- **Test answers where you tested:** a connector's Test shows its answer right below the button (green when it worked, red when not) instead of at the top of the page, with **Open the log (Automate) →**, which opens the log showing only Automate's entries.
- **Starting point:** the same as the normal app 4.0.3 (see its release: NAS agent 1.1 with your phone's time zone, one automation at a time, and more). New experimental features will be listed here as they come.
