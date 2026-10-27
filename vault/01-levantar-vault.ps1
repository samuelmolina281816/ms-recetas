# Levanta HashiCorp Vault en modo dev SIN Docker (Windows PowerShell)
# Requisito: vault.exe descargado de https://developer.hashicorp.com/vault/install
param([string]$VaultExe = "C:\vault\vault.exe")

if (-not (Test-Path $VaultExe)) {
    Write-Host "No se encontro $VaultExe" -ForegroundColor Red
    Write-Host "Descarga Vault y vuelve a ejecutar:  .\01-levantar-vault.ps1 -VaultExe 'C:\ruta\vault.exe'"
    exit 1
}
Write-Host "Vault en http://localhost:8200  (token: root)" -ForegroundColor Green
Write-Host "DEJA ESTA VENTANA ABIERTA: es el servidor." -ForegroundColor Yellow
& $VaultExe server -dev -dev-root-token-id=root
