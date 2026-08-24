"""
Testy jednostkowe: CoT XML Bridge
"""
import sys
sys.path.insert(0, "/tmp/project/api")
sys.path.insert(0, "/tmp/project")

from api.cot import node_to_cot, cot_to_node, mission_to_cot, ETAP_TO_COT_TYPE
import xml.etree.ElementTree as ET
import pytest


SAMPLE_NODE = {
    "id": "node_test_001",
    "name": "Alfa-1",
    "status": "online",
    "role": "Koordynator",
    "ip": "192.168.1.10",
    "lat": 52.2297,
    "lon": 21.0122,
    "last_seen": 1700000000,
}

SAMPLE_MISSION = {
    "id": "m_001",
    "name": "Operacja Tarcza",
    "status": "active",
    "priority": 10,
    "nodes": ["node_test_001", "node_test_002"],
    "created_at": 1700000000,
}


class TestNodeToCot:
    def test_valid_xml(self):
        xml = node_to_cot(SAMPLE_NODE)
        root = ET.fromstring(xml.strip())
        assert root.tag == "event"

    def test_uid_format(self):
        xml = node_to_cot(SAMPLE_NODE)
        root = ET.fromstring(xml.strip())
        assert root.get("uid") == "RFMISSION-node_test_001"

    def test_cot_type_mapping(self):
        xml = node_to_cot(SAMPLE_NODE)
        root = ET.fromstring(xml.strip())
        assert root.get("type") == ETAP_TO_COT_TYPE["Koordynator"]

    def test_point_coordinates(self):
        xml = node_to_cot(SAMPLE_NODE)
        root = ET.fromstring(xml.strip())
        point = root.find("point")
        assert float(point.get("lat")) == pytest.approx(52.2297)
        assert float(point.get("lon")) == pytest.approx(21.0122)

    def test_callsign_in_detail(self):
        xml = node_to_cot(SAMPLE_NODE)
        root = ET.fromstring(xml.strip())
        detail = root.find("detail")
        contact = detail.find("contact")
        assert contact.get("callsign") == "Alfa-1"

    def test_rfmission_extension(self):
        xml = node_to_cot(SAMPLE_NODE)
        root = ET.fromstring(xml.strip())
        rfm = root.find("detail/rfmission")
        assert rfm is not None
        assert rfm.get("etap_role") == "Koordynator"
        assert rfm.get("version") == "6.2"

    def test_unknown_role_fallback(self):
        node = {**SAMPLE_NODE, "role": "unknown_role_xyz"}
        xml = node_to_cot(node)
        root = ET.fromstring(xml.strip())
        # Powinien użyć fallbacku "a-f-G-U-R"
        assert root.get("type") == "a-f-G-U-R"


class TestCotToNode:
    def test_roundtrip(self):
        """Export → Import roundtrip zachowuje kluczowe dane"""
        xml = node_to_cot(SAMPLE_NODE)
        node = cot_to_node(xml)
        assert node is not None
        assert node["name"] == "Alfa-1"
        assert node["role"] == "Koordynator"
        assert abs(node["lat"] - 52.2297) < 0.001
        assert abs(node["lon"] - 21.0122) < 0.001

    def test_invalid_xml(self):
        assert cot_to_node("not xml at all") is None

    def test_wrong_root_tag(self):
        assert cot_to_node("<notanevent/>") is None

    def test_atak_style_cot(self):
        """Parsowanie typowego CoT z ATAK"""
        atak_cot = """<?xml version='1.0' encoding='UTF-8'?>
<event version='2.0' uid='ANDROID-abc123' type='a-f-G-U-C-F'
       time='2024-01-01T12:00:00.000Z' start='2024-01-01T12:00:00.000Z'
       stale='2024-01-01T12:05:00.000Z' how='m-g'>
  <point lat='52.2297' lon='21.0122' hae='100.0' ce='10.0' le='5.0'/>
  <detail>
    <contact callsign='Bravo-2' endpoint='*:-1:stcp'/>
    <uid Droid='Bravo-2'/>
    <remarks>ATAK Android node</remarks>
  </detail>
</event>"""
        node = cot_to_node(atak_cot)
        assert node is not None
        assert node["name"] == "Bravo-2"
        assert node["cot_type"] == "a-f-G-U-C-F"


class TestMissionToCot:
    def test_valid_xml(self):
        xml = mission_to_cot(SAMPLE_MISSION)
        root = ET.fromstring(xml.strip())
        assert root.tag == "event"

    def test_mission_uid(self):
        xml = mission_to_cot(SAMPLE_MISSION)
        root = ET.fromstring(xml.strip())
        assert "m_001" in root.get("uid")

    def test_active_mission_type(self):
        xml = mission_to_cot(SAMPLE_MISSION)
        root = ET.fromstring(xml.strip())
        assert root.get("type") == "a-f-G"

    def test_task_detail(self):
        xml = mission_to_cot(SAMPLE_MISSION)
        root = ET.fromstring(xml.strip())
        task = root.find("detail/task")
        assert task.get("name") == "Operacja Tarcza"
        assert task.get("priority") == "10"


class TestEtapMapping:
    def test_all_roles_have_cot_type(self):
        roles = ["Meta-Wola", "Koordynator", "Wykonawca", "Infiltrator",
                 "Próbobiorca", "Pomiarowiec", "Kombinator", "Obserwator"]
        for role in roles:
            assert role in ETAP_TO_COT_TYPE, f"Brak mapowania CoT dla roli: {role}"

    def test_cot_types_are_valid_format(self):
        for role, cot in ETAP_TO_COT_TYPE.items():
            parts = cot.split("-")
            assert parts[0] == "a", f"CoT type musi zaczynać się od 'a': {cot}"
            assert len(parts) >= 3, f"CoT type za krótki: {cot}"
