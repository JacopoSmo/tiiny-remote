# Tiiny Remote NAS agent

Runs the automations you mark **"on the NAS"** in Tiiny Remote around the clock, also when your phone is off or away.
The app sends it those automations and what they need; the agent runs them at their time and keeps the results,
which the app shows under **Automate → Results** (and, if you like, pushes to your phone via ntfy).

It's one small Python file using only the standard library, in a Docker container. Automations run one at a time (a second one waits for the first), on your phone's time zone, which the app sends.

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

## What it does and doesn't

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
