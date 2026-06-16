package com.example.nearme.model;

/**
 * The kind of place the user is searching for. The user picks exactly one at a
 * time. Only GAS carries a crowdsourced price; the others surface rating,
 * distance, and opening hours instead.
 */
public enum PlaceCategory {
    GAS,
    COFFEE,
    RESTAURANT,
    HOTEL,
    MECHANIC,
    HOSPITAL;

    /** Only gas has a meaningful single crowdsourceable price. */
    public boolean hasPrice() {
        return this == GAS;
    }
}
