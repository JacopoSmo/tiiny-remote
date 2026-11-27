"""Automated checks of the NAS agent, run on every build (a failing check stops the release). A small fake Tiiny
answers like the real one, so the night shift and the tests are checked end to end."""
import base64
import json
import os
import sys
import tempfile
import threading
import time
import unittest
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

os.environ["DATA_DIR"] = tempfile.mkdtemp(prefix="agent-test-")
os.environ["AGENT_TOKEN"] = "test-token-0123456789abcdef"
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import agent  # noqa: E402

PNG = base64.b64encode(b"\x89PNG\r\n\x1a\n" + b"x" * 200).decode()


class FakeTiiny(BaseHTTPRequestHandler):
    running = set()
    models = [{"id": "Q/Big", "thinking": {"supported": True, "toggleable": True}},
              {"id": "Q/Small", "thinking": {"supported": False}},
              {"id": "openai/gpt-oss-20b", "thinking": {"supported": True, "toggleable": False}},
              {"id": "Q/Judge", "thinking": {"supported": True, "toggleable": True}}]
    chat_bodies = []
    fail_next = 0       # how many chat requests answer 500
    slow = 0.0          # seconds each chat answer takes
    verdict = '{"A": {"score": 7, "why": "fine"}, "B": {"score": 4, "why": "weak"}}'

    def log_message(self, *a):
        pass

    def send(self, code, obj, ctype="application/json"):
        body = obj if isinstance(obj, bytes) else json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/v1/models":
            return self.send(200, {"data": self.models})
        if self.path == "/api/v1/models/running":
            return self.send(200, {"instances": {"running": [{"model_id": m} for m in sorted(self.running)]}})
        if self.path == "/api/v1/npu/status":
            return self.send(200, {"npu": {"status": "ready", "reason": None}})
        self.send(404, {"error": "nope"})

    def do_POST(self):
        n = int(self.headers.get("Content-Length", "0") or 0)
        body = json.loads(self.rfile.read(n) or b"{}")
        p = self.path
        if p.endswith("/launch"):
            FakeTiiny.running.add(body["model_id"])
            return self.send(200, {"message": "start loading"})
        if p.endswith("/stop"):
            FakeTiiny.running.discard(urllib.request.unquote(p.split("/")[-2]))
            return self.send(200, {"message": "stopped"})
        if p == "/v1/images/generations":
            return self.send(200, {"data": [{"b64_json": PNG}]})
        if p == "/v1/chat/completions":
            FakeTiiny.chat_bodies.append(body)
            if FakeTiiny.fail_next > 0:
                FakeTiiny.fail_next -= 1
                return self.send(500, {"error": {"message": "server error"}})
            if body["model"] not in self.running:
                return self.send(400, {"error": {"message": "model not loaded"}})
            time.sleep(FakeTiiny.slow)
            user = body["messages"][-1]["content"]
            if "grading answers" in user:
                text = FakeTiiny.verdict
            elif "Summarise this earlier part" in body["messages"][0]["content"]:
                text = "They chose Cascais over Sintra. " * 10
            else:
                text = "word " * 50
            if body.get("stream"):
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream")
                self.end_headers()
                for piece in ("Cas", "cais"):
                    self.wfile.write(b"data: " + json.dumps({"choices": [{"delta": {"content": piece}}]}).encode() + b"\n\n")
                    self.wfile.flush()
                self.wfile.write(b"data: [DONE]\n\n")
                return
            return self.send(200, {"choices": [{"message": {"content": text}}], "usage": {"completion_tokens": 300}})
        self.send(404, {"error": "nope"})


def serve(handler):
    srv = ThreadingHTTPServer(("127.0.0.1", 0), handler)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    return srv


TIINY = serve(FakeTiiny)
TEXTS = {"story": "Write a story.", "job_system": "Summarise this earlier part of a conversation", "job_text": "User: hi",
         "prefix_text": "Note 1\n\n" + "x" * 2000, "q1": "Which town?", "q2": "How long?"}


def setup_app(night=None):
    cfg = {"tiiny": {"url": f"http://127.0.0.1:{TIINY.server_port}", "api_key": "k"}, "time_zone": "Europe/Zurich",
           "automations": []}
    if night:
        cfg["night"] = night
    agent.save(agent.CONFIG_F, {"apps": {"normal": cfg}})
    agent.save(agent.JOBS_F, [])
    agent.save(agent.RUNS_F, [])
    agent.save(agent.NIGHT_F, {})
    agent.CTX.app = "normal"
    agent.THINKING.clear()
    FakeTiiny.running = {"Q/Embed"}
    FakeTiiny.chat_bodies = []
    FakeTiiny.fail_next = 0
    FakeTiiny.slow = 0.0


def job(kind, spec, when="now", jid=None):
    return agent.new_job({"kind": kind, "when": when, "spec": spec, "title": kind, "id": jid or None})


def finished(jid):
    return next(j for j in agent.jobs_all() if j["id"] == jid)


