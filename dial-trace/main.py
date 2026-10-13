import os
import re
from datetime import datetime

import requests
import urllib3
from fastapi import FastAPI, HTTPException
from fastapi.staticfiles import StaticFiles
from dotenv import load_dotenv

load_dotenv()

# ---------------------------------------------------------------------------
# Ayarlar (.env)
# ---------------------------------------------------------------------------
ELK_URL = os.getenv("ELK_URL", "http://localhost:9200").rstrip("/")
ELK_INDEX = os.getenv("ELK_INDEX", "nonturkcell-*")
ELK_VIA_KIBANA = os.getenv("ELK_VIA_KIBANA", "false").lower() == "true"
ELK_USER = os.getenv("ELK_USER", "")
ELK_PASS = os.getenv("ELK_PASS", "")
ELK_API_KEY = os.getenv("ELK_API_KEY", "")
APPLICATION_NAME = os.getenv("APPLICATION_NAME", "Powerdialer")
TIME_WINDOW = os.getenv("TIME_WINDOW", "now-30d")
VERIFY_SSL = os.getenv("ELK_VERIFY_SSL", "false").lower() == "true"
if not VERIFY_SSL:
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

SOURCE_FIELDS = ["@timestamp", "application_name", "logger_name", "thread_name", "level", "message"]

# ---------------------------------------------------------------------------
# Adim tanimlari
# ---------------------------------------------------------------------------
# AMI event'leri: SADECE 'Event received:' iceren satirlarda taninir.
# Bu sayede 'ClientPredictiveAnswer for: Call', 'Hangup for: Call' gibi
# yardimci log satirlari otomatik elenir.
# (kind, kaynak, hedef, temel etiket)
AMI_EVENTS = {
    "VarSetEvent":                 ("PD",  "AST", "Cagri olustu (VarSet)"),
    "OriginateResponseEvent":      ("AST", "PD",  "OriginateResponse"),
    "AniDefinitionEvent":          ("AST", "PD",  "AniDefinition"),
    "ClientPredictiveAnswerEvent": ("C",   "PD",  "Musteri cevapladi"),
    "AgentCalledByDialerEvent":    ("AST", "A",   "Agent araniyor"),
    "AgentPredictiveAnswerEvent":  ("A",   "PD",  "Agent cevapladi"),
    "NewTransferChannelEvent":     ("AST", "PD",  "NewTransferChannel (bridge)"),
    "DialerHangupEvent":           ("AST", "PD",  "DialerHangup"),
}

# AGI dial adimi: ClientPredictiveAnswer sonrasi agent'i baglamak icin
# AGI script Asterisk'e Dial komutu yolluyor. Bu iki log satiri tek ok olur.
AGI_KIND = "AgiDial"
AGI_STEP = ("PD", "AST", "AGI: agent'a dial komutu")
AGI_LOGGER = "DialWithAbandonTimeoutScript"
AGI_MARKERS = ["Executing AGI script for Call", "Sending Dial command for Call"]

# Webphone (REDIS) — hangup sonrasi gelen user result code mesaji.
# Log: "WebphoneSubscriber - Got message: {...\"entityType\":\"RESULT_CODE\"...}" (parentId=interactionId)
WP_KIND = "WebphoneResultCode"
WP_STEP = ("WP", "PD", "Result code")
WP_LOGGER = "WebphoneSubscriber"
_WP_USER_RE = re.compile(r'"userId"\s*:\s*"?(\d+)')

# Etikete eklenecek detaylar (key isimlerini kendi loguna gore daralt/genislet)
EVENT_DETAILS = {
    "VarSetEvent": [
        ("client", ["calledNumber", "clientNumber", "destination", "msisdn", "phone", "number", "dialNumber"]),
        ("from",   ["callerId", "callerid", "ani", "callerIdNum", "fromNumber"]),
    ],
    "OriginateResponseEvent": [("resp", ["response", "reason", "responseCode"])],
    "AniDefinitionEvent": [("ani", ["ani", "callerId", "callerid", "callerIdNum"])],
    "ClientPredictiveAnswerEvent": [("client", ["calledNumber", "clientNumber", "destination", "msisdn", "phone", "number"])],
    "AgentCalledByDialerEvent": [("agent", ["agentId", "userId", "agent", "user", "interface", "peer", "extension"])],
    "AgentPredictiveAnswerEvent": [("agent", ["agentId", "userId", "agent", "user", "interface", "peer", "extension"])],
}

