package com.warehouseshipping;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Shipment {

    public enum Status {
        CREATED, IN_TRANSIT, DELIVERED;

        public Status next() {
            switch (this) {
                case CREATED:
                    return IN_TRANSIT;
                case IN_TRANSIT:
                    return DELIVERED;
                default:
                    return null;
            }
        }
    }

    public enum Priority {
        STANDARD("Standard", 1.0), EXPRESS("Express", 1.5), OVERNIGHT("Overnight", 2.0);

        private final String label;
        private final double multiplier;

        Priority(String label, double multiplier) {
            this.label = label;
            this.multiplier = multiplier;
        }

        public String getLabel() { return label; }
        public double getMultiplier() { return multiplier; }
    }

    public static final double BASE_CHARGE = 50.0;
    public static final double RATE_PER_KG = 12.0;
    public static final double DISTANCE_RATE = 0.02;
    public static final double MIN_CHARGE = 80.0;
    private static int shipmentCount = 0;

    private final String shipId;
    private final String originWhId;
    private final String destination;
    private final String carrier;
    private final Priority priority;
    private final double distanceKm;
    private final LocalDate createdOn;
    private Status status;
    private final List<ShipmentLine> lines = new ArrayList<>();
    private final List<String> trackingLog = new ArrayList<>();
    private LocalDate lastEventDate;

    public Shipment(String shipId, String originWhId, String destination, String carrier,
                    Priority priority, double distanceKm, LocalDate createdOn) {
        this.shipId = requireText(shipId, "Shipment ID");
        this.originWhId = requireText(originWhId, "Origin warehouse");
        this.destination = requireText(destination, "Destination");
        this.carrier = requireText(carrier, "Carrier");
        if (priority == null) {
            throw new IllegalArgumentException("Priority must not be null.");
        }
        if (Double.isNaN(distanceKm) || Double.isInfinite(distanceKm) || distanceKm <= 0) {
            throw new IllegalArgumentException("Distance must be a positive number.");
        }
        if (createdOn == null) {
            throw new IllegalArgumentException("Creation date must not be null.");
        }
        this.priority = priority;
        this.distanceKm = distanceKm;
        this.createdOn = createdOn;
        this.status = Status.CREATED;
        this.lastEventDate = createdOn;
        trackingLog.add(createdOn + " | CREATED    | Packed at warehouse " + originWhId
                + ", awaiting dispatch");
        shipmentCount++;
    }

    public String getShipId() { return shipId; }
    public String getOriginWhId() { return originWhId; }
    public String getDestination() { return destination; }
    public String getCarrier() { return carrier; }
    public Priority getPriority() { return priority; }
    public double getDistanceKm() { return distanceKm; }
    public LocalDate getCreatedOn() { return createdOn; }
    public Status getStatus() { return status; }
    public LocalDate getLastEventDate() { return lastEventDate; }
    public static int getShipmentCount() { return shipmentCount; }
    public List<ShipmentLine> getLines() { return Collections.unmodifiableList(lines); }
    public List<String> getTrackingLog() { return Collections.unmodifiableList(trackingLog); }

    void addLine(ShipmentLine line) {
        if (status != Status.CREATED) {
            throw new IllegalStateException("Items can only be added while the shipment is CREATED.");
        }
        lines.add(line);
    }

    public List<Item> getItems() {
        List<Item> items = new ArrayList<>();
        for (ShipmentLine line : lines) {
            items.add(line.getItem());
        }
        return items;
    }

    public int getTotalUnits() {
        int units = 0;
        for (ShipmentLine line : lines) {
            units += line.getQty();
        }
        return units;
    }

    public double getTotalWeight() {
        double weight = 0;
        for (ShipmentLine line : lines) {
            weight += line.getLineWeight();
        }
        return weight;
    }

    public double getGoodsValue() {
        double value = 0;
        for (ShipmentLine line : lines) {
            value += line.getLineValue();
        }
        return value;
    }

    public double getHandlingCost() {
        double total = 0;
        for (ShipmentLine line : lines) {
            total += line.getLineHandling();
        }
        return total;
    }

    public double shippingCost() {
        return shippingCost(getTotalWeight(), distanceKm, priority);
    }

    public double shippingCost(double weightKg) {
        checkWeight(weightKg);
        return Math.max(MIN_CHARGE, BASE_CHARGE + weightKg * RATE_PER_KG);
    }

    public double shippingCost(double weightKg, double distanceKm, Priority priority) {
        checkWeight(weightKg);
        if (Double.isNaN(distanceKm) || Double.isInfinite(distanceKm) || distanceKm < 0) {
            throw new IllegalArgumentException("Distance cannot be negative.");
        }
        if (priority == null) {
            throw new IllegalArgumentException("Priority must not be null.");
        }
        double raw = BASE_CHARGE + weightKg * RATE_PER_KG + weightKg * distanceKm * DISTANCE_RATE;
        return Math.max(MIN_CHARGE, raw * priority.getMultiplier());
    }

    public void dispatch(LocalDate date) {
        updateStatus(Status.IN_TRANSIT, date);
    }

    public void updateStatus(Status newStatus, LocalDate date) {
        if (newStatus == null) {
            throw new IllegalArgumentException("New status must not be null.");
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("Shipment " + shipId + " has no items.");
        }
        if (status.next() != newStatus) {
            String allowed = (status.next() == null)
                    ? "none, DELIVERED is final" : status.next().toString();
            throw new IllegalStateException("Shipment " + shipId + " cannot move from " + status
                    + " to " + newStatus + ". Allowed next status: " + allowed + ".");
        }
        if (date == null || date.isBefore(lastEventDate)) {
            throw new IllegalArgumentException("Status date cannot be missing or before "
                    + lastEventDate + ".");
        }
        status = newStatus;
        lastEventDate = date;
        if (newStatus == Status.IN_TRANSIT) {
            trackingLog.add(date + " | IN_TRANSIT | Handed to " + carrier + " for " + destination);
        } else {
            trackingLog.add(date + " | DELIVERED  | Received at " + destination);
        }
    }

    public String summaryLine() {
        return String.format("%-7s %-12s %-11s %-9s %8.1f %-11s",
                shipId, destination, carrier, priority.getLabel(), getTotalWeight(), status);
    }

    @Override
    public String toString() {
        return shipId + " [" + destination + ", " + status + "]";
    }

    private static void checkWeight(double weightKg) {
        if (Double.isNaN(weightKg) || Double.isInfinite(weightKg) || weightKg <= 0) {
            throw new IllegalArgumentException("Weight must be a positive number, but was "
                    + weightKg + ".");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return value.trim();
    }
}
