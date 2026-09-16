#!/usr/bin/env python3
"""Conecte Phone: local AMI listener and Android/iOS push gateway."""

from __future__ import annotations

import json
import logging
import os
import re
import signal
import socket
import tempfile
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Dict, Iterable, Optional, Set

import requests
import httpx
import jwt
from google.auth.transport.requests import Request
from google.oauth2 import service_account


LOG = logging.getLogger("conecte-push")
FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
INSTALLATION_RE = re.compile(
    r"^ConectePhone/[^;\s]+;id=([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$"
)
EXTENSION_RE = re.compile(r"^[A-Za-z0-9_.-]{1,64}$")
TOKEN_RE = re.compile(r"^[A-Za-z0-9_:\-]{20,4096}$")


def env(name: str, default: Optional[str] = None) -> str:
    value = os.environ.get(name, default)
    if value is None or not value.strip():
        raise RuntimeError(f"Variável obrigatória ausente: {name}")
    return value.strip()


class DeviceStore:
    def __init__(self, path: str) -> None:
        self.path = Path(path)
        self.lock = threading.RLock()
        self.devices: Dict[str, Dict[str, Dict[str, str]]] = {}
        self._load()

    def _load(self) -> None:
        with self.lock:
            if not self.path.exists():
                return
            raw = json.loads(self.path.read_text(encoding="utf-8"))
            if not isinstance(raw, dict):
                raise ValueError("Arquivo de dispositivos inválido")
            self.devices = raw

    def register(self, extension: str, installation_id: str, token: str,
                 platform: str = "android", push_type: str = "fcm") -> None:
        with self.lock:
            bucket = self.devices.setdefault(extension, {})
            bucket[installation_id] = {
                "token": token,
                "platform": platform,
                "pushType": push_type,
                "updatedAt": datetime.now(timezone.utc).isoformat(),
            }
            self._save_locked()

    def remove(self, extension: str, installation_id: str) -> None:
        with self.lock:
            bucket = self.devices.get(extension)
            if bucket is None:
                return
            bucket.pop(installation_id, None)
            if not bucket:
                self.devices.pop(extension, None)
            self._save_locked()

    def tokens(self, extension: str) -> list[str]:
        with self.lock:
            return [entry["token"] for entry in self.devices.get(extension, {}).values()
                    if entry.get("platform", "android") == "android"]

    def registrations(self, extension: str, platform: str) -> list[Dict[str, str]]:
        with self.lock:
            return [dict(entry) for entry in self.devices.get(extension, {}).values()
                    if entry.get("platform", "android") == platform]

    def remove_token(self, token: str) -> None:
        with self.lock:
            changed = False
            for extension in list(self.devices):
                bucket = self.devices[extension]
                for installation_id in list(bucket):
                    if bucket[installation_id].get("token") == token:
                        del bucket[installation_id]
                        changed = True
                if not bucket:
                    del self.devices[extension]
            if changed:
                self._save_locked()

    def _save_locked(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        payload = json.dumps(self.devices, ensure_ascii=False, indent=2, sort_keys=True)
        fd, temporary = tempfile.mkstemp(prefix="devices-", suffix=".json", dir=self.path.parent)
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as handle:
                handle.write(payload)
                handle.flush()
                os.fsync(handle.fileno())
            os.chmod(temporary, 0o600)
            os.replace(temporary, self.path)
        finally:
            if os.path.exists(temporary):
                os.unlink(temporary)


class FcmSender:
    def __init__(self, credentials_path: str, store: DeviceStore) -> None:
        self.credentials = service_account.Credentials.from_service_account_file(
            credentials_path, scopes=[FCM_SCOPE]
        )
        self.project_id = self.credentials.project_id
        self.store = store
        self.session = requests.Session()
        self.lock = threading.Lock()

    def send_call(
        self,
        extension: str,
        event_type: str,
        call_id: str,
        display_name: str = "Ligação recebida",
        handle: str = "privado",
    ) -> None:
        for token in self.store.tokens(extension):
            self._send(token, extension, event_type, call_id, display_name, handle)

    def _access_token(self) -> str:
        with self.lock:
            if not self.credentials.valid or self.credentials.expired:
                self.credentials.refresh(Request())
            return str(self.credentials.token)

    def _send(
        self,
        token: str,
        extension: str,
        event_type: str,
        call_id: str,
        display_name: str,
        handle: str,
    ) -> None:
        if event_type == "incoming_call":
            data = {
                "type": event_type,
                "callId": call_id,
                "displayName": display_name or handle or "Ligação recebida",
                "handle": handle or "privado",
                "accountId": extension,
                "issuedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            }
            ttl = "45s"
        else:
            data = {"type": event_type, "callId": call_id}
            ttl = "10s"
        response = self.session.post(
            f"https://fcm.googleapis.com/v1/projects/{self.project_id}/messages:send",
            headers={
                "Authorization": f"Bearer {self._access_token()}",
                "Content-Type": "application/json",
            },
            json={
                "message": {
                    "token": token,
                    "data": data,
                    "android": {"priority": "HIGH", "ttl": ttl},
                }
            },
            timeout=10,
        )
        if response.ok:
            LOG.info("FCM %s enviado: ramal=%s callId=%s", event_type, extension, call_id)
            return
        body = response.text[:1000]
        LOG.error("Falha FCM: status=%s ramal=%s resposta=%s", response.status_code, extension, body)
        if response.status_code in (400, 404) and (
            "UNREGISTERED" in body or "registration-token-not-registered" in body
        ):
            self.store.remove_token(token)


class ApnsSender:
    def __init__(self, key_path: str, key_id: str, team_id: str,
                 bundle_id: str, store: DeviceStore, sandbox: bool = False) -> None:
        self.private_key = Path(key_path).read_text(encoding="utf-8")
        self.key_id = key_id
        self.team_id = team_id
        self.topic = f"{bundle_id}.voip"
        self.store = store
        self.base_url = "https://api.sandbox.push.apple.com" if sandbox else "https://api.push.apple.com"
        self.client = httpx.Client(http2=True, timeout=10)
        self._provider_token = ""
        self._provider_token_time = 0
        self.lock = threading.Lock()

    def send_call(self, extension: str, event_type: str, call_id: str,
                  display_name: str = "Ligação recebida", handle: str = "privado") -> None:
        # PushKit deve iniciar chamadas. Encerramentos seguem pelo diálogo SIP.
        if event_type != "incoming_call":
            return
        for registration in self.store.registrations(extension, "ios"):
            self._send(registration["token"], extension, call_id, display_name, handle)

    def _jwt(self) -> str:
        with self.lock:
            now = int(time.time())
            if not self._provider_token or now - self._provider_token_time > 3000:
                self._provider_token = jwt.encode(
                    {"iss": self.team_id, "iat": now}, self.private_key,
                    algorithm="ES256", headers={"kid": self.key_id},
                )
                self._provider_token_time = now
            return self._provider_token

    def _send(self, token: str, extension: str, call_id: str,
              display_name: str, handle: str) -> None:
        response = self.client.post(
            f"{self.base_url}/3/device/{token}",
            headers={
                "authorization": f"bearer {self._jwt()}",
                "apns-topic": self.topic,
                "apns-push-type": "voip",
                "apns-priority": "10",
                "apns-expiration": str(int(time.time()) + 45),
            },
            json={"aps": {"content-available": 1}, "type": "incoming_call",
                  "callId": call_id, "displayName": display_name or handle,
                  "handle": handle or "privado", "accountId": extension},
        )
        if response.is_success:
            LOG.info("APNs VoIP enviado: ramal=%s callId=%s", extension, call_id)
        else:
            LOG.error("Falha APNs: status=%s ramal=%s resposta=%s",
                      response.status_code, extension, response.text[:500])
            if response.status_code in (400, 410):
                reason = response.json().get("reason", "") if response.content else ""
                if reason in ("BadDeviceToken", "DeviceTokenNotForTopic", "Unregistered"):
                    self.store.remove_token(token)


class AmiClient(threading.Thread):
    def __init__(self, host: str, port: int, username: str, secret: str) -> None:
        super().__init__(name="ami-client", daemon=True)
        self.host = host
        self.port = port
        self.username = username
        self.secret = secret
        self.stop_event = threading.Event()
        self.connected = threading.Event()
        self.send_lock = threading.Lock()
        self.cache_lock = threading.Condition()
        self.refresh_lock = threading.Lock()
        self.contacts: Dict[str, Set[str]] = {}
        self.cache_generation = 0
        self.socket: Optional[socket.socket] = None
        self.event_handler = lambda _: None
        self._contact_action_id: Optional[str] = None
        self._pending_contacts: Dict[str, Set[str]] = {}

    def run(self) -> None:
        delay = 1
        while not self.stop_event.is_set():
            try:
                self._run_connection()
                delay = 1
            except Exception:
                LOG.exception("Conexão AMI interrompida")
            self.connected.clear()
            with self.refresh_lock:
                self._contact_action_id = None
            if self.socket:
                try:
                    self.socket.close()
                except OSError:
                    pass
                self.socket = None
            self.stop_event.wait(delay)
            delay = min(delay * 2, 30)

    def _run_connection(self) -> None:
        sock = socket.create_connection((self.host, self.port), timeout=5)
        sock.settimeout(1)
        self.socket = sock
        # Consume exatamente a linha do banner. Um recv() simples pode deixar
        # parte dela no buffer e misturá-la com a primeira resposta AMI.
        buffer = b""
        while b"\n" not in buffer:
            chunk = sock.recv(1024)
            if not chunk:
                raise ConnectionError("AMI encerrou a conexão antes do banner")
            buffer += chunk
        _, buffer = buffer.split(b"\n", 1)
        self._send({
            "Action": "Login",
            "Username": self.username,
            "Secret": self.secret,
            "Events": "on",
        })
        last_refresh = 0.0
        while not self.stop_event.is_set():
            if time.monotonic() - last_refresh >= 20:
                self._start_contact_refresh()
                last_refresh = time.monotonic()
            try:
                chunk = sock.recv(65536)
                if not chunk:
                    raise ConnectionError("AMI encerrou a conexão")
                buffer += chunk
            except socket.timeout:
                continue
            while True:
                raw, buffer = self._pop_message(buffer)
                if raw is None:
                    break
                message = self._parse(raw)
                if message:
                    self._handle(message)

    @staticmethod
    def _pop_message(buffer: bytes) -> tuple[Optional[bytes], bytes]:
        """Retira uma mensagem AMI aceitando terminações CRLF ou LF."""
        delimiters = ((b"\r\n\r\n", 4), (b"\n\n", 2))
        matches = [(buffer.find(delimiter), size) for delimiter, size in delimiters]
        matches = [(position, size) for position, size in matches if position >= 0]
        if not matches:
            return None, buffer
        position, size = min(matches, key=lambda match: match[0])
        return buffer[:position], buffer[position + size:]

    @staticmethod
    def _parse(raw: bytes) -> Dict[str, str]:
        result: Dict[str, str] = {}
        for line in raw.decode("utf-8", "replace").splitlines():
            if ":" not in line:
                continue
            key, value = line.split(":", 1)
            result[key.strip().lower()] = value.strip()
        return result

    def _handle(self, message: Dict[str, str]) -> None:
        if "response" in message and not self.connected.is_set():
            if message.get("response", "").lower() != "success":
                raise ConnectionError(
                    f"AMI recusou autenticação: {message.get('message', 'sem detalhe')}"
                )
            self.connected.set()
            LOG.info("AMI autenticado em %s:%s", self.host, self.port)
            self._start_contact_refresh()
            return
        event = message.get("event", "").lower()
        action_id = message.get("actionid")
        if event == "contactlist" and action_id == self._contact_action_id:
            extension = message.get("endpoint", "")
            user_agent = message.get("useragent", "")
            expiration = int(message.get("expirationtime", "0") or "0")
            match = INSTALLATION_RE.match(user_agent)
            if match and EXTENSION_RE.fullmatch(extension) and expiration > int(time.time()):
                self._pending_contacts.setdefault(extension, set()).add(str(uuid.UUID(match.group(1))))
            return
        if event == "contactlistcomplete" and action_id == self._contact_action_id:
            with self.cache_lock:
                self.contacts = self._pending_contacts
                self._pending_contacts = {}
                self.cache_generation += 1
                self.cache_lock.notify_all()
            with self.refresh_lock:
                self._contact_action_id = None
            return
        if event == "userevent":
            self.event_handler(message)

    def _send(self, fields: Dict[str, str]) -> None:
        sock = self.socket
        if sock is None:
            raise ConnectionError("AMI desconectado")
        payload = "".join(f"{key}: {value}\r\n" for key, value in fields.items()) + "\r\n"
        with self.send_lock:
            sock.sendall(payload.encode("utf-8"))

    def _start_contact_refresh(self) -> None:
        if not self.connected.is_set():
            return
        with self.refresh_lock:
            if self._contact_action_id is not None:
                return
            action_id = f"contacts-{uuid.uuid4()}"
            self._contact_action_id = action_id
            self._pending_contacts = {}
        self._send({"Action": "PJSIPShowContacts", "ActionID": action_id})

    def validate_installation(self, extension: str, installation_id: str) -> bool:
        if not self.connected.wait(timeout=3):
            return False
        with self.cache_lock:
            generation = self.cache_generation
        try:
            self._start_contact_refresh()
        except OSError:
            return False
        with self.cache_lock:
            self.cache_lock.wait_for(lambda: self.cache_generation > generation, timeout=3)
            return installation_id in self.contacts.get(extension, set())

    def close(self) -> None:
        self.stop_event.set()
        if self.socket:
            try:
                self.socket.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass


class Gateway:
    def __init__(self) -> None:
        self.store = DeviceStore(env("CP_DEVICE_STORE", "/var/lib/conecte-push/devices.json"))
        self.fcm = FcmSender(env("GOOGLE_APPLICATION_CREDENTIALS"), self.store)
        apns_values = [os.environ.get(name, "").strip() for name in
                       ("CP_APNS_KEY_FILE", "CP_APNS_KEY_ID", "CP_APNS_TEAM_ID", "CP_APNS_BUNDLE_ID")]
        self.apns = ApnsSender(*apns_values, self.store,
                               os.environ.get("CP_APNS_SANDBOX", "false").lower() == "true") \
            if all(apns_values) else None
        self.ami = AmiClient(
            env("CP_AMI_HOST", "127.0.0.1"),
            int(env("CP_AMI_PORT", "5038")),
            env("CP_AMI_USERNAME"),
            env("CP_AMI_SECRET"),
        )
        self.pool = ThreadPoolExecutor(max_workers=4, thread_name_prefix="push")
        self.ami.event_handler = self._on_ami_event

    def start(self) -> None:
        self.ami.start()

    def _on_ami_event(self, event: Dict[str, str]) -> None:
        kind = event.get("userevent", "")
        extension = event.get("extension", "")
        call_id = event.get("callid", "")
        if not EXTENSION_RE.fullmatch(extension) or not call_id:
            return
        if kind == "ConecteIncoming":
            name = event.get("callername") or event.get("callernum") or "Ligação recebida"
            handle = event.get("callernum") or "privado"
            self.pool.submit(self.fcm.send_call, extension, "incoming_call", call_id, name, handle)
            if self.apns:
                self.pool.submit(self.apns.send_call, extension, "incoming_call", call_id, name, handle)
        elif kind == "ConecteEnded":
            self.pool.submit(self.fcm.send_call, extension, "call_ended", call_id)

    def close(self) -> None:
        self.ami.close()
        self.pool.shutdown(wait=False, cancel_futures=True)


def canonical_device(body: object) -> tuple[str, str, str, str, str]:
    if not isinstance(body, dict):
        raise ValueError("JSON deve ser um objeto")
    extension = str(body.get("extension", "")).strip()
    installation_id = str(uuid.UUID(str(body.get("installationId", ""))))
    token = str(body.get("pushToken", "")).strip()
    if not EXTENSION_RE.fullmatch(extension):
        raise ValueError("Ramal inválido")
    platform = str(body.get("platform", ""))
    push_type = str(body.get("pushType", ""))
    if (platform, push_type) not in (("android", "fcm"), ("ios", "apns_voip")):
        raise ValueError("Plataforma de push inválida")
    if not TOKEN_RE.fullmatch(token):
        raise ValueError("Token de push inválido")
    return extension, installation_id, token, platform, push_type


def handler_factory(gateway: Gateway):
    class Handler(BaseHTTPRequestHandler):
        server_version = "ConectePush/1.0"

        def do_GET(self) -> None:
            if self.path != "/healthz":
                self._json(404, {"error": "not_found"})
                return
            self._json(200, {"status": "ok", "ami": gateway.ami.connected.is_set(),
                             "fcm": True, "apns": gateway.apns is not None})

        def do_PUT(self) -> None:
            if self.path != "/v1/mobile/devices":
                self._json(404, {"error": "not_found"})
                return
            try:
                extension, installation_id, token, platform, push_type = canonical_device(self._body())
            except (ValueError, json.JSONDecodeError) as error:
                self._json(400, {"error": "invalid_request", "message": str(error)})
                return
            if not gateway.ami.validate_installation(extension, installation_id):
                self._json(403, {"error": "sip_registration_not_verified"})
                return
            gateway.store.register(extension, installation_id, token, platform, push_type)
            LOG.info("Dispositivo vinculado: ramal=%s instalação=%s", extension, installation_id)
            self.send_response(204)
            self.end_headers()

        def do_DELETE(self) -> None:
            if self.path != "/v1/mobile/devices":
                self._json(404, {"error": "not_found"})
                return
            try:
                extension, installation_id, _, _, _ = canonical_device(self._body())
            except (ValueError, json.JSONDecodeError) as error:
                self._json(400, {"error": "invalid_request", "message": str(error)})
                return
            if not gateway.ami.validate_installation(extension, installation_id):
                self._json(403, {"error": "sip_registration_not_verified"})
                return
            gateway.store.remove(extension, installation_id)
            self.send_response(204)
            self.end_headers()

        def _body(self) -> object:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 32768:
                raise ValueError("Tamanho de corpo inválido")
            return json.loads(self.rfile.read(length).decode("utf-8"))

        def _json(self, status: int, body: object) -> None:
            payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def log_message(self, fmt: str, *args: object) -> None:
            LOG.info("HTTP %s - %s", self.client_address[0], fmt % args)

    return Handler


def main() -> None:
    logging.basicConfig(
        level=getattr(logging, env("CP_LOG_LEVEL", "INFO").upper()),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    gateway = Gateway()
    gateway.start()
    server = ThreadingHTTPServer(
        (env("CP_BIND", "127.0.0.1"), int(env("CP_PORT", "8787"))),
        handler_factory(gateway),
    )
    server.daemon_threads = True

    def stop(*_: object) -> None:
        threading.Thread(target=server.shutdown, daemon=True).start()

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    LOG.info("Gateway ouvindo em http://%s:%s", *server.server_address)
    try:
        server.serve_forever()
    finally:
        gateway.close()
        server.server_close()


if __name__ == "__main__":
    main()
