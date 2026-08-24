"""
RF Mission Stack — CoT (Cursor-on-Target) XML Bridge
Kompatybilność z ATAK / WinTAK / iTAK / FreeTAKServer

CoT v2.0 spec: https://www.mitre.org/sites/default/files/pdf/09_4937.pdf
"""
import xml.etree.ElementTree as ET
from xml.dom import minidom
from datetime import datetime, timezone
from typing import Optional
import time
import uuid


def ts_cot(unix_ts: float) -> str:
    """Unix timestamp → CoT datetime string (ISO 8601 UTC z timezone suffix)"""
    dt = datetime.fromtimestamp(unix_ts, tz=timezone.utc)
    return dt.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def now_cot() -> str:
    return ts_cot(time.time())


def stale_cot(seconds: int = 300) -> str:
    return ts_cot(time.time() + seconds)


# ── ETAP role → CoT type mapping ─────────────────────────────────────────────
ETAP_TO_COT_TYPE = {
    "Meta-Wola":    "a-f-G-U-C",        # Friendly Ground Unit Commander
    "Koordynator":  "a-f-G-U-C-I",      # Friendly Ground Unit Commander Intel
    "Wykonawca":    "a-f-G-U-C-F",      # Friendly Ground Unit Combat
    "Infiltrator":  "a-f-G-U-I",        # Friendly Ground Intel
    "Próbobiorca":  "a-f-G-U-S",        # Friendly Ground Sensor
    "Pomiarowiec":  "a-f-G-U-S-E",      # Friendly Ground Sensor Electronic
    "Kombinator":   "a-f-G-U-C-I-F",   # Friendly Ground Command Intel Combat
    "Obserwator":   "a-f-G-U-R",        # Friendly Ground Recon
    # Generic fallback
    "observer":     "a-f-G-U-R",
    "operator":     "a-f-G-U-C-F",
    "commander":    "a-f-G-U-C",
}

COT_TYPE_TO_ETAP = {v: k for k, v in ETAP_TO_COT_TYPE.items()}


# ── Export: RF Mission Node → CoT XML ────────────────────────────────────────
def node_to_cot(node: dict) -> str:
    """
    Konwertuje węzeł RF Mission Stack do formatu CoT XML.
    Kompatybilne z ATAK, WinTAK, iTAK, FreeTAKServer.
    """
    uid = f"RFMISSION-{node.get('id', str(uuid.uuid4()))}"
    role = node.get("role", "observer")
    cot_type = ETAP_TO_COT_TYPE.get(role, "a-f-G-U-R")
    last_seen = node.get("last_seen", time.time())

    event = ET.Element("event")
    event.set("version", "2.0")
    event.set("uid", uid)
    event.set("type", cot_type)
    event.set("time", ts_cot(last_seen))
    event.set("start", ts_cot(last_seen))
    event.set("stale", stale_cot(3600))  # 1h stale
    event.set("how", "m-g")  # machine generated

    # Point — GPS (domyślnie 0,0 jeśli brak danych)
    point = ET.SubElement(event, "point")
    point.set("lat", str(node.get("lat", 0.0)))
    point.set("lon", str(node.get("lon", 0.0)))
    point.set("hae", str(node.get("hae", 0.0)))   # Height above ellipsoid
    point.set("ce", "9999999.0")  # Circular error (unknown)
    point.set("le", "9999999.0")  # Linear error (unknown)

    # Detail
    detail = ET.SubElement(event, "detail")

    contact = ET.SubElement(detail, "contact")
    contact.set("callsign", node.get("name", uid))
    if node.get("ip"):
        contact.set("endpoint", f"*:-1:stcp")
        contact.set("hostname", node.get("ip", ""))

    uid_el = ET.SubElement(detail, "uid")
    uid_el.set("Droid", node.get("name", uid))

    # RF Mission Stack extension
    rfm = ET.SubElement(detail, "rfmission")
    rfm.set("version", "6.2")
    rfm.set("node_id", node.get("id", ""))
    rfm.set("status", node.get("status", "offline"))
    rfm.set("etap_role", role)

    # Remarks
    remarks = ET.SubElement(detail, "remarks")
    remarks.text = f"RF Mission Stack v6.2 | role={role} | status={node.get('status','?')}"

    raw = ET.tostring(event, encoding="unicode")
    return minidom.parseString(raw).toprettyxml(indent="  ", newl="\n")


