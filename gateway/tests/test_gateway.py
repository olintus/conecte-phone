import os
import tempfile
import time
import unittest
from pathlib import Path

from conecta_import import gateway


class DeviceStoreTests(unittest.TestCase):
    def test_register_and_remove_are_persisted(self):
        with tempfile.TemporaryDirectory() as directory:
            path = str(Path(directory) / "devices.json")
            store = gateway.DeviceStore(path)
            store.register("312", "75410edb-850a-4455-aa1e-b42dfc240daa", "a" * 40)
            self.assertEqual(["a" * 40], gateway.DeviceStore(path).tokens("312"))
            store.remove("312", "75410edb-850a-4455-aa1e-b42dfc240daa")
            self.assertEqual([], gateway.DeviceStore(path).tokens("312"))


class ValidationTests(unittest.TestCase):
    def test_canonical_device(self):
        extension, installation, token, platform, push_type = gateway.canonical_device({
            "extension": "312",
            "installationId": "75410edb-850a-4455-aa1e-b42dfc240daa",
            "pushToken": "a" * 40,
            "platform": "android",
            "pushType": "fcm",
        })
        self.assertEqual("312", extension)
        self.assertEqual("75410edb-850a-4455-aa1e-b42dfc240daa", installation)
        self.assertEqual("a" * 40, token)
        self.assertEqual("android", platform)
        self.assertEqual("fcm", push_type)

    def test_canonical_ios_voip_device(self):
        extension, _, token, platform, push_type = gateway.canonical_device({
            "extension": "313",
            "installationId": "75410edb-850a-4455-aa1e-b42dfc240daa",
            "pushToken": "ab" * 32,
            "platform": "ios",
            "pushType": "apns_voip",
        })
        self.assertEqual("313", extension)
        self.assertEqual("ab" * 32, token)
        self.assertEqual("ios", platform)
        self.assertEqual("apns_voip", push_type)

    def test_contact_list_extracts_authenticated_installation(self):
        ami = gateway.AmiClient("127.0.0.1", 5038, "user", "secret")
        ami._contact_action_id = "test"
        ami._handle({
            "event": "ContactList",
            "actionid": "test",
            "endpoint": "312",
            "useragent": "ConectePhone/0.4.0-debug;id=75410edb-850a-4455-aa1e-b42dfc240daa",
            "expirationtime": str(int(time.time()) + 300),
        })
        ami._handle({"event": "ContactListComplete", "actionid": "test"})
        self.assertIn("75410edb-850a-4455-aa1e-b42dfc240daa", ami.contacts["312"])

    def test_ami_message_framing_accepts_crlf_and_lf(self):
        first, remainder = gateway.AmiClient._pop_message(
            b"Response: Success\r\nMessage: Authentication accepted\r\n\r\nnext"
        )
        self.assertEqual(first, b"Response: Success\r\nMessage: Authentication accepted")
        self.assertEqual(remainder, b"next")

        second, remainder = gateway.AmiClient._pop_message(
            b"Response: Success\nMessage: Authentication accepted\n\nnext"
        )
        self.assertEqual(second, b"Response: Success\nMessage: Authentication accepted")
        self.assertEqual(remainder, b"next")


if __name__ == "__main__":
    unittest.main()