# DialerHangup cause kodu cozumlemesi — pd_telephony_rc tablosundan (code -> (isim, kategori))
CAUSE_KEYS = ["cause", "causeCode", "hangupCause", "causeTxt"]
CODE_MAP = {
    1: ("Unallocated (unassigned) number", "Dial Error"),
    2: ("No route to specified transit network", "Dial Error"),
    3: ("No route to destination", "Dial Error"),
    4: ("Send special information tone", "Dial Error"),
    5: ("Misdialled trunk prefix", "Dial Error"),
    6: ("Channel unacceptable", "Dial Error"),
    7: ("Call awarded, delivered in established channel", "No answer"),
    8: ("Preemption", "User Busy"),
    9: ("Preemption - circuit reserved for reuse", "User Busy"),
    16: ("Normal call clearing", "Answered"),
    17: ("User busy", "User Busy"),
    18: ("No user responding", "No answer"),
    19: ("No answer from user (user alerted)", "No answer"),
    20: ("Subscriber absent", "No answer"),
    21: ("Call rejected", "User Busy"),
    22: ("Number changed", "Wrong Number"),
    23: ("Redirection to new destination", "User Busy"),
    25: ("Exchange routing error", "Dial Error"),
    26: ("Non-selected user clearing", "User Busy"),
    27: ("Destination out of order", "Dial Error"),
    28: ("Invalid number format (address incomplete)", "Wrong Number"),
    29: ("Facility rejected", "Dial Error"),
    30: ("Response to STATUS ENQUIRY", "User Busy"),
    31: ("Normal, unspecified", "Answered"),
    34: ("No circuit/channel available", "Dial Error"),
    38: ("Network out of order", "Dial Error"),
    39: ("Permanent frame mode connection out of service", "Dial Error"),
    40: ("Permanent frame mode connection operational", "Dial Error"),
    41: ("Temporary failure", "Dial Error"),
    42: ("Switching equipment congestion", "Dial Error"),
    43: ("Access information discarded", "Dial Error"),
    44: ("Requested circuit/channel not available", "Dial Error"),
    46: ("Precedence call blocked", "Dial Error"),
    47: ("Resource unavailable, unspecified", "Dial Error"),
    49: ("Quality of service not available", "Dial Error"),
    50: ("Requested facility not subscribed", "Dial Error"),
    53: ("Outgoing calls barred within CUG", "Dial Error"),
    55: ("Incoming calls barred within CUG", "Dial Error"),
    57: ("Bearer capability not authorized", "Dial Error"),
    58: ("Bearer capability not presently available", "Dial Error"),
    62: ("Inconsistency in outgoing access info / subscriber class", "Dial Error"),
    63: ("Service or option not available, unspecified", "Dial Error"),
    65: ("Bearer capability not implemented", "Dial Error"),
    66: ("Channel type not implemented", "Dial Error"),
    69: ("Requested facility not implemented", "Dial Error"),
    70: ("Only restricted digital info bearer capability available", "Dial Error"),
    79: ("Service or option not implemented, unspecified", "Dial Error"),
    81: ("Invalid call reference value", "Dial Error"),
    82: ("Identified channel does not exist", "Dial Error"),
    83: ("Suspended call exists, but call identity does not", "Dial Error"),
    84: ("Call identity in use", "Dial Error"),
    85: ("No call suspended", "Dial Error"),
    86: ("Call with requested call identity cleared", "Dial Error"),
    87: ("User not member of CUG", "Dial Error"),
    88: ("Incompatible destination", "Dial Error"),
    90: ("Non-existent CUG", "Dial Error"),
    91: ("Invalid transit network selection", "Dial Error"),
    95: ("Invalid message, unspecified", "Dial Error"),
    96: ("Mandatory information element is missing", "Dial Error"),
    97: ("Message type non-existent / not implemented", "Dial Error"),
    98: ("Message not compatible with call state", "Dial Error"),
    99: ("Info element/parameter nonexistent / not implemented", "Dial Error"),
    100: ("Invalid information element contents", "Dial Error"),
    101: ("Message not compatible with call state", "Dial Error"),
    102: ("Recovery on timer expiry", "Dial Error"),
    103: ("Parameter non-existent / not implemented", "Dial Error"),
    110: ("Message with unrecognized parameter, discarded", "Dial Error"),
    111: ("Protocol error, unspecified", "Dial Error"),
    127: ("Interworking, unspecified", "Dial Error"),
    0: ("Internal error", "Internal Error"),
    71: ("AMD (answering machine detection)", "Answering Machine"),
    900: ("AMD (answering machine detection)", "Answering Machine"),
    901: ("Call expiration timestamp has passed", "Internal Error"),
    902: ("Campaign stopped forcefully", "Internal Error"),
    903: ("AMI connection loss", "Internal Error"),
    904: ("Filtered by DNC", "Do Not Call"),
    905: ("Blocked by LSC", "LCS Filter"),
    906: ("Abandon timeout exceeded", "Abandoned"),
    907: ("Originate action error", "Internal Error"),
    908: ("LCS Timeout", "Internal Error"),
    909: ("Orphaned call terminated", "Internal Error"),
    910: ("No callerId available", ""),
    911: ("Customer hangup before agent connection", "Abandoned"),
}

