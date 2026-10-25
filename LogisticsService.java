package com.warehouseshipping;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class LogisticsService {

    public static final double GST_RATE = 0.18;
    private static final Pattern ID_PATTERN = Pattern.compile("SH-\\d{3,}");
    private static int totalShipmentsCreated = 0;

    private final String companyName;
    private final List<Warehouse> warehouses = new ArrayList<>();
    private final List<Shipment> shipments = new ArrayList<>();
    private final Map<String, Double> invoices = new LinkedHashMap<>();
    private final List<String> auditLog = new ArrayList<>();

    public LogisticsService(String companyName) {
        if (companyName == null || companyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Company name must not be blank.");
        }
        this.companyName = companyName.trim();
    }

    public String getCompanyName() { return companyName; }
    public int getShipmentCount() { return shipments.size(); }
    public static int getTotalShipmentsCreated() { return totalShipmentsCreated; }
    public List<Warehouse> getWarehouses() { return Collections.unmodifiableList(warehouses); }
    public List<Shipment> getShipments() { return Collections.unmodifiableList(shipments); }
    public List<String> getAuditLog() { return Collections.unmodifiableList(auditLog); }

    public void addWarehouse(Warehouse warehouse) {
        if (warehouse == null) {
            throw new IllegalArgumentException("Warehouse must not be null.");
        }
        for (Warehouse w : warehouses) {
            if (w.getWhId().equalsIgnoreCase(warehouse.getWhId())) {
                throw new IllegalArgumentException("Duplicate warehouse ID: " + warehouse.getWhId());
            }
        }
        warehouses.add(warehouse);
        audit("WAREHOUSE ADDED " + warehouse.getWhId());
    }

    public Warehouse findWarehouse(String whId) {
        if (whId != null) {
            for (Warehouse w : warehouses) {
                if (w.getWhId().equalsIgnoreCase(whId.trim())) {
                    return w;
                }
            }
        }
        throw new IllegalArgumentException("No warehouse found with ID " + whId + ".");
    }

    public Shipment findShipment(String shipId) {
        if (shipId != null) {
            for (Shipment s : shipments) {
                if (s.getShipId().equalsIgnoreCase(shipId.trim())) {
                    return s;
                }
            }
        }
        throw new IllegalArgumentException("No shipment found with ID " + shipId + ".");
    }

    // ---------------- inbound ----------------

    public void inbound(String whId, Item item, int qty) {
        Warehouse warehouse = findWarehouse(whId);
        warehouse.receive(item, qty);
        audit("INBOUND " + qty + " x " + item.getSku() + " into " + warehouse.getWhId());
    }

    // ---------------- outbound ----------------

    public Shipment createShipment(String shipId, String whId, String destination,
                                   String carrier, Shipment.Priority priority,
                                   double distanceKm, LocalDate date,
                                   Map<String, Integer> request) {
        if (shipId == null || !ID_PATTERN.matcher(shipId.trim()).matches()) {
            throw new IllegalArgumentException("Invalid shipment ID '" + shipId
                    + "'. Expected format: SH-001.");
        }
        if (exists(shipId)) {
            throw new IllegalArgumentException("Duplicate shipment ID: " + shipId.trim()
                    + " is already in use.");
        }
        if (request == null || request.isEmpty()) {
            throw new IllegalArgumentException("A shipment needs at least one item.");
        }
        Warehouse warehouse = findWarehouse(whId);
        List<ShipmentLine> newLines = new ArrayList<>();
        List<Item> picked = new ArrayList<>();
        List<Integer> pickedQty = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : request.entrySet()) {
            Item item = warehouse.findItem(entry.getKey());
            int qty = entry.getValue();
            if (item == null) {
                undoPicks(picked, pickedQty);
                throw new IllegalArgumentException("SKU " + entry.getKey()
                        + " is not stocked in warehouse " + warehouse.getWhId() + ".");
            }
            int got = item.pickPack(qty, date);
            if (got == 0) {
                String reason = item.getLastNote();
                undoPicks(picked, pickedQty);
                throw new IllegalStateException("Shipment " + shipId.trim()
                        + " not created, nothing was picked. " + reason);
            }
            picked.add(item);
            pickedQty.add(got);
            newLines.add(new ShipmentLine(item, got, item.getLastNote()));
        }
        Shipment shipment;
        try {
            shipment = new Shipment(shipId, warehouse.getWhId(), destination, carrier,
                    priority, distanceKm, date);
        } catch (IllegalArgumentException ex) {
            undoPicks(picked, pickedQty);
            throw ex;
        }
        for (ShipmentLine line : newLines) {
            shipment.addLine(line);
        }
        shipments.add(shipment);
        totalShipmentsCreated++;
        audit("SHIPMENT CREATED " + shipment.getShipId() + " from " + warehouse.getWhId());
        return shipment;
    }

    public void dispatch(String shipId, LocalDate date) {
        Shipment shipment = findShipment(shipId);
        shipment.dispatch(date);
        audit("DISPATCHED " + shipment.getShipId() + " via " + shipment.getCarrier());
    }

    public void updateStatus(String shipId, Shipment.Status status, LocalDate date) {
        Shipment shipment = findShipment(shipId);
        shipment.updateStatus(status, date);
        audit("STATUS " + shipment.getShipId() + " -> " + status);
    }

    // ---------------- tracking ----------------

    public void track(String shipId) {
        Shipment s = findShipment(shipId);
        System.out.println("Tracking " + s.getShipId() + " to " + s.getDestination()
                + " via " + s.getCarrier() + " - current status: " + s.getStatus());
        for (String event : s.getTrackingLog()) {
            System.out.println("    " + event);
        }
    }

    public List<Shipment> listByStatus(Shipment.Status status) {
        List<Shipment> result = new ArrayList<>();
        for (Shipment s : shipments) {
            if (s.getStatus() == status) {
                result.add(s);
            }
        }
        return result;
    }

    // ---------------- billing ----------------

    private double[] computeCharges(Shipment s) {
        double handling = s.getHandlingCost();
        double shipping = s.shippingCost();
        double taxable = handling + shipping;
        double gst = taxable * GST_RATE;
        return new double[] { handling, shipping, taxable, gst, taxable + gst };
    }

    public double calculateBill(String shipId) {
        Shipment s = findShipment(shipId);
        if (s.getStatus() == Shipment.Status.CREATED) {
            throw new IllegalStateException("Shipment " + s.getShipId()
                    + " is still CREATED; dispatch it before billing.");
        }
        return computeCharges(s)[4];
    }

    public double printInvoice(String shipId) {
        double total = calculateBill(shipId);
        Shipment s = findShipment(shipId);
        double[] c = computeCharges(s);
        System.out.println("  INVOICE " + s.getShipId() + " | " + s.getDestination() + " | "
                + s.getCarrier() + " | " + s.getPriority().getLabel() + " | " + s.getStatus());
        for (ShipmentLine line : s.getLines()) {
            System.out.printf("    %-26s handling %12s%n", line, Item.formatMoney(line.getLineHandling()));
        }
        System.out.printf("    Weight %.1f kg x %.0f km -> freight        %14s%n",
                s.getTotalWeight(), s.getDistanceKm(), Item.formatMoney(c[1]));
        System.out.printf("    Handling (polymorphic total)             %14s%n", Item.formatMoney(c[0]));
        System.out.printf("    GST @ %.0f%%                                %14s%n",
                GST_RATE * 100, Item.formatMoney(c[3]));
        System.out.printf("    TOTAL PAYABLE                            %14s%n", Item.formatMoney(total));
        invoices.putIfAbsent(s.getShipId(), total);
        audit("INVOICED " + s.getShipId() + " " + Item.formatMoney(total));
        return total;
    }

    public double getInvoicedTotal() {
        double total = 0;
        for (double amount : invoices.values()) {
            total += amount;
        }
        return total;
    }

    public int getInvoiceCount() { return invoices.size(); }

    public static double estimateHandling(List<Item> items, int qtyEach) {
        double total = 0;
        for (Item item : items) {
            total += item.handlingCharge(qtyEach);
        }
        return total;
    }

    // ---------------- reports ----------------

    public void printStockSnapshot() {
        for (Warehouse w : warehouses) {
            w.printSnapshot();
            System.out.println();
        }
        int units = 0;
        double value = 0;
        for (Warehouse w : warehouses) {
            units += w.getUsedCapacity();
            value += w.totalStockValue();
        }
        System.out.println("Network stock: " + units + " units, value " + Item.formatMoney(value));
    }

    public void printShipmentSummary() {
        System.out.printf("%-7s %-12s %-11s %-9s %8s %-11s%n",
                "ID", "Destination", "Carrier", "Priority", "Kg", "Status");
        for (Shipment s : shipments) {
            System.out.println(s.summaryLine());
        }
        System.out.println("Created " + shipments.size() + " | CREATED "
                + listByStatus(Shipment.Status.CREATED).size() + " | IN_TRANSIT "
                + listByStatus(Shipment.Status.IN_TRANSIT).size() + " | DELIVERED "
                + listByStatus(Shipment.Status.DELIVERED).size());
    }

    public void printCostSummary() {
        System.out.println("(amounts in Rs.; GST " + (int) (GST_RATE * 100) + "% on handling + freight)");
        System.out.printf("%-7s %-9s %12s %12s %10s %12s%n",
                "ID", "Priority", "Handling", "Freight", "GST", "Total");
        double h = 0, f = 0, g = 0, t = 0;
        for (Shipment s : shipments) {
            double[] c = computeCharges(s);
            h += c[0];
            f += c[1];
            g += c[3];
            t += c[4];
            System.out.printf("%-7s %-9s %12s %12s %10s %12s%n", s.getShipId(),
                    s.getPriority().getLabel(), Item.formatAmount(c[0]), Item.formatAmount(c[1]),
                    Item.formatAmount(c[3]), Item.formatAmount(c[4]));
        }
        System.out.printf("%-17s %12s %12s %10s %12s%n", "ALL SHIPMENTS",
                Item.formatAmount(h), Item.formatAmount(f), Item.formatAmount(g), Item.formatAmount(t));
        System.out.println("Invoiced so far (dispatched/delivered): " + getInvoiceCount()
                + " invoice(s), " + Item.formatMoney(getInvoicedTotal()));
    }

    public double calculateTotalCost() {
        double total = 0;
        for (Shipment s : shipments) {
            total += computeCharges(s)[4];
        }
        return total;
    }

    // ---------------- helpers ----------------

    public boolean exists(String shipId) {
        if (shipId == null) {
            return false;
        }
        for (Shipment s : shipments) {
            if (s.getShipId().equalsIgnoreCase(shipId.trim())) {
                return true;
            }
        }
        return false;
    }

    private void undoPicks(List<Item> picked, List<Integer> qtys) {
        for (int i = 0; i < picked.size(); i++) {
            picked.get(i).receive(qtys.get(i));
        }
    }

    private void audit(String entry) {
        auditLog.add(entry);
    }
}
