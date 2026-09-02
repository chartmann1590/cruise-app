// Cruise Chat guest client – vanilla JS, offline-safe
(function() {
  const STORAGE_GUEST_ID = 'cruiseChatGuestId';
  const STORAGE_GUEST_NAME = 'cruiseChatGuestName';
  const stateJoin = document.getElementById('state-join');
  const stateConnecting = document.getElementById('state-connecting');
  const stateChat = document.getElementById('state-chat');
  const nameInput = document.getElementById('nameInput');
  const joinBtn = document.getElementById('joinBtn');
  const joinError = document.getElementById('joinError');
  const connectingText = document.getElementById('connectingText');
  const messageList = document.getElementById('messageList');
  const chatInput = document.getElementById('chatInput');
  const composer = document.getElementById('composer');
  const connDot = document.getElementById('connDot');
  const connText = document.getElementById('connText');
  const statusBar = document.getElementById('statusBar');

  function getOrCreateGuestId() {
    let id = localStorage.getItem(STORAGE_GUEST_ID);
    if (!id) {
      id = (crypto.randomUUID && crypto.randomUUID()) || 'guest-' + Date.now() + '-' + Math.random().toString(36).slice(2,9);
      localStorage.setItem(STORAGE_GUEST_ID, id);
    }
    return id;
  }

  function getStoredName() {
    return (localStorage.getItem(STORAGE_GUEST_NAME) || '').trim();
  }

  function setStoredName(name) {
    try { localStorage.setItem(STORAGE_GUEST_NAME, name); } catch (_) {}
  }

  function showState(which) {
    stateJoin.classList.add('hidden');
    stateConnecting.classList.add('hidden');
    stateChat.classList.add('hidden');
    if (which === 'join') stateJoin.classList.remove('hidden');
    else if (which === 'connecting') stateConnecting.classList.remove('hidden');
    else if (which === 'chat') stateChat.classList.remove('hidden');
  }

  function setConnectionStatus(isOnline) {
    if (isOnline) {
      connDot.classList.remove('offline');
      connDot.classList.add('online');
      connDot.title = 'online';
      connText.textContent = 'Connected';
    } else {
      connDot.classList.remove('online');
      connDot.classList.add('offline');
      connDot.title = 'offline';
      connText.textContent = 'Offline';
    }
  }

  function formatTime(ts) {
    try {
      const d = new Date(ts);
      return d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
    } catch (_) { return ''; }
  }

  function appendMessage({ id, sender, text, ts, self }) {
    // Dedupe by id if already in DOM
    if (id && document.querySelector('[data-msg-id="' + CSS.escape(id) + '"]')) return;

    const row = document.createElement('div');
    row.className = 'msg-row ' + (self ? 'self' : 'other');
    row.setAttribute('data-msg-id', id || '');

    if (!self) {
      const meta = document.createElement('div');
      meta.className = 'msg-meta';
      meta.textContent = sender || 'Guest';
      row.appendChild(meta);
    }

    const bubble = document.createElement('div');
    bubble.className = 'bubble';
    bubble.textContent = text;
    row.appendChild(bubble);

    const time = document.createElement('div');
    time.className = 'msg-time';
    time.textContent = formatTime(ts);
    row.appendChild(time);

    messageList.appendChild(row);
    // Scroll to bottom
    requestAnimationFrame(() => {
      messageList.scrollTop = messageList.scrollHeight;
    });
  }

  function appendOptimistic(id, text) {
    const row = document.createElement('div');
    row.className = 'msg-row self sending';
    row.setAttribute('data-msg-id', id);
    const bubble = document.createElement('div');
    bubble.className = 'bubble';
    bubble.textContent = text;
    row.appendChild(bubble);
    const time = document.createElement('div');
    time.className = 'msg-time';
    time.textContent = 'sending…';
    row.appendChild(time);
    messageList.appendChild(row);
    messageList.scrollTop = messageList.scrollHeight;
    return row;
  }

  function finalizeOptimistic(id) {
    const el = document.querySelector('[data-msg-id="' + CSS.escape(id) + '"]');
    if (el) {
      el.classList.remove('sending');
      const timeEl = el.querySelector('.msg-time');
      if (timeEl) timeEl.textContent = formatTime(Date.now());
    }
  }

  // State
  let ws = null;
  let guestName = getStoredName();
  let backoffMs = 1000;
  let shouldReconnect = true;
  let inFlightIds = new Set();
  let reconnectTimer = null;

  // If name already stored, skip join screen? Keep UX: allow editing but prefill.
  if (guestName) {
    nameInput.value = guestName;
  }

  function getWsUrl() {
    const proto = location.protocol === 'https:' ? 'wss://' : 'ws://';
    return proto + location.host + '/ws';
  }

  let hasConnectedOnce = false;
  let offlineDebounce = null;
  let pingInterval = null;

  function connect() {
    const url = getWsUrl();
    const isReconnect = hasConnectedOnce;
    // Only show full-screen "Connecting…" on first ever connect; reconnects keep chat visible to avoid flicker
    if (!isReconnect) {
      connectingText.textContent = 'Connecting…';
      showState('connecting');
    } else {
      // Reconnecting silently – keep chat visible, just show subtle offline until back online
      // Don't hide chat; status dot will indicate
    }
    // Don't immediately flip to offline; debounce so brief blips don't flash "Offline"
    clearTimeout(offlineDebounce);
    try {
      ws = new WebSocket(url);
    } catch (e) {
      console.error('WebSocket create failed', e);
      scheduleReconnect();
      return;
    }

    ws.onopen = () => {
      backoffMs = 1000;
      hasConnectedOnce = true;
      setConnectionStatus(true);
      clearTimeout(offlineDebounce);
      // Only switch to chat state on first connect; subsequent reconnects were already on chat
      if (!isReconnect) showState('chat');
      statusBar.classList.add('hidden');
      // Send join per wire protocol – must be first
      const payload = JSON.stringify({ type: 'join', guestId: getOrCreateGuestId(), name: guestName });
      try { ws.send(payload); } catch (e) { console.error('join send failed', e); }
      // Keep-alive ping every 25s (opportunistic, server ignores but keeps NAT/Wi-Fi alive)
      clearInterval(pingInterval);
      pingInterval = setInterval(() => {
        try { if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ type: 'ping' })); } catch (_) {}
      }, 25000);
    };

    ws.onmessage = (ev) => {
      let data;
      try { data = JSON.parse(ev.data); } catch (e) { console.error('bad json', e); return; }
      if (data.type === 'ping' || data.type === 'pong') return; // ignore keepalive
      if (data.type === 'history') {
        const msgs = data.messages || [];
        if (msgs.length === 0 && messageList.children.length === 0) {
          const empty = document.createElement('div');
          empty.className = 'muted small';
          empty.style.textAlign = 'center';
          empty.style.padding = '18px';
          empty.textContent = 'No messages yet. Say hello!';
          messageList.appendChild(empty);
        } else if (msgs.length > 0) {
          // For initial load, clear placeholder if present
          const placeholder = messageList.querySelector('.muted.small');
          if (placeholder) placeholder.remove();
          // Merge history without clearing existing messages to avoid flash on reconnect
          // Only add messages not already in DOM
          const existingIds = new Set(Array.from(messageList.querySelectorAll('[data-msg-id]')).map(el => el.getAttribute('data-msg-id')));
          let added = 0;
          msgs.forEach(m => {
            if (!m.id || !existingIds.has(m.id)) {
              appendMessage({ id: m.id, sender: m.sender, text: m.text, ts: m.ts, self: !!m.self });
              added++;
            }
          });
          // If this is first load and we had no messages, the above added them; if reconnect, we only added new ones
          if (added === 0 && messageList.children.length === 0) {
            // fallback: if we somehow have no children after merge, render all
            msgs.forEach(m => appendMessage({ id: m.id, sender: m.sender, text: m.text, ts: m.ts, self: !!m.self }));
          }
        }
      } else if (data.type === 'chat') {
        const id = data.id;
        // If this is our own in-flight id, finalize instead of duplicating
        if (id && inFlightIds.has(id)) {
          finalizeOptimistic(id);
          // Remove after short delay to cover server echo timing edge
          setTimeout(() => inFlightIds.delete(id), 1000);
          return;
        }
        appendMessage({ id: data.id, sender: data.sender, text: data.text, ts: data.ts, self: false });
      } else if (data.type === 'error') {
        const msg = data.message || data.code || 'Unknown error';
        statusBar.textContent = msg;
        statusBar.classList.remove('hidden');
        // If NAME_REQUIRED, go back to join screen
        if (data.code === 'NAME_REQUIRED') {
          shouldReconnect = false;
          try { ws.close(); } catch (_) {}
          showState('join');
          joinError.textContent = msg;
          joinError.classList.remove('hidden');
        }
      }
    };

    ws.onclose = () => {
      clearInterval(pingInterval);
      // Debounce offline indicator – only show after 800ms to avoid flicker on quick reconnect
      clearTimeout(offlineDebounce);
      offlineDebounce = setTimeout(() => setConnectionStatus(false), 800);
      if (shouldReconnect) {
        // Keep chat visible; show subtle reconnecting banner only after a moment
        clearTimeout(reconnectTimer);
        // Delay showing banner slightly as well
        setTimeout(() => {
          if (!hasConnectedOnce || (ws && ws.readyState === WebSocket.OPEN)) return;
          statusBar.textContent = 'Reconnecting…';
          statusBar.classList.remove('hidden');
        }, 1200);
        scheduleReconnect();
      }
    };

    ws.onerror = () => {
      console.warn('WebSocket error');
    };
  }

  function scheduleReconnect() {
    if (!shouldReconnect) return;
    clearTimeout(reconnectTimer);
    // Don't immediately show offline banner on reconnect; let onclose's debounced handling do it
    // Only show if we haven't connected before (first attempt)
    if (!hasConnectedOnce) {
      statusBar.textContent = 'Offline — retrying…';
      statusBar.classList.remove('hidden');
    }
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null;
      backoffMs = Math.min(backoffMs * 2, 15000);
      if (shouldReconnect) connect();
    }, backoffMs);
  }

  function handleJoinSubmit() {
    const name = nameInput.value.trim();
    if (!name) {
      joinError.textContent = 'Please enter your name.';
      joinError.classList.remove('hidden');
      nameInput.focus();
      return;
    }
    if (name.length > 40) {
      joinError.textContent = 'Name must be 40 characters or less.';
      joinError.classList.remove('hidden');
      return;
    }
    joinError.classList.add('hidden');
    guestName = name;
    setStoredName(name);
    shouldReconnect = true;
    backoffMs = 1000;
    connect();
  }

  joinBtn.addEventListener('click', handleJoinSubmit);
  nameInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleJoinSubmit();
    }
  });

  composer.addEventListener('submit', (e) => {
    e.preventDefault();
    const text = chatInput.value.trim();
    if (!text) return;
    if (text.length > 2000) {
      statusBar.textContent = 'Message too long (max 2000 chars)';
      statusBar.classList.remove('hidden');
      return;
    }
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      statusBar.textContent = 'Not connected — message not sent. Reconnecting…';
      statusBar.classList.remove('hidden');
      return;
    }
    const id = (crypto.randomUUID && crypto.randomUUID()) || 'msg-' + Date.now() + '-' + Math.random().toString(36).slice(2,6);
    inFlightIds.add(id);
    appendOptimistic(id, text);
    try {
      ws.send(JSON.stringify({ type: 'chat', id, text }));
      chatInput.value = '';
      // Clear status
      statusBar.classList.add('hidden');
      // Optimistically mark as sent after short delay – server skips echo to sender, so we won't get broadcast back
      setTimeout(() => finalizeOptimistic(id), 400);
      // Auto-expire inFlight after 10s in case echo missed
      setTimeout(() => inFlightIds.delete(id), 10000);
    } catch (err) {
      console.error('send failed', err);
      statusBar.textContent = 'Failed to send — will retry when reconnected.';
      statusBar.classList.remove('hidden');
    }
  });

  // Auto-connect if name already stored (fast path); otherwise show join screen
  if (guestName) {
    // Give DOM a moment then connect; but also allow user to edit name if desired?
    // We'll show join screen with name prefilled; user must tap Join again – explicit opt-in.
    // However for reconnection scenario (refresh while already joined), auto-join is better.
    // Heuristic: if name stored and we have guestId, we can attempt auto-join without showing join card again.
    // Check if we've already shown chat before? On first load after storing name, auto-join immediately.
    const autoJoin = true; // per spec reconnect should keep identity without re-prompting
    if (autoJoin) {
      showState('connecting');
      shouldReconnect = true;
      connect();
    } else {
      showState('join');
    }
  } else {
    showState('join');
    // Focus input on mobile
    setTimeout(() => nameInput.focus(), 200);
  }

  // Handle visibility change: if tab hidden then shown, ensure WS is alive
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && ws && ws.readyState === WebSocket.CLOSED && shouldReconnect) {
      connect();
    }
  });
})();
