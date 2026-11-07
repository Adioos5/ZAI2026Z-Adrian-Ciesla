#!/usr/bin/env bash
# One-time preparation of a fresh Ubuntu VM (Azure): Docker Engine + Compose plugin and a swap file.
# Run on the VM:  bash deploy/setup-vm.sh   (then log out and back in so the docker group applies)
set -euo pipefail

if ! command -v docker >/dev/null 2>&1; then
    curl -fsSL https://get.docker.com | sudo sh
fi
sudo usermod -aG docker "$USER"

# Small VMs (1 GB RAM) need swap to build the backend image and run Spring Boot next to PostgreSQL.
if ! swapon --show | grep -q /swapfile; then
    sudo fallocate -l 2G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

echo "Done. Log out and back in, then: cp .env.example .env && nano .env && bash deploy/deploy.sh"
