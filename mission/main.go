package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/go-redis/redis/v8"
	"github.com/gorilla/mux"
)

type Mission struct {
	ID          string            `json:"id"`
	Name        string            `json:"name"`
	Status      string            `json:"status"` // pending|active|completed|failed
	Priority    int               `json:"priority"`
	Nodes       []string          `json:"nodes"`
	Metadata    map[string]string `json:"metadata"`
	CreatedAt   int64             `json:"created_at"`
	UpdatedAt   int64             `json:"updated_at"`
}

type Engine struct {
	rdb *redis.Client
}

func newEngine() *Engine {
	addr := os.Getenv("REDIS_URL")
	if addr == "" {
		addr = "localhost:6379"
	}
	rdb := redis.NewClient(&redis.Options{
		Addr:     addr,
		Password: os.Getenv("REDIS_PASSWORD"),
	})
	return &Engine{rdb: rdb}
}

func (e *Engine) saveMission(ctx context.Context, m *Mission) error {
	m.UpdatedAt = time.Now().Unix()
	data, _ := json.Marshal(m)
	pipe := e.rdb.Pipeline()
	pipe.Set(ctx, "mission:"+m.ID, data, 0)
	pipe.ZAdd(ctx, "missions:by_priority", &redis.Z{Score: float64(m.Priority), Member: m.ID})
	pipe.Publish(ctx, "mission:updates", string(data))
	_, err := pipe.Exec(ctx)
	return err
}

func (e *Engine) getMission(ctx context.Context, id string) (*Mission, error) {
	data, err := e.rdb.Get(ctx, "mission:"+id).Bytes()
	if err != nil {
		return nil, err
	}
	var m Mission
	json.Unmarshal(data, &m)
	return &m, nil
}

func (e *Engine) listMissions(ctx context.Context) ([]*Mission, error) {
	ids, err := e.rdb.ZRevRange(ctx, "missions:by_priority", 0, -1).Result()
	if err != nil {
		return nil, err
	}
	missions := make([]*Mission, 0, len(ids))
	for _, id := range ids {
		m, err := e.getMission(ctx, id)
		if err == nil {
			missions = append(missions, m)
		}
	}
	return missions, nil
}

func (e *Engine) handleCreate(w http.ResponseWriter, r *http.Request) {
	var m Mission
	if err := json.NewDecoder(r.Body).Decode(&m); err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	if m.ID == "" {
		m.ID = fmt.Sprintf("m_%d", time.Now().UnixNano())
	}
	m.Status = "pending"
	m.CreatedAt = time.Now().Unix()
	if err := e.saveMission(r.Context(), &m); err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(m)
}

func (e *Engine) handleGet(w http.ResponseWriter, r *http.Request) {
	id := mux.Vars(r)["id"]
	m, err := e.getMission(r.Context(), id)
	if err != nil {
		http.Error(w, "Not found", 404)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(m)
}

func (e *Engine) handleList(w http.ResponseWriter, r *http.Request) {
	missions, err := e.listMissions(r.Context())
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(missions)
}

func (e *Engine) handleUpdateStatus(w http.ResponseWriter, r *http.Request) {
	id := mux.Vars(r)["id"]
	var body struct{ Status string `json:"status"` }
	json.NewDecoder(r.Body).Decode(&body)
	m, err := e.getMission(r.Context(), id)
	if err != nil {
		http.Error(w, "Not found", 404)
		return
	}
	validStatuses := map[string]bool{"pending": true, "active": true, "completed": true, "failed": true}
	if !validStatuses[body.Status] {
		http.Error(w, "Invalid status", 400)
		return
	}
	m.Status = body.Status
	e.saveMission(r.Context(), m)
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(m)
}

func (e *Engine) handleHealth(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()
	if err := e.rdb.Ping(ctx).Err(); err != nil {
		http.Error(w, `{"status":"unhealthy","redis":"down"}`, 503)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	fmt.Fprintf(w, `{"status":"healthy","ts":%d}`, time.Now().Unix())
}

func main() {
	engine := newEngine()
	router := mux.NewRouter()
	router.HandleFunc("/health", engine.handleHealth).Methods("GET")
	router.HandleFunc("/missions", engine.handleList).Methods("GET")
	router.HandleFunc("/missions", engine.handleCreate).Methods("POST")
	router.HandleFunc("/missions/{id}", engine.handleGet).Methods("GET")
	router.HandleFunc("/missions/{id}/status", engine.handleUpdateStatus).Methods("PATCH")

	port := os.Getenv("MISSION_PORT")
	if port == "" { port = "8081" }
	log.Printf("[Mission Engine] Listening on :%s", port)
	log.Fatal(http.ListenAndServe(":"+port, router))
}
