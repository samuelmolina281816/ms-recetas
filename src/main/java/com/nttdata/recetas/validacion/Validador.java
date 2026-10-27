package com.nttdata.recetas.validacion;

import com.nttdata.recetas.FirmaService;
import com.nttdata.recetas.RecetaResource;
import com.nttdata.recetas.model.ConfigResponse;
import com.nttdata.recetas.model.FirmaRequest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Verificaciones automaticas del taller: 20 puntos. (Resuelto: no modificar)
 *
 * Comprueba cuatro cosas:
 *   A. Que Vault este levantado y el secreto creado con sus 3 keys.
 *   B. Que ningun valor sensible este en el codigo ni en application.properties.
 *   C. Que la inyeccion use los nombres exactos de las keys.
 *   D. Que la logica de negocio sea correcta.
 *
 * Ejecutalo con el boton Play. No hace falta que la aplicacion este arrancada,
 * pero SI que Vault este corriendo con el secreto creado.
 */
public class Validador {

    private static final String VAULT = "http://localhost:8200";
    private static final String TOKEN = "root";
    private static final String RUTA_SECRETO = "clinica/ms-recetas";

    // Valores que NUNCA deben aparecer en el codigo fuente
    private static final String API_KEY = "rx-key-2026-abcd";
    private static final String FIRMA_CLAVE = "s3cr3t-firma-2026";
    private static final String FARMACIA_URL = "https://api.farmaciared.com";

    private static final Map<String, int[]> RESUMEN = new LinkedHashMap<>();
    private static int numero = 0;

    @FunctionalInterface
    interface Prueba {
        boolean ejecutar() throws Exception;
    }

