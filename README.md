# ClinicApp — Taller de HashiCorp Vault (proyecto base)

Proyecto base del **Taller 5: HashiCorp Vault**. Completa los `// TODO` siguiendo el documento del taller.

| Dato | Valor |
|---|---|
| Microservicio | `ms-recetas` · puerto `8080` |
| Secreto en Vault | `secret/clinica/ms-recetas` |
| Validador | `src/main/java/com/nttdata/recetas/validacion/Validador.java` |
| Endpoints | `GET /api/recetas/config` · `POST /api/recetas/firmar` |

## Requisitos
- JDK 17 · IntelliJ IDEA · Postman
- **Vault**: el ejecutable de `developer.hashicorp.com/vault/install` (no requiere instalación)
- Conexión a internet la primera vez (descarga de dependencias)

## 1. Levanta Vault y crea el secreto
Los scripts están en la carpeta `vault/`.

**Windows (PowerShell), en dos ventanas distintas:**
```
.\vault\01-levantar-vault.ps1 -VaultExe "C:\vault\vault.exe"
.\vault\02-crear-secreto.ps1  -VaultExe "C:\vault\vault.exe"
```

También puedes crearlo desde la consola web (**http://localhost:8200**, método Token, valor `root`):
path `clinica/ms-recetas` con estos tres pares:

| Key | Value |
|---|---|
| `farmacia.api-key` | `rx-key-2026-abcd` |
| `farmacia.url` | `https://api.farmaciared.com` |
| `firma.clave` | `s3cr3t-firma-2026` |

> ⚠️ El modo dev guarda en memoria: si reinicias Vault tendrás que volver a crear el secreto.

## 2. Abre el proyecto
**File > Open…** sobre la carpeta `ms-recetas` (la que contiene el `pom.xml`).

## 3. Ciclo de trabajo
```
1. Completas los TODO
2. Ejecutas el Validador  (botón ▶ sobre Validador.java)
3. Repites hasta llegar a 20/20
4. Ejecutas mvn quarkus:dev y pruebas en Postman
```

El Validador **no necesita que la aplicación esté arrancada**, pero **sí que Vault esté corriendo
con el secreto creado**: las 3 primeras verificaciones lo comprueban.

| Criterio | Puntos |
|---|---|
| Vault y configuración | 6 |
| Seguridad | 4 |
| Inyección | 4 |
| Lógica de negocio | 6 |
| **Total** | **20** |

Sin resolver nada obtienes 3/20 (los puntos de haber creado el secreto en Vault).

## 4. Prueba en Postman
Cuando el Validador llegue a 20/20, ejecuta `mvn quarkus:dev` e importa
`ms-recetas.postman_collection.json`.

| # | Petición | Esperado |
|---|---|---|
| 1 | `GET /api/recetas/config` | 200 · `apiKey` = `****abcd` |
| 2 | `POST /api/recetas/firmar` con `RX-001` | 200 · firma `1a45beed02c70f30` |
| 3 | `POST` con `RX-002` | 200 · firma `99feb00014ff424c` |
| 4 | `POST` con código vacío | 400 |

## Problemas frecuentes
| Problema | Solución |
|---|---|
| Las 3 primeras verificaciones fallan | Vault no está corriendo o el secreto no existe. Revisa la sección 1. |
| `Can't read Jandex index version` | Pusiste una `<version>` en la dependencia `quarkus-vault`. Quítala. |
| `SRCFG00014: The config property ... is required` | El nombre del `@ConfigProperty` no coincide con la key del secreto. |
| `Port 8080 was already in use` | Cierra la ejecución anterior o cambia `quarkus.http.port`. |