# Cevapsiz (musteri acmadi) durumda cause kategorisine gore sonuc etiketi/tonu
OUTCOME_BY_CATEGORY = {
    "User Busy": ("MEŞGUL", "muted"),
    "No answer": ("CEVAPSIZ", "muted"),
    "Wrong Number": ("HATALI NUMARA", "muted"),
    "Dial Error": ("ŞEBEKE HATASI", "err"),
    "Internal Error": ("İÇ HATA", "err"),
    "Do Not Call": ("DNC FİLTRELİ", "muted"),
    "LCS Filter": ("LCS FİLTRELİ", "muted"),
    "Abandoned": ("ABANDONED", "warn"),
    "Answered": ("CEVAPLANMADI", "muted"),
}

# Bacak (agent/client) tespiti
CHANNEL_KEYS = ["channel", "channelName", "peer", "interface", "uniqueid", "uniqueId", "linkedid"]
LEG_HINT_KEYS = ["leg", "legType", "party", "side", "callLeg", "channelType"]

PARTICIPANTS = {"PD": "Powerdialer", "AST": "Asterisk", "C": "Musteri", "A": "Agent"}

_KV_RE = re.compile(r"(\w+)\s*=\s*('[^']*'|\"[^\"]*\"|[^,\]\}\s]+)")
INTERACTION_ID_RE = re.compile(r"dl-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")


# ---------------------------------------------------------------------------
# ELK
# ---------------------------------------------------------------------------
def es_auth_and_headers():
    headers = {"Content-Type": "application/json"}
    auth = None
    if ELK_API_KEY:
        headers["Authorization"] = f"ApiKey {ELK_API_KEY}"
    elif ELK_USER:
        auth = (ELK_USER, ELK_PASS)
    return auth, headers


def build_query(interaction_id: str, gte: str, lt: str = "") -> dict:
    rng = {"gte": gte, "time_zone": "+03:00"}
    if lt:
        rng["lt"] = lt
    return {
        "size": 10000,
        "_source": SOURCE_FIELDS,
        "query": {
            "bool": {
                "must": [
                    {"term": {"application_name": APPLICATION_NAME}},
                    {"range": {"@timestamp": rng}},
                    {"query_string": {"query": f'"{interaction_id}"'}},
                ]
            }
        },
        "sort": [{"@timestamp": {"order": "asc"}}],
    }


def fetch_hits(interaction_id: str, gte: str, index: str, lt: str = "") -> list:
    auth, headers = es_auth_and_headers()
    query = build_query(interaction_id, gte, lt)
    if ELK_VIA_KIBANA:
        url = f"{ELK_URL}/api/console/proxy"
        headers["kbn-xsrf"] = "true"
        params = {"path": f"{index}/_search", "method": "POST"}
        resp = requests.post(url, params=params, json=query, auth=auth,
                             headers=headers, verify=VERIFY_SSL, timeout=30)
    else:
        url = f"{ELK_URL}/{index}/_search"
        resp = requests.post(url, json=query, auth=auth,
                             headers=headers, verify=VERIFY_SSL, timeout=30)
    resp.raise_for_status()
    return resp.json().get("hits", {}).get("hits", [])


# ---------------------------------------------------------------------------
# Donusum
# ---------------------------------------------------------------------------
def parse_hits(hits: list) -> list:
    rows = []
    for h in hits:
        src = h.get("_source", {})
        rows.append({
            "ts": src.get("@timestamp", ""),
            "level": src.get("level", ""),
            "logger": src.get("logger_name", ""),
            "message": src.get("message", ""),
        })
    return rows


def classify(message: str, logger: str):
    msg = message or ""
    # 1) AMI event: sadece 'Event received:' satirlari
    if "Event received:" in msg:
        for ev in AMI_EVENTS:
            if ev in msg:
                return ev
        return None
    # 2) AGI dial
    if AGI_LOGGER in (logger or "") or AGI_LOGGER in msg:
        for mk in AGI_MARKERS:
            if mk in msg:
                return AGI_KIND
    # 3) Webphone result code (REDIS) — hangup sonrasi
    if WP_LOGGER in (logger or "") or WP_LOGGER in msg:
        if "Got message:" in msg and "RESULT_CODE" in msg:
            return WP_KIND
    return None


def parse_kv(text: str) -> dict:
    pairs = {}
    for m in _KV_RE.finditer(text or ""):
        k, v = m.group(1), m.group(2).strip("'\"")
        if k not in pairs:
            pairs[k] = v
    return pairs


def safe_label(s: str) -> str:
    return (s or "").replace("\n", " ").replace("\r", " ").replace(";", ",").replace(":", ".").strip()


def detail_str(kind: str, kv: dict) -> str:
    out = []
    for label, keys in EVENT_DETAILS.get(kind, []):
        for k in keys:
            if kv.get(k):
                out.append(f"{label}={safe_label(kv[k])}")
                break
    return " ".join(out)


def first_channel(kv: dict) -> str:
    for k in CHANNEL_KEYS:
        if kv.get(k):
            return kv[k]
    return ""


def _cause_code(kv: dict):
    for k in CAUSE_KEYS:
        if kv.get(k):
            try:
                return int(safe_label(kv[k]))
            except ValueError:
                return None
    return None


def cause_str(kv: dict) -> str:
    code = _cause_code(kv)
    if code is None:
        for k in CAUSE_KEYS:
            if kv.get(k):
                return f"cause={safe_label(kv[k])}"
        return ""
    info = CODE_MAP.get(code)
    return f"cause={code} ({info[0]})" if info else f"cause={code}"


def cause_category(code) -> str:
    info = CODE_MAP.get(code)
    return info[1] if info else ""


def parse_dt(ts: str):
    if not ts:
        return None
    try:
        return datetime.fromisoformat(ts.replace("Z", "+00:00"))
    except ValueError:
        return None


def fmt_delta(sec) -> str:
    if sec is None or sec < 0:
        return ""
    if sec < 1:
        return f"+{int(sec * 1000)}ms"
    if sec < 60:
        return f"+{sec:.1f}s"
    m, s = divmod(int(sec), 60)
    return f"+{m}m{s}s"


def human_dur(sec) -> str:
    if sec is None:
        return "-"
    if sec < 1:
        return f"{int(sec * 1000)} ms"
    if sec < 60:
        return f"{sec:.1f} sn"
    m, s = divmod(int(round(sec)), 60)
    return f"{m} dk {s} sn"


def short_time(ts: str) -> str:
    return ts[11:19].replace(":", ".") if len(ts) >= 19 else ts


def build_steps(rows: list) -> list:
    # 1) siniflandir
    raw = []
    for r in rows:
        kind = classify(r["message"], r["logger"])
        if not kind:
            continue
        raw.append({**r, "kind": kind, "kv": parse_kv(r["message"])})

    # 2) ardisik AGI satirlarini tek adima birlestir
    merged = []
    for s in raw:
        if s["kind"] == AGI_KIND and merged and merged[-1]["kind"] == AGI_KIND:
            merged[-1]["message"] += "\n" + s["message"]
            continue
        merged.append(s)

    # 3) bacak tespiti icin kanallari topla (hangup oncesi tum adimlardan)
    agent_ch, client_ch = set(), set()
    for s in merged:
        ch = first_channel(s["kv"])
        if not ch:
            continue
        if s["kind"] in ("AgentCalledByDialerEvent", "AgentPredictiveAnswerEvent"):
            agent_ch.add(ch)
        elif s["kind"] in ("VarSetEvent", "OriginateResponseEvent", "AniDefinitionEvent", "ClientPredictiveAnswerEvent"):
            client_ch.add(ch)

    def explicit_leg(kv):
        for k in LEG_HINT_KEYS:
            v = (kv.get(k) or "").lower()
            if "agent" in v:
                return "agent bacagi"
            if "client" in v or "customer" in v or "cust" in v:
                return "client bacagi"
        return ""

    def leg_of(s):
        leg = explicit_leg(s["kv"])
        if leg:
            return leg
        ch = first_channel(s["kv"])
        if ch:
            for a in agent_ch:
                if a and (a in ch or ch in a):
                    return "agent bacagi"
            for c in client_ch:
                if c and (c in ch or ch in c):
                    return "client bacagi"
        return "? bacak"

    # 4) adim listesi + etiket
    steps = []
    for s in merged:
        kind = s["kind"]
        leg = ""
        if kind == AGI_KIND:
            a, b, base = AGI_STEP
            extra = ""
        elif kind == WP_KIND:
            a, b, base = WP_STEP
            m = _WP_USER_RE.search(s["message"])
            extra = f"user={m.group(1)}" if m else ""
        else:
            a, b, base = AMI_EVENTS[kind]
            extra = detail_str(kind, s["kv"])
        if kind == "DialerHangupEvent":
            leg = leg_of(s)
            extra = f"{cause_str(s['kv'])} ({leg})".strip()
        steps.append({
            "n": len(steps) + 1,
            "kind": kind,
            "ts": s["ts"],
            "level": s["level"],
            "logger": s["logger"],
            "message": s["message"],
            "leg": leg,
            "src": a, "dst": b,
            "label": f"{base} {extra}".strip(),
        })

    # 5) adimlar arasi sure (delta)
    prev = None
    for s in steps:
        cur = parse_dt(s["ts"])
        s["delta_s"] = (cur - prev).total_seconds() if (cur and prev) else None
        prev = cur or prev

    return steps


