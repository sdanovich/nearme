package com.example.nearme.model;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;

import java.time.Instant;

/**
 * A point of interest of some category (GAS today; COFFEE/RESTAURANT later).
 * `location` is a PostGIS geography point (lon/lat, SRID 4326) for distance
 * and nearest-neighbor queries.
 */
@Entity
@Table(name = "place", indexes = {
        @Index(name = "idx_place_category", columnList = "category")
})
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlaceCategory category;

    /** OpenStreetMap node id when sourced from OSM (nullable for user-created). */
    @Column(unique = true)
    private Long osmId;

    private String name;
    private String brand;
    private String address;

    /** SRID 4326 point. JTS Point is (x=longitude, y=latitude). */
    @Column(columnDefinition = "geography(Point,4326)", nullable = false)
    private Point location;

    private Instant createdAt;

    /** Average rating 0–5 when known (null for places with no rating data). */
    private Double rating;

    /** Opening-hours string (OSM-style or free text), null if unknown. */
    private String openingHours;

    public Place() {}

    public Long getId() { return id; }
    public PlaceCategory getCategory() { return category; }
    public void setCategory(PlaceCategory category) { this.category = category; }
    public Long getOsmId() { return osmId; }
    public void setOsmId(Long osmId) { this.osmId = osmId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Point getLocation() { return location; }
    public void setLocation(Point location) { this.location = location; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String openingHours) { this.openingHours = openingHours; }
}
