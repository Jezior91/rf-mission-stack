#!/bin/bash
cd "$(dirname "$0")"
docker compose up -d
echo "RF Mission 7.0 OK"
echo "API: http://localhost:8000  Dashboard: http://localhost"
