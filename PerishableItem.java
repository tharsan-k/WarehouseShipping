package com.warehouseshipping;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class PerishableItem extends Item {

    public static final int NEAR_EXPIRY_DAYS = 3;
    public static final double COLD_CHAIN_FEE_PER_UNIT = 15.0;
    private static int perishableCount = 0;

    private final LocalDate expiryDate;
    private double storageTempC;

    public PerishableItem(String sku, String name, double unitPrice, int qtyOnHand,
                          double weightKg, LocalDate expiryDate, double storageTempC) {
        super(sku, name, unitPrice, qtyOnHand, weightKg);
        if (expiryDate == null) {
            throw new IllegalArgumentException("Expiry date must not be null.");
        }
        this.expiryDate = expiryDate;
        this.storageTempC = storageTempC;
        perishableCount++;
    }

    public LocalDate getExpiryDate() { return expiryDate; }
    public double getStorageTempC() { return storageTempC; }
    public void setStorageTempC(double storageTempC) { this.storageTempC = storageTempC; }
    public static int getPerishableCount() { return perishableCount; }

    public boolean isExpired(LocalDate onDate) {
        return onDate.isAfter(expiryDate);
    }

    public long daysToExpiry(LocalDate onDate) {
        return ChronoUnit.DAYS.between(onDate, expiryDate);
    }

    @Override
    public String getItemType() { return "Perishable"; }

    @Override
    public String storageRule() {
        return "Keep at " + storageTempC + " C, first-expiry-first-out, expires " + expiryDate + ".";
    }

    @Override
    public double handlingCharge(int qty) {
        return super.handlingCharge(qty) + qty * COLD_CHAIN_FEE_PER_UNIT;
    }

    @Override
    public int pickPack(int qty, LocalDate onDate) {
        requirePositiveQty(qty, "Pick quantity");
        if (onDate == null) {
            throw new IllegalArgumentException("Pick date must not be null.");
        }
        if (isExpired(onDate)) {
            setLastNote("[EXPIRED] " + getSku() + " expired on " + expiryDate
                    + "; it cannot be picked for shipping on " + onDate + ".");
            return 0;
        }
        if (qty > getQtyOnHand()) {
            setLastNote("[SHORT] " + getSku() + ": requested " + qty + " but only "
                    + getQtyOnHand() + " on hand.");
            return 0;
        }
        removeStock(qty);
        String warning = daysToExpiry(onDate) <= NEAR_EXPIRY_DAYS
                ? " WARNING: expires in " + daysToExpiry(onDate) + " day(s)." : "";
        setLastNote("Picked " + qty + " x " + getSku() + " (expiry " + expiryDate
                + "); packed in insulated cold-chain box at " + storageTempC + " C." + warning);
        return qty;
    }
}