# ── Export: RF Mission → CoT XML ─────────────────────────────────────────────
def mission_to_cot(mission: dict) -> str:
    """Konwertuje misję do CoT XML jako Mission Package."""
    uid = f"RFMISSION-TASK-{mission.get('id', str(uuid.uuid4()))}"
    status_map = {
        "pending":   "a-u-G",   # unknown ground
        "active":    "a-f-G",   # friendly ground
        "completed": "a-n-G",   # neutral ground
        "failed":    "a-h-G",   # hostile ground
    }
    cot_type = status_map.get(mission.get("status", "pending"), "a-u-G")
    ts = mission.get("created_at", time.time())

    event = ET.Element("event")
    event.set("version", "2.0")
    event.set("uid", uid)
    event.set("type", cot_type)
    event.set("time", ts_cot(ts))
    event.set("start", ts_cot(ts))
    event.set("stale", stale_cot(86400))
    event.set("how", "h-g-i-g-o")  # human generated

    point = ET.SubElement(event, "point")
    point.set("lat", "0.0")
    point.set("lon", "0.0")
    point.set("hae", "0.0")
    point.set("ce", "9999999.0")
    point.set("le", "9999999.0")

    detail = ET.SubElement(event, "detail")
    task = ET.SubElement(detail, "task")
    task.set("name", mission.get("name", "Unknown"))
    task.set("status", mission.get("status", "pending"))
    task.set("priority", str(mission.get("priority", 0)))

    rfm = ET.SubElement(detail, "rfmission")
    rfm.set("version", "6.2")
    rfm.set("mission_id", mission.get("id", ""))
    rfm.set("nodes", ",".join(mission.get("nodes", [])))

    remarks = ET.SubElement(detail, "remarks")
    remarks.text = mission.get("name", "RF Mission Stack Task")

    raw = ET.tostring(event, encoding="unicode")
    return minidom.parseString(raw).toprettyxml(indent="  ", newl="\n")


# ── Import: CoT XML → RF Mission Node ────────────────────────────────────────
def cot_to_node(xml_str: str) -> Optional[dict]:
    """
    Parsuje CoT XML (z ATAK/WinTAK/FreeTAKServer) do węzła RF Mission Stack.
    Zwraca None jeśli XML jest nieprawidłowy.
    """
    try:
        root = ET.fromstring(xml_str.strip())
        if root.tag != "event":
            return None

        uid = root.get("uid", "")
        cot_type = root.get("type", "")

        # Parsuj point
        point = root.find("point")
        lat = float(point.get("lat", 0)) if point is not None else 0.0
        lon = float(point.get("lon", 0)) if point is not None else 0.0

        # Parsuj detail
        detail = root.find("detail")
        callsign = uid
        status = "online"
        etap_role = COT_TYPE_TO_ETAP.get(cot_type, "Obserwator")
        ip = None

        if detail is not None:
            contact = detail.find("contact")
            if contact is not None:
                callsign = contact.get("callsign", uid)
                ip = contact.get("hostname")

            uid_el = detail.find("uid")
            if uid_el is not None:
                callsign = uid_el.get("Droid", callsign)

            # RF Mission Stack extension (jeśli CoT pochodzi z RFM)
            rfm = detail.find("rfmission")
            if rfm is not None:
                etap_role = rfm.get("etap_role", etap_role)
                status = rfm.get("status", "online")

        node_id = uid.replace("RFMISSION-", "").lower().replace("-", "_")

        return {
            "id": node_id,
            "name": callsign,
            "status": status,
            "role": etap_role,
            "ip": ip,
            "lat": lat,
            "lon": lon,
            "last_seen": int(time.time()),
            "source": "cot_import",
            "cot_type": cot_type,
        }
    except Exception as e:
        return None
