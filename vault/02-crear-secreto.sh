#!/usr/bin/env bash
# Crea el secreto del taller (Linux o macOS)
export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=root

vault kv put secret/clinica/ms-recetas \
    farmacia.api-key="rx-key-2026-abcd" \
    farmacia.url="https://api.farmaciared.com" \
    firma.clave="s3cr3t-firma-2026"

echo "--- Secreto creado ---"
vault kv get secret/clinica/ms-recetas
