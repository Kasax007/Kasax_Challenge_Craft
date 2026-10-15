"""A small RCON client for the training servers (Minecraft's remote console)."""
import socket
import struct
import threading


class RconError(Exception):
    pass


class Rcon:
    """One connection to a server's RCON port; reconnects on demand, one command at a time."""

    def __init__(self, port, password="test", host="127.0.0.1", timeout=30):
        self.host, self.port, self.password, self.timeout = host, port, password, timeout
        self.sock = None
        self.lock = threading.Lock()
        self.ids = 0

    def _packet(self, kind, body):
        self.ids += 1
        data = body.encode("utf-8") + b"\x00\x00"
        return self.ids, struct.pack("<iii", len(data) + 8, self.ids, kind) + data

    def _recv_exact(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise RconError("connection closed")
            buf += chunk
        return buf

    def _recv(self):
        size = struct.unpack("<i", self._recv_exact(4))[0]
        data = self._recv_exact(size)
        rid, kind = struct.unpack("<ii", data[:8])
        return rid, kind, data[8:-2].decode("utf-8", errors="replace")

    def connect(self):
        self.close()
        s = socket.create_connection((self.host, self.port), timeout=self.timeout)
        self.sock = s
        rid, pkt = self._packet(3, self.password)
        s.sendall(pkt)
        got, _, _ = self._recv()
        if got == -1:
            self.close()
            raise RconError("wrong password")

    def command(self, cmd):
        with self.lock:
            for attempt in range(2):
                try:
                    if self.sock is None:
                        self.connect()
                    rid, pkt = self._packet(2, cmd)
                    self.sock.sendall(pkt)
                    _, _, body = self._recv()
                    return body
                except (OSError, RconError, struct.error):
                    self.close()
                    if attempt == 1:
                        raise
        return ""

    def close(self):
        if self.sock is not None:
            try:
                self.sock.close()
            except OSError:
                pass
        self.sock = None
