#!/usr/bin/env bash
# Moments (feed) through the desktop client's frontend, driven by dsb.
# alice publishes a text post from the moments window and an image post through the upload path
# (presigned MinIO upload + push, as the mobile client does); bob is notified live, sees both posts
# with the image, likes and comments; alice's moments window updates live with the like and comment.
# Needs the MinIO storage engine (see README). Usage: scripts/client-feed-e2e.sh [output-dir]
cd "$(dirname "$0")"; set -eo pipefail
OUT=$(mkdir -p "${1:-client-e2e-out}" && cd "${1:-client-e2e-out}" && pwd)
source ./lib.sh
source ./client-lib.sh
task dsb_a "${A_ID:-4321}"
task dsb_b "${B_ID:-4322}"

read EA UA EB UB < <(./make-friends.sh | tail -1)
NA=${EA%@*}; NB=${EB%@*}
TEXT_POST="今天天气很好，这是我的第一条朋友圈"
IMAGE_POST="傍晚的晚霞，配一张图"
COMMENT="确实不错，周末一起出去走走"
echo "alice=$EA bob=$EB"

# a 160x100 test image
python3 -I - "$OUT/sunset.png" <<'EOF'
import sys, zlib, struct
w, h = 160, 100
raw = b''.join(b'\x00' + bytes(sum(([255, 120 + y, 60 + x // 2] for x in range(w)), [])) for y in range(h))
def chunk(t, b):
    return struct.pack('>I', len(b)) + t + b + struct.pack('>I', zlib.crc32(t + b) & 0xffffffff)
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
open(sys.argv[1], 'wb').write(png)
EOF

login dsb_a "$A_URL" "$EA" "$NB"
login dsb_b "$B_URL" "$EB" "$NA"

# alice opens moments (a secondary window -> new tab) and publishes a text post
dsb_a run click_element_by_selector -p 'selector=svg:has(> use[href="#fire"])'
use_tab dsb_a /dynamic
wait_text dsb_a 暂无动态
dsb_a run click_element_by_selector -p 'selector=svg:has(> use[href="#plus"])'
dsb_a run wait_for_element -p selector=textarea -p timeoutMs=5000
dsb_a run input_text_by_selector -p selector=textarea -p "text=$TEXT_POST"
shot dsb_a 31-feed-publish.png
dsb_a run click_element_by_text -p text=发布 -p exact=true
wait_text dsb_a 发布成功
wait_text dsb_a "$TEXT_POST"

# the image post goes through the same upload path as chat images: presigned PUT to MinIO, then push.
# The desktop moments window only publishes text, so this step uses the API like the mobile client.
read TA2 _ < <(login_user "$EA" MOBILE m1)
CRED=$(curl -s "$HOST/api/system/anyTenant/ossToken?scene=feed&fileName=sunset.png" -H "token: $TA2")
UPLOAD_URL=$(echo "$CRED" | json "d['data']['uploadUrl']")
IMAGE_URL=$(echo "$CRED" | json "d['data']['downloadUrl']")
curl -sf -XPUT "$UPLOAD_URL" -H 'Content-Type: image/png' --data-binary "@$OUT/sunset.png"
api POST /api/im/feed/pushFeed "$TA2" "{\"content\":\"$IMAGE_POST\",\"mediaType\":1,\"urls\":[\"$IMAGE_URL\"],\"permission\":\"open\"}" \
  | json "d['code']" | grep -qx 200

# bob gets the feedSendMsg push: the moments entry shows the unread count
use_tab dsb_b /message
dsb_b run wait_for_function -p "expression=!!document.querySelector('.n-badge:has(> svg > use[href=\"#fire\"]) .n-badge-sup')" -p timeoutSeconds=10
shot dsb_b 32-bob-feed-badge.png
dsb_b run click_element_by_selector -p 'selector=svg:has(> use[href="#fire"])'
use_tab dsb_b /dynamic
wait_text dsb_b "$IMAGE_POST"
wait_text dsb_b "$TEXT_POST"
dsb_b run wait_for_function -p "expression=document.body.innerText.indexOf('$IMAGE_POST') < document.body.innerText.indexOf('$TEXT_POST')" -p timeoutSeconds=5
dsb_b run wait_for_function -p "expression=[...document.images].some(i => i.src === '$IMAGE_URL' && i.complete && i.naturalWidth === 160)" -p timeoutSeconds=15
shot dsb_b 33-bob-feed.png

# bob likes and comments on the text post
POST_JS="[...document.querySelectorAll('div')].filter(d => d.innerText && d.innerText.startsWith('$NA') && d.innerText.includes('$TEXT_POST') && d.innerText.includes('评论')).pop()"
dsb_b js "(() => { const post = $POST_JS; [...post.querySelectorAll('*')].find(e => e.childElementCount === 0 && e.textContent.trim() === '赞').click(); return 1 })()"
dsb_b run wait_for_function -p "expression=($POST_JS).innerText.includes('已赞')" -p timeoutSeconds=10
dsb_b js "(() => { const post = $POST_JS; [...post.querySelectorAll('*')].find(e => e.childElementCount === 0 && e.textContent.trim() === '评论').click(); return 1 })()"
dsb_b run wait_for_element -p selector=textarea -p timeoutMs=5000
dsb_b run input_text_by_selector -p selector=textarea -p "text=$COMMENT"
dsb_b run click_element_by_text -p text=发送 -p exact=true
wait_text dsb_b "$NB：$COMMENT"
sleep 1
shot dsb_b 34-bob-liked-commented.png

# alice's open moments window receives feedNotify and shows the like, the comment and the unread count
use_tab dsb_a /dynamic
dsb_a run wait_for_function -p "expression=($POST_JS).innerText.includes('$NB：$COMMENT') && ($POST_JS).innerText.includes('评论 1')" -p timeoutSeconds=10
dsb_a run wait_for_function -p "expression=document.querySelector('.n-badge-sup')?.title === '2'" -p timeoutSeconds=10
shot dsb_a 35-alice-feed-notified.png

# the post detail page lists the like and the comment from the server
dsb_a js "(() => { const post = $POST_JS; [...post.querySelectorAll('*')].find(e => e.childElementCount === 0 && e.textContent.trim() === '$TEXT_POST').click(); return 1 })()"
dsb_a run wait_for_url -p "url=**/dynamic/*" -p timeoutMs=10000
wait_text dsb_a "$COMMENT"
sleep 1
shot dsb_a 36-alice-feed-detail.png

finish dsb_a
finish dsb_b
ls -la "$OUT"
