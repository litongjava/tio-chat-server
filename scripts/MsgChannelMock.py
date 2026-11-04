#!/usr/bin/env python3
"""Local stand-ins for message channels: an SMTP sink on :18025 and a Chuanglan SMS API on :18026.
Everything received is appended, one line each, to the file given as the first argument."""
import asyncore, json, smtpd, sys, threading
from email import message_from_bytes
from email.header import decode_header
from http.server import BaseHTTPRequestHandler, HTTPServer

out = sys.argv[1]


def record(kind, data):
    with open(out, 'a', encoding='utf-8') as f:
        f.write(kind + ' ' + json.dumps(data, ensure_ascii=False) + '\n')


class Smtp(smtpd.SMTPServer):
    def process_message(self, peer, mailfrom, rcpttos, data, **kwargs):
        subject = ''.join(p.decode(c or 'utf-8') if isinstance(p, bytes) else p
                          for p, c in decode_header(message_from_bytes(data).get('Subject', '')))
        record('MAIL', {'to': rcpttos, 'subject': subject})


class Sms(BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
        record('SMS', {'path': self.path, 'phone': body.get('phone'), 'params': body.get('params'), 'msg': body.get('msg')})
        reply = json.dumps({'code': '0', 'msgId': '1', 'time': '1', 'errorMsg': ''}).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(reply)))
        self.end_headers()
        self.wfile.write(reply)

    def log_message(self, *args):
        pass


Smtp(('127.0.0.1', 18025), None, decode_data=False)
threading.Thread(target=asyncore.loop, daemon=True).start()
HTTPServer(('127.0.0.1', 18026), Sms).serve_forever()