def build_mermaid(steps: list) -> str:
    lines = ["sequenceDiagram", "    autonumber"]
    for code, label in PARTICIPANTS.items():
        lines.append(f"    participant {code} as {label}")
    if any(s["kind"] == WP_KIND for s in steps):
        lines.append("    participant WP as Webphone")
    if not steps:
        lines.append("    Note over PD: Bu interaction id icin eslesen event bulunamadi")
        return "\n".join(lines)
    for s in steps:
        d = fmt_delta(s.get("delta_s"))
        tlabel = short_time(s["ts"]) + (f" {d}" if d else "")
        lines.append(f"    {s['src']}->>{s['dst']}: {s['label']} [{tlabel}]")
    return "\n".join(lines)


def _first_ts(steps, kind):
    for s in steps:
        if s["kind"] == kind:
            return parse_dt(s["ts"])
    return None


# Tam bagli bir cagrinin beklenen kanonik akisi (hangup haric, o terminator)
CANONICAL_FLOW = [
    ("VarSetEvent", "Çağrı oluştu"),
    ("AniDefinitionEvent", "ANI"),
    ("ClientPredictiveAnswerEvent", "Müşteri cevap"),
    ("AgiDial", "AGI dial"),
    ("AgentCalledByDialerEvent", "Agent çağrıldı"),
    ("AgentPredictiveAnswerEvent", "Agent cevap"),
]


def build_flowcheck(steps: list) -> dict:
    kinds = {s["kind"] for s in steps}
    present = [k in kinds for k, _ in CANONICAL_FLOW]
    furthest = max((i for i, p in enumerate(present) if p), default=-1)

    items = []
    for i, (k, label) in enumerate(CANONICAL_FLOW):
        if present[i]:
            st = "ok"
        elif i < furthest:
            st = "gap"          # ortada atlanmis adim = anomali
        elif i == furthest + 1:
            st = "stopped"      # akis burada durdu
        else:
            st = "skip"         # bu noktaya hic ulasilmadi
        items.append({"label": label, "status": st})

    has_hangup = "DialerHangupEvent" in kinds
    items.append({"label": "Hangup", "status": "ok" if has_hangup else "gap"})

    gaps = [CANONICAL_FLOW[i][1] for i in range(len(CANONICAL_FLOW)) if items[i]["status"] == "gap"]
    if gaps:
        note = "Akışta atlanan adım: " + ", ".join(gaps)
    elif not has_hangup:
        note = "Hangup görünmüyor (çağrı düzgün kapanmamış olabilir)"
    else:
        stopped = [it["label"] for it in items if it["status"] == "stopped"]
        note = ("Akış '" + stopped[0] + "' adımına ulaşmadan durdu") if stopped else "Tam akış"

    return {"items": items, "note": note}