class NightWindow(unittest.TestCase):
    def test_window(self):
        self.assertTrue(agent.in_window(2, 0, 1, 6))
        self.assertFalse(agent.in_window(6, 0, 1, 6))
        self.assertTrue(agent.in_window(23, 30, 22, 6))   # over midnight
        self.assertTrue(agent.in_window(5, 59, 22, 6))
        self.assertFalse(agent.in_window(12, 0, 22, 6))
        self.assertFalse(agent.in_window(3, 0, 4, 4))     # an empty window


class PrivateAddresses(unittest.TestCase):
    def test_private_and_public(self):
        for ip in ("192.168.3.143", "10.0.0.1", "127.0.0.1", "100.64.1.1", "::1", "::ffff:192.168.1.1"):
            self.assertTrue(agent.not_public(ip), ip)
        for ip in ("8.8.8.8", "1.1.1.1"):
            self.assertFalse(agent.not_public(ip), ip)


class GradePrompt(unittest.TestCase):
    def test_same_as_the_app(self):
        # The same fixture as QualityMathTest.gradePromptShape in the app.
        g = {"intro": "INTRO\n\n", "outro": "OUTRO"}
        t = {"prompt": "P", "rubric": "R"}
        got = agent.grade_prompt(g, t, [("A", "one"), ("B", "  ")])
        self.assertEqual(got, "INTRO\n\nTASK:\nP\n\nWHAT A GOOD ANSWER DOES:\nR\n\n=== ANSWER A ===\none\n\n=== ANSWER B ===\n(no answer)\n\nOUTRO")


class SpeedTest(unittest.TestCase):
    def test_pair_end_to_end(self):
        setup_app()
        j = job("speed", {"runs": 2, "texts": TEXTS, "pairs": [{"big": {"id": "Q/Big", "name": "Big"}, "small": {"id": "Q/Small", "name": "Small"}}]})
        agent.run_job(j)
        done = finished(j["id"])
        self.assertEqual(done["status"], "done", done.get("summary"))
        res = agent.load(os.path.join(agent.JOBS_DIR, j["id"], "result.json"), {})
        raw = res["raws"][0]
        self.assertEqual(len(raw["runs"]), 2)
        self.assertEqual(set(raw["runs"][0]), {"big_alone", "small_alone", "big_both", "small_both", "help1", "help2", "small_job", "big_job"})
        self.assertEqual(raw["runs"][0]["big_alone"]["tokens"], 300)
        self.assertEqual({c["model"] for c in raw["prefix"]}, {"Big", "Small"})
        # The models loaded before are back, the test's own unloaded.
        self.assertEqual(FakeTiiny.running, {"Q/Embed"})
        # Thinking off as the app does it: a switchable model gets the switch, one that can't think nothing.
        big = next(b for b in FakeTiiny.chat_bodies if b["model"] == "Q/Big" and not b.get("stream"))
        self.assertEqual(big.get("chat_template_kwargs"), {"enable_thinking": False})

    def test_a_500_is_tried_again(self):
        setup_app()
        FakeTiiny.running.add("Q/Big")
        FakeTiiny.fail_next = 1
        agent.call  # noqa: B018
        t0 = time.monotonic()
        orig = agent.time.sleep
        agent.time.sleep = lambda s: None
        try:
            out = agent.speed_one("x", "Q/Big", "story")
        finally:
            agent.time.sleep = orig
        self.assertEqual(out["tokens"], 300)
        self.assertLess(time.monotonic() - t0, 5)

    def test_stop_now(self):
        setup_app()
        FakeTiiny.slow = 0.5
        j = job("speed", {"runs": 5, "texts": TEXTS, "pairs": [{"big": {"id": "Q/Big", "name": "Big"}, "small": {"id": "Q/Small", "name": "Small"}}]})
        t = threading.Thread(target=agent.run_job, args=(j,))
        t.start()
        time.sleep(1.5)
        agent.stop_job(j["id"], True)
        t.join(30)
        self.assertFalse(t.is_alive())
        self.assertEqual(finished(j["id"])["status"], "stopped")


class QualityTest(unittest.TestCase):
    def test_answers_and_grades(self):
        setup_app()
        tasks = [{"id": "t1", "title": "T1", "prompt": "Do one", "rubric": "R1", "max_tokens": 100},
                 {"id": "t2", "title": "T2", "prompt": "Do two", "rubric": "R2", "max_tokens": 100}]
        spec = {"models": [{"id": "Q/Big", "name": "Big"}, {"id": "Q/Small", "name": "Small"}], "judge": {"id": "Q/Judge", "name": "Judge"},
                "tasks": tasks, "answer_system": "Be helpful.", "grade": {"intro": "You are grading answers.\n\n", "outro": "JSON"},
                "order": {"t1": ["Small", "Big"], "t2": ["Big", "Small"]}}
        j = job("quality", spec)
        agent.run_job(j)
        self.assertEqual(finished(j["id"])["status"], "done")
        raw = agent.load(os.path.join(agent.JOBS_DIR, j["id"], "result.json"), {})["raw"]
        self.assertEqual(len(raw["answers"]), 4)
        self.assertEqual(set(raw["verdicts"]), {"t1", "t2"})
        self.assertIn("score", raw["verdicts"]["t1"])
        # The grader saw the answers in the order the app chose.
        graded = [b for b in FakeTiiny.chat_bodies if b["model"] == "Q/Judge"]
        self.assertEqual(len(graded), 2)


