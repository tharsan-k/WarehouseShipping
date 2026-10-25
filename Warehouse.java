package com.warehouseshipping;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Warehouse {

    private static int warehouseCount = 0;

    private final String whId;
    private String name;
    private String location;
    private final int capacity;
    private final List<Item> inventory = new ArrayList<>();

    public Warehouse(String whId, String name, String location, int capacity) {
        this.whId = requireText(whId, "Warehouse ID");
        this.name = requireText(name, "Warehouse name");
        this.location = requireText(location, "Location");
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than zero.");
        }
        this.capacity = capacity;
        warehouseCount++;
    }

    public String getWhId() { return whId; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public int getCapacity() { return capacity; }
    public static int getWarehouseCount() { return warehouseCount; }
    public List<Item> getInventory() { return Collections.unmodifiableList(inventory); }

    public void setName(String name) { this.name = requireText(name, "Warehouse name"); }
    public void setLocation(String location) {
        this.location = requireText(location, "Location");
    }

    public int getUsedCapacity() {
        int used = 0;
        for (Item item : inventory) {
            used += item.getQtyOnHand();
        }
        return used;
    }

    public int getFreeCapacity() { return capacity - getUsedCapacity(); }

    public Item findItem(String sku) {
        if (sku == null) {
            return null;
        }
        for (Item item : inventory) {
            if (item.getSku().equalsIgnoreCase(sku.trim())) {
                return item;
            }
        }
        return null;
    }

    public void receive(Item item, int qty) {
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null.");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("Received quantity must be greater than zero, but was "
                    + qty + ".");
        }
        Item existing = findItem(item.getSku());
        if (existing == null && item.getQtyOnHand() != 0) {
            throw new IllegalArgumentException("New item " + item.getSku()
                    + " must be registered with zero stock; stock enters only through receive().");
        }
        if (qty > getFreeCapacity()) {
            throw new IllegalStateException("Warehouse " + whId + " capacity exceeded: free space is "
                    + getFreeCapacity() + " unit(s) but " + qty + " were offered.");
        }
        if (existing == null) {
            inventory.add(item);
            item.receive(qty);
        } else {
            existing.receive(qty);
        }
    }

    public int pickPack(String sku, int qty, LocalDate onDate) {
        Item item = findItem(sku);
        if (item == null) {
            throw new IllegalArgumentException("SKU " + sku + " is not stocked in warehouse "
                    + whId + ".");
        }
        return item.pickPack(qty, onDate);
    }

    public double totalStockValue() {
        double total = 0;
        for (Item item : inventory) {
            total += item.stockValue();
        }
        return total;
    }

    public List<Item> lowStockItems(int threshold) {
        List<Item> low = new ArrayList<>();
        for (Item item : inventory) {
            if (item.getQtyOnHand() <= threshold) {
                low.add(item);
            }
        }
        return low;
    }

    public void printSnapshot() {
        double pct = 100.0 * getUsedCapacity() / capacity;
        System.out.printf("Warehouse %s - %s (%s)%n", whId, name, location);
        System.out.printf("  Capacity used: %d / %d units (%.1f%%) | Stock value: %s%n",
                getUsedCapacity(), capacity, pct, Item.formatMoney(totalStockValue()));
        System.out.printf("  %-9s %-11s %-22s %5s %12s %14s%n",
                "SKU", "Type", "Name", "Qty", "Unit(Rs.)", "Value(Rs.)");
        for (Item item : inventory) {
            System.out.printf("  %-9s %-11s %-22s %5d %12s %14s%n",
                    item.getSku(), item.getItemType(), item.getName(), item.getQtyOnHand(),
                    Item.formatAmount(item.getUnitPrice()), Item.formatAmount(item.stockValue()));
        }
    }

    @Override
    public String toString() { return whId + " (" + name + ")"; }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return value.trim();
    }
}
