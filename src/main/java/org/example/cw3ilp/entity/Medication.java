package org.example.cw3ilp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "medications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Medication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "requires_refrigeration")
    private Boolean requiresRefrigeration = false;

    @Column(name = "weight_grams", nullable = false)
    private Integer weightGrams;

    @Column(name = "stock_quantity")
    private Integer stockQuantity = 0;
}