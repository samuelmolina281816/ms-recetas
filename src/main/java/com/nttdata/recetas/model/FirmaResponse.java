package com.nttdata.recetas.model;

/** Respuesta de POST /api/recetas/firmar. (Resuelto) */
public record FirmaResponse(String codigoReceta, String paciente, String firma) {
}
