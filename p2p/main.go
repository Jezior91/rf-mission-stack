package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/libp2p/go-libp2p"
	"github.com/libp2p/go-libp2p/core/host"
	"github.com/libp2p/go-libp2p/core/network"
	"github.com/libp2p/go-libp2p/core/peer"
	"github.com/libp2p/go-libp2p/p2p/discovery/mdns"
	"github.com/multiformats/go-multiaddr"
)

const ProtocolID = "/rfmission/1.0.0"
const DiscoveryTag = "rfmission-network"

type Message struct {
	Type      string          `json:"type"`
	NodeID    string          `json:"node_id"`
	Payload   json.RawMessage `json:"payload"`
	Timestamp int64           `json:"timestamp"`
}

type Node struct {
	host host.Host
	id   string
}

type discoveryNotifee struct {
	h host.Host
}

func (n *discoveryNotifee) HandlePeerFound(pi peer.AddrInfo) {
	log.Printf("[mDNS] Discovered peer: %s", pi.ID)
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := n.h.Connect(ctx, pi); err != nil {
		log.Printf("[mDNS] Connect failed: %v", err)
	} else {
		log.Printf("[mDNS] Connected: %s", pi.ID)
	}
}

func newNode(port int) (*Node, error) {
	h, err := libp2p.New(
		libp2p.ListenAddrStrings(fmt.Sprintf("/ip4/0.0.0.0/tcp/%d", port)),
	)
	if err != nil {
		return nil, err
	}
	h.SetStreamHandler(ProtocolID, handleStream)
	log.Printf("[P2P] Node started: %s", h.ID())
	for _, addr := range h.Addrs() {
		log.Printf("[P2P] Listening: %s/p2p/%s", addr, h.ID())
	}
	return &Node{host: h, id: h.ID().String()}, nil
}

func handleStream(s network.Stream) {
	defer s.Close()
	var msg Message
	dec := json.NewDecoder(s)
	if err := dec.Decode(&msg); err != nil {
		log.Printf("[stream] decode error: %v", err)
		return
	}
	log.Printf("[stream] Received type=%s from=%s", msg.Type, msg.NodeID)
	switch msg.Type {
	case "ping":
		resp := Message{Type: "pong", NodeID: s.Conn().LocalPeer().String(), Timestamp: time.Now().Unix()}
		json.NewEncoder(s).Encode(resp)
	case "mission_update":
		log.Printf("[mission] Update payload: %s", string(msg.Payload))
	default:
		log.Printf("[stream] Unknown message type: %s", msg.Type)
	}
}

func (n *Node) sendMessage(ctx context.Context, peerID peer.ID, msg Message) error {
	s, err := n.host.NewStream(ctx, peerID, ProtocolID)
	if err != nil {
		return err
	}
	defer s.Close()
	msg.NodeID = n.id
	msg.Timestamp = time.Now().Unix()
	return json.NewEncoder(s).Encode(msg)
}

func main() {
	port := flag.Int("port", 9000, "TCP port")
	connectAddr := flag.String("connect", "", "Peer multiaddr to connect to")
	flag.Parse()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	node, err := newNode(*port)
	if err != nil {
		log.Fatalf("Failed to create node: %v", err)
	}
	defer node.host.Close()

	// mDNS discovery
	disc := mdns.NewMdnsService(node.host, DiscoveryTag, &discoveryNotifee{h: node.host})
	if err := disc.Start(); err != nil {
		log.Printf("[mDNS] Failed to start: %v", err)
	}

	// Connect to peer if specified
	if *connectAddr != "" {
		ma, err := multiaddr.NewMultiaddr(*connectAddr)
		if err != nil {
			log.Fatalf("Invalid multiaddr: %v", err)
		}
		pi, err := peer.AddrInfoFromP2pAddr(ma)
		if err != nil {
			log.Fatalf("Invalid peer addr: %v", err)
		}
		if err := node.host.Connect(ctx, *pi); err != nil {
			log.Printf("[connect] Failed: %v", err)
		} else {
			log.Printf("[connect] Connected to %s", pi.ID)
			// Send ping
			node.sendMessage(ctx, pi.ID, Message{Type: "ping"})
		}
	}

	// Export node info to stdout as JSON for API
	info := map[string]interface{}{
		"node_id": node.id,
		"addrs":   node.host.Addrs(),
		"port":    *port,
	}
	enc := json.NewEncoder(os.Stdout)
	enc.Encode(info)

	// Wait for signal
	ch := make(chan os.Signal, 1)
	signal.Notify(ch, syscall.SIGINT, syscall.SIGTERM)
	<-ch
	log.Println("[P2P] Shutting down...")
}
