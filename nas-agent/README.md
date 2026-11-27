# Tiiny Remote NAS agent 2.0

Works for Tiiny Remote around the clock, also when your phone is off or away:

- **Automations** you mark "on the NAS", at their time; the results show under **Automate → Results** (and, if you
  like, come to your phone via ntfy).
- **Tests** from the app's **Test** page (speed and quality), run now or tonight; the app turns what the agent measured
  into results exactly as it does for tests on the phone.
- **The night shift** (agent 2.0): in the hours set in the app (Settings → Background efficiency tools) it works through
  the night queue: tests, and images, edits, videos and songs sent with 🌙 Tonight. It loads the models each job needs and
  puts back the ones that were loaded before; in the morning the app brings the results in (made files into the
  Gallery) and a report comes as a notice and under Automate → Results.
- **Load ahead** (Automatic): loads the model you usually use shortly before the hour, into free NPU room only.
- **Backups**, encrypted on the phone before they arrive.

It's one Python file using only the standard library, in a Docker container. One thing at a time on the Tiiny (an
automation waits for a night job and the other way round), on your phone's time zone, which the app sends.

## Set it up (Ugreen NAS, or any Docker host on your home network)

1. Make a secret: `openssl rand -hex 24` (or any 32+ random letters and digits).
2. In Docker (Ugreen: **Docker → Project → Create**), paste [`docker-compose.yml`](docker-compose.yml),
   put your secret in `AGENT_TOKEN` and deploy. (No time zone to set: the app sends your phone's.)
3. In the app: **Automate → Connectors → NAS agent**: address `http://<your NAS's IP>:8765`, the same token,
   **Save**, then **Test**. It should say the Tiiny is reachable.
4. Optional, in the same card: your calendars' **private iCal links** (one field per calendar, up to 10; agent 1.3 or newer
   reads them all, older ones only the first; the NAS can't read the phone's calendar;
   Google Calendar: Settings → your calendar → "Secret address in iCal format") and an **ntfy** topic URL for push.
5. Edit an automation, set **Runs on → The NAS agent**, save. The app sends it to the NAS.

Tiiny Remote and Tiiny Remote Exp can both use the same agent: from version 1.5 on it keeps each app's automations,
connectors and results apart.

### From a terminal instead (SSH from the phone: Termius, Termux…)

The app shows these steps with a Copy button each: **Automate → Connectors → NAS agent → Set up NAS**.

```
mkdir -p ~/tiiny-agent && cd ~/tiiny-agent
TOKEN=$(openssl rand -hex 24); echo $TOKEN     # copy it for the app
curl -fsSL https://raw.githubusercontent.com/JacopoSmo/tiiny-remote/main/nas-agent/docker-compose.yml -o docker-compose.yml
sed -i "s/change-me-to-a-long-random-secret/$TOKEN/" docker-compose.yml
sudo docker compose up -d                       # or: sudo docker-compose up -d
sudo docker logs tiiny-agent                    # "… on port 8765"
hostname -I                                     # the NAS's address for the app
```

After an update (`sudo docker compose pull && sudo docker compose up -d`), optionally free space by deleting the
old images nothing uses any more: `sudo docker image prune -f`.

Lost the token? The NAS still has it: `grep AGENT_TOKEN ~/tiiny-agent/docker-compose.yml`, or from the running agent:
`sudo docker exec tiiny-agent printenv AGENT_TOKEN`.

The image is built from this folder by GitHub Actions (`ghcr.io/jacoposmo/tiiny-remote-agent`). If you'd rather build it
yourself, replace the `image:` line with `build: https://github.com/JacopoSmo/tiiny-remote.git#main:nas-agent`.

## Encrypted link and backups (agent 1.7 and newer)

- **Encrypted link:** the agent makes its own certificate on first start (kept in the data volume, `/data/tls`). The app
  trusts the one it meets first and from then on only that one, so the token, your Tiiny key and the email password
  travel encrypted on the home network. Check it once: the agent's log shows "Certificate fingerprint …"; the app's
  **Test** shows the same characters. After the app has used the encrypted link, the agent refuses unencrypted requests
  (only `/health` stays open). Set the agent up from scratch? Tap **Forget certificate** in the app. To allow unencrypted
  requests again (an old app), delete `/data/tls/https-only`.
- **Backups:** Settings → Privacy and backup in the app. The phone encrypts everything with your backup password before it
  leaves; the agent only stores the files (`/data/backups`, the newest five, at most 20 GB each) and can't read them.

## What it does and doesn't

- **Jobs** (agent 2.0): at most 50 waiting; one at a time; Stop from the app after the current step or at once. Made
  files wait in the data volume (`/data/jobs`) until the app has fetched them, at most two weeks. When the Tiiny reports
  an NPU fault during a job, the app is told which models were on it.

- **Processes:** news or web search (your SearXNG if set in the app, else DuckDuckGo), calendar (iCal link; daily and
  weekly repeats included), email (IMAP, read-only) → the model → the result.
- **Agents:** a goal and tools (web search, news, open pages, calendar, email) in a loop, up to 12 steps or 15 minutes.
- **Models:** uses the chosen model, loads it if there's NPU room (never unloads another), or any loaded chat model.
- It doesn't unlock the Tiiny after a restart; the app (or the NAS's `tiiny login`) does that.

## Safety

- Every request needs the token (compared in constant time; wrong tokens are slowed down). Without a token of at least
  16 characters the service won't start.
- It holds your Tiiny's API key and the email password: its files are readable only inside the container. Keep port
  8765 on your home network (don't forward it on the router); reach it from outside through your VPN.
- Agents only open links from their own search results, never pages on your own network, treat page and email text as
  data rather than instructions, and don't go online again in a run once they've read your calendar or email.
