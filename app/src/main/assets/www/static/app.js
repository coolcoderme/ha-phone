/* ES5 UI modeled on Home Assistant's sidebar, cards, and more-info dialog.
   Talks to the local HA-compatible REST/WebSocket API. No cloud, no account. */
(function () {
  var ICONS = {
    dashboard: '<svg viewBox="0 0 24 24"><path d="M13 3v6h8V3h-8m0 18h8V11h-8v10M3 21h8v-6H3v6m0-8h8V3H3v10z"/></svg>',
    devices: '<svg viewBox="0 0 24 24"><path d="M4 6h18V4H4c-1.1 0-2 .9-2 2v11H0v3h14v-3H4V6m19 2h-6c-.5 0-1 .5-1 1v10c0 .5.5 1 1 1h6c.5 0 1-.5 1-1V9c0-.5-.5-1-1-1m-1 9h-4v-7h4v7z"/></svg>',
    sofa: '<svg viewBox="0 0 24 24"><path d="M7 11c-1.1 0-2 .9-2 2v2h14v-2c0-1.1-.9-2-2-2H7m12-4V6c0-1.1-.9-2-2-2H7C5.9 4 5 4.9 5 6v1c-1.7 0-3 1.3-3 3v6h2v2h2v-2h12v2h2v-2h2v-6c0-1.7-1.3-3-3-3z"/></svg>',
    palette: '<svg viewBox="0 0 24 24"><path d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.8 0 1.5-.7 1.5-1.5 0-.4-.1-.7-.3-1-.2-.3-.3-.6-.3-1 0-.8.7-1.5 1.5-1.5H16c2.8 0 5-2.2 5-5 0-4.6-4.5-8.5-9-8.5m-5.5 9c-.8 0-1.5-.7-1.5-1.5S5.7 8 6.5 8 8 8.7 8 9.5 7.3 11 6.5 11m3-4C8.7 7 8 6.3 8 5.5S8.7 4 9.5 4s1.5.7 1.5 1.5S10.3 7 9.5 7m5 0c-.8 0-1.5-.7-1.5-1.5S13.7 4 14.5 4s1.5.7 1.5 1.5S15.3 7 14.5 7m3 4c-.8 0-1.5-.7-1.5-1.5S16.7 8 17.5 8s1.5.7 1.5 1.5-.7 1.5-1.5 1.5z"/></svg>',
    robot: '<svg viewBox="0 0 24 24"><path d="M12 2a2 2 0 0 1 2 2c0 .7-.4 1.4-1 1.7V7h3a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3v-8a3 3 0 0 1 3-3h3V5.7c-.6-.3-1-1-1-1.7a2 2 0 0 1 2-2M7.5 13A1.5 1.5 0 0 0 6 14.5 1.5 1.5 0 0 0 7.5 16 1.5 1.5 0 0 0 9 14.5 1.5 1.5 0 0 0 7.5 13m9 0a1.5 1.5 0 0 0-1.5 1.5 1.5 1.5 0 0 0 1.5 1.5 1.5 1.5 0 0 0 1.5-1.5 1.5 1.5 0 0 0-1.5-1.5z"/></svg>',
    cog: '<svg viewBox="0 0 24 24"><path d="M12 15.5A3.5 3.5 0 0 1 8.5 12 3.5 3.5 0 0 1 12 8.5a3.5 3.5 0 0 1 3.5 3.5 3.5 3.5 0 0 1-3.5 3.5m7.4-4c0-.3 0-.5-.1-.8l2-1.5-2-3.5-2.4 1c-.5-.4-1.1-.7-1.7-.9L14.5 2h-5L9 5.8c-.6.2-1.2.5-1.7.9L4.9 5.7l-2 3.5 2 1.5c0 .3-.1.5-.1.8s0 .5.1.8l-2 1.5 2 3.5 2.4-1c.5.4 1.1.7 1.7.9L9.5 22h5l.7-3.8c.6-.2 1.2-.5 1.7-.9l2.4 1 2-3.5-2-1.5c.1-.3.1-.5.1-.8z"/></svg>',
    menu: '<svg viewBox="0 0 24 24"><path d="M3 6h18v2H3V6m0 5h18v2H3v-2m0 5h18v2H3v-2z"/></svg>',
    plus: '<svg viewBox="0 0 24 24"><path d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg>',
    light: '<svg viewBox="0 0 24 24"><path d="M12 2a7 7 0 0 1 7 7c0 2.4-1.2 4.5-3 5.7V17a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1v-2.3C6.2 13.5 5 11.4 5 9a7 7 0 0 1 7-7M9 21v-1h6v1a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1z"/></svg>',
    switch: '<svg viewBox="0 0 24 24"><path d="M17 7H7a5 5 0 0 0-5 5 5 5 0 0 0 5 5h10a5 5 0 0 0 5-5 5 5 0 0 0-5-5m0 8a3 3 0 0 1-3-3 3 3 0 0 1 3-3 3 3 0 0 1 3 3 3 3 0 0 1-3 3z"/></svg>',
    media: '<svg viewBox="0 0 24 24"><path d="M12 3v10.6A4 4 0 1 0 14 17V7h4V3h-6z"/></svg>',
    sensor: '<svg viewBox="0 0 24 24"><path d="M6 2v6h.01L6 8.01 10 12l-4 4 .01.01H6V22h12v-5.99h-.01L18 16l-4-4 4-3.99-.01-.01H18V2H6z"/></svg>',
    close: '<svg viewBox="0 0 24 24"><path d="M19 6.4L17.6 5 12 10.6 6.4 5 5 6.4 10.6 12 5 17.6 6.4 19 12 13.4 17.6 19 19 17.6 13.4 12 19 6.4z"/></svg>'
  };

  var PAGES = [
    { id: "overview", label: "Overview", icon: "dashboard" },
    { id: "devices", label: "Devices", icon: "devices" },
    { id: "areas", label: "Areas", icon: "sofa" },
    { id: "scenes", label: "Scenes", icon: "palette" },
    { id: "automations", label: "Automations", icon: "robot" },
    { id: "settings", label: "Settings", icon: "cog" }
  ];

  var state = {
    page: "overview",
    states: [],
    devices: [],
    rooms: [],
    scenes: [],
    automations: [],
    discovered: [],
    settings: {},
    ws: null,
    token: "",
    scanStatus: "",
    dialogOpen: false
  };

  try { state.token = localStorage.getItem("haphoneToken") || ""; } catch (e) {}

  function $(id) { return document.getElementById(id); }

  function asList(data) {
    if (!data) return [];
    if (Object.prototype.toString.call(data) === "[object Array]") return data;
    if (typeof data.length === "number") {
      var out = [];
      var n;
      for (n = 0; n < data.length; n++) out.push(data[n]);
      return out;
    }
    return [];
  }

  function xhr(method, url, body, cb) {
    var req = new XMLHttpRequest();
    var u = url;
    if (method === "GET") u += (url.indexOf("?") >= 0 ? "&" : "?") + "_ts=" + Date.now();
    req.open(method, u, true);
    req.setRequestHeader("Content-Type", "application/json");
    if (state.token) req.setRequestHeader("Authorization", "Bearer " + state.token);
    req.onreadystatechange = function () {
      if (req.readyState === 4) {
        if (req.status === 401) {
          showPinGate(function () { xhr(method, url, body, cb); });
          return;
        }
        var parsed = null;
        try { parsed = req.responseText ? JSON.parse(req.responseText) : null; } catch (e) {}
        if (cb) cb(parsed, req.status);
      }
    };
    req.send(body ? JSON.stringify(body) : null);
  }

  function icon(name) { return ICONS[name] || ICONS.sensor; }

  function isOn(entity) {
    return entity.state === "on" || entity.state === "playing";
  }

  function domainOf(entityId) {
    return (entityId || "").split(".")[0];
  }

  function iconFor(entity) {
    var d = domainOf(entity.entity_id);
    if (d === "light") return "light";
    if (d === "switch" || d === "automation") return "switch";
    if (d === "media_player") return "media";
    if (d === "scene") return "palette";
    return "sensor";
  }

  function applyTheme() {
    var t = (state.settings && state.settings.theme) || "auto";
    var light = t === "light";
    if (t === "auto" && window.matchMedia) {
      try { light = window.matchMedia("(prefers-color-scheme: light)").matches; } catch (e) { light = false; }
    }
    document.body.className = light ? "theme-light" : "";
  }

  function refresh(cb) {
    var left = 6;
    function done() { left -= 1; if (left <= 0 && cb) cb(); }
    xhr("GET", "/api/states", null, function (data) { state.states = asList(data); done(); });
    xhr("GET", "/api/devices", null, function (data) { state.devices = asList(data); done(); });
    xhr("GET", "/api/rooms", null, function (data) { state.rooms = asList(data); done(); });
    xhr("GET", "/api/scenes", null, function (data) { state.scenes = asList(data); done(); });
    xhr("GET", "/api/automations", null, function (data) { state.automations = asList(data); done(); });
    xhr("GET", "/api/settings", null, function (data) { state.settings = data || {}; applyTheme(); done(); });
  }

  function callService(domain, service, entityId, data) {
    var body = data ? data : {};
    body.entity_id = entityId;
    xhr("POST", "/api/services/" + domain + "/" + service, body, function () {
      setTimeout(function () { refresh(render); }, 400);
    });
  }

  function connectWs() {
    var proto = location.protocol === "https:" ? "wss://" : "ws://";
    var ws = new WebSocket(proto + location.host + "/api/websocket");
    state.ws = ws;
    ws.onopen = function () {};
    ws.onmessage = function (ev) {
      var msg;
      try { msg = JSON.parse(ev.data); } catch (e) { return; }
      if (msg.type === "auth_required") {
        ws.send(JSON.stringify({ type: "auth", access_token: state.token || "local-owner-token" }));
      } else if (msg.type === "auth_ok") {
        ws.send(JSON.stringify({ id: 1, type: "subscribe_events", event_type: "state_changed" }));
      } else if (msg.type === "auth_invalid") {
        showPinGate(function () { try { ws.close(); } catch (e) {} connectWs(); });
      } else if (msg.type === "event" && msg.event && msg.event.event_type === "state_changed") {
        var ns = msg.event.data && msg.event.data.new_state;
        if (ns) {
          var i;
          for (i = 0; i < state.states.length; i++) {
            if (state.states[i].entity_id === ns.entity_id) { state.states[i] = ns; break; }
          }
          if (i === state.states.length) state.states.push(ns);
          if (state.page === "overview") render();
        }
      }
    };
    ws.onclose = function () { setTimeout(connectWs, 3000); };
  }

  function setPage(id) {
    state.page = id;
    $("app").className = "";
    render();
  }

  function closeDialog() {
    $("dialog").className = "hidden";
    state.dialogOpen = false;
  }

  function openDialog(html) {
    $("dialogCard").innerHTML = html;
    $("dialog").className = "";
    state.dialogOpen = true;
  }

  function showPinGate(after) {
    var gate = $("pinGate");
    gate.className = "";
    $("pinError").className = "error hidden";
    $("pinSubmit").onclick = function () {
      var pin = ($("pinInput").value || "").trim();
      if (!pin) { $("pinError").className = "error"; $("pinError").textContent = "Enter a PIN"; return; }
      state.token = pin;
      try { localStorage.setItem("haphoneToken", pin); } catch (e) {}
      xhr("GET", "/api/", null, function (_data, status) {
        if (status === 401) {
          $("pinError").className = "error";
          $("pinError").textContent = "Wrong PIN";
          return;
        }
        gate.className = "hidden";
        if (after) after();
      });
    };
  }

  window.HaPhoneUi = {
    onBack: function () {
      if (state.dialogOpen && $("dialog").className.indexOf("hidden") < 0) {
        closeDialog();
        return true;
      }
      if ($("app").className === "drawer") {
        $("app").className = "";
        return true;
      }
      if (state.page !== "overview") {
        setPage("overview");
        return true;
      }
      return false;
    }
  };

  function renderNav() {
    var html = "";
    var i;
    for (i = 0; i < PAGES.length; i++) {
      var p = PAGES[i];
      html += '<div class="nav-item' + (state.page === p.id ? " active" : "") + '" data-page="' + p.id + '">' +
        icon(p.icon) + "<span>" + p.label + "</span></div>";
    }
    $("nav").innerHTML = html;
    var items = $("nav").querySelectorAll(".nav-item");
    for (i = 0; i < items.length; i++) {
      items[i].onclick = function () { setPage(this.getAttribute("data-page")); };
    }
  }

  function entityCardRows(entityIds, all) {
    var html = "";
    var i, j;
    var list = [];
    if (entityIds) {
      for (i = 0; i < entityIds.length; i++) {
        for (j = 0; j < all.length; j++) {
          if (all[j].entity_id === entityIds[i]) list.push(all[j]);
        }
      }
    } else {
      list = all;
    }
    for (i = 0; i < list.length; i++) html += entityRow(list[i]);
    return html;
  }

  function entityRow(e) {
    var on = isOn(e);
    var d = domainOf(e.entity_id);
    var toggle = (d === "light" || d === "switch" || d === "automation" || d === "media_player");
    var name = (e.attributes && e.attributes.friendly_name) || e.entity_id;
    var extra = e.state === "scening" ? "Scene" : e.state;
    if (e.attributes && e.attributes.brightness != null && on) extra = Math.round(e.attributes.brightness * 100 / 255) + "%";
    var bri = "";
    if (d === "light" && e.attributes && e.attributes.brightness != null) {
      bri = '<input class="entity-bri" data-bri="' + e.entity_id + '" type="range" min="1" max="255" value="' +
        (e.attributes.brightness || 1) + '"/>';
    }
    return '<div class="entity-row" data-eid="' + e.entity_id + '">' +
      '<div class="entity-icon' + (on ? "" : " off") + '">' + icon(iconFor(e)) + "</div>" +
      '<div class="entity-name">' + escapeHtml(name) + "</div>" +
      '<div class="entity-state">' + escapeHtml(extra) + "</div>" +
      bri +
      (toggle ? '<div class="switch' + (on ? " on" : "") + '" data-toggle="' + e.entity_id + '"><i></i></div>' : "") +
      "</div>";
  }

  function escapeHtml(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function bindEntityRows(root) {
    var rows = root.querySelectorAll(".entity-row");
    var i;
    for (i = 0; i < rows.length; i++) {
      rows[i].onclick = function (ev) {
        var t = ev.target;
        var eid = this.getAttribute("data-eid");
        if (t.className.indexOf("switch") >= 0 || (t.parentNode && t.parentNode.className.indexOf("switch") >= 0)) {
          ev.stopPropagation();
          callService(domainOf(eid), "toggle", eid, {});
          return;
        }
        openMoreInfo(eid);
      };
    }
    var bris = root.querySelectorAll("[data-bri]");
    for (i = 0; i < bris.length; i++) {
      bris[i].onclick = function (ev) { ev.stopPropagation(); };
      bris[i].onchange = function (ev) {
        ev.stopPropagation();
        callService("light", "turn_on", this.getAttribute("data-bri"), { brightness: parseInt(this.value, 10) });
      };
    }
  }

  function renderOverview() {
    $("pageTitle").textContent = "Overview";
    $("headerAction").innerHTML = icon("plus");
    $("headerAction").onclick = function () { openAddDevice(); };
    var rooms = state.rooms;
    var html = '<div class="masonry">';
    var used = {};
    var i, j, k;
    if (!state.states.length) {
      html += '<div class="ha-card"><div class="empty">No devices yet. Connect this phone to Wi-Fi, then open Settings and scan the network. Hue, WLED, Shelly, Tasmota, Kasa, LIFX, Roku, Sonos, ESPHome and DLNA are discovered automatically. HTTP-only devices can also be added by IP.</div></div>';
    }
    for (i = 0; i < rooms.length; i++) {
      var ids = [];
      for (j = 0; j < state.devices.length; j++) {
        if (state.devices[j].roomId === rooms[i].id) {
          var ents = state.devices[j].entities || [];
          for (k = 0; k < ents.length; k++) {
            ids.push(ents[k].entity_id);
            used[ents[k].entity_id] = true;
          }
        }
      }
      if (!ids.length) continue;
      html += '<div class="ha-card"><h2>' + escapeHtml(rooms[i].name) + "</h2>" + entityCardRows(ids, state.states) + "</div>";
    }
    var leftover = [];
    for (i = 0; i < state.states.length; i++) {
      var d0 = domainOf(state.states[i].entity_id);
      if (d0 === "scene" || d0 === "automation") continue;
      if (!used[state.states[i].entity_id]) leftover.push(state.states[i].entity_id);
    }
    if (leftover.length) {
      html += '<div class="ha-card"><h2>Devices</h2>' + entityCardRows(leftover, state.states) + "</div>";
    }
    if (state.scenes.length) {
      var sids = [];
      for (i = 0; i < state.scenes.length; i++) sids.push("scene." + state.scenes[i].id);
      html += '<div class="ha-card"><h2>Scenes</h2>' + entityCardRows(sids, state.states) + "</div>";
    }
    html += "</div>";
    $("content").innerHTML = html;
    bindEntityRows($("content"));
  }

  function hostLabel(d) {
    if (!d.host) return d.kind + " · no address";
    return d.kind + " · " + d.host + (d.port ? ":" + d.port : "");
  }

  function renderDevices() {
    $("pageTitle").textContent = "Devices";
    $("headerAction").innerHTML = icon("plus");
    $("headerAction").onclick = function () { openAddDevice(); };
    var html = "";
    var i;
    for (i = 0; i < state.devices.length; i++) {
      var d = state.devices[i];
      html += '<div class="ha-card"><h2>' + escapeHtml(d.name || "(unnamed)") + '</h2>' +
        '<div class="muted">' + escapeHtml(hostLabel(d)) + "</div>";
      var ents = d.entities || [];
      var j;
      for (j = 0; j < ents.length; j++) {
        var st = null;
        var k;
        for (k = 0; k < state.states.length; k++) {
          if (state.states[k].entity_id === ents[j].entity_id) st = state.states[k];
        }
        if (st) html += entityRow(st);
      }
      html += '<div class="row-actions">';
      if (d.kind === "HUE") html += '<button class="btn outline" data-hue="' + d.id + '">Pair Hue</button>';
      html += '<button class="btn quiet" data-del="' + d.id + '">Remove</button></div></div>';
    }
    if (!state.devices.length) html = '<div class="ha-card"><div class="empty">Nothing added yet. Scan Wi-Fi from Settings, or tap + to add by IP.</div></div>';
    $("content").innerHTML = html;
    bindEntityRows($("content"));
    var dels = $("content").querySelectorAll("[data-del]");
    for (i = 0; i < dels.length; i++) {
      dels[i].onclick = function () {
        var id = this.getAttribute("data-del");
        openConfirm("Remove this device?", function () {
          xhr("DELETE", "/api/devices/" + id, null, function () { refresh(render); });
        });
      };
    }
    var hues = $("content").querySelectorAll("[data-hue]");
    for (i = 0; i < hues.length; i++) {
      hues[i].onclick = function () {
        xhr("POST", "/api/devices/" + this.getAttribute("data-hue") + "/hue_pair", {}, function (data) {
          openMessage((data && data.message) || "Done");
          refresh(render);
        });
      };
    }
  }

  function renderAreas() {
    $("pageTitle").textContent = "Areas";
    $("headerAction").innerHTML = icon("plus");
    $("headerAction").onclick = function () { openNameDialog("New area", "", function (name) {
      xhr("POST", "/api/rooms", { name: name }, function () { refresh(render); });
    }); };
    var html = '<div class="masonry">';
    var i, j;
    for (i = 0; i < state.rooms.length; i++) {
      var room = state.rooms[i];
      html += '<div class="ha-card"><h2>' + escapeHtml(room.name) + "</h2>";
      html += '<div class="area-devices">';
      for (j = 0; j < state.devices.length; j++) {
        var d = state.devices[j];
        if (d.roomId !== room.id) continue;
        html += '<div class="item"><div>' + escapeHtml(d.name) + '</div><span class="muted">' + escapeHtml(d.kind) + "</span></div>";
      }
      html += "</div>";
      html += '<label class="muted">Assign device</label><select class="field" data-assign="' + room.id + '"><option value="">—</option>';
      for (j = 0; j < state.devices.length; j++) {
        html += '<option value="' + state.devices[j].id + '"' + (state.devices[j].roomId === room.id ? " selected" : "") + ">" +
          escapeHtml(state.devices[j].name) + "</option>";
      }
      html += "</select>";
      html += '<div class="row-actions">' +
        '<button class="btn outline" data-edit-room="' + room.id + '">Rename</button>' +
        '<button type="button" class="btn outline hit" data-del-room="' + room.id + '">Delete</button></div></div>';
    }
    html += "</div>";
    if (!state.rooms.length) html = '<div class="ha-card"><div class="empty">No areas yet.</div></div>';
    $("content").innerHTML = html;
    var edits = $("content").querySelectorAll("[data-edit-room]");
    for (i = 0; i < edits.length; i++) {
      edits[i].onclick = function () {
        var id = this.getAttribute("data-edit-room");
        var room = null;
        var n;
        for (n = 0; n < state.rooms.length; n++) if (state.rooms[n].id === id) room = state.rooms[n];
        openNameDialog("Rename area", room ? room.name : "", function (name) {
          xhr("POST", "/api/rooms", { id: id, name: name }, function () { refresh(render); });
        });
      };
    }
    var dels = $("content").querySelectorAll("[data-del-room]");
    for (i = 0; i < dels.length; i++) {
      dels[i].onclick = function (ev) {
        if (ev && ev.stopPropagation) ev.stopPropagation();
        var id = this.getAttribute("data-del-room");
        openConfirm("Delete this area?", function () {
          xhr("DELETE", "/api/rooms/" + id, null, function () { refresh(render); });
        });
      };
    }
    var assigns = $("content").querySelectorAll("[data-assign]");
    for (i = 0; i < assigns.length; i++) {
      assigns[i].onchange = function () {
        var roomId = this.getAttribute("data-assign");
        var deviceId = this.value;
        if (!deviceId) return;
        xhr("POST", "/api/devices/" + deviceId + "/room", { roomId: roomId }, function () { refresh(render); });
      };
    }
  }

  function renderScenes() {
    $("pageTitle").textContent = "Scenes";
    $("headerAction").innerHTML = icon("plus");
    $("headerAction").onclick = function () {
      openNameDialog("Scene name (snapshots current lights)", "", function (name) {
        xhr("POST", "/api/scenes", { name: name }, function () { refresh(render); });
      });
    };
    var html = "";
    var i;
    for (i = 0; i < state.scenes.length; i++) {
      html += '<div class="ha-card"><h2>' + escapeHtml(state.scenes[i].name) + "</h2>" +
        '<div class="row-actions">' +
        '<button class="btn" data-scene="' + state.scenes[i].id + '">Activate</button>' +
        '<button class="btn outline" data-edit-scene="' + state.scenes[i].id + '">Rename</button>' +
        '<button type="button" class="btn outline hit" data-del-scene="' + state.scenes[i].id + '">Delete</button></div></div>';
    }
    if (!html) html = '<div class="ha-card"><div class="empty">Create a scene to snapshot lights and switches as they are now.</div></div>';
    $("content").innerHTML = html;
    var btns = $("content").querySelectorAll("[data-scene]");
    for (i = 0; i < btns.length; i++) {
      btns[i].onclick = function () {
        xhr("POST", "/api/scenes/" + this.getAttribute("data-scene") + "/activate", {}, function () { refresh(render); });
      };
    }
    var edits = $("content").querySelectorAll("[data-edit-scene]");
    for (i = 0; i < edits.length; i++) {
      edits[i].onclick = function () {
        var id = this.getAttribute("data-edit-scene");
        var sc = null;
        var n;
        for (n = 0; n < state.scenes.length; n++) if (state.scenes[n].id === id) sc = state.scenes[n];
        openNameDialog("Rename scene", sc ? sc.name : "", function (name) {
          xhr("POST", "/api/scenes", { id: id, name: name }, function () { refresh(render); });
        });
      };
    }
    var dels = $("content").querySelectorAll("[data-del-scene]");
    for (i = 0; i < dels.length; i++) {
      dels[i].onclick = function (ev) {
        if (ev && ev.stopPropagation) ev.stopPropagation();
        var id = this.getAttribute("data-del-scene");
        openConfirm("Delete this scene?", function () {
          xhr("DELETE", "/api/scenes/" + id, null, function () { refresh(render); });
        });
      };
    }
  }

  function formatTrigger(t) {
    if (!t) return "";
    var platform = t.platform || t.type || "";
    if (platform === "time") return "At " + (t.at || t.time || "?");
    if (platform === "state") {
      return (t.entity_id || "entity") + " becomes " + (t.to || "any");
    }
    return JSON.stringify(t);
  }

  function formatAction(actions) {
    if (!actions || !actions.length) return "";
    var a = actions[0];
    return (a.service || a.action || "turn_on") + " " + (a.entity_id || "");
  }

  function renderAutomations() {
    $("pageTitle").textContent = "Automations";
    $("headerAction").innerHTML = icon("plus");
    $("headerAction").onclick = function () { openAutomation(); };
    var html = "";
    var i;
    for (i = 0; i < state.automations.length; i++) {
      var a = state.automations[i];
      html += '<div class="ha-card"><h2>' + escapeHtml(a.name) + '</h2>' +
        '<div class="muted">' + escapeHtml(formatTrigger(a.trigger)) +
        (a.actions ? " · " + escapeHtml(formatAction(a.actions)) : "") + "</div>" +
        '<div class="row-actions">' +
        '<div class="switch' + (a.enabled ? " on" : "") + '" data-auto="' + a.id + '"><i></i></div>' +
        '<button class="btn outline" data-edit-auto="' + a.id + '">Edit</button>' +
        '<button type="button" class="btn outline hit" data-del-auto="' + a.id + '">Delete</button></div></div>';
    }
    if (!html) html = '<div class="ha-card"><div class="empty">Add a time or state trigger. Example: at 07:00 turn on a light, or when a switch turns on, activate a scene.</div></div>';
    $("content").innerHTML = html;
    var sw = $("content").querySelectorAll("[data-auto]");
    for (i = 0; i < sw.length; i++) {
      sw[i].onclick = function () {
        var id = this.getAttribute("data-auto");
        callService("automation", "toggle", "automation." + id, {});
      };
    }
    var edits = $("content").querySelectorAll("[data-edit-auto]");
    for (i = 0; i < edits.length; i++) {
      edits[i].onclick = function () {
        var id = this.getAttribute("data-edit-auto");
        var row = null;
        var n;
        for (n = 0; n < state.automations.length; n++) if (state.automations[n].id === id) row = state.automations[n];
        openAutomation(row);
      };
    }
    var dels = $("content").querySelectorAll("[data-del-auto]");
    for (i = 0; i < dels.length; i++) {
      dels[i].onclick = function (ev) {
        if (ev && ev.stopPropagation) ev.stopPropagation();
        var id = this.getAttribute("data-del-auto");
        openConfirm("Delete this automation?", function () {
          xhr("DELETE", "/api/automations/" + id, null, function () { refresh(render); });
        });
      };
    }
  }

  function renderSettings() {
    $("pageTitle").textContent = "Settings";
    $("headerAction").innerHTML = "";
    $("headerAction").onclick = null;
    xhr("GET", "/api/discovery", null, function (disc) {
      var list = asList(disc);
      if (list.length || !state.discovered.length) state.discovered = list;
      paintSettings();
    });
  }

  function paintSettings() {
      var s = state.settings;
      var html = '<div class="ha-card"><h2>Hub</h2>' +
        '<label class="muted">Sidebar name</label><input class="field" id="hubName" value="' + escapeHtml(s.hub_name || "ha-phone") + '"/>' +
        '<label class="muted">Theme</label><select class="field" id="themeSel">' +
        '<option value="auto"' + (s.theme === "auto" || !s.theme ? " selected" : "") + ">Follow system</option>" +
        '<option value="dark"' + (s.theme === "dark" ? " selected" : "") + ">Dark</option>" +
        '<option value="light"' + (s.theme === "light" ? " selected" : "") + ">Light</option></select>" +
        '<div class="muted">Wi-Fi: ' + escapeHtml(s.ssid || "unknown") + " · " + escapeHtml(s.url || "") + "</div>" +
        '<label class="muted"><input type="checkbox" id="webDash"' + (s.web_dashboard === false ? "" : " checked") + "/> LAN dashboard on port 8123 (restart hub after change)</label>" +
        '<div class="row-actions"><button class="btn" id="saveHub">Save</button></div></div>';
      html += '<div class="ha-card"><h2>Discovery</h2>' +
        '<div class="muted">mDNS, SSDP, Kasa UDP, LIFX, plus a short HTTP probe of the emulator host and LAN gateway. Full subnet sweeps are not done.</div>' +
        '<div class="muted" id="scanStatus">' + escapeHtml(state.scanStatus || "") + "</div>" +
        '<div class="row-actions"><button class="btn" id="scanBtn">Scan network</button></div>';
      var i;
      if (!state.discovered.length && state.scanStatus.indexOf("Nothing") === 0) {
        html += '<div class="empty">Nothing found. Add WLED/Shelly/generic HTTP by IP under Devices.</div>';
      }
      for (i = 0; i < state.discovered.length; i++) {
        var d = state.discovered[i];
        html += '<div class="item"><div>' + escapeHtml(d.name) + '<div class="muted">' + escapeHtml(d.kind + " " + d.host) +
          '</div></div><button class="btn outline" data-add="' + i + '">Add</button></div>';
      }
      html += "</div>";
      html += '<div class="ha-card"><h2>MQTT</h2>' +
        '<input class="field" id="mqttBroker" placeholder="Broker host" value="' + escapeHtml(s.mqtt_broker || "") + '"/>' +
        '<input class="field" id="mqttPort" placeholder="1883" value="' + escapeHtml(String(s.mqtt_port || 1883)) + '"/>' +
        '<div class="row-actions"><button class="btn" id="saveMqtt">Save & reconnect</button></div></div>';
      html += '<div class="ha-card"><h2>LAN PIN</h2>' +
        '<p class="muted">Off by default. With no PIN, anyone on this Wi-Fi can open ' +
        escapeHtml(s.url || "http://PHONE:8123/") + " and control devices (CORS allows any origin). Set a PIN to require it on LAN browsers and REST/WebSocket. The on-phone UI always works.</p>" +
        '<input class="field" id="lanPin" type="password" placeholder="' + (s.lan_pin_set ? "PIN is set — type to change, blank keeps it" : "Leave blank for open LAN") + '"/>' +
        '<div class="row-actions"><button class="btn" id="savePin">Save PIN</button>' +
        (s.lan_pin_set ? '<button class="btn quiet" id="clearPin">Clear PIN</button>' : "") + "</div></div>";
      html += '<div class="ha-card"><h2>Backup</h2>' +
        '<div class="row-actions"><button class="btn outline" id="doBackup">Download backup</button></div>' +
        '<textarea class="field" id="restoreBox" rows="3" placeholder="Paste backup JSON"></textarea>' +
        '<button class="btn" id="doRestore">Restore</button></div>';
      html += '<div class="ha-card"><h2>About</h2>' +
        '<p class="muted">ha-phone is a local hub for this phone. Not affiliated with Home Assistant. No cloud, no account, no Supervisor. Default mark is a generic house — see README to swap artwork.</p></div>';
      $("content").innerHTML = html;
      $("saveHub").onclick = function () {
        xhr("POST", "/api/settings", {
          hub_name: $("hubName").value,
          theme: $("themeSel").value,
          autostart: true,
          web_dashboard: $("webDash").checked
        }, function () { refresh(render); });
      };
      $("scanBtn").onclick = function () {
        runScan();
      };
      $("saveMqtt").onclick = function () {
        xhr("POST", "/api/settings", { mqtt_broker: $("mqttBroker").value, mqtt_port: parseInt($("mqttPort").value, 10) || 1883 }, function () {
          if (window.HaPhone && window.HaPhone.mqttReconnect) window.HaPhone.mqttReconnect();
          refresh(render);
        });
      };
      $("savePin").onclick = function () {
        var pin = $("lanPin").value;
        if (!pin && s.lan_pin_set) { refresh(render); return; }
        xhr("POST", "/api/settings", { lan_pin: pin }, function () { refresh(render); });
      };
      if ($("clearPin")) $("clearPin").onclick = function () {
        xhr("POST", "/api/settings", { lan_pin: "" }, function () { refresh(render); });
      };
      $("doBackup").onclick = function () {
        xhr("GET", "/api/backup", null, function (data) {
          var blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
          var a = document.createElement("a");
          a.href = URL.createObjectURL(blob);
          a.download = "haphone-backup.json";
          a.click();
        });
      };
      $("doRestore").onclick = function () {
        try {
          var json = JSON.parse($("restoreBox").value);
          xhr("POST", "/api/restore", json, function () { refresh(render); });
        } catch (e) { openMessage("Invalid JSON"); }
      };
      var adds = $("content").querySelectorAll("[data-add]");
      for (i = 0; i < adds.length; i++) {
        adds[i].onclick = function () {
          var item = state.discovered[parseInt(this.getAttribute("data-add"), 10)];
          xhr("POST", "/api/discovery/add", item, function () { refresh(render); });
        };
      }
  }

  function runScan() {
    state.scanStatus = "Scanning mDNS, SSDP, Kasa, LIFX and local HTTP…";
    if ($("scanStatus")) $("scanStatus").textContent = state.scanStatus;
    xhr("POST", "/api/discovery/scan", {}, function () {
      if (window.HaPhone && window.HaPhone.scan) window.HaPhone.scan();
      var tries = 0;
      function poll() {
        tries += 1;
        xhr("GET", "/api/discovery", null, function (found) {
          var list = asList(found);
          if (list.length || tries >= 10) {
            state.discovered = list;
            state.scanStatus = list.length
              ? ("Found " + list.length + " device(s).")
              : "Nothing found. Add a device by IP under Devices.";
            paintSettings();
            return;
          }
          setTimeout(poll, 1000);
        });
      }
      setTimeout(poll, 800);
    });
  }

  function openMoreInfo(entityId) {
    var e = null;
    var i;
    for (i = 0; i < state.states.length; i++) {
      if (state.states[i].entity_id === entityId) e = state.states[i];
    }
    if (!e) return;
    var on = isOn(e);
    var d = domainOf(entityId);
    var name = (e.attributes && e.attributes.friendly_name) || entityId;
    var html = '<div class="more-info-head"><div class="big-icon">' + icon(iconFor(e)) + "</div>" +
      "<h3>" + escapeHtml(name) + "</h3><div class='muted'>" + escapeHtml(e.state === "scening" ? "Scene" : e.state) + "</div></div>";
    if (d === "light" || d === "switch" || d === "media_player") {
      html += '<div class="row-actions" style="justify-content:center">' +
        '<button class="btn" id="miOn">On</button><button class="btn outline" id="miOff">Off</button></div>';
    }
    if (d === "light" && e.attributes && e.attributes.brightness != null) {
      html += '<p class="muted">Brightness</p><input id="miBri" type="range" min="1" max="255" value="' + (e.attributes.brightness || 1) + '"/>';
    }
    if (d === "scene") {
      html += '<div class="row-actions" style="justify-content:center"><button class="btn" id="miScene">Activate</button></div>';
    }
    html += '<p class="muted">' + escapeHtml(entityId) + "</p>";
    html += '<button class="icon-btn" id="miClose" style="position:absolute;right:8px;top:8px">' + icon("close") + "</button>";
    openDialog(html);
    $("miClose").onclick = closeDialog;
    if ($("miOn")) $("miOn").onclick = function () { callService(d, "turn_on", entityId, {}); closeDialog(); };
    if ($("miOff")) $("miOff").onclick = function () { callService(d, "turn_off", entityId, {}); closeDialog(); };
    if ($("miBri")) {
      $("miBri").onchange = function () {
        callService("light", "turn_on", entityId, { brightness: parseInt($("miBri").value, 10) });
      };
    }
    if ($("miScene")) $("miScene").onclick = function () { callService("scene", "turn_on", entityId, {}); closeDialog(); };
  }

  function openNameDialog(title, value, onSave) {
    var html = "<h3>" + escapeHtml(title) + "</h3>" +
      '<input class="field" id="dlgName" value="' + escapeHtml(value || "") + '" placeholder="Name"/>' +
      '<div class="error hidden" id="dlgErr">Name is required</div>' +
      '<div class="row-actions"><button class="btn" id="dlgSave">Save</button><button class="btn outline" id="dlgCancel">Cancel</button></div>';
    openDialog(html);
    $("dlgCancel").onclick = closeDialog;
    $("dlgSave").onclick = function () {
      var name = ($("dlgName").value || "").trim();
      if (!name) { $("dlgErr").className = "error"; return; }
      closeDialog();
      onSave(name);
    };
  }

  function openConfirm(message, onYes) {
    var html = "<h3>" + escapeHtml(message) + "</h3>" +
      '<p class="muted">This cannot be undone.</p>' +
      '<button type="button" class="btn danger" id="dlgYes" style="width:100%;margin:8px 0">Delete</button>' +
      '<button type="button" class="btn outline" id="dlgCancel" style="width:100%;margin:8px 0">Cancel</button>';
    openDialog(html);
    $("dlgCancel").onclick = function (ev) { if (ev && ev.stopPropagation) ev.stopPropagation(); closeDialog(); };
    $("dlgYes").onclick = function (ev) { if (ev && ev.stopPropagation) ev.stopPropagation(); closeDialog(); onYes(); };
  }

  function openMessage(message) {
    var html = "<h3>" + escapeHtml(message) + "</h3>" +
      '<div class="row-actions"><button class="btn" id="dlgOk">OK</button></div>';
    openDialog(html);
    $("dlgOk").onclick = closeDialog;
  }

  function extrasFields(kind) {
    if (kind === "GENERIC_HTTP") {
      return '<input class="field" id="adOnUrl" placeholder="On URL (use {host})"/>' +
        '<input class="field" id="adOffUrl" placeholder="Off URL"/>' +
        '<input class="field" id="adStatusUrl" placeholder="Status URL"/>' +
        '<input class="field" id="adOnContains" placeholder="Status text that means on (optional)"/>';
    }
    if (kind === "MQTT") {
      return '<input class="field" id="adCmd" placeholder="Command topic"/>' +
        '<input class="field" id="adState" placeholder="State topic"/>' +
        '<input class="field" id="adOnPay" placeholder="On payload (ON)"/>' +
        '<input class="field" id="adOffPay" placeholder="Off payload (OFF)"/>';
    }
    return "";
  }

  function roomOptions() {
    var html = '<option value="">No room</option>';
    var i;
    for (i = 0; i < state.rooms.length; i++) {
      html += '<option value="' + state.rooms[i].id + '">' + escapeHtml(state.rooms[i].name) + "</option>";
    }
    return html;
  }

  function openAddDevice() {
    var html = "<h3>Add device</h3>" +
      '<label class="muted">Type</label><select class="field" id="adKind">' +
      "<option>WLED</option><option>SHELLY</option><option>TASMOTA</option><option>HUE</option>" +
      "<option>KASA</option><option>LIFX</option><option>ROKU</option><option>SONOS</option>" +
      "<option>ESPHOME</option><option>GENERIC_HTTP</option><option>MQTT</option></select>" +
      '<input class="field" id="adName" placeholder="Name"/>' +
      '<input class="field" id="adHost" placeholder="IP or hostname"/>' +
      '<input class="field" id="adPort" placeholder="Port (80)"/>' +
      '<label class="muted">Room</label><select class="field" id="adRoom">' + roomOptions() + "</select>" +
      '<div id="adExtras"></div>' +
      '<div class="error hidden" id="adErr">Name and host are required</div>' +
      '<div class="row-actions"><button class="btn" id="adSave">Add</button><button class="btn outline" id="adCancel">Cancel</button></div>';
    openDialog(html);
    function syncExtras() { $("adExtras").innerHTML = extrasFields($("adKind").value); }
    $("adKind").onchange = syncExtras;
    syncExtras();
    $("adCancel").onclick = closeDialog;
    $("adSave").onclick = function () {
      var name = ($("adName").value || "").trim();
      var host = ($("adHost").value || "").trim();
      var kind = $("adKind").value;
      if (!name || (kind !== "MQTT" && !host)) {
        $("adErr").className = "error";
        return;
      }
      var extras = {};
      if (kind === "GENERIC_HTTP") {
        extras.onUrl = $("adOnUrl") ? $("adOnUrl").value : "";
        extras.offUrl = $("adOffUrl") ? $("adOffUrl").value : "";
        extras.statusUrl = $("adStatusUrl") ? $("adStatusUrl").value : "";
        extras.onContains = $("adOnContains") ? $("adOnContains").value : "";
        if (!extras.onUrl && !extras.offUrl && !extras.statusUrl) {
          $("adErr").className = "error";
          $("adErr").textContent = "Set at least one on/off/status URL";
          return;
        }
      }
      if (kind === "MQTT") {
        extras.commandTopic = $("adCmd") ? $("adCmd").value : "";
        extras.stateTopic = $("adState") ? $("adState").value : "";
        extras.onPayload = $("adOnPay") ? $("adOnPay").value : "ON";
        extras.offPayload = $("adOffPay") ? $("adOffPay").value : "OFF";
        if (!extras.commandTopic && !extras.stateTopic) {
          $("adErr").className = "error";
          $("adErr").textContent = "Set a command or state topic";
          return;
        }
      }
      xhr("POST", "/api/devices/manual", {
        kind: kind,
        name: name,
        host: host,
        port: parseInt($("adPort").value, 10) || (kind === "MQTT" ? 1883 : 80),
        roomId: $("adRoom").value || null,
        extras: extras
      }, function (_data, status) {
        if (status >= 400) {
          $("adErr").className = "error";
          $("adErr").textContent = (_data && _data.error) || "Could not add";
          return;
        }
        closeDialog();
        refresh(render);
      });
    };
  }

  function openAutomation(existing) {
    var html = "<h3>" + (existing ? "Edit automation" : "New automation") + "</h3>" +
      '<input class="field" id="auName" placeholder="Name" value="' + escapeHtml(existing && existing.name || "") + '"/>' +
      '<label class="muted">Trigger type</label><select class="field" id="auType"><option value="time">Time of day</option><option value="state">Entity state</option></select>' +
      '<input class="field" id="auAt" placeholder="07:00 or entity_id"/>' +
      '<input class="field" id="auTo" placeholder="to state (on) — state triggers only"/>' +
      '<input class="field" id="auTarget" placeholder="Action entity_id (light.xxx)"/>' +
      '<select class="field" id="auSvc"><option>turn_on</option><option>turn_off</option><option>toggle</option></select>' +
      '<div class="error hidden" id="auErr">Name is required</div>' +
      '<div class="row-actions"><button class="btn" id="auSave">Save</button><button class="btn outline" id="auCancel">Cancel</button></div>';
    openDialog(html);
    $("auCancel").onclick = closeDialog;
    $("auSave").onclick = function () {
      var name = ($("auName").value || "").trim();
      if (!name) { $("auErr").className = "error"; return; }
      var type = $("auType").value;
      var trigger = type === "time"
        ? { platform: "time", at: $("auAt").value }
        : { platform: "state", entity_id: $("auAt").value, to: $("auTo").value || "on" };
      var target = $("auTarget").value;
      var domain = target.split(".")[0] || "homeassistant";
      var body = {
        name: name,
        trigger: trigger,
        actions: [{ domain: domain, service: $("auSvc").value, entity_id: target }]
      };
      if (existing && existing.id) body.id = existing.id;
      xhr("POST", "/api/automations", body, function () { closeDialog(); refresh(render); });
    };
  }

  function render() {
    var title = (state.settings && state.settings.hub_name) || "ha-phone";
    $("brandTitle").textContent = title;
    $("brandSub").textContent = "Local hub";
    document.title = title;
    applyTheme();
    renderNav();
    $("menuBtn").innerHTML = icon("menu");
    if (state.page === "overview") renderOverview();
    else if (state.page === "devices") renderDevices();
    else if (state.page === "areas") renderAreas();
    else if (state.page === "scenes") renderScenes();
    else if (state.page === "automations") renderAutomations();
    else renderSettings();
  }

  $("menuBtn").onclick = function () {
    var app = $("app");
    app.className = app.className ? "" : "drawer";
  };
  $("scrim").onclick = function () { $("app").className = ""; };
  $("dialog").onclick = function (ev) { if (ev.target.id === "dialog") closeDialog(); };
  $("dialogCard").onclick = function (ev) { if (ev && ev.stopPropagation) ev.stopPropagation(); };

  refresh(render);
  connectWs();
  setInterval(function () { refresh(function () { if (state.page !== "settings") render(); }); }, 20000);
})();