class MakeThings(unittest.TestCase):
    def test_image_and_files(self):
        setup_app()
        j = job("image", {"model": "Q/Img", "body": {"model": "Q/Img", "prompt": "a cat"}, "count": 2, "seed": 5})
        agent.run_job(j)
        done = finished(j["id"])
        self.assertEqual(done["status"], "done")
        self.assertEqual(done["files"], ["image_1.png", "image_2.png"])
        self.assertTrue(os.path.getsize(os.path.join(agent.JOBS_DIR, j["id"], "image_1.png")) > 100)

    def test_bad_jobs_refused(self):
        setup_app()
        for bad in ({"kind": "rm -rf", "spec": {}}, {"kind": "chat", "when": "later", "spec": {}},
                    {"kind": "chat", "spec": {}, "id": "../x"}, {"kind": "chat", "spec": "x"}):
            with self.assertRaises(ValueError):
                agent.new_job(bad)


class NightShift(unittest.TestCase):
    def test_night_jobs_wait_for_the_window(self):
        setup_app(night={"on": False, "from": 1, "to": 6})
        j = job("chat", {"model": "Q/Big", "prompt": "hi"}, when="night")
        self.assertIsNone(agent.next_job())
        now = job("chat", {"model": "Q/Big", "prompt": "hi"})
        self.assertEqual(agent.next_job()["id"], now["id"])
        # A window that is open right now.
        h = time.localtime().tm_hour
        setup_app(night={"on": True, "from": 0, "to": 0})
        self.assertFalse(agent.night_open(agent.config()))
        import datetime as dt
        z = agent.zone()
        h = dt.datetime.now(z).hour
        setup_app(night={"on": True, "from": h, "to": (h + 1) % 24})
        j = job("chat", {"model": "Q/Big", "prompt": "hi"}, when="night")
        self.assertEqual(agent.next_job()["id"], j["id"])

    def test_morning_report(self):
        import datetime as dt
        h = dt.datetime.now(agent.zone()).hour
        setup_app(night={"on": True, "from": h, "to": (h + 1) % 24})
        j = job("chat", {"model": "Q/Big", "prompt": "hi"}, when="night")
        agent.run_job(j)
        self.assertEqual(finished(j["id"])["status"], "done")
        agent.morning("normal")
        runs = agent.load(agent.RUNS_F, [])
        self.assertEqual(runs[0]["name"], "Night shift")
        self.assertTrue(runs[0]["ok"])
        self.assertIn("chat", runs[0]["text"])
        # The model loaded before the night is back, the night's own unloaded.
        self.assertEqual(FakeTiiny.running, {"Q/Embed"})


class Endpoints(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        agent.TOKEN = os.environ["AGENT_TOKEN"]
        cls.srv = serve(agent.Handler)

    def req(self, method, path, body=None, token=True):
        data = json.dumps(body).encode() if body is not None else None
        r = urllib.request.Request(f"http://127.0.0.1:{self.srv.server_port}{path}", data=data, method=method,
                                   headers={"Authorization": "Bearer " + agent.TOKEN} if token else {})
        try:
            with urllib.request.urlopen(r, timeout=10) as resp:
                return resp.status, json.loads(resp.read() or b"{}")
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read() or b"{}")

    def test_jobs_over_http(self):
        setup_app()
        code, j = self.req("POST", "/jobs", {"kind": "chat", "when": "night", "spec": {"model": "Q/Big", "prompt": "hi"}, "title": "Hello"})
        self.assertEqual(code, 202)
        self.assertNotIn("spec", j)
        code, lst = self.req("GET", "/jobs")
        self.assertEqual([x["id"] for x in lst["jobs"]], [j["id"]])
        code, _ = self.req("POST", f"/jobs/{j['id']}/stop", {"now": False})
        self.assertEqual(finished(j["id"])["status"], "stopped")
        code, _ = self.req("DELETE", f"/jobs/{j['id']}")
        self.assertEqual(code, 200)
        self.assertEqual(self.req("GET", "/jobs")[1]["jobs"], [])
        # Without the token: nothing.
        self.assertEqual(self.req("GET", "/jobs", token=False)[0], 401)
        # A file name that tries to leave the job's folder isn't served.
        self.assertEqual(self.req("GET", "/jobs/abc/files/..%2Fconfig.json")[0], 404)

    def test_delete_runs(self):
        setup_app()
        agent.save(agent.RUNS_F, [{"id": "r1", "app": "normal"}, {"id": "r2", "app": "normal"}, {"id": "r1", "app": "exp"}])
        code, out = self.req("DELETE", "/runs?id=r1")
        self.assertEqual(out["deleted"], 1)
        left = agent.load(agent.RUNS_F, [])
        self.assertEqual([(r["id"], r["app"]) for r in left], [("r2", "normal"), ("r1", "exp")])


if __name__ == "__main__":
    unittest.main()
