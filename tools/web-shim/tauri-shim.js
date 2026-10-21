/*
 * Browser shim for the desktop IM client.
 *
 * The desktop client talks to a Rust layer through window.__TAURI_INTERNALS__.invoke.
 * This file provides that object in a plain browser and implements the Rust commands
 * directly against tio-chat-server over HTTP and WebSocket, so the original frontend
 * can run in Chrome and be driven by browser automation.
 *
 * window.__SHIM_IM_URLS__ (url name -> [method, path]) and window.__SHIM_LANG__ are prepended
 * by start-client.sh.
 */
(function () {
  'use strict'

  const IM_URLS = window.__SHIM_IM_URLS__ || {}
  const API_BASE = location.origin + '/api'
  const WS_URL = (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/api/ws/ws'
  const BASIC_AUTH = btoa('luohuo_web_pro:luohuo_web_pro_secret')
  const AUTH_KEY = '__shim_auth'
  const TIME_BLOCK_MS = 10 * 60 * 1000
  const PAYLOAD_PREFIX = '__shim_payload:'

  // The window label survives in-tab navigation through window.name; window.open(url, label)
  // sets it for secondary windows. Fall back to the first path segment.
  const firstSegment = location.pathname.split('/')[1] || ''
  const LABEL = window.name || (firstSegment === '' ? 'login' : firstSegment)
  window.name = LABEL

  const log = (...args) => console.debug('[shim]', ...args)

  // Headless Chrome reports en-US; let the launcher pick the UI language instead.
  if (window.__SHIM_LANG__) {
    const lang = window.__SHIM_LANG__
    Object.defineProperty(navigator, 'language', { get: () => lang })
    Object.defineProperty(navigator, 'languages', { get: () => [lang] })
  }

  // ---------- fake camera and microphone ----------
  // Headless browsers have no media devices. With localStorage.__shim_fake_media = '1' calls get a
  // generated stream instead: a tone for the microphone and an animated canvas for the camera.
  const FAKE_MEDIA_KEY = '__shim_fake_media'
  const fakeMediaEnabled = () => {
    try {
      return localStorage.getItem(FAKE_MEDIA_KEY) === '1'
    } catch (e) {
      return false
    }
  }

  function fakeAudioTrack() {
    const ctx = new (window.AudioContext || window.webkitAudioContext)()
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    const dest = ctx.createMediaStreamDestination()
    osc.frequency.value = 440
    gain.gain.value = 0.05
    osc.connect(gain).connect(dest)
    osc.start()
    const track = dest.stream.getAudioTracks()[0]
    track.addEventListener('ended', () => ctx.close())
    return track
  }

  function fakeVideoTrack() {
    const canvas = document.createElement('canvas')
    canvas.width = 320
    canvas.height = 240
    const g = canvas.getContext('2d')
    let frame = 0
    const draw = () => {
      frame++
      g.fillStyle = 'hsl(' + ((frame * 4) % 360) + ', 60%, 45%)'
      g.fillRect(0, 0, canvas.width, canvas.height)
      g.fillStyle = '#fff'
      g.font = '28px sans-serif'
      g.fillText('fake camera ' + frame, 30, 130)
    }
    draw()
    // timers keep drawing in background tabs, where requestAnimationFrame stops
    const timer = setInterval(draw, 100)
    const track = canvas.captureStream(10).getVideoTracks()[0]
    track.addEventListener('ended', () => clearInterval(timer))
    const stop = track.stop.bind(track)
    track.stop = () => {
      clearInterval(timer)
      stop()
    }
    return track
  }

  if (navigator.mediaDevices) {
    const realGetUserMedia = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices)
    const realEnumerate = navigator.mediaDevices.enumerateDevices.bind(navigator.mediaDevices)
    const fakeDevice = (kind, deviceId, label) => ({
      kind,
      deviceId,
      groupId: 'fake',
      label,
      toJSON() {
        return { kind, deviceId, groupId: 'fake', label }
      }
    })
    navigator.mediaDevices.getUserMedia = (constraints) => {
      if (!fakeMediaEnabled()) {
        return realGetUserMedia(constraints)
      }
      const c = constraints || {}
      const tracks = []
      if (c.audio) {
        tracks.push(fakeAudioTrack())
      }
      if (c.video) {
        tracks.push(fakeVideoTrack())
      }
      log('fake media stream', tracks.map((t) => t.kind).join(','))
      return Promise.resolve(new MediaStream(tracks))
    }
    navigator.mediaDevices.enumerateDevices = () => {
      if (!fakeMediaEnabled()) {
        return realEnumerate()
      }
      return Promise.resolve([
        fakeDevice('audioinput', 'default', 'Fake microphone'),
        fakeDevice('videoinput', 'fake-camera', 'Fake camera'),
        fakeDevice('audiooutput', 'default', 'Fake speaker')
      ])
    }
  }

  // ---------- callbacks (transformCallback / Channel) ----------
  const callbacks = new Map()
  let callbackSeq = 1

  function transformCallback(callback, once) {
    const id = callbackSeq++
    callbacks.set(id, (data) => {
      if (once) {
        callbacks.delete(id)
      }
      return callback && callback(data)
    })
    return id
  }

  function unregisterCallback(id) {
    callbacks.delete(id)
  }

  function runCallback(id, data) {
    const fn = callbacks.get(id)
    if (fn) {
      fn(data)
    }
  }

  function channelId(channel) {
    if (channel == null) {
      return null
    }
    if (typeof channel === 'object' && 'id' in channel) {
      return channel.id
    }
    const text = String(channel)
    return text.startsWith('__CHANNEL__:') ? Number(text.slice(12)) : null
  }

  function channelSend(channel, message) {
    const id = channelId(channel)
    if (id != null) {
      runCallback(id, { index: 0, message })
      runCallback(id, { index: 1, end: true })
    }
  }

  // A channel that carries several ordered messages (progress updates, body chunks).
  function channelStream(channel) {
    const id = channelId(channel)
    let index = 0
    return {
      send(message) {
        if (id != null) {
          runCallback(id, { index: index++, message })
        }
      },
      end() {
        if (id != null) {
          runCallback(id, { index: index++, end: true })
        }
      }
    }
  }

  // ---------- events ----------
  const listeners = new Map()
  let listenerSeq = 1
  const bus = typeof BroadcastChannel === 'function' ? new BroadcastChannel('tauri-shim-events') : null

  function deliver(event, payload) {
    const list = listeners.get(event) || []
    for (const item of list.slice()) {
      runCallback(item.handler, { event, id: item.id, payload })
    }
  }

  function targetMatches(target) {
    if (target == null) {
      return true
    }
    if (typeof target === 'string') {
      return target === LABEL
    }
    if (target.kind === 'Any') {
      return true
    }
    return target.label === LABEL
  }

  function emit(event, payload, target) {
    if (targetMatches(target)) {
      deliver(event, payload)
    }
    if (bus) {
      bus.postMessage({ event, payload, target: target == null ? null : target })
    }
  }

  if (bus) {
    bus.onmessage = (e) => {
      const msg = e.data || {}
      if (msg.wsSend !== undefined) {
        wsSend(msg.wsSend)
        return
      }
      if (msg.closeLabel !== undefined) {
        if (msg.closeLabel === LABEL) {
          closeWindow()
        }
        return
      }
      if (targetMatches(msg.target)) {
        deliver(msg.event, msg.payload)
      }
    }
  }

  // ---------- third-party login callback ----------
  // The desktop app listens on 127.0.0.1:36677 for the server's redirect after Gitee/GitHub/GitCode login.
  // Here the redirect target is this origin: the callback tab checks the one-time secret, hands the tokens
  // to the login window over the event bus and closes itself.
  const OAUTH_SECRET_KEY = '__shim_oauth_secret'

  function startOauthServer() {
    const secret = (crypto.randomUUID && crypto.randomUUID()) || String(Math.random()).slice(2) + Date.now()
    localStorage.setItem(OAUTH_SECRET_KEY, secret)
    return { port: Number(location.port) || (location.protocol === 'https:' ? 443 : 80), secret }
  }

  function handleOauthCallback() {
    // the server appends "?token=..." to a redirect that already has "?secret=...", so read both parts
    const query = new URLSearchParams(location.search.replace(/\?/g, '&'))
    const secret = query.get('secret')
    if (!secret || (!query.get('token') && !query.get('code'))) {
      return false
    }
    const expected = localStorage.getItem(OAUTH_SECRET_KEY)
    let ok = !!expected && expected === secret
    if (ok) {
      localStorage.removeItem(OAUTH_SECRET_KEY)
      if (query.get('token')) {
        const payload = `token=${query.get('token')}&refreshToken=${query.get('refreshToken') || ''}&uid=${query.get('uid') || ''}`
        emit('oauth-token', payload, null)
      } else {
        emit('gitee-auth-code', query.get('code'), null)
      }
    }
    window.stop()
    document.documentElement.innerHTML = ok
      ? "<body style='display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;font-family:sans-serif'><h1>登录成功</h1><p>您可以关闭此窗口返回应用。</p></body>"
      : '<body>invalid or missing secret</body>'
    if (ok) {
      setTimeout(() => window.close(), 2000)
    }
    return true
  }

  if (handleOauthCallback()) {
    return
  }

  window.__TAURI_EVENT_PLUGIN_INTERNALS__ = {
    unregisterListener(event, id) {
      const list = listeners.get(event)
      if (list) {
        listeners.set(
          event,
          list.filter((item) => item.id !== id)
        )
      }
    }
  }

  // ---------- auth state ----------
  const auth = {
    get() {
      try {
        return JSON.parse(localStorage.getItem(AUTH_KEY) || '{}')
      } catch (e) {
        return {}
      }
    },
    set(value) {
      try {
        localStorage.setItem(AUTH_KEY, JSON.stringify(value))
      } catch (e) {
        log('auth store failed', e)
      }
    },
    patch(value) {
      this.set(Object.assign(this.get(), value))
    }
  }

  // ---------- HTTP ----------
  function buildUrl(path, params) {
    let url = API_BASE + '/' + path
    if (params && typeof params === 'object') {
      const qs = new URLSearchParams()
      for (const [k, v] of Object.entries(params)) {
        if (v !== undefined && v !== null) {
          qs.append(k, typeof v === 'object' ? JSON.stringify(v) : String(v))
        }
      }
      const s = qs.toString()
      if (s) {
        url += '?' + s
      }
    }
    return url
  }

  async function rawRequest(method, path, body, params, withToken) {
    const headers = { 'Content-Type': 'application/json', Authorization: BASIC_AUTH }
    const token = auth.get().token
    if (withToken && token) {
      headers.token = token
    }
    const init = { method, headers }
    if (method !== 'GET' && method !== 'HEAD') {
      init.body = JSON.stringify(body == null ? {} : body)
    }
    let res
    try {
      res = await fetch(buildUrl(path, params), init)
    } catch (e) {
      throw 'network_error: ' + e
    }
    try {
      return await res.json()
    } catch (e) {
      throw 'network_error: invalid response ' + res.status
    }
  }

  async function refreshToken() {
    const refresh = auth.get().refreshToken
    if (!refresh) {
      throw '请重新登录'
    }
    const result = await rawRequest('POST', IM_URLS.refreshToken[1], { refreshToken: refresh }, null, false)
    if (!result || !result.success || !result.data || !result.data.token) {
      throw '请重新登录'
    }
    auth.patch({ token: result.data.token, refreshToken: result.data.refreshToken || refresh })
  }

  async function request(method, path, body, params) {
    const isLogout = path === IM_URLS.logout[1]
    for (let attempt = 0; attempt <= 2; attempt++) {
      const result = await rawRequest(method, path, body, params, true)
      const code = result && result.code
      if (code === 200) {
        return result.data === undefined ? null : result.data
      }
      if (isLogout && (code === 406 || code === 401)) {
        return result.data === undefined ? null : result.data
      }
      if (code === 406) {
        await refreshToken()
        continue
      }
      if (code === 401) {
        if (auth.get().uid) {
          emit('relogin', null, 'home')
        }
        throw '请重新登录'
      }
      throw (result && result.msg) || '请求失败'
    }
    throw 'token过期，刷新token失败'
  }

  function api(name, body, params) {
    const entry = IM_URLS[name]
    if (!entry) {
      return Promise.reject('unknown url: ' + name)
    }
    return request(entry[0], entry[1], body, params)
  }

  // ---------- WebSocket ----------
  const ws = {
    socket: null,
    state: 'DISCONNECTED',
    clientId: null,
    heartbeat: null,
    closing: false,
    attempts: 0
  }

  // Server push type -> [event, target window]. Like the Rust layer, chat and contact events go to
  // the main window only, while call, login and session events reach every window (tab).
  const WS_EVENTS = {
    loginQrCode: ['ws-login-qr-code', null],
    waitingAuthorize: ['ws-waiting-authorize', null],
    loginSuccess: ['ws-login-success', 'home'],
    receiveMessage: ['ws-receive-message', 'home'],
    msgRecall: ['ws-msg-recall', 'home'],
    msgMarkItem: ['ws-msg-mark-item', 'home'],
    online: ['ws-online', 'home'],
    offline: ['ws-offline', 'home'],
    userStateChange: ['ws-user-state-change', 'home'],
    notifyEvent: ['ws-request-notify-event', 'home'],
    groupSetAdmin: ['ws-group-set-admin-success', 'home'],
    newApply: ['ws-request-new-apply', 'home'],
    requestApprovalFriend: ['ws-request-approval-friend', 'home'],
    memberChange: ['ws-member-change', 'home'],
    roomInfoChange: ['ws-room-info-change', 'home'],
    myRoomInfoChange: ['ws-my-room-info-change', 'home'],
    roomGroupNoticeMsg: ['ws-room-group-notice-msg', 'home'],
    roomEditGroupNoticeMsg: ['ws-room-edit-group-notice-msg', 'home'],
    roomDissolution: ['ws-room-dissolution', 'home'],
    VideoCallRequest: ['ws-video-call-request', null],
    CallAccepted: ['ws-call-accepted', null],
    CallRejected: ['ws-call-rejected', null],
    RoomClosed: ['ws-room-closed', null],
    WEBRTC_SIGNAL: ['ws-webrtc-signal', null],
    JoinVideo: ['ws-join-video', null],
    LeaveVideo: ['ws-leave-video', null],
    DROPPED: ['ws-dropped', null],
    CANCEL: ['ws-cancel', null],
    TIMEOUT: ['ws-timeout', null],
    tokenExpired: ['ws-token-expired', null],
    invalidUser: ['ws-invalid-user', null],
    deleteFriend: ['ws-delete-friend', null],
    feedSendMsg: ['ws-feed-send-msg', 'home'],
    feedNotify: ['ws-feed-notify', 'home']
  }

  // The tab that owns the socket (the main window) shares its state; other tabs send through it.
  const WS_STATE_KEY = '__shim_ws_state'

  function ownsSocket() {
    return !!ws.socket && ws.socket.readyState === WebSocket.OPEN
  }

  function sharedWsState() {
    if (ws.socket) {
      return ws.state
    }
    try {
      return localStorage.getItem(WS_STATE_KEY) || ws.state
    } catch (e) {
      return ws.state
    }
  }

  function wsSetState(state, isReconnection) {
    ws.state = state
    try {
      localStorage.setItem(WS_STATE_KEY, state)
    } catch (e) {
      log('ws state store failed', e)
    }
    emit('websocket-event', {
      type: 'connectionStateChanged',
      state,
      is_reconnection: !!isReconnection,
      isReconnection: !!isReconnection
    })
  }

  function wsSend(data) {
    if (ownsSocket()) {
      ws.socket.send(typeof data === 'string' ? data : JSON.stringify(data))
      return true
    }
    return false
  }

  // Secondary windows (call window, ...) have no socket of their own: relay to the owner tab.
  function wsSendAnywhere(data) {
    if (wsSend(data)) {
      return true
    }
    if (bus && sharedWsState() === 'CONNECTED') {
      bus.postMessage({ wsSend: data })
      return true
    }
    return false
  }

  function wsOnMessage(text) {
    let json
    try {
      json = JSON.parse(text)
    } catch (e) {
      emit('websocket-event', { type: 'messageReceived', message: text }, null)
      return
    }
    const type = json && json.type
    const data = json ? json.data : null
    if (type === 'receiveMessage' && data && data.message && data.message.id) {
      wsSend({ type: '15', data: { msgId: String(data.message.id), timestamp: Date.now() } })
    }
    const route = WS_EVENTS[type]
    if (route) {
      emit(route[0], data === undefined ? null : data, route[1])
    } else if (type !== undefined) {
      emit('ws-unknown-message', json, null)
    }
    emit('websocket-event', { type: 'messageReceived', message: json }, null)
  }

  // A closing main tab must not leave other tabs believing the socket is still up.
  window.addEventListener('pagehide', () => {
    if (ws.socket) {
      try {
        localStorage.setItem(WS_STATE_KEY, 'DISCONNECTED')
      } catch (e) {
        log('ws state store failed', e)
      }
    }
  })

  function wsConnect(isReconnection) {
    const token = auth.get().token
    const url = WS_URL + '?clientId=' + encodeURIComponent(ws.clientId || '') + (token ? '&Token=' + encodeURIComponent(token) : '')
    ws.closing = false
    wsSetState(isReconnection ? 'RECONNECTING' : 'CONNECTING', isReconnection)
    const socket = new WebSocket(url)
    ws.socket = socket
    socket.onopen = () => {
      ws.attempts = 0
      wsSetState('CONNECTED', isReconnection)
      clearInterval(ws.heartbeat)
      ws.heartbeat = setInterval(() => wsSend({ type: '2' }), 9900)
    }
    socket.onmessage = (e) => wsOnMessage(e.data)
    socket.onclose = () => {
      clearInterval(ws.heartbeat)
      if (ws.socket !== socket) {
        return
      }
      if (ws.closing) {
        wsSetState('DISCONNECTED', false)
        return
      }
      const delay = Math.min(1000 * Math.pow(2, ws.attempts++), 15000)
      wsSetState('RECONNECTING', true)
      setTimeout(() => {
        if (!ws.closing && ws.socket === socket) {
          wsConnect(true)
        }
      }, delay)
    }
  }

  function wsDisconnect() {
    ws.closing = true
    clearInterval(ws.heartbeat)
    if (ws.socket) {
      ws.socket.close()
    }
    ws.socket = null
    wsSetState('DISCONNECTED', false)
  }

  // ---------- message helpers ----------
  function sortAndBlock(list) {
    const items = (list || []).slice().sort((a, b) => ((a.message && a.message.sendTime) || 0) - ((b.message && b.message.sendTime) || 0))
    let prev = null
    for (let i = 0; i < items.length; i++) {
      const sendTime = (items[i].message && items[i].message.sendTime) || 0
      if (i === 0) {
        items[i].timeBlock = 1
      } else if (prev != null && sendTime - prev >= TIME_BLOCK_MS) {
        items[i].timeBlock = sendTime - prev
      } else {
        items[i].timeBlock = null
      }
      prev = sendTime
    }
    return items
  }

  function sortMembers(members) {
    return (members || []).slice().sort((a, b) => {
      const ra = a.groupRole == null ? null : a.groupRole
      const rb = b.groupRole == null ? null : b.groupRole
      if (ra != null && rb != null && ra !== rb) {
        return ra - rb
      }
      const sa = a.activeStatus == null ? 255 : a.activeStatus
      const sb = b.activeStatus == null ? 255 : b.activeStatus
      if (sa !== sb) {
        return sa - sb
      }
      return String(a.name || '').toLowerCase().localeCompare(String(b.name || '').toLowerCase())
    })
  }

  // ---------- window helpers ----------
  const monitor = () => ({
    name: 'browser',
    size: { width: screen.width, height: screen.height },
    position: { x: 0, y: 0 },
    workArea: { position: { x: 0, y: 0 }, size: { width: screen.width, height: screen.height } },
    scaleFactor: window.devicePixelRatio || 1
  })

  // Main windows replace the current page; secondary windows open in a new tab.
  const PAGE_WINDOWS = new Set(['home', 'login'])

  function openWindow(options) {
    const label = options && options.label
    const url = (options && options.url) || '/' + label
    if (label === LABEL) {
      return null
    }
    if (PAGE_WINDOWS.has(label)) {
      window.name = label
      setTimeout(() => location.assign(url), 50)
    } else {
      window.open(url, label)
    }
    return null
  }

  // Secondary windows were opened with window.open, so the browser lets them close themselves.
  // Closing another window by label is relayed to its tab.
  function closeWindow(label) {
    if (label && label !== LABEL) {
      if (bus) {
        bus.postMessage({ closeLabel: label })
      }
      return
    }
    if (!PAGE_WINDOWS.has(LABEL)) {
      setTimeout(() => window.close(), 50)
    }
  }

  // ---------- in-memory file system ----------
  // The client stages picked, pasted and cached files on disk before uploading them by path.
  // In the browser those bytes live here, keyed by the path string the client uses.
  const vfs = new Map()
  const fileHandles = new Map()
  let resourceSeq = 1

  // Every base directory resolves to APP_ROOT, so a relative path and its absolute form share one key.
  const APP_ROOT = '/tmp/app'
  const vfsKey = (path) =>
    String(path || '')
      .replace(/\\/g, '/')
      .replace(/\/+/g, '/')
      .replace(new RegExp('^' + APP_ROOT + '/'), '')

  // convertFileSrc(): files the client cached (downloaded thumbnails, pasted images) become blob URLs.
  const blobUrls = new Map()

  function convertFileSrc(path) {
    const key = vfsKey(path)
    const entry = vfs.get(key)
    if (!entry) {
      return path
    }
    const cached = blobUrls.get(key)
    if (cached && cached.entry === entry && cached.mtime === entry.mtime) {
      return cached.url
    }
    const url = URL.createObjectURL(new Blob([entry.bytes], { type: entry.type }))
    blobUrls.set(key, { entry, mtime: entry.mtime, url })
    return url
  }

  function toBytes(data) {
    if (data == null) {
      return new Uint8Array(0)
    }
    if (data instanceof Uint8Array) {
      return data
    }
    if (data instanceof ArrayBuffer) {
      return new Uint8Array(data)
    }
    if (ArrayBuffer.isView(data)) {
      return new Uint8Array(data.buffer, data.byteOffset, data.byteLength)
    }
    if (Array.isArray(data)) {
      return Uint8Array.from(data)
    }
    if (typeof data === 'string') {
      return new TextEncoder().encode(data)
    }
    return new Uint8Array(0)
  }

  function concatBytes(a, b) {
    const out = new Uint8Array(a.byteLength + b.byteLength)
    out.set(a, 0)
    out.set(b, a.byteLength)
    return out
  }

  function vfsPut(path, bytes, type) {
    vfs.set(vfsKey(path), { bytes, type: type || mimeOf(path), mtime: Date.now() })
  }

  // Files the user saves through the "save as" dialog
  const SAVE_DIR = '/browser-downloads/'
  const downloads = []
  const opened = []

  function browserDownload(path) {
    const entry = vfsGet(path)
    const name = String(path).split('/').pop()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob([entry.bytes], { type: entry.type }))
    a.download = name
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(a.href), 60000)
    downloads.push({ name, size: entry.bytes.byteLength })
  }

  function vfsGet(path) {
    const entry = vfs.get(vfsKey(path))
    if (!entry) {
      throw 'file not found: ' + path
    }
    return entry
  }

  function vfsIsDir(path) {
    const prefix = vfsKey(path).replace(/\/$/, '') + '/'
    for (const key of vfs.keys()) {
      if (key.startsWith(prefix)) {
        return true
      }
    }
    return false
  }

  const MIME = {
    png: 'image/png',
    jpg: 'image/jpeg',
    jpeg: 'image/jpeg',
    gif: 'image/gif',
    webp: 'image/webp',
    bmp: 'image/bmp',
    svg: 'image/svg+xml',
    mp4: 'video/mp4',
    mp3: 'audio/mpeg',
    wav: 'audio/wav',
    pdf: 'application/pdf',
    txt: 'text/plain',
    json: 'application/json',
    zip: 'application/zip',
    doc: 'application/msword',
    docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    xls: 'application/vnd.ms-excel',
    xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  }

  function extOf(path) {
    const m = String(path || '').match(/\.([^./\\]+)$/)
    return m ? m[1] : ''
  }

  function mimeOf(path) {
    return MIME[extOf(path).toLowerCase()] || 'application/octet-stream'
  }

  function fileInfo(entry, isDir) {
    const now = Date.now()
    return {
      isFile: !isDir,
      isDirectory: !!isDir,
      isSymlink: false,
      size: isDir ? 0 : entry.bytes.byteLength,
      mtime: isDir ? now : entry.mtime,
      atime: isDir ? now : entry.mtime,
      birthtime: isDir ? now : entry.mtime,
      readonly: false,
      fileAttributes: null,
      dev: null,
      ino: null,
      mode: null,
      nlink: null,
      uid: null,
      gid: null,
      rdev: null,
      blksize: null,
      blocks: null
    }
  }

  function fsStat(path) {
    const entry = vfs.get(vfsKey(path))
    if (entry) {
      return fileInfo(entry, false)
    }
    if (vfsIsDir(path)) {
      return fileInfo(null, true)
    }
    throw 'file not available in browser: ' + path
  }

  function fsOpen(path, options) {
    const key = vfsKey(path)
    const opts = options || {}
    if (!vfs.has(key)) {
      if (!opts.create && !opts.createNew && opts.write === undefined && opts.append === undefined) {
        throw 'file not found: ' + path
      }
      vfsPut(key, new Uint8Array(0))
    } else if (opts.truncate || (opts.write && !opts.append && !opts.read)) {
      vfsPut(key, new Uint8Array(0))
    }
    const rid = resourceSeq++
    fileHandles.set(rid, { key, pos: 0 })
    return rid
  }

  function fsWrite(rid, data) {
    const handle = fileHandles.get(rid)
    if (!handle) {
      throw 'bad file handle'
    }
    const entry = vfs.get(handle.key)
    const bytes = toBytes(data)
    entry.bytes = concatBytes(entry.bytes, bytes)
    entry.mtime = Date.now()
    return bytes.byteLength
  }

  // Files chosen through the picker are kept under a pseudo absolute path.
  let pickSeq = 1

  function storePickedFile(file) {
    const path = '/browser-files/' + pickSeq++ + '/' + file.name
    return file.arrayBuffer().then((buffer) => {
      vfsPut(path, new Uint8Array(buffer), file.type || mimeOf(file.name))
      return path
    })
  }

  // plugin-dialog open(): a hidden <input type=file>; automation can set its files directly.
  function dialogOpen(options) {
    const opts = options || {}
    if (opts.directory) {
      return Promise.resolve(null)
    }
    const input = document.createElement('input')
    input.type = 'file'
    input.id = '__shim_file_input'
    input.multiple = !!opts.multiple
    const exts = []
    for (const filter of opts.filters || []) {
      for (const ext of filter.extensions || []) {
        if (ext && ext !== '*') {
          exts.push('.' + ext)
        }
      }
    }
    if (exts.length) {
      input.accept = exts.join(',')
    }
    input.style.display = 'none'
    const old = document.getElementById('__shim_file_input')
    if (old) {
      old.dispatchEvent(new Event('shim-replaced'))
    }
    document.body.appendChild(input)
    return new Promise((resolve) => {
      const openedAt = Date.now()
      const finish = (value) => {
        input.remove()
        resolve(value)
      }
      input.addEventListener('shim-replaced', () => finish(null))
      // A headless browser cancels the chooser at once; keep the input so automation can set its files.
      input.addEventListener('cancel', () => {
        if (Date.now() - openedAt > 1000) {
          finish(null)
        }
      })
      input.addEventListener('change', async () => {
        const files = Array.from(input.files || [])
        if (!files.length) {
          finish(null)
          return
        }
        const paths = await Promise.all(files.map(storePickedFile))
        finish(opts.multiple ? paths : paths[0])
      })
      try {
        input.click()
      } catch (e) {
        log('file picker blocked', e)
      }
    })
  }

  // plugin-http: requests run through window.fetch, so the target must allow CORS.
  const httpRequests = new Map()

  async function httpCommand(command, args) {
    switch (command) {
      case 'fetch': {
        const config = args.clientConfig || {}
        const rid = resourceSeq++
        const controller = new AbortController()
        const init = { method: config.method || 'GET', headers: config.headers || [], signal: controller.signal }
        if (config.data != null) {
          init.body = toBytes(config.data)
        }
        httpRequests.set(rid, { url: config.url, init, controller })
        return rid
      }
      case 'fetch_cancel': {
        const req = httpRequests.get(args.rid)
        if (req) {
          req.controller.abort()
        }
        return null
      }
      case 'fetch_send': {
        const req = httpRequests.get(args.rid)
        if (!req) {
          throw 'unknown request'
        }
        httpRequests.delete(args.rid)
        const res = await fetch(req.url, req.init)
        const rid = resourceSeq++
        httpRequests.set(rid, { response: res })
        return {
          status: res.status,
          statusText: res.statusText,
          url: res.url,
          headers: Array.from(res.headers.entries()),
          rid
        }
      }
      case 'fetch_read_body': {
        const entry = httpRequests.get(args.rid)
        httpRequests.delete(args.rid)
        const stream = channelStream(args.streamChannel)
        const body = entry && entry.response && entry.response.body
        if (body) {
          const reader = body.getReader()
          while (true) {
            const { done, value } = await reader.read()
            if (done) {
              break
            }
            // Each chunk carries a trailing flag byte: 0 = data, 1 = end of body.
            stream.send(concatBytes(value, Uint8Array.of(0)))
          }
        }
        stream.send(Uint8Array.of(1))
        stream.end()
        return null
      }
      default:
        return null
    }
  }

  // Uploads a staged file with a PUT to a presigned URL, reporting progress on the channel.
  function uploadPut(args) {
    const entry = vfsGet(args.path)
    const progress = channelStream(args.onProgress)
    const total = entry.bytes.byteLength
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open('PUT', args.url)
      for (const [k, v] of Object.entries(args.headers || {})) {
        xhr.setRequestHeader(k, v)
      }
      xhr.upload.onprogress = (e) => progress.send({ progressTotal: e.loaded, total: e.lengthComputable ? e.total : total })
      xhr.onload = () => {
        progress.send({ progressTotal: total, total })
        progress.end()
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(null)
        } else {
          reject('Upload failed with status ' + xhr.status + ': ' + xhr.responseText)
        }
      }
      xhr.onerror = () => {
        progress.end()
        reject('Upload request failed')
      }
      xhr.send(new Blob([entry.bytes], { type: (args.headers && args.headers['Content-Type']) || entry.type }))
    })
  }

  // ---------- AI streaming (ai_message_send_stream / ai_message_cancel_stream) ----------
  const aiStreams = new Map()

  function streamError(status) {
    if (status === 406) {
      return 'token过期，请刷新后重试'
    }
    if (status === 401) {
      return '请重新登录'
    }
    return '请求失败，状态码: ' + status
  }

  // Opens the SSE request. The server reports auth failures as a JSON body, so a JSON reply is
  // turned into an error (after one token refresh for 406), like the plain HTTP requests.
  async function openAiStream(body, signal) {
    for (let attempt = 0; attempt <= 1; attempt++) {
      const headers = {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        Authorization: BASIC_AUTH
      }
      const token = auth.get().token
      if (token) {
        headers.token = token
      }
      const res = await fetch(buildUrl(IM_URLS.messageSendStream[1]), {
        method: IM_URLS.messageSendStream[0],
        headers,
        body: JSON.stringify(body || {}),
        signal
      })
      if (!res.ok) {
        throw streamError(res.status)
      }
      if (!(res.headers.get('content-type') || '').includes('application/json')) {
        return res
      }
      const result = await res.json().catch(() => null)
      const code = result && result.code
      if (code === 406 && attempt === 0) {
        await refreshToken()
        continue
      }
      throw code === 406 || code === 401 ? streamError(code) : (result && result.msg) || '请求失败'
    }
    throw streamError(406)
  }

  // Mirrors the Rust command: once the response headers arrive the command returns, and the body is
  // parsed in the background. Every `data:` line becomes a "chunk" event with the raw payload, and the
  // end of the stream sends "done" with all payloads concatenated (also after a read error).
  async function aiSendStream(args) {
    const requestId = args.requestId
    const channel = channelStream(args.onEvent)
    const event = (eventType, data, error) => ({ eventType, data: data ?? null, error: error ?? null, requestId })
    const controller = new AbortController()
    let res
    try {
      res = await openAiStream(args.body, controller.signal)
    } catch (e) {
      const message = typeof e === 'string' ? e : String((e && e.message) || e)
      channel.send(event('error', null, message))
      throw message
    }
    aiStreams.set(requestId, controller)
    ;(async () => {
      const reader = res.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      let full = ''
      const handleText = (text) => {
        buffer += text
        let pos
        while ((pos = buffer.indexOf('\n\n')) >= 0) {
          const message = buffer.slice(0, pos)
          buffer = buffer.slice(pos + 2)
          for (const line of message.split(/\r?\n/)) {
            if (line.startsWith('data:')) {
              const data = line.startsWith('data: ') ? line.slice(6) : line.slice(5)
              full += data
              channel.send(event('chunk', data))
            }
          }
        }
      }
      try {
        for (;;) {
          const { done, value } = await reader.read()
          if (done) {
            handleText(decoder.decode())
            break
          }
          handleText(decoder.decode(value, { stream: true }))
        }
      } catch (e) {
        if (controller.signal.aborted) {
          // cancelled: the Rust task is aborted and sends nothing more
          return
        }
        channel.send(event('error', null, String((e && e.message) || e)))
      }
      if (controller.signal.aborted) {
        return
      }
      // like the Rust JoinHandle, the entry stays registered after the stream ends
      channel.send(event('done', full))
      channel.end()
    })()
    return null
  }

  function aiCancelStream(args) {
    const requestId = args.requestId
    const controller = aiStreams.get(requestId)
    if (!controller) {
      throw '未找到指定请求ID的任务: ' + requestId
    }
    aiStreams.delete(requestId)
    controller.abort()
    return null
  }

  // ---------- plugin commands ----------
  function pluginCommand(plugin, command, args, options) {
    switch (plugin) {
      case 'event':
        if (command === 'listen') {
          const id = listenerSeq++
          const list = listeners.get(args.event) || []
          list.push({ id, handler: args.handler, target: args.target })
          listeners.set(args.event, list)
          return id
        }
        if (command === 'unlisten') {
          window.__TAURI_EVENT_PLUGIN_INTERNALS__.unregisterListener(args.event, args.eventId)
          return null
        }
        if (command === 'emit') {
          emit(args.event, args.payload, null)
          return null
        }
        if (command === 'emit_to') {
          emit(args.event, args.payload, args.target)
          return null
        }
        return null
      case 'window':
        switch (command) {
          case 'get_all_windows':
            return [LABEL]
          case 'primary_monitor':
          case 'current_monitor':
          case 'monitor_from_point':
            return monitor()
          case 'available_monitors':
            return [monitor()]
          case 'scale_factor':
            return window.devicePixelRatio || 1
          case 'inner_size':
          case 'outer_size':
            return { width: window.innerWidth, height: window.innerHeight }
          case 'inner_position':
          case 'outer_position':
            return { x: 0, y: 0 }
          case 'is_visible':
          case 'is_focused':
          case 'is_decorated':
          case 'is_resizable':
          case 'is_enabled':
          case 'is_closable':
          case 'is_movable':
            return true
          case 'is_maximized':
          case 'is_minimized':
          case 'is_fullscreen':
          case 'is_always_on_top':
            return false
          case 'theme':
            return 'light'
          case 'title':
            return document.title
          case 'close':
          case 'destroy':
            closeWindow(args && args.label)
            return null
          default:
            return null
        }
      case 'webview':
        if (command === 'create_webview_window') {
          return openWindow(args.options)
        }
        if (command === 'get_all_webviews') {
          return [{ label: LABEL, windowLabel: LABEL }]
        }
        if (command === 'webview_position') {
          return { x: 0, y: 0 }
        }
        if (command === 'webview_size') {
          return { width: window.innerWidth, height: window.innerHeight }
        }
        return null
      case 'os':
        return window.__TAURI_OS_PLUGIN_INTERNALS__[command] ?? osInfo[command] ?? null
      case 'app':
        if (command === 'version') {
          return '3.0.0'
        }
        if (command === 'tauri_version') {
          return '2.11.0'
        }
        if (command === 'name') {
          return document.title || 'app'
        }
        if (command === 'identifier') {
          return 'app.web'
        }
        return null
      case 'path':
        return pathCommand(command, args)
      case 'fs':
        return fsCommand(command, args, options)
      case 'resources':
        if (command === 'close') {
          fileHandles.delete(args.rid)
        }
        return null
      case 'dialog':
        if (command === 'open') {
          return dialogOpen(args.options)
        }
        if (command === 'save') {
          // "Save as": hand out a path in a virtual downloads folder; writing it triggers a browser download.
          const name = String((args.options && args.options.defaultPath) || 'download').split(/[\\/]/).pop()
          return SAVE_DIR + name
        }
        if (command === 'ask' || command === 'confirm') {
          return window.confirm(args.message || '')
        }
        if (command === 'message') {
          window.alert(args.message || '')
          return 'Ok'
        }
        return null
      case 'http':
        return httpCommand(command, args)
      case 'log':
        console.log('[app]', args && args.message)
        return null
      case 'notification':
        if (command === 'is_permission_granted') {
          return false
        }
        if (command === 'request_permission') {
          return 'denied'
        }
        return null
      case 'autostart':
      case 'global-shortcut':
        if (command === 'is_enabled' || command === 'is_registered') {
          return false
        }
        return null
      case 'clipboard-manager':
        if (command === 'write_text') {
          return navigator.clipboard ? navigator.clipboard.writeText(args.text || '').then(() => null) : null
        }
        if (command === 'read_text') {
          return ''
        }
        return null
      case 'process':
        if (command === 'restart') {
          location.reload()
        }
        return null
      case 'shell':
        // shell.open(path): links and the third-party login page open in a new tab
        if (command === 'open' && args && args.path) {
          window.open(args.path, '_blank', 'noreferrer')
        }
        return null
      case 'opener':
        if (command === 'open_url' && args && args.url) {
          window.open(args.url, '_blank')
        }
        // Opening a downloaded file shows it in a new tab; "show in folder" saves it through the browser.
        if (command === 'open_path' && args && args.path) {
          const url = /^(https?|blob):/.test(args.path) ? args.path : convertFileSrc(args.path)
          if (url === args.path && !/^(https?|blob):/.test(url)) {
            throw 'file not found: ' + args.path
          }
          opened.push(args.path)
          window.open(url, '_blank')
        }
        if (command === 'reveal_item_in_dir' && args) {
          const paths = [].concat(args.paths || args.path || [])
          for (const path of paths) {
            if (vfs.has(vfsKey(path))) {
              browserDownload(path)
            }
          }
        }
        return null
      default:
        return null
    }
  }

  function fsCommand(command, args, options) {
    switch (command) {
      case 'exists':
        return vfs.has(vfsKey(args.path)) || vfsIsDir(args.path)
      case 'mkdir':
      case 'watch':
      case 'unwatch':
        return null
      case 'read_dir':
        return []
      case 'create':
        return fsOpen(args.path, { write: true, create: true, truncate: true })
      case 'open':
        return fsOpen(args.path, args.options)
      case 'write':
        return fsWrite(args.rid, args.data)
      case 'write_file':
      case 'write_text_file': {
        // Raw-body invoke: the bytes are the args, the path travels in the headers.
        const headers = (options && options.headers) || {}
        const path = decodeURIComponent(headers.path || '')
        vfsPut(path, toBytes(args))
        if (path.startsWith(SAVE_DIR)) {
          browserDownload(path)
        }
        return null
      }
      case 'read_file':
        return vfsGet(args.path).bytes.slice().buffer
      case 'read_text_file':
        return new TextDecoder().decode(vfsGet(args.path).bytes)
      case 'stat':
      case 'lstat':
        return fsStat(args.path)
      case 'size':
        return vfsGet(args.path).bytes.byteLength
      case 'remove':
        vfs.delete(vfsKey(args.path))
        return null
      case 'copy_file': {
        const entry = vfsGet(args.fromPath)
        vfsPut(args.toPath, entry.bytes, entry.type)
        return null
      }
      case 'rename': {
        const entry = vfsGet(args.oldPath)
        vfs.delete(vfsKey(args.oldPath))
        vfsPut(args.newPath, entry.bytes, entry.type)
        return null
      }
      default:
        return null
    }
  }

  const osInfo = {
    platform: 'linux',
    version: '6.1.0',
    os_type: 'linux',
    family: 'unix',
    arch: 'x86_64',
    exe_extension: '',
    eol: '\n',
    locale: navigator.language || 'zh-CN',
    hostname: 'browser'
  }
  window.__TAURI_OS_PLUGIN_INTERNALS__ = Object.assign({}, osInfo)

  function pathCommand(command, args) {
    const join = (parts) => parts.filter((p) => p != null && p !== '').join('/').replace(/\/+/g, '/')
    switch (command) {
      case 'resolve_directory':
        return join([APP_ROOT, args && args.path])
      case 'join':
        return join(args.paths || [])
      case 'resolve':
      case 'normalize':
        return join(args.paths || [args.path])
      case 'dirname':
        return String(args.path).replace(/\/[^/]*$/, '') || '/'
      case 'basename':
        return String(args.path).split('/').pop()
      case 'extname':
        return (String(args.path).match(/\.([^./]+)$/) || [null, ''])[1]
      case 'is_absolute':
        return String(args.path).startsWith('/')
      default:
        return null
    }
  }


  // ---------- chat history and file manager (local SQLite in the desktop app, server queries here) ----------
  const DAY_MS = 24 * 60 * 60 * 1000
  const HISTORY_TYPES = { image: '3,6', file: '4' }

  // Server ChatMessageResp -> the Rust MessageResp shape
  function toMessageResp(item) {
    const msg = item.message || {}
    return {
      createId: msg.id,
      createTime: msg.sendTime,
      updateId: null,
      updateTime: null,
      fromUser: { uid: String((item.fromUser && item.fromUser.uid) || ''), nickname: (item.fromUser && item.fromUser.name) || null },
      message: {
        id: msg.id,
        roomId: msg.roomId,
        type: msg.type,
        body: msg.body,
        messageMarks: msg.messageMarks && Object.keys(msg.messageMarks).length ? msg.messageMarks : null,
        sendTime: msg.sendTime
      },
      oldMsgId: null,
      timeBlock: null
    }
  }

  function isLocalMidnight(ms) {
    const d = new Date(ms)
    return d.getHours() === 0 && d.getMinutes() === 0 && d.getSeconds() === 0 && d.getMilliseconds() === 0
  }

  async function queryChatHistory(param) {
    param = param || {}
    const pagination = param.pagination || { page: 1, pageSize: 20 }
    const range = param.dateRange || {}
    let endTime = range.endTime == null ? null : range.endTime
    // The date picker yields midnight of the last day; include that whole day.
    if (endTime != null && isLocalMidnight(endTime)) {
      endTime += DAY_MS - 1
    }
    const keyword = param.searchKeyword && param.searchKeyword.trim()
    const page = await request('GET', 'im/chat/msg/search', null, {
      roomId: param.roomId,
      types: HISTORY_TYPES[param.messageType] || '',
      keyword: keyword || '',
      startTime: range.startTime == null ? '' : range.startTime,
      endTime: endTime == null ? '' : endTime,
      sort: param.sortOrder === 'asc' ? 'asc' : 'desc',
      pageNo: pagination.page,
      pageSize: pagination.pageSize
    })
    const list = (page && page.list) || []
    return { messages: list.map(toMessageResp), hasMore: !!page && !page.isLast, currentPage: pagination.page }
  }

  function pad2(n) {
    return String(n).padStart(2, '0')
  }

  function formatLocal(ms) {
    const d = new Date(ms)
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
  }

  function displayDate(dateText) {
    const [y, m, d] = dateText.split('-').map(Number)
    const day = new Date(y, m - 1, d)
    const now = new Date()
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    const diff = Math.round((today - day) / DAY_MS)
    if (diff === 0) {
      return '今天'
    }
    if (diff === 1) {
      return '昨天'
    }
    if (y === today.getFullYear()) {
      return `${pad2(m)}月${pad2(d)}日`
    }
    return `${y}年${pad2(m)}月${pad2(d)}日`
  }

  // File/video message -> the Rust FileInfo shape
  function toFileInfo(item) {
    const msg = item.message || {}
    const body = msg.body || {}
    if (msg.type !== 4 && msg.type !== 6) {
      return null
    }
    let fileName = body.fileName
    if (!fileName && msg.type === 6 && body.url) {
      fileName = String(body.url).split('/').pop().split('?')[0]
    }
    if (!fileName) {
      return null
    }
    const size = body.fileSize != null ? body.fileSize : body.size
    const sender = item.fromUser || {}
    return {
      id: msg.id,
      fileName,
      fileSize: Number(size) || 0,
      fileType: msg.type === 4 ? 'file' : 'video',
      uploadTime: formatLocal(msg.sendTime || 0),
      sender: { id: String(sender.uid || ''), name: sender.name || '未知用户', avatar: sender.avatar || '/avatars/default.jpg', isOnline: null },
      downloadUrl: body.url || body.downloadUrl || null,
      isDownloaded: false,
      status: 'completed',
      thumbnailUrl: body.thumbnailUrl || body.thumbUrl || null
    }
  }

  async function queryFiles(param) {
    param = param || {}
    const query = { keyword: (param.searchKeyword && param.searchKeyword.trim()) || '', pageNo: param.page || 1, pageSize: param.pageSize || 20 }
    switch (param.navigationType) {
      case 'myFiles':
        break
      case 'senders':
        query.senderUid = param.selectedUser || ''
        break
      case 'sessions':
      case 'groups':
        query.roomId = param.roomId || ''
        break
      default:
        throw '不支持的导航类型'
    }
    const page = await request('GET', 'im/chat/msg/files', null, query)
    let files = ((page && page.list) || []).map(toFileInfo).filter(Boolean)
    const users = new Map()
    for (const f of files) {
      users.set(f.sender.id, f.sender)
    }
    if (query.keyword) {
      const kw = query.keyword.toLowerCase()
      files = files.filter((f) => f.fileName.toLowerCase().includes(kw))
    }
    const total = files.length
    const groups = new Map()
    for (const f of files) {
      const date = f.uploadTime.slice(0, 10)
      if (!groups.has(date)) {
        groups.set(date, [])
      }
      groups.get(date).push(f)
    }
    const timeGroupedFiles = [...groups.keys()]
      .sort()
      .reverse()
      .map((date) => ({
        date,
        displayDate: displayDate(date),
        files: groups.get(date).sort((a, b) => b.uploadTime.localeCompare(a.uploadTime))
      }))
    return { timeGroupedFiles, userList: [...users.values()], hasMore: total >= (param.pageSize || 20), currentPage: param.page || 1 }
  }

  // First frame of a video in the in-memory file system, like the desktop app's get_video_thumbnail
  function videoThumbnail(args) {
    const entry = vfsGet(args.videoPath)
    const url = URL.createObjectURL(new Blob([entry.bytes], { type: entry.type || 'video/mp4' }))
    return new Promise((resolve, reject) => {
      const video = document.createElement('video')
      const done = (fn, value) => {
        URL.revokeObjectURL(url)
        video.removeAttribute('src')
        fn(value)
      }
      video.muted = true
      video.preload = 'auto'
      video.onerror = () => done(reject, '视频文件无法解码')
      video.onloadedmetadata = () => {
        video.currentTime = Math.min(args.targetTime || 0, Math.max(0, (video.duration || 0) - 0.05))
      }
      video.onseeked = () => {
        const canvas = document.createElement('canvas')
        canvas.width = video.videoWidth
        canvas.height = video.videoHeight
        canvas.getContext('2d').drawImage(video, 0, 0)
        const base64 = canvas.toDataURL('image/jpeg', 0.85).split(',')[1]
        done(resolve, { thumbnail_base64: base64, width: canvas.width, height: canvas.height, duration: video.duration || 0 })
      }
      video.src = url
    })
  }

  const NAVIGATION_ITEMS = [
    { key: 'myFiles', label: '我的文件', icon: 'file', active: true },
    { key: 'senders', label: '发送人', icon: 'avatar', active: false },
    { key: 'sessions', label: '会话', icon: 'message', active: false },
    { key: 'groups', label: '群聊', icon: 'peoples', active: false }
  ]

  // ---------- app commands (Rust layer) ----------
  const handlers = {
    get_settings: () => ({
      database: { sqlite_file: '' },
      backend: { base_url: API_BASE, ws_url: WS_URL }
    }),
    update_settings: () => null,
    is_app_state_ready: () => true,
    switch_user_database: () => null,

    async login_command(args) {
      const data = (args && args.data) || {}
      if (data.isAutoLogin) {
        const stored = auth.get()
        if (!stored.refreshToken || (data.uid && stored.uid !== data.uid)) {
          throw '自动登录失败，请手动登录'
        }
        await refreshToken()
        const now = auth.get()
        return { token: now.token, client: '', refreshToken: now.refreshToken, expire: '', uid: now.uid }
      }
      const result = await rawRequest('POST', IM_URLS.login[1], data, null, false)
      if (!result || result.code !== 200 || !result.data) {
        throw (result && result.msg) || '登录失败'
      }
      const resp = result.data
      auth.set({ token: resp.token, refreshToken: resp.refreshToken, uid: String(resp.uid) })
      return {
        token: resp.token,
        client: resp.client || '',
        refreshToken: resp.refreshToken,
        expire: resp.expire == null ? '' : String(resp.expire),
        uid: String(resp.uid)
      }
    },

    im_request_command(args) {
      return api(args.url, args.body == null ? null : args.body, args.params == null ? null : args.params)
    },

    get_user_tokens: () => {
      const a = auth.get()
      return { token: a.token || null, refreshToken: a.refreshToken || null }
    },
    update_token(args) {
      const req = (args && args.req) || {}
      auth.patch({ token: req.token, refreshToken: req.refreshToken, uid: req.uid || auth.get().uid })
      return null
    },
    remove_tokens() {
      auth.set({})
      return null
    },
    save_user_info: () => null,
    update_user_last_opt_time: () => null,

    list_contacts_command: () => api('getContactList'),
    hide_contact_command: (args) => api('setHide', args.data).then(() => null),

    async page_msg(args) {
      const param = (args && args.param) || {}
      const page = await api('getMsgPage', null, {
        roomId: param.roomId,
        pageSize: param.pageSize,
        cursor: param.cursor || ''
      })
      const data = page || {}
      return { cursor: data.cursor, isLast: !!data.isLast, list: sortAndBlock(data.list), total: data.total }
    },
    sync_messages: () => null,
    async send_msg(args) {
      const data = args.data
      try {
        const resp = await api('sendMsg', data)
        if (!resp) {
          throw 'empty response'
        }
        resp.oldMsgId = data.id
        channelSend(args.successChannel, resp)
      } catch (e) {
        log('send_msg failed', e)
        channelSend(args.errorChannel, data.id)
      }
      return null
    },
    save_msg: () => null,
    save_message_mark: () => null,
    update_message_recall_status: () => null,
    delete_message: () => null,
    delete_room_messages: () => 0,
    query_chat_history: (args) => queryChatHistory(args && args.param),

    page_room: (args) => api('groupList', null, args.pageParam),
    async get_room_members(args) {
      const members = await api('groupListMember', null, { roomId: args.roomId || args.room_id })
      return sortMembers(members || [])
    },
    async cursor_page_room_members(args) {
      const param = (args && args.param) || {}
      const members = sortMembers((await api('groupListMember', null, { roomId: param.roomId })) || [])
      return { cursor: '', isLast: true, list: members, total: members.length }
    },
    update_my_room_info: (args) => api('updateMyRoomInfo', args.myRoomInfo).then(() => null),

    ws_init_connection(args) {
      const params = (args && args.params) || {}
      ws.clientId = params.clientId || params.client_id || ws.clientId
      if (!ws.socket || ws.socket.readyState > WebSocket.OPEN) {
        wsConnect(false)
      }
      return { success: true, message: null }
    },
    ws_disconnect() {
      wsDisconnect()
      return { success: true, message: null }
    },
    ws_send_message(args) {
      const data = args && args.params ? args.params.data : null
      if (!wsSendAnywhere(data)) {
        throw 'WebSocket not connected'
      }
      return { success: true, message: null }
    },
    ws_get_state: () => sharedWsState(),
    ws_is_connected: () => ownsSocket() || (!ws.socket && sharedWsState() === 'CONNECTED'),
    ws_get_health: () => ({ is_healthy: sharedWsState() === 'CONNECTED', last_pong_time: null, consecutive_failures: 0, round_trip_time: null }),
    ws_force_reconnect() {
      if (ws.socket) {
        ws.closing = true
        ws.socket.close()
      }
      wsConnect(true)
      return { success: true, message: null }
    },
    ws_update_config: () => ({ success: true, message: null }),
    ws_set_app_background_state: () => ({ success: true, message: null }),
    ws_get_app_background_state: () => false,

    // Native window chrome has no browser equivalent.
    set_height: () => null,
    hide_title_bar_buttons: () => null,
    show_title_bar_buttons: () => null,
    set_window_movable: () => null,
    set_window_level_above_menubar: () => null,
    set_macos_traffic_lights_spacing: () => null,
    hide_splash_screen: () => null,
    set_complete: () => null,

    get_navigation_items: () => NAVIGATION_ITEMS.map((item) => ({ ...item })),
    query_files: (args) => queryFiles(args && args.param),
    // Window payloads are kept in localStorage so the window opened in another tab can read them.
    get_window_payload(args) {
      const key = PAYLOAD_PREFIX + args.label
      try {
        const text = localStorage.getItem(key)
        if (args.once !== false) {
          localStorage.removeItem(key)
        }
        return text == null ? null : JSON.parse(text)
      } catch (e) {
        return null
      }
    },
    push_window_payload(args) {
      const key = PAYLOAD_PREFIX + args.label
      let old = null
      try {
        const text = localStorage.getItem(key)
        old = text == null ? null : JSON.parse(text)
        localStorage.setItem(key, JSON.stringify(args.payload === undefined ? null : args.payload))
      } catch (e) {
        log('payload store failed', e)
      }
      return old
    },
    get_windows_scale_info: () => ({ scale: window.devicePixelRatio || 1 }),
    parse_markdown: (args) => (args && (args.content || args.markdown)) || '',
    get_readme_html: () => '',
    default_window_icon: () => null,

    async get_files_meta(args) {
      return ((args && args.filesPath) || []).map((path) => {
        const isUrl = /^https?:\/\//.test(path)
        return {
          name: String(path).split(/[\\/]/).pop(),
          path,
          file_type: extOf(path),
          mime_type: isUrl ? mimeOf(path) : (vfs.get(vfsKey(path)) || {}).type || mimeOf(path),
          exists: !isUrl && vfs.has(vfsKey(path))
        }
      })
    },
    // Storage settings: the "disk" is the browser's storage quota, the directory is the in-memory file system.
    async get_directory_usage_info_with_progress(args) {
      const prefix = vfsKey(args.directoryPath || '').replace(/\/$/, '')
      let total = 0
      let files = 0
      for (const [key, entry] of vfs) {
        if (!prefix || key === prefix || key.startsWith(prefix + '/')) {
          total += entry.bytes.byteLength
          files++
        }
      }
      let quota = 0
      let usage = 0
      try {
        const estimate = await navigator.storage.estimate()
        quota = estimate.quota || 0
        usage = estimate.usage || 0
      } catch (e) {
        log('storage estimate failed', e)
      }
      deliver('directory-scan-complete', {
        current_path: args.directoryPath || '',
        files_processed: files,
        total_size: total,
        elapsed_time: 0,
        elapsed_seconds: 0,
        progress_percentage: 100
      })
      return {
        path: args.directoryPath || '',
        total_size: total,
        disk_mount_point: '/',
        disk_total_space: quota,
        disk_used_space: usage,
        disk_usage_percentage: quota ? (usage / quota) * 100 : 0,
        usage_percentage: quota ? (total / quota) * 100 : 0
      }
    },
    cancel_directory_scan: () => null,
    start_oauth_server: () => startOauthServer(),
    get_video_thumbnail: (args) => videoThumbnail(args),
    upload_file_put: (args) => uploadPut(args),
    ai_message_send_stream: (args) => aiSendStream(args),
    ai_message_cancel_stream: (args) => aiCancelStream(args),
    qiniu_upload_resumable() {
      throw 'qiniu resumable upload is not available in the browser shim; use the MinIO storage engine'
    }
  }

  // Commands without a browser implementation, for diagnostics: window.__TAURI_SHIM__.unhandled
  const unhandled = new Set()
  const failures = []

  async function invoke(cmd, args, options) {
    args = args || {}
    try {
      if (cmd.startsWith('plugin:')) {
        const sep = cmd.indexOf('|')
        return await pluginCommand(cmd.slice(7, sep), cmd.slice(sep + 1), args, options)
      }
      const handler = handlers[cmd]
      if (!handler) {
        log('unhandled command', cmd, args)
        unhandled.add(cmd)
        return null
      }
      return await handler(args, options)
    } catch (e) {
      log('command failed', cmd, e)
      failures.push({ cmd, error: typeof e === 'string' ? e : String((e && e.message) || e) })
      throw typeof e === 'string' ? e : String((e && e.message) || e)
    }
  }

  window.__TAURI_INTERNALS__ = {
    invoke,
    transformCallback,
    unregisterCallback,
    runCallback,
    callbacks,
    convertFileSrc,
    metadata: {
      currentWindow: { label: LABEL },
      currentWebview: { label: LABEL, windowLabel: LABEL }
    },
    plugins: { path: { sep: '/', delimiter: ':' } }
  }
  window.__TAURI_SHIM__ = { auth, ws, label: LABEL, api, unhandled, failures, downloads, opened }
  log('installed for window', LABEL)
})()
