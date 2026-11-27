"""Automated checks of the NAS agent, run on every build (a failing check stops the release)."""
import os
import sys
import tempfile
import unittest

os.environ.setdefault("DATA_DIR", tempfile.mkdtemp(prefix="agent-test-"))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import agent  # noqa: E402


class PrivateAddresses(unittest.TestCase):
    def test_private_and_public(self):
        for ip in ("192.168.3.143", "10.0.0.1", "127.0.0.1", "100.64.1.1", "::1", "::ffff:192.168.1.1"):
            self.assertTrue(agent.not_public(ip), ip)
        for ip in ("8.8.8.8", "1.1.1.1"):
            self.assertFalse(agent.not_public(ip), ip)


if __name__ == "__main__":
    unittest.main()
