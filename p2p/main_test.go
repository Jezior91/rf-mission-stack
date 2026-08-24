package main

import (
	"context"
	"encoding/json"
	"strings"
	"testing"
	"time"
)

func TestMessageSerialization(t *testing.T) {
	msg := Message{
		Type:      "ping",
		NodeID:    "test-node-001",
		Timestamp: time.Now().Unix(),
	}
	data, err := json.Marshal(msg)
	if err != nil {
		t.Fatalf("Marshal failed: %v", err)
	}
	var decoded Message
	if err := json.Unmarshal(data, &decoded); err != nil {
		t.Fatalf("Unmarshal failed: %v", err)
	}
	if decoded.Type != "ping" {
		t.Errorf("Expected type=ping, got %s", decoded.Type)
	}
	if decoded.NodeID != "test-node-001" {
		t.Errorf("Expected node_id=test-node-001, got %s", decoded.NodeID)
	}
}

func TestMessageTypes(t *testing.T) {
	validTypes := []string{"ping", "pong", "mission_update", "node_register"}
	for _, msgType := range validTypes {
		msg := Message{Type: msgType, NodeID: "n1", Timestamp: time.Now().Unix()}
		data, err := json.Marshal(msg)
		if err != nil {
			t.Errorf("Marshal failed for type %s: %v", msgType, err)
		}
		if !strings.Contains(string(data), msgType) {
			t.Errorf("Serialized message missing type %s", msgType)
		}
	}
}

func TestNewNodeCreation(t *testing.T) {
	// Test na porcie 0 (system przydziela wolny port)
	node, err := newNode(0)
	if err != nil {
		t.Fatalf("newNode failed: %v", err)
	}
	defer node.host.Close()

	if node.id == "" {
		t.Error("Node ID should not be empty")
	}
	if node.host == nil {
		t.Error("Node host should not be nil")
	}
	addrs := node.host.Addrs()
	if len(addrs) == 0 {
		t.Error("Node should have at least one address")
	}
}

func TestTwoNodesPing(t *testing.T) {
	node1, err := newNode(0)
	if err != nil {
		t.Fatalf("node1 creation failed: %v", err)
	}
	defer node1.host.Close()

	node2, err := newNode(0)
	if err != nil {
		t.Fatalf("node2 creation failed: %v", err)
	}
	defer node2.host.Close()

	// Połącz node1 → node2
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	node2Info := *node2.host.Peerstore().PeerInfo(node2.host.ID())
	node2Info.Addrs = node2.host.Addrs()

	if err := node1.host.Connect(ctx, node2Info); err != nil {
		t.Fatalf("Connect failed: %v", err)
	}

	// Wyślij ping
	err = node1.sendMessage(ctx, node2.host.ID(), Message{Type: "ping"})
	if err != nil {
		t.Fatalf("sendMessage failed: %v", err)
	}
}

func TestProtocolID(t *testing.T) {
	if ProtocolID == "" {
		t.Error("ProtocolID should not be empty")
	}
	if !strings.HasPrefix(string(ProtocolID), "/rfmission/") {
		t.Errorf("ProtocolID should start with /rfmission/, got: %s", ProtocolID)
	}
}