    public static void main(String[] args) {
        System.out.println("ClinicApp - Validacion automatica del taller HashiCorp Vault");
        System.out.println("===========================================================");

        String json = leerSecretoDeVault();

        // ================= A. Vault y configuracion (6) =================
        verificar("Vault y configuracion", "Vault responde en " + VAULT,
                () -> json != null);

        verificar("Vault y configuracion", "El secreto existe en secret/" + RUTA_SECRETO,
                () -> json != null && json.contains("\"data\""));

        verificar("Vault y configuracion", "El secreto tiene las 3 keys esperadas",
                () -> json != null
                        && json.contains("farmacia.api-key")
                        && json.contains("farmacia.url")
                        && json.contains("firma.clave"));

        verificar("Vault y configuracion", "application.properties define quarkus.vault.url",
                () -> {
                    String v = propiedad("quarkus.vault.url");
                    return v != null && v.contains("8200");
                });

        verificar("Vault y configuracion", "secret-config-kv-path apunta a " + RUTA_SECRETO + " (RN-06)",
                () -> RUTA_SECRETO.equals(propiedad("quarkus.vault.secret-config-kv-path")));

        verificar("Vault y configuracion", "El pom declara quarkus-vault sin version fijada",
                () -> {
                    String pom = texto("pom.xml");
                    if (pom == null || !pom.contains("quarkus-vault")) return false;
                    int i = pom.indexOf("quarkus-vault");
                    int fin = pom.indexOf("</dependency>", i);
                    return fin > 0 && !pom.substring(i, fin).contains("<version>");
                });

        // ================= B. Seguridad (4) =================
        verificar("Seguridad", "Los secretos se inyectan y ningun .java guarda su valor (RN-02)",
                () -> {
                    List<String> malos = fuentesConSecretos();
                    if (!malos.isEmpty()) System.out.println("          archivos: " + malos);
                    long inyectados = Arrays.stream(RecetaResource.class.getDeclaredFields())
                            .filter(f -> f.getAnnotation(ConfigProperty.class) != null).count();
                    return malos.isEmpty() && inyectados == 3;
                });

        verificar("Seguridad", "application.properties configura Vault y no guarda ningun valor",
                () -> {
                    String p = texto("src/main/resources/application.properties");
                    if (p == null || p.contains(API_KEY) || p.contains(FIRMA_CLAVE)) return false;
                    return propiedad("quarkus.vault.authentication.client-token") != null
                            && propiedad("quarkus.vault.secret-config-kv-path") != null;
                });

        verificar("Seguridad", "config() enmascara la api-key: nunca la devuelve completa (RN-03)",
                () -> {
                    ConfigResponse r = recurso().config();
                    return r.apiKey() != null && !r.apiKey().contains(API_KEY) && r.apiKey().startsWith("****");
                });

        verificar("Seguridad", "La respuesta nunca incluye la clave de firma",
                () -> {
                    ConfigResponse r = recurso().config();
                    String todo = String.valueOf(r);
                    return !todo.contains(FIRMA_CLAVE);
                });

        // ================= C. Inyeccion (4) =================
        verificar("Inyeccion", "RecetaResource expone la ruta /api/recetas",
                () -> {
                    Path p = RecetaResource.class.getAnnotation(Path.class);
                    return p != null && p.value().equals("/api/recetas");
                });

        verificar("Inyeccion", "Los 3 @ConfigProperty usan las keys exactas del secreto (RN-01)",
                () -> {
                    List<String> nombres = new ArrayList<>();
                    for (Field f : RecetaResource.class.getDeclaredFields()) {
                        ConfigProperty cp = f.getAnnotation(ConfigProperty.class);
                        if (cp != null) nombres.add(cp.name());
                    }
                    return nombres.containsAll(List.of("farmacia.api-key", "farmacia.url", "firma.clave"));
                });

        verificar("Inyeccion", "config() es @GET en /config y produce JSON",
                () -> {
                    Method m = RecetaResource.class.getMethod("config");
                    Path p = m.getAnnotation(Path.class);
                    Produces pr = m.getAnnotation(Produces.class);
                    return m.isAnnotationPresent(GET.class) && p != null && p.value().equals("/config")
                            && pr != null && pr.value()[0].contains("json");
                });

        verificar("Inyeccion", "firmar() es @POST en /firmar y produce JSON",
                () -> {
                    Method m = RecetaResource.class.getMethod("firmar", FirmaRequest.class);
                    Path p = m.getAnnotation(Path.class);
                    Produces pr = m.getAnnotation(Produces.class);
                    return m.isAnnotationPresent(POST.class) && p != null && p.value().equals("/firmar")
                            && pr != null && pr.value()[0].contains("json");
                });

        // ================= D. Logica de negocio (6) =================
        verificar("Logica de negocio", "config() devuelve la URL de la farmacia",
                () -> FARMACIA_URL.equals(recurso().config().farmaciaUrl()));

        verificar("Logica de negocio", "El enmascarado deja **** y los ultimos 4 caracteres (RN-03)",
                () -> "****abcd".equals(recurso().config().apiKey()));

        verificar("Logica de negocio", "config() indica que la firma esta configurada",
                () -> recurso().config().firmaConfigurada());

        verificar("Logica de negocio", "firmar() calcula el SHA-256 correcto para RX-001 (RN-04)",
                () -> {
                    Object r = recurso().firmar(new FirmaRequest("RX-001", "Ana Torres"));
                    Object cuerpo = entidad(r);
                    return estado(r) == 200 && "1a45beed02c70f30".equals(leer(cuerpo, "firma"));
                });

        verificar("Logica de negocio", "firmar() con codigo vacio devuelve 400 (RN-05)",
                () -> estado(recurso().firmar(new FirmaRequest("   ", "Ana Torres"))) == 400);

        verificar("Logica de negocio", "Dos recetas distintas producen firmas distintas",
                () -> {
                    RecetaResource r = recurso();
                    String f1 = String.valueOf(leer(entidad(r.firmar(new FirmaRequest("RX-002", "A"))), "firma"));
                    String f2 = String.valueOf(leer(entidad(r.firmar(new FirmaRequest("RX-777", "B"))), "firma"));
                    return "99feb00014ff424c".equals(f1) && "c1bf386deee12eea".equals(f2);
                });

        // ================= Resumen =================
        System.out.println();
        System.out.println("Resumen por criterio");
        System.out.println("--------------------");
        int total = 0;
        for (Map.Entry<String, int[]> e : RESUMEN.entrySet()) {
            System.out.printf("%-26s %d / %d%n", e.getKey(), e.getValue()[0], e.getValue()[1]);
            total += e.getValue()[0];
        }
        System.out.println("--------------------");
        System.out.printf("PUNTAJE AUTOMATICO: %d / 20%n", total);
        if (json == null) {
            System.out.println();
            System.out.println("AVISO: no se pudo leer el secreto desde Vault.");
            System.out.println("Levanta Vault y crea el secreto secret/" + RUTA_SECRETO + " antes de validar.");
        }
    }

