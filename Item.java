package com.warehouseshipping;

import java.time.LocalDate;
import java.util.Locale;

public class Item {

    public static final double HANDLING_RATE_PER_KG = 2.0;
    private static int itemCount = 0;

    private final String sku;
    private String name;
    private double unitPrice;
    private int qtyOnHand;
    private final double weightKg;
    private String lastNote = "";

    public Item(String sku, String name, double unitPrice, int qtyOnHand, double weightKg) {
        this.sku = requireText(sku, "SKU");
        this.name = requireText(name, "Item name");
        this.unitPrice = requirePositiveAmount(unitPrice, "Unit price");
        if (qtyOnHand < 0) {
            throw new IllegalArgumentException("Opening quantity cannot be negative.");
        }
        this.qtyOnHand = qtyOnHand;
        this.weightKg = requirePositiveAmount(weightKg, "Weight");
        itemCount++;
    }

    public String getSku() { return sku; }
    public String getName() { return name; }
    public double getUnitPrice() { return unitPrice; }
    public int getQtyOnHand() { return qtyOnHand; }
    public double getWeightKg() { return weightKg; }
    public String getLastNote() { return lastNote; }
    public static int getItemCount() { return itemCount; }

    public void setName(String name) { this.name = requireText(name, "Item name"); }
    public void setUnitPrice(double unitPrice) {
        this.unitPrice = requirePositiveAmount(unitPrice, "Unit price");
    }

    protected void setLastNote(String note) { this.lastNote = note; }

    public String getItemType() { return "Standard"; }

    public String storageRule() {
        return "Store at room temperature on standard racks.";
    }

    public double handlingCharge(int qty) {
        requirePositiveQty(qty, "Handling quantity");
        return qty * weightKg * HANDLING_RATE_PER_KG;
    }

    public void receive(int qty) {
        requirePositiveQty(qty, "Received quantity");
        qtyOnHand += qty;
    }

    public int pickPack(int qty, LocalDate onDate) {
        requirePositiveQty(qty, "Pick quantity");
        if (qty > qtyOnHand) {
            lastNote = "[SHORT] " + sku + ": requested " + qty + " but only "
                    + qtyOnHand + " on hand.";
            return 0;
        }
        removeStock(qty);
        lastNote = "Picked " + qty + " x " + sku + ", packed in standard cartons.";
        return qty;
    }

    protected void removeStock(int qty) {
        if (qty <= 0 || qty > qtyOnHand) {
            throw new IllegalStateException("Cannot remove " + qty + " unit(s) of " + sku
                    + "; on hand is " + qtyOnHand + ".");
        }
        qtyOnHand -= qty;
    }

    public double stockValue() { return qtyOnHand * unitPrice; }

    public boolean isInStock() { return qtyOnHand > 0; }

    public void displayDetails() {
        System.out.printf("  %-9s %-11s %-22s %10s %6.2f kg%n",
                sku, getItemType(), name, formatAmount(unitPrice), weightKg);
        System.out.println("            Rule: " + storageRule());
    }

    public static String formatAmount(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    public static String formatMoney(double value) {
        return "Rs. " + formatAmount(value);
    }

    @Override
    public String toString() {
        return sku + " (" + name + ")";
    }

    protected static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return value.trim();
    }

    protected static double requirePositiveAmount(double value, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0) {
            throw new IllegalArgumentException(field + " must be a positive number, but was "
                    + value + ".");
        }
        return value;
    }

    protected static void requirePositiveQty(int qty, String field) {
        if (qty <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero, but was "
                    + qty + ".");
        }
    }
}
