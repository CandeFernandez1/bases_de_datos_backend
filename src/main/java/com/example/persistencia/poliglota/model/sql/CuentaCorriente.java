package com.example.persistencia.poliglota.model.sql;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "cuenta_corriente")
public class CuentaCorriente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idCuenta;

    @OneToOne
    @JoinColumn(name = "usuario_id", unique = true)
    private Usuario usuario;

    private Double saldoActual = 0.0;
}
