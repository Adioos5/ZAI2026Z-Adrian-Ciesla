# Deployment on Azure (single VM, Docker Compose)

The whole `docker-compose.yml` stack runs on one Ubuntu VM. Caddy (`--profile prod`) is the only
public entry point: it serves HTTPS with an automatic Let's Encrypt certificate and forwards to the
backend. PostgreSQL, the backend and the sensor emulator listen on the VM's loopback only.

```
internet ──443/80──▶ caddy ──▶ backend:8080 ──▶ db:5432
                                 ▲
                     sensor_server (optional)
```

Cost: free with **Azure for Students** (credit, no card). A `Standard_B1s` / `Standard_B2ats_v2`
VM (1 GB RAM) works thanks to the 2 GB swap file created by `setup-vm.sh`.

## 1. Create the VM (once, from your computer)

With the Azure CLI (`az login` first), or the same settings in the Portal:

```bash
az group create -n zai-rg -l polandcentral

az vm create -g zai-rg -n zai-vm \
  --image Ubuntu2404 --size Standard_B2ats_v2 \
  --admin-username azureuser --generate-ssh-keys \
  --public-ip-sku Standard --public-ip-address-dns-name <unique-label>

az vm open-port -g zai-rg -n zai-vm --port 80,443 --priority 1010
```

The public hostname is then `<unique-label>.polandcentral.cloudapp.azure.com`.
Only ports 22, 80 and 443 are open; do not open 5432, 8000 or 8080.

## 2. Prepare the VM (once)

```bash
ssh azureuser@<unique-label>.polandcentral.cloudapp.azure.com
git clone <repository-url> zai && cd zai
bash deploy/setup-vm.sh        # Docker + swap
exit                           # log in again so the docker group applies
```

## 3. Configure and start

```bash
ssh azureuser@<unique-label>.polandcentral.cloudapp.azure.com
cd zai
cp .env.example .env
nano .env                      # DOMAIN, POSTGRES_PASSWORD, ADMIN_PASSWORD (>= 8 chars)
bash deploy/deploy.sh          # builds, starts with Caddy, waits for https://$DOMAIN/api/health
```

`deploy.sh` refuses to start with the placeholder or default passwords. Run it again after every
`git push` to update the server (it does `git pull` and rebuilds).

## 4. Load the sample data (F11)

From your computer, through the public API:

```bash
python seed_sample_data.py --backend-url https://<unique-label>.polandcentral.cloudapp.azure.com
```

## Operations

| Task | Command (on the VM, in `~/zai`) |
|---|---|
| Status | `docker compose --profile prod ps` |
| Logs | `docker compose --profile prod logs -f backend caddy` |
| Restart after code change | `bash deploy/deploy.sh` |
| Stop (data kept) | `docker compose --profile prod down` |
| Database shell | `docker compose exec db psql -U zai zai` |

Never run `docker compose down -v` on the server: it deletes the database volume, including the
admin account and the sample data.

The containers have `restart: unless-stopped`, so the stack comes back after a VM reboot.