    // ------------------------- utilidades -------------------------

    /** Construye el recurso inyectando los secretos a mano (no hace falta arrancar Quarkus). */
    private static RecetaResource recurso() throws Exception {
        RecetaResource r = new RecetaResource(new FirmaService());
        asignar(r, "apiKey", API_KEY);
        asignar(r, "farmaciaUrl", FARMACIA_URL);
        asignar(r, "firmaClave", FIRMA_CLAVE);
        return r;
    }

    private static void asignar(Object destino, String campo, String valor) throws Exception {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }

    /** Lee el secreto desde la API de Vault, sin dependencias externas. */
    private static String leerSecretoDeVault() {
        try {
            URI uri = URI.create(VAULT + "/v1/secret/data/" + RUTA_SECRETO);
            HttpURLConnection con = (HttpURLConnection) uri.toURL().openConnection();
            con.setRequestProperty("X-Vault-Token", TOKEN);
            con.setConnectTimeout(2500);
            con.setReadTimeout(2500);
            if (con.getResponseCode() != 200) return null;
            try (InputStream in = con.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String texto(String ruta) {
        try {
            return Files.readString(Paths.get(ruta));
        } catch (Exception e) {
            return null;
        }
    }

    private static String propiedad(String clave) throws Exception {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(Paths.get("src/main/resources/application.properties"))) {
            p.load(in);
        }
        String v = p.getProperty(clave);
        return v == null ? null : v.trim();
    }

    /** Busca valores de secretos escritos a mano en el codigo fuente. */
    private static List<String> fuentesConSecretos() throws Exception {
        List<String> malos = new ArrayList<>();
        try (Stream<java.nio.file.Path> s = Files.walk(Paths.get("src/main/java"))) {
            for (java.nio.file.Path p : s.filter(x -> x.toString().endsWith(".java")).toList()) {
                if (p.toString().contains("validacion")) continue;   // este archivo los declara a proposito
                String c = Files.readString(p);
                if (c.contains(API_KEY) || c.contains(FIRMA_CLAVE)) malos.add(p.getFileName().toString());
            }
        }
        return malos;
    }

    private static int estado(Object response) throws Exception {
        return (int) response.getClass().getMethod("getStatus").invoke(response);
    }

    private static Object entidad(Object response) throws Exception {
        return response.getClass().getMethod("getEntity").invoke(response);
    }

    private static Object leer(Object record, String componente) throws Exception {
        if (record == null) return null;
        return record.getClass().getMethod(componente).invoke(record);
    }

    private static void verificar(String criterio, String descripcion, Prueba prueba) {
        numero++;
        boolean ok;
        String detalle = "";
        try {
            ok = prueba.ejecutar();
        } catch (UnsupportedOperationException e) {
            ok = false;
            detalle = "  (pendiente: " + e.getMessage() + ")";
        } catch (NullPointerException e) {
            ok = false;
            detalle = "  (valor no inyectado o metodo sin implementar)";
        } catch (Throwable e) {
            ok = false;
            detalle = "  (" + e.getClass().getSimpleName() + ")";
        }
        int[] r = RESUMEN.computeIfAbsent(criterio, k -> new int[2]);
        r[1]++;
        if (ok) r[0]++;
        System.out.printf("%s %2d. %s%s%n", ok ? "[OK]" : "[X] ", numero, descripcion, detalle);
    }
}
