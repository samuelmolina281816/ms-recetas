# Crea el secreto del taller. Ejecutar en una SEGUNDA ventana.
#param([string]$VaultExe = "C:\vault\vault.exe")
param([string]$VaultExe = "C:\Dev\Software\vault\vault.exe")

$env:VAULT_ADDR  = "http://127.0.0.1:8200"
$env:VAULT_TOKEN = "root"

& $VaultExe kv put secret/clinica/ms-recetas `
    "farmacia.api-key=rx-key-2026-abcd" `
    "farmacia.url=https://api.farmaciared.com" `
    "firma.clave=s3cr3t-firma-2026"

Write-Host "`n--- Secreto creado ---" -ForegroundColor Green
& $VaultExe kv get secret/clinica/ms-recetas
