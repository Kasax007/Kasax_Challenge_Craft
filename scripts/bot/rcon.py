#!/usr/bin/env python3
"""Minimal RCON client: rcon.py CMD [CMD ...] (server on 127.0.0.1:25575, password 'test')."""
import socket, struct, sys


def pkt(i, t, body):
    b = body.encode() + b'\x00\x00'
    return struct.pack('<iii', len(b) + 8, i, t) + b


def recv(s):
    n = struct.unpack('<i', s.recv(4))[0]
    d = b''
    while len(d) < n:
        d += s.recv(n - len(d))
    return d[8:-2].decode(errors='replace')


s = socket.create_connection(('127.0.0.1', 25575), timeout=30)
s.send(pkt(1, 3, 'test'))
recv(s)
for c in sys.argv[1:]:
    s.send(pkt(2, 2, c))
    print(recv(s))
