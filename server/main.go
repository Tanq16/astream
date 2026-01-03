package main

import (
	"embed"
	"log"
	"net/http"
	"sync"

	"github.com/gorilla/websocket"
)

//go:embed index.html
var staticFiles embed.FS

var (
	viewers  = make(map[*websocket.Conn]bool)
	mu       sync.Mutex
	upgrader = websocket.Upgrader{
		CheckOrigin: func(r *http.Request) bool { return true },
	}
)

func main() {
	http.HandleFunc("/", serveHome)
	http.HandleFunc("/publish", handlePublisher)
	http.HandleFunc("/view", handleViewer)
	log.Printf("Starting server on port 8080")
	if err := http.ListenAndServe(":8080", nil); err != nil {
		log.Fatal("Server error:", err)
	}
}

func handlePublisher(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Println("Publisher upgrade failed:", err)
		return
	}
	defer conn.Close()
	log.Printf("Publisher connected: %s", conn.RemoteAddr())
	for {
		messageType, data, err := conn.ReadMessage()
		if err != nil {
			log.Printf("Publisher disconnected: %v", err)
			break
		}
		if messageType == websocket.BinaryMessage {
			broadcast(data)
		}
	}
}

func handleViewer(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Println("Viewer upgrade failed:", err)
		return
	}
	mu.Lock()
	viewers[conn] = true
	mu.Unlock()
	log.Printf("Viewer connected: %s", conn.RemoteAddr())
	for {
		if _, _, err := conn.NextReader(); err != nil {
			break
		}
	}
	mu.Lock()
	delete(viewers, conn)
	mu.Unlock()
	conn.Close()
	log.Printf("Viewer left: %s", conn.RemoteAddr())
}

func serveHome(w http.ResponseWriter, r *http.Request) {
	content, err := staticFiles.ReadFile("index.html")
	if err != nil {
		http.Error(w, "Player not found", 404)
		return
	}
	w.Header().Set("Content-Type", "text/html")
	w.Write(content)
}

func broadcast(data []byte) {
	mu.Lock()
	defer mu.Unlock()
	for conn := range viewers {
		err := conn.WriteMessage(websocket.BinaryMessage, data)
		if err != nil {
			conn.Close()
			delete(viewers, conn)
		}
	}
}
