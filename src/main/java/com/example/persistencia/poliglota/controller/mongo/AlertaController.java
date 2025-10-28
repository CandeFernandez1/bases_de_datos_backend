package com.example.persistencia.poliglota.controller.mongo;

import com.example.persistencia.poliglota.model.mongo.Alerta;
import com.example.persistencia.poliglota.service.mongo.AlertaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Controlador REST para la gestión de alertas (MongoDB).
 * Permite crear, listar, resolver y buscar alertas activas o por ubicación.
 */
@RestController
@RequestMapping("/api/mongo/alertas")// para evitar problemas de CORS al conectar con el front
public class AlertaController {

    private final AlertaService service;

    public AlertaController(AlertaService service) {
        this.service = service;
    }

    // -----------------------------------------------------------------------
    // 🔍 LISTAR TODAS LAS ALERTAS
    // -----------------------------------------------------------------------
    @GetMapping
    public ResponseEntity<List<Alerta>> getAll() {
        return ResponseEntity.ok(service.listar());
    }

    // -----------------------------------------------------------------------
    // 🟢 LISTAR SOLO LAS ALERTAS ACTIVAS
    // -----------------------------------------------------------------------
    @GetMapping("/activas")
    public ResponseEntity<List<Alerta>> getActivas() {
        return ResponseEntity.ok(service.listarActivas());
    }

    // -----------------------------------------------------------------------
    // 🌍 BUSCAR ALERTAS POR CIUDAD Y PAÍS
    // Ejemplo: /api/mongo/alertas/ubicacion?ciudad=Rosario&pais=Argentina
    // -----------------------------------------------------------------------
    @GetMapping("/ubicacion")
    public ResponseEntity<List<Alerta>> getPorUbicacion(
            @RequestParam String ciudad,
            @RequestParam String pais
    ) {
        List<Alerta> alertas = service.buscarPorUbicacion(ciudad, pais);
        if (alertas.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(alertas);
    }

    // -----------------------------------------------------------------------
    // 🚨 CREAR ALERTA MANUAL O POR BACKEND
    // Recibe un JSON con los campos:
    // tipo, descripcion, ciudad, pais, sensorId (opcional), detalles (map)
    // -----------------------------------------------------------------------
    @PostMapping
    public ResponseEntity<Alerta> crear(@RequestBody Map<String, Object> body) {
        try {
            // Extraemos los datos del body
            String tipo = (String) body.getOrDefault("tipo", "climatica");
            String descripcion = (String) body.getOrDefault("descripcion", "Alerta generada");
            String ciudad = (String) body.getOrDefault("ciudad", "Desconocida");
            String pais = (String) body.getOrDefault("pais", "Desconocido");
            UUID sensorId = body.containsKey("sensorId")
                    ? UUID.fromString((String) body.get("sensorId"))
                    : null;
            Map<String, Object> detalles = (Map<String, Object>) body.getOrDefault("detalles", Map.of());

            // Creamos la alerta
            Alerta alerta = service.crearConDetalles(sensorId, tipo, descripcion, ciudad, pais, detalles);
            return ResponseEntity.status(HttpStatus.CREATED).body(alerta);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> error = Map.of(
                    "error", "Error al crear la alerta",
                    "detalle", e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    // -----------------------------------------------------------------------
    // 🧩 RESOLVER ALERTA POR ID
    // Cambia el estado a "resuelta"
    // -----------------------------------------------------------------------
    @PutMapping("/{id}/resolver")
    public ResponseEntity<Alerta> resolver(@PathVariable UUID id) {
        try {
            Alerta alerta = service.resolver(id);
            return ResponseEntity.ok(alerta);
        } catch (Exception e) {
            Map<String, Object> error = Map.of(
                    "error", "No se pudo resolver la alerta",
                    "detalle", e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    // -----------------------------------------------------------------------
    // 🗑️ ELIMINAR ALERTA POR ID (solo para mantenimiento)
    // -----------------------------------------------------------------------
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> eliminar(@PathVariable UUID id) {
        try {
            service.eliminar(id);
            return ResponseEntity.ok(Map.of("mensaje", "Alerta eliminada correctamente"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No se encontró la alerta con el ID especificado"));
        }
    }

    // -----------------------------------------------------------------------
// 🎯 FILTRAR ALERTAS DINÁMICAMENTE
// Ejemplo:
// /api/mongo/alertas/filtrar?tipo=climatica&severidad=critica&ciudad=Rosario&pais=Argentina
// Todos los parámetros son opcionales.
// -----------------------------------------------------------------------
@GetMapping("/filtrar")
public ResponseEntity<List<Alerta>> filtrarAlertas(
        @RequestParam(required = false) String tipo,
        @RequestParam(required = false) String severidad,
        @RequestParam(required = false) String ciudad,
        @RequestParam(required = false) String pais
) {
    List<Alerta> alertas = service.filtrar(tipo, severidad, ciudad, pais);
    if (alertas.isEmpty()) {
        return ResponseEntity.noContent().build();
    }
    return ResponseEntity.ok(alertas);
}

// -----------------------------------------------------------------------
// 🌐 ALERTAS GLOBALES (Mongo + Cassandra)
// Combina las alertas de Mongo con la última medición de Cassandra
// -----------------------------------------------------------------------
@GetMapping("/global")
public ResponseEntity<List<Map<String, Object>>> getAlertasGlobales() {
    List<Map<String, Object>> resultado = new ArrayList<>();

    try {
        // ✅ Obtenemos todas las alertas
        List<Alerta> alertas = service.listar();

        for (Alerta alerta : alertas) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sensorId", alerta.getSensorId());
            item.put("ciudad", alerta.getCiudad());
            item.put("pais", alerta.getPais());
            item.put("descripcion", alerta.getDescripcion());
            item.put("severidad", alerta.getSeveridad());
            item.put("fechaAlerta", alerta.getFecha());
            item.put("fuente", alerta.getFuente());
            item.put("estado", alerta.getEstado());

            // ⚙️ Consultamos Cassandra (vía HTTP) para obtener la última medición del sensor
            try {
                var restTemplate = new org.springframework.web.client.RestTemplate();
                var url = "http://localhost:8080/api/cassandra/mediciones/sensor/" + alerta.getSensorId();
                var response = restTemplate.getForEntity(url, List.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && !response.getBody().isEmpty()) {
                    // Tomamos la última medición
                    List<?> mediciones = response.getBody();
                    item.put("ultimaMedicion", mediciones.get(mediciones.size() - 1));
                }
            } catch (Exception e) {
                item.put("ultimaMedicion", "No disponible");
            }

            resultado.add(item);
        }

        return ResponseEntity.ok(resultado);

    } catch (Exception e) {
        Map<String, Object> error = Map.of(
                "error", "No se pudieron combinar las alertas con las mediciones",
                "detalle", e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(List.of(error));
    }
}
@GetMapping("/resueltas")
public ResponseEntity<List<Alerta>> getResueltas() {
    return ResponseEntity.ok(service.listarResueltas());
}

}
