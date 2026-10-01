package com.nutricard.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "foods")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String category;

    private String description;

    private Integer servingSizeG;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FoodRole foodRole;

    // How often to eat it, with direction: "Daily", "At least 2× a week", "Up to 3× a week", "Max 2 a day".
    private String frequency;
}
