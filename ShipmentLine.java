package com.warehouseshipping;

public class ShipmentLine {

    private static int linesCreated = 0;

    private final Item item;
    private final int qty;
    private final double unitPriceAtPick;
    private final String packNote;

    public ShipmentLine(Item item, int qty, String packNote) {
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null.");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("Line quantity must be greater than zero.");
        }
        this.item = item;
        this.qty = qty;
        this.unitPriceAtPick = item.getUnitPrice();
        this.packNote = (packNote == null) ? "" : packNote;
        linesCreated++;
    }

    public Item getItem() { return item; }
    public int getQty() { return qty; }
    public double getUnitPriceAtPick() { return unitPriceAtPick; }
    public String getPackNote() { return packNote; }
    public static int getLinesCreated() { return linesCreated; }

    public double getLineValue() { return qty * unitPriceAtPick; }
    public double getLineWeight() { return qty * item.getWeightKg(); }

    public double getLineHandling() { return item.handlingCharge(qty); }

    @Override
    public String toString() {
        return qty + " x " + item.getSku() + " (" + item.getItemType() + ")";
    }
}
