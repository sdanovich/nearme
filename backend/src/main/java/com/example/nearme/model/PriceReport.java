package com.example.nearme.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A crowdsourced price report tied to a Place. `priceType` is a generic string
 * (for GAS it holds a FuelType name like "REGULAR"; for COFFEE it might be
 * "SMALL", etc.), so the same table serves every category. Reports are never
 * overwritten — the newest is "current", and the full set is the history.
 */
@Entity
@Table(name = "price_report", indexes = {
        @Index(name = "idx_report_place_type_time", columnList = "place_id,priceType,reportedAt")
})
public class PriceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id")
    private Place place;

    /** Generic price type; for GAS this is a FuelType name. */
    private String priceType;

    @Column(precision = 8, scale = 3)
    private BigDecimal price;

    private String reporterId;
    private Instant reportedAt;

    public PriceReport() {}

    public Long getId() { return id; }
    public Place getPlace() { return place; }
    public void setPlace(Place place) { this.place = place; }
    public String getPriceType() { return priceType; }
    public void setPriceType(String priceType) { this.priceType = priceType; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getReporterId() { return reporterId; }
    public void setReporterId(String reporterId) { this.reporterId = reporterId; }
    public Instant getReportedAt() { return reportedAt; }
    public void setReportedAt(Instant reportedAt) { this.reportedAt = reportedAt; }
}
