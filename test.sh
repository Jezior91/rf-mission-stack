#!/bin/bash
cd "$(dirname "$0")"
python3 -m pytest api/tests/ -v
cd p2p && go test ./... -v
