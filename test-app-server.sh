#!/bin/bash

# Test app-server protocol
(
  # 1. Initialize
  echo '{"id":"1","method":"initialize","params":{"clientInfo":{"name":"test","version":"1.0"}}}'
  sleep 0.5

  # 2. Send initialized notification
  echo '{"method":"initialized"}'
  sleep 0.5

  # 3. Create new conversation
  echo '{"id":"2","method":"newConversation","params":{"cwd":"'"$(pwd)"'","approvalPolicy":"never","sandbox":"read-only"}}'
  sleep 2

) | codex app-server 2>&1 | while IFS= read -r line; do
  echo "$line"

  # Extract conversationId from newConversation response
  if echo "$line" | jq -e '.result.conversationId' > /dev/null 2>&1; then
    CONV_ID=$(echo "$line" | jq -r '.result.conversationId')
    echo "Got conversationId: $CONV_ID" >&2

    # Now send followup requests with the real conversation ID
    (
      sleep 1
      echo "{\"id\":\"3\",\"method\":\"addConversationListener\",\"params\":{\"conversationId\":\"$CONV_ID\"}}"
      sleep 1
      echo "{\"id\":\"4\",\"method\":\"sendUserMessage\",\"params\":{\"conversationId\":\"$CONV_ID\",\"items\":[{\"type\":\"text\",\"data\":{\"text\":\"Say 'test successful' and nothing else\"}}]}}"
      sleep 15
    ) | codex app-server 2>&1 &
  fi
done
