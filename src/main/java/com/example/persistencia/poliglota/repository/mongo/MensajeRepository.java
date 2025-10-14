package com.example.persistencia.poliglota.repository.mongo;

import com.example.persistencia.poliglota.model.mongo.Mensaje;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MensajeRepository extends MongoRepository<Mensaje, UUID> {
}

