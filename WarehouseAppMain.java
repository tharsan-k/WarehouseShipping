package com.warehouseshipping;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WarehouseAppMain {

    private static final int WIDTH = 78;
    private static final String LINE = "=".repeat(WIDTH);
    private static final String SUB_LINE = "-".repeat(WIDTH);
    private static final LocalDate D0 = LocalDate.of(2026, 10, 20);

    private static final LogisticsService service =
            new LogisticsService("Southern Freight and Storage Pvt Ltd");
    private static final List<Item> catalogue = new ArrayList<>();

    private static int stepNo = 0;
    private static int checksRun = 0;
    private static int checksPassed = 0;

    private WarehouseAppMain() { }

    public static void main(String[] args) {
        printBanner();
        setupWarehousesAndCatalogue();
        receiveInventory();
        printHeading("STOCK SNAPSHOT BEFORE SHIPMENTS");
        service.printStockSnapshot();
        createShipments();
        updateTracking();
        printHeading("STOCK SNAPSHOT AFTER SHIPMENTS");
        service.printStockSnapshot();
        printBilling();
        demonstrateOopConcepts();
        runValidationTests();
        runSelfChecks();
        printFooter();
    }

    private static Item copyOf(String sku) {
        for (Item i : catalogue) {
            if (i.getSku().equals(sku)) {
                if (i instanceof FragileItem) {
                    FragileItem f = (FragileItem) i;
                    return new FragileItem(f.getSku(), f.getName(), f.getUnitPrice(), 0,
                            f.getWeightKg(), f.getFragility(), f.getPaddingMaterial());
                }
                if (i instanceof PerishableItem) {
                    PerishableItem p = (PerishableItem) i;
                    return new PerishableItem(p.getSku(), p.getName(), p.getUnitPrice(), 0,
                            p.getWeightKg(), p.getExpiryDate(), p.getStorageTempC());
                }
                return new Item(i.getSku(), i.getName(), i.getUnitPrice(), 0, i.getWeightKg());
            }
        }
        throw new IllegalArgumentException("Unknown SKU " + sku);
    }

    private static void setupWarehousesAndCatalogue() {
        printHeading("SET UP WAREHOUSES AND ITEM CATALOGUE");
        service.addWarehouse(new Warehouse("WH01", "Chennai Hub", "Chennai, Tamil Nadu", 600));
        service.addWarehouse(new Warehouse("WH02", "Bengaluru Depot", "Bengaluru, Karnataka", 250));

        catalogue.add(new Item("BOLT-101", "Steel Bolt Box", 450.00, 0, 1.5));
        catalogue.add(new Item("CHAIR-102", "Office Chair", 6500.00, 0, 9.0));
        catalogue.add(new FragileItem("GLASS-201", "Glass Tumbler Set", 1200.00, 0, 2.5,
                FragileItem.Fragility.MEDIUM, "foam"));
        catalogue.add(new FragileItem("MON-202", "LED Monitor", 12500.00, 0, 4.8,
                FragileItem.Fragility.HIGH, "moulded foam"));
        catalogue.add(new PerishableItem("CHOC-301", "Chocolate Hamper", 800.00, 0, 1.2,
                LocalDate.of(2026, 11, 20), 18.0));
        catalogue.add(new PerishableItem("YOG-302", "Fresh Yogurt Tray", 350.00, 0, 3.0,
                LocalDate.of(2026, 10, 18), 4.0));

        System.out.println("Company : " + service.getCompanyName());
        for (Warehouse w : service.getWarehouses()) {
            System.out.printf("  Warehouse %-5s %-16s %-22s capacity %d units%n",
                    w.getWhId(), w.getName(), w.getLocation(), w.getCapacity());
        }
        System.out.println();
        System.out.printf("  %-9s %-11s %-22s %10s %9s%n", "SKU", "Type", "Name", "Price(Rs.)", "Weight");
        for (Item i : catalogue) {
            i.displayDetails();
        }
        System.out.println("Created " + Item.getItemCount() + " items ("
                + FragileItem.getFragileCount() + " fragile, "
                + PerishableItem.getPerishableCount() + " perishable) and "
                + Warehouse.getWarehouseCount() + " warehouses.");
    }

    private static void receiveInventory() {
        printHeading("INBOUND: RECEIVE INVENTORY INTO WAREHOUSES");
        receive("WH01", "BOLT-101", 200);
        receive("WH01", "CHAIR-102", 40);
        receive("WH01", "GLASS-201", 60);
        receive("WH01", "MON-202", 25);
        receive("WH01", "CHOC-301", 80);
        receive("WH01", "YOG-302", 30);
        receive("WH02", "BOLT-101", 100);
        receive("WH02", "MON-202", 30);
        receive("WH02", "CHOC-301", 50);

        printSubHeading("Capacity guard (must be blocked)");
        attempt("Receive 200 more BOLT-101 into WH01 (free space 165)",
                () -> service.inbound("WH01", copyOf("BOLT-101"), 200));
        attempt("Receive 80 more MON-202 into WH02 (free space 70)",
                () -> service.inbound("WH02", copyOf("MON-202"), 80));
        Warehouse wh1 = service.findWarehouse("WH01");
        Warehouse wh2 = service.findWarehouse("WH02");
        System.out.println();
        System.out.println("WH01 used " + wh1.getUsedCapacity() + "/" + wh1.getCapacity()
                + " | WH02 used " + wh2.getUsedCapacity() + "/" + wh2.getCapacity());
    }

    private static void createShipments() {
        printHeading("OUTBOUND: PICK, PACK AND CREATE SHIPMENTS");
        createAndShow("SH-001", "WH01", "Mumbai", "BlueDart", Shipment.Priority.EXPRESS, 1330,
                order("BOLT-101", 50, "CHAIR-102", 10));
        createAndShow("SH-002", "WH01", "Coimbatore", "DTDC", Shipment.Priority.STANDARD, 500,
                order("GLASS-201", 12, "MON-202", 5));
        createAndShow("SH-003", "WH02", "Hyderabad", "Delhivery", Shipment.Priority.OVERNIGHT, 570,
                order("CHOC-301", 20, "MON-202", 4));
        createAndShow("SH-004", "WH01", "Madurai", "DTDC", Shipment.Priority.STANDARD, 460,
                order("CHOC-301", 10, "BOLT-101", 20));

        printSubHeading("Picking guards (must be blocked, stock must stay unchanged)");
        attempt("Ship 5 YOG-302 (expired on 2026-10-18)",
                () -> service.createShipment("SH-005", "WH01", "Salem", "DTDC",
                        Shipment.Priority.STANDARD, 340, D0, order("YOG-302", 5)));
        attempt("Ship 100 MON-202 from WH01 (only 20 on hand)",
                () -> service.createShipment("SH-005", "WH01", "Salem", "DTDC",
                        Shipment.Priority.STANDARD, 340, D0, order("BOLT-101", 10, "MON-202", 100)));
        System.out.println();
        System.out.println("Shipments created: " + service.getShipmentCount());
    }

    private static void updateTracking() {
        printHeading("TRACKING: CREATED -> IN_TRANSIT -> DELIVERED");
        service.dispatch("SH-001", LocalDate.of(2026, 10, 20));
        service.updateStatus("SH-001", Shipment.Status.DELIVERED, LocalDate.of(2026, 10, 22));
        service.dispatch("SH-002", LocalDate.of(2026, 10, 21));
        service.updateStatus("SH-002", Shipment.Status.DELIVERED, LocalDate.of(2026, 10, 23));
        service.dispatch("SH-003", LocalDate.of(2026, 10, 21));
        for (Shipment s : service.getShipments()) {
            service.track(s.getShipId());
        }

        printSubHeading("Invalid status changes (must be blocked)");
        attempt("SH-004 CREATED -> DELIVERED (skips IN_TRANSIT)",
                () -> service.updateStatus("SH-004", Shipment.Status.DELIVERED, D0));
        attempt("Dispatch SH-001 again", () -> service.dispatch("SH-001", D0));
        attempt("SH-003 IN_TRANSIT -> CREATED (backwards)",
                () -> service.updateStatus("SH-003", Shipment.Status.CREATED, D0));
        attempt("SH-003 delivered before it was dispatched",
                () -> service.updateStatus("SH-003", Shipment.Status.DELIVERED,
                        LocalDate.of(2026, 10, 19)));
        System.out.println();
        service.printShipmentSummary();
    }

    private static void printBilling() {
        printHeading("BILLING AND SHIPPING COST SUMMARY");
        service.printInvoice("SH-001");
        System.out.println();
        service.printInvoice("SH-002");
        System.out.println();
        service.printInvoice("SH-003");
        printSubHeading("Billing guard (must be blocked)");
        attempt("Invoice SH-004 while still CREATED", () -> service.printInvoice("SH-004"));
        System.out.println();
        service.printCostSummary();
    }

    private static void demonstrateOopConcepts() {
        printHeading("OOP CONCEPT DEMONSTRATIONS");

        printSubHeading("1. Inheritance: FragileItem and PerishableItem extend Item");
        Item glass = service.findWarehouse("WH01").findItem("GLASS-201");
        Item choc = service.findWarehouse("WH01").findItem("CHOC-301");
        System.out.println("  FragileItem is an Item   : " + (glass instanceof Item));
        System.out.println("  PerishableItem is an Item: " + (choc instanceof Item));
        System.out.println("  Inherited getSku()/getName(): " + glass.getSku() + " / " + glass.getName());
        System.out.println("  Inherited getQtyOnHand()    : " + choc.getQtyOnHand());
        printWrapped("  Storage rule (Fragile)   : ", "                             ", glass.storageRule()); 
        printWrapped("  Storage rule (Perishable): ", "                             ", choc.storageRule());

        printSubHeading("2. Overriding + 4. Polymorphism: List<Item> with overridden behaviour");
        List<Item> mixed = mixedItems();
        System.out.println("  Handling charge for 10 units of each item (same call, different result):");
        for (Item i : mixed) {
            System.out.printf("    %-9s %-11s handlingCharge(10) = %12s%n",
                    i.getSku(), i.getItemType(), Item.formatMoney(i.handlingCharge(10)));
        }
        System.out.println("  Total for the List<Item>: "
                + Item.formatMoney(LogisticsService.estimateHandling(mixed, 10)));

        printSubHeading("2. Overriding: pickPack() behaves differently per subclass");
        System.out.println("  (standalone demo items, so warehouse stock is not touched)");
        Item bolt = copyOf("BOLT-101");
        Item mon = copyOf("MON-202");
        Item choc2 = copyOf("CHOC-301");
        bolt.receive(10);
        mon.receive(10);
        choc2.receive(10);
        bolt.pickPack(5, D0);
        printWrapped("  Item       : ", "               ", bolt.getLastNote());
        mon.pickPack(7, D0);
        printWrapped("  FragileItem: ", "               ", mon.getLastNote());
        choc2.pickPack(2, LocalDate.of(2026, 11, 19));
        printWrapped("  Perishable : ", "               ", choc2.getLastNote());
        choc2.pickPack(2, LocalDate.of(2026, 11, 21));
        printWrapped("  Perishable : ", "               ", choc2.getLastNote());

        printSubHeading("3. Overloading: shippingCost() with different parameters");
        Shipment s1 = service.findShipment("SH-001");
        System.out.println("  SH-001 weight " + s1.getTotalWeight() + " kg, distance "
                + s1.getDistanceKm() + " km, " + s1.getPriority().getLabel());
        System.out.println("  shippingCost()                         = "
                + Item.formatMoney(s1.shippingCost()));
        System.out.println("  shippingCost(weight)                   = "
                + Item.formatMoney(s1.shippingCost(s1.getTotalWeight())));
        System.out.println("  shippingCost(weight, 1330, STANDARD)   = "
                + Item.formatMoney(s1.shippingCost(s1.getTotalWeight(), 1330,
                        Shipment.Priority.STANDARD)));
        System.out.println("  shippingCost(weight, 1330, OVERNIGHT)  = "
                + Item.formatMoney(s1.shippingCost(s1.getTotalWeight(), 1330,
                        Shipment.Priority.OVERNIGHT)));

        printSubHeading("5. Encapsulation: stock and status cannot be edited directly");
        attempt("receive(0) on an item",
                () -> service.findWarehouse("WH01").findItem("BOLT-101").receive(0));
        attempt("setUnitPrice(-10) on an item",
                () -> service.findWarehouse("WH01").findItem("BOLT-101").setUnitPrice(-10));
        attempt("Add a line to DELIVERED SH-001",
                () -> service.findShipment("SH-001").addLine(
                        new ShipmentLine(service.findWarehouse("WH01").findItem("BOLT-101"), 1, "")));
        System.out.println("  (No setQtyOnHand() or setStatus() exists: stock changes only through");
        System.out.println("   receive()/pickPack(), status only through dispatch()/updateStatus())");
    }

    private static void runValidationTests() {
        printHeading("VALIDATION TESTS (INVALID INPUT MUST BE BLOCKED)");
        attempt("Duplicate shipment ID SH-001",
                () -> service.createShipment("SH-001", "WH01", "Pune", "DTDC",
                        Shipment.Priority.STANDARD, 1200, D0, order("BOLT-101", 1)));
        attempt("Invalid shipment ID 'S1'",
                () -> service.createShipment("S1", "WH01", "Pune", "DTDC",
                        Shipment.Priority.STANDARD, 1200, D0, order("BOLT-101", 1)));
        attempt("Unknown warehouse WH99",
                () -> service.createShipment("SH-009", "WH99", "Pune", "DTDC",
                        Shipment.Priority.STANDARD, 1200, D0, order("BOLT-101", 1)));
        attempt("Unknown SKU 'TV-999'",
                () -> service.createShipment("SH-009", "WH01", "Pune", "DTDC",
                        Shipment.Priority.STANDARD, 1200, D0, order("TV-999", 1)));
        attempt("Zero quantity in an order",
                () -> service.createShipment("SH-009", "WH01", "Pune", "DTDC",
                        Shipment.Priority.STANDARD, 1200, D0, order("BOLT-101", 0)));
        attempt("Blank carrier",
                () -> service.createShipment("SH-009", "WH01", "Pune", " ",
                        Shipment.Priority.STANDARD, 1200, D0, order("BOLT-101", 1)));
        attempt("Negative distance",
                () -> service.createShipment("SH-009", "WH01", "Pune", "DTDC",
                        Shipment.Priority.STANDARD, -5, D0, order("BOLT-101", 1)));
        attempt("Track unknown shipment SH-999", () -> service.track("SH-999"));
        attempt("Negative item price",
                () -> new Item("X-1", "Test", -5, 0, 1.0));
        attempt("Warehouse with zero capacity",
                () -> new Warehouse("WH09", "Test", "Nowhere", 0));
        System.out.println();
        System.out.println("Shipments stored after all invalid attempts: " + service.getShipmentCount()
                + " (unchanged)");
    }

    private static void runSelfChecks() {
        printHeading("AUTOMATIC SELF-CHECKS");
        Warehouse w1 = service.findWarehouse("WH01");
        Warehouse w2 = service.findWarehouse("WH02");
        check(service.getShipmentCount() == 4 && LogisticsService.getTotalShipmentsCreated() == 4
                && Shipment.getShipmentCount() == 4, "4 shipments stored; none added by invalid attempts");
        check(w1.getUsedCapacity() == 328, "WH01 holds 328 units after picks");
        check(w2.getUsedCapacity() == 156, "WH02 holds 156 units after picks");
        check(w1.findItem("YOG-302").getQtyOnHand() == 30,
                "expired YOG-302 stock untouched (30)");
        check(w1.findItem("MON-202").getQtyOnHand() == 20,
                "short pick did not reduce MON-202 at WH01 (20)");
        check(w1.findItem("BOLT-101").getQtyOnHand() == 130,
                "failed multi-item order rolled back: BOLT-101 still 130");
        check(service.listByStatus(Shipment.Status.CREATED).size() == 1
                && service.listByStatus(Shipment.Status.IN_TRANSIT).size() == 1
                && service.listByStatus(Shipment.Status.DELIVERED).size() == 2,
                "status counts: CREATED 1, IN_TRANSIT 1, DELIVERED 2");
        check(near(service.findShipment("SH-001").getTotalWeight(), 165.0), "SH-001 weight = 165.0 kg");
        check(near(service.findShipment("SH-001").shippingCost(), 9628.50),
                "SH-001 freight = 9,628.50");
        check(near(service.findShipment("SH-002").getHandlingCost(), 822.20),
                "SH-002 handling (fragile overrides) = 822.20");
        check(near(service.findShipment("SH-001").shippingCost(165.0), 2030.00),
                "weight-only shippingCost(165 kg) = 2,030.00");
        check(near(LogisticsService.estimateHandling(mixedItems(), 10), 1200.90),
                "polymorphic List<Item> handling for 10 units each = 1,200.90");
        check(near(service.calculateBill("SH-001"), 11751.03), "SH-001 total payable = 11,751.03");
        check(service.getInvoiceCount() == 3, "3 invoices recorded (CREATED SH-004 not billed)");
        check(near(service.getInvoicedTotal(), 17348.62), "invoiced total = 17,348.62");
        check(near(service.calculateTotalCost(), 18734.41), "all-shipment cost = 18,734.41");
        check(service.getAuditLog().size() > 0, "audit log has " + service.getAuditLog().size()
                + " entries");
        System.out.println();
        System.out.println("Self-checks passed: " + checksPassed + " / " + checksRun);
    }

    // ---------------- helpers ----------------

    private static List<Item> mixedItems() {
        List<Item> mixed = new ArrayList<>();
        mixed.add(service.findWarehouse("WH01").findItem("BOLT-101"));
        mixed.add(service.findWarehouse("WH01").findItem("GLASS-201"));
        mixed.add(service.findWarehouse("WH01").findItem("MON-202"));
        mixed.add(service.findWarehouse("WH01").findItem("CHOC-301"));
        return mixed;
    }

    private static Map<String, Integer> order(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    private static void receive(String whId, String sku, int qty) {
        Item item = copyOf(sku);
        service.inbound(whId, item, qty);
        System.out.printf("[RECEIVED] %-5s %4d x %-9s %-20s %-13s now %d on hand%n",
                whId, qty, sku, item.getName(), "(" + item.getItemType() + ")",
                service.findWarehouse(whId).findItem(sku).getQtyOnHand());
    }

    private static void createAndShow(String shipId, String whId, String dest, String carrier,
                                      Shipment.Priority priority, double km,
                                      Map<String, Integer> request) {
        Shipment s = service.createShipment(shipId, whId, dest, carrier, priority, km, D0, request);
        System.out.println("[CREATED] " + s.getShipId() + " | " + whId + " -> " + dest + " | "
                + carrier + " | " + priority.getLabel() + " | " + s.getTotalUnits() + " unit(s), "
                + String.format("%.1f", s.getTotalWeight()) + " kg | " + s.getStatus());
        for (ShipmentLine line : s.getLines()) {
            printWrapped("    " + line + ": ", "        ", line.getPackNote());
        }
    }

    private static void attempt(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  [OK]      " + label);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            System.out.println("  [BLOCKED] " + label);
            printWrapped("            -> ", "               ", ex.getMessage());
        }
    }

    private static void printWrapped(String firstPrefix, String continuationPrefix, String text) {
        StringBuilder line = new StringBuilder(firstPrefix);
        boolean lineIsEmpty = true;
        for (String word : text.split(" ")) {
            if (!lineIsEmpty && line.length() + 1 + word.length() > WIDTH) {
                System.out.println(line);
                line = new StringBuilder(continuationPrefix);
                lineIsEmpty = true;
            }
            if (!lineIsEmpty) {
                line.append(' ');
            }
            line.append(word);
            lineIsEmpty = false;
        }
        System.out.println(line);
    }

    private static void check(boolean condition, String label) {
        checksRun++;
        if (condition) {
            checksPassed++;
        }
        System.out.println("  [" + (condition ? "PASS" : "FAIL") + "] " + label);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.005;
    }

    private static void printHeading(String title) {
        stepNo++;
        System.out.println();
        System.out.println(LINE);
        System.out.println("STEP " + stepNo + ": " + title);
        System.out.println(LINE);
    }

    private static void printSubHeading(String title) {
        System.out.println();
        System.out.println("--- " + title);
    }

    private static void printBanner() {
        System.out.println(LINE);
        System.out.println("  WAREHOUSE AND SHIPPING SYSTEM - 23CSR303 OOP WITH JAVA - ASSIGNMENT 2");
        System.out.println("  Receive -> Pick/Pack -> Dispatch -> Track -> Bill");
        System.out.println(LINE);
    }

    private static void printFooter() {
        System.out.println();
        System.out.println(SUB_LINE);
        System.out.println("Program finished. All steps ran with built-in sample data.");
        System.out.println(SUB_LINE);
    }
}
