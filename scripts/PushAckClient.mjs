// usage: node PushAckClient.mjs <ws url> <seconds> <ack|noack|frames>
// Prints how many times each message id was received. Acks with the frame the client sends (type 15) when asked.
const [url, seconds, mode] = process.argv.slice(2);
const counts = {};
const ws = new WebSocket(url);
ws.onmessage = (e) => {
  const frame = JSON.parse(String(e.data));
  if (mode === 'frames') {
    console.log(frame.type);
    return;
  }
  if (frame.type !== 'receiveMessage') {
    return;
  }
  const id = String(frame.data.message.id);
  counts[id] = (counts[id] || 0) + 1;
  if (mode === 'ack') {
    ws.send(JSON.stringify({ type: 15, data: { msgId: id } }));
  }
};
setTimeout(() => {
  if (mode !== 'frames') {
    console.log(Object.values(counts).join(',') || 'none');
  }
  process.exit(0);
}, Number(seconds) * 1000);
