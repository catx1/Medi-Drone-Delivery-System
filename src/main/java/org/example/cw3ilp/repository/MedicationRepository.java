package org.example.cw3ilp.repository;

import org.example.cw3ilp.entity.Medication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicationRepository extends JpaRepository<Medication, Long> {
    List<Medication> findByStockQuantityGreaterThan(int quantity);
}