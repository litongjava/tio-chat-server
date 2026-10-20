#!/usr/bin/env bash
# End-to-end test of image and file messages in the desktop client's frontend (browser shim) against
# tio-chat-server with the MinIO storage engine. Alice picks an image and a file through the file
# button (the shim turns the native picker into a hidden <input type=file>, filled by dsb upload_file),
# the client uploads them to presigned MinIO URLs, and Bob must receive both in real time.
# Usage: scripts/client-upload-e2e.sh [output-dir]   (run with docker access, e.g. sg docker -c ...)
cd "$(dirname "$0")"; set -eo pipefail
OUT=$(mkdir -p "${1:-client-upload-e2e-out}" && cd "${1:-client-upload-e2e-out}" && pwd)
source ./client-lib.sh
A_ID=${A_ID:-4311}; B_ID=${B_ID:-4312}
task dsb_a "$A_ID"
task dsb_b "$B_ID"

# Test files: a 160x100 gradient PNG and a small text file
python3 -I - "$OUT" <<'EOF'
import sys, zlib, struct
d = sys.argv[1]; w, h = 160, 100
raw = b''.join(b'\x00' + bytes(sum(([x * 255 // w, y * 255 // h, 180] for x in range(w)), [])) for y in range(h))
def chunk(t, b):
    return struct.pack('>I', len(b)) + t + b + struct.pack('>I', zlib.crc32(t + b) & 0xffffffff)
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
open(d + '/sample.png', 'wb').write(png)
open(d + '/report.txt', 'w').write('tio-chat-server file message test\n' * 200)
EOF

# Pick a file through the chat footer's file button: pick <task> <file> <content-type>
pick() {
  "$1" run click_element_by_selector -p 'selector=svg:has(> use[href="#file2"])'
  "$1" run wait_for_function -p "expression=!!document.getElementById('__shim_file_input')" -p timeoutSeconds=5
  "$1" run upload_file -p 'selector=#__shim_file_input' -p "contentBase64=$(base64 -w0 "$2")" -p "filename=$(basename "$2")" -p "contentType=$3"
}

read EA UA EB UB < <(./make-friends.sh | tail -1)
echo "alice=$EA bob=$EB"

login dsb_b "$B_URL" "$EB" "${EA%@*}"
open_chat dsb_b "${EA%@*}"
login dsb_a "$A_URL" "$EA" "${EB%@*}"
open_chat dsb_a "${EB%@*}"

# Image: inserted into the input box, sent with Enter
pick dsb_a "$OUT/sample.png" image/png
dsb_a run wait_for_function -p "expression=!!document.querySelector('#message-input img')" -p timeoutSeconds=10
dsb_a run press_key -p key=Enter
IMG_OK="Array.from(document.querySelectorAll('.n-image img')).some(i => i.complete && i.naturalWidth === 160)"
dsb_b run wait_for_function -p "expression=$IMG_OK" -p timeoutSeconds=20

# File: confirmed in the send-file dialog
pick dsb_a "$OUT/report.txt" text/plain
dsb_a run wait_for_text -p "text=发送 (1)" -p timeoutMs=10000
dsb_a run click_element_by_text -p "text=发送 (1)"
dsb_b run wait_for_function -p "expression=document.body.innerText.includes('report.txt') && document.body.innerText.includes('6.64 KB')" -p timeoutSeconds=20
dsb_a run wait_for_function -p "expression=$IMG_OK && document.body.innerText.includes('report.txt')" -p timeoutSeconds=10
sleep 1
shot dsb_a 01-alice-sent-image-file.png
shot dsb_b 02-bob-received-image-file.png

dsb_a close
dsb_b close
ls -la "$OUT"