def build_summary(steps: list, err_count: int) -> dict:
    if not steps:
        return {"outcome": None, "metrics": []}

    t0 = parse_dt(steps[0]["ts"])
    tlast = parse_dt(steps[-1]["ts"])
    t_client = _first_ts(steps, "ClientPredictiveAnswerEvent")
    t_agent = _first_ts(steps, "AgentPredictiveAnswerEvent")
    t_hangup = _first_ts(steps, "DialerHangupEvent")

    def secs(a, b):
        return (b - a).total_seconds() if (a and b) else None

    metrics = []

    def add(label, a, b):
        v = secs(a, b)
        if v is not None and v >= 0:
            metrics.append({"label": label, "value": human_dur(v)})

    add("Musteri cevaplama", t0, t_client)
    add("Agent baglanma", t_client, t_agent)
    add("Konusma", t_agent, t_hangup)
    add("Toplam", t0, tlast)
    if err_count:
        metrics.append({"label": "Hata satiri", "value": str(err_count)})

    has_client = t_client is not None
    has_agent = t_agent is not None

    # ilk DialerHangup'in cause kategorisi
    hangup_cat = ""
    for s in steps:
        if s["kind"] == "DialerHangupEvent":
            c = _cause_code(parse_kv(s["message"]))
            hangup_cat = cause_category(c) if c is not None else ""
            break

    if hangup_cat == "Answering Machine":
        status, tone = "TELESEKRETER (AMD)", "warn"
    elif has_client and has_agent:
        status, tone = "BAĞLANDI", "ok"
    elif has_client and not has_agent:
        status, tone = "ABANDONED", "warn"
    else:
        status, tone = OUTCOME_BY_CATEGORY.get(hangup_cat, ("CEVAPLANMADI", "muted"))

    # ilk kapatan bacak
    detail = ""
    for s in steps:
        if s["kind"] == "DialerHangupEvent" and s.get("leg"):
            detail = f"ilk kapatan: {s['leg']}"
            break

    return {"outcome": {"status": status, "tone": tone, "detail": detail}, "metrics": metrics}


# ---------------------------------------------------------------------------
# API
# ---------------------------------------------------------------------------
app = FastAPI(title="Powerdialer AMI Trace")


@app.get("/api/trace/{interaction_id}")
def trace(interaction_id: str, window: str = TIME_WINDOW, lt: str = "", index: str = ELK_INDEX):
    try:
        hits = fetch_hits(interaction_id, window, index, lt)
    except requests.RequestException as e:
        raise HTTPException(status_code=502, detail=f"ELK sorgu hatasi: {e}")

    rows = parse_hits(hits)
    steps = build_steps(rows)
    err_count = sum(1 for r in rows if (r["level"] or "").upper() == "ERROR")
    summary = build_summary(steps, err_count)
    flow = build_flowcheck(steps)

    return {
        "interaction_id": interaction_id,
        "index": index,
        "window": window,
        "count": len(hits),
        "mermaid": build_mermaid(steps),
        "outcome": summary["outcome"],
        "metrics": summary["metrics"],
        "flow": flow,
        "steps": [
            {"n": s["n"], "ts": s["ts"], "event": s["kind"],
             "level": s["level"], "logger": s["logger"], "message": s["message"]}
            for s in steps
        ],
        "events": [
            {"ts": r["ts"], "level": r["level"], "logger": r["logger"],
             "event": classify(r["message"], r["logger"]) or "-",
             "message": r["message"]}
            for r in rows
        ],
    }


@app.get("/api/search")
def search(q: str, window: str = TIME_WINDOW, lt: str = "", index: str = ELK_INDEX):
    term = q.strip()
    if not term:
        raise HTTPException(status_code=400, detail="Arama terimi bos")
    try:
        hits = fetch_hits(term, window, index, lt)  # query_string = "term"
    except requests.RequestException as e:
        raise HTTPException(status_code=502, detail=f"ELK sorgu hatasi: {e}")

    groups = {}
    for h in hits:
        src = h.get("_source", {})
        ts = src.get("@timestamp", "")
        for iid in set(INTERACTION_ID_RE.findall(src.get("message", ""))):
            g = groups.get(iid)
            if not g:
                g = {"interaction_id": iid, "count": 0, "first_ts": ts, "last_ts": ts}
                groups[iid] = g
            g["count"] += 1
            if ts and (not g["first_ts"] or ts < g["first_ts"]):
                g["first_ts"] = ts
            if ts and ts > g["last_ts"]:
                g["last_ts"] = ts

    results = sorted(groups.values(), key=lambda x: x["last_ts"], reverse=True)
    return {"q": term, "index": index, "total_hits": len(hits), "results": results}


BASE_DIR = os.path.dirname(os.path.abspath(__file__))
app.mount("/", StaticFiles(directory=os.path.join(BASE_DIR, "static"), html=True), name="static")