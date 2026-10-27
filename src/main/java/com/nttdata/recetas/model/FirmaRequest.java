package com.nttdata.recetas.model;

/** Cuerpo de la peticion POST /api/recetas/firmar. (Resuelto) */
public record FirmaRequest(String codigoReceta, String paciente) {
}
