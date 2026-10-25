package com.warehouseshipping;

import java.time.LocalDate;

public class FragileItem extends Item {

    public enum Fragility {
        LOW("Low", 1.10), MEDIUM("Medium", 1.25), HIGH("High", 1.40);

        private final String label;
        private final double handlingFactor;

        Fragility(String label, double handlingFactor) {
            this.label = label;
            this.handlingFactor = handlingFactor;
        }

        public String getLabel() { return label; }
        public double getHandlingFactor() { return handlingFactor; }
    }

    public static final int UNITS_PER_CARTON = 6;
    public static final double PACKING_FEE_PER_UNIT = 40.0;
    private static int fragileCount = 0;

    private final Fragility fragility;
    private String paddingMaterial;

    public FragileItem(String sku, String name, double unitPrice, int qtyOnHand,
                       double weightKg, Fragility fragility, String paddingMaterial) {
        super(sku, name, unitPrice, qtyOnHand, weightKg);
        if (fragility == null) {
            throw new IllegalArgumentException("Fragility level must not be null.");
        }
        this.fragility = fragility;
        this.paddingMaterial = requireText(paddingMaterial, "Padding material");
        fragileCount++;
    }

    public Fragility getFragility() { return fragility; }
    public String getPaddingMaterial() { return paddingMaterial; }
    public void setPaddingMaterial(String paddingMaterial) {
        this.paddingMaterial = requireText(paddingMaterial, "Padding material");
    }
    public static int getFragileCount() { return fragileCount; }

    public int cartonsRequired(int qty) {
        requirePositiveQty(qty, "Carton quantity");
        return (qty + UNITS_PER_CARTON - 1) / UNITS_PER_CARTON;
    }

    @Override
    public String getItemType() { return "Fragile"; }

    @Override
    public String storageRule() {
        return fragility.getLabel() + " fragility: bottom shelf, no stacking, " + paddingMaterial
                + ", max " + UNITS_PER_CARTON + "/carton.";
    }

    @Override
    public double handlingCharge(int qty) {
        return super.handlingCharge(qty) * fragility.getHandlingFactor()
                + qty * PACKING_FEE_PER_UNIT;
    }

    @Override
    public int pickPack(int qty, LocalDate onDate) {
        requirePositiveQty(qty, "Pick quantity");
        if (qty > getQtyOnHand()) {
            setLastNote("[SHORT] " + getSku() + ": requested " + qty + " but only "
                    + getQtyOnHand() + " on hand.");
            return 0;
        }
        removeStock(qty);
        int cartons = cartonsRequired(qty);
        setLastNote("Picked " + qty + " x " + getSku() + " with care; packed in " + cartons
                + " padded carton(s) using " + paddingMaterial + " ('FRAGILE' label).");
        return qty;
    }
}
