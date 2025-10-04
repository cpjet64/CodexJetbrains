#!/usr/bin/env python3
import subprocess
import json
import time
import sys
import os

# Start app-server
proc = subprocess.Popen(
    ['codex', 'app-server'],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
    bufsize=1
)

def send(msg):
    """Send JSON-RPC message"""
    line = json.dumps(msg) + '\n'
    print(f"→ {line.strip()}", file=sys.stderr)
    proc.stdin.write(line)
    proc.stdin.flush()

def recv():
    """Receive JSON-RPC message"""
    line = proc.stdout.readline()
    if not line:
        return None
    print(f"← {line.strip()}", file=sys.stderr)
    try:
        return json.loads(line)
    except:
        return None

# 1. Initialize
send({"id": "1", "method": "initialize", "params": {"clientInfo": {"name": "JetBrainsTest", "version": "1.0"}}})
init_resp = recv()
print(f"Initialize response: {init_resp}", file=sys.stderr)

# 2. Send initialized notification
send({"method": "initialized"})
time.sleep(0.5)

# 3. Create conversation
send({"id": "2", "method": "newConversation", "params": {
    "cwd": os.getcwd(),
    "approvalPolicy": "never",
    "sandbox": "read-only"
}})

conv_resp = recv()
print(f"NewConversation response: {conv_resp}", file=sys.stderr)

if not conv_resp or 'result' not in conv_resp:
    print("ERROR: No conversation created", file=sys.stderr)
    proc.terminate()
    sys.exit(1)

conv_id = conv_resp['result']['conversationId']
print(f"\n✓ Got conversationId: {conv_id}\n", file=sys.stderr)

# 4. Add listener
send({"id": "3", "method": "addConversationListener", "params": {"conversationId": conv_id}})
listener_resp = recv()
print(f"AddListener response: {listener_resp}", file=sys.stderr)

# 5. Send user message
send({"id": "4", "method": "sendUserMessage", "params": {
    "conversationId": conv_id,
    "items": [{"type": "text", "data": {"text": "Say 'test successful' and nothing else"}}]
}})

# Read events for 15 seconds
print("\n--- Events ---", file=sys.stderr)
import select
timeout = 15
start = time.time()
while time.time() - start < timeout:
    # Check if stdout has data
    ready, _, _ = select.select([proc.stdout], [], [], 0.5)
    if ready:
        msg = recv()
        if msg:
            # Print event type if available
            if 'method' in msg:
                print(f"EVENT: {msg['method']}", file=sys.stderr)

proc.terminate()
print("\n✓ Test complete", file=sys.stderr)
