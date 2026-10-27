package com.nttdata.recetas.model;

/** Respuesta de GET /api/recetas/config. (Resuelto) */
public record ConfigResponse(String farmaciaUrl, String apiKey, boolean firmaConfigurada) {
}
