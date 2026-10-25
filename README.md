# WarehouseShipping

Console-based Warehouse & Shipping system in Java (OOP) - 23CSR303 Object Oriented Programming with Java, Assignment II, Question 11.

**Author:** Tharsan K (717825F155) - II IT, Section A, Karpagam College of Engineering

## What it does
Receive inventory into warehouses, pick/pack orders, create shipments, dispatch them, track `CREATED -> IN_TRANSIT -> DELIVERED`, and bill shipping charges (with 18% GST).

## Classes (package `com.warehouseshipping`)
| Class | Role |
|---|---|
| `Item` | sku, name, unitPrice, qtyOnHand, weight; `receive()`, `pickPack()`, `handlingCharge()` |
| `FragileItem` | extends `Item`; padded cartons, fragility surcharge |
| `PerishableItem` | extends `Item`; expiry check, cold-chain fee |
| `Warehouse` | whId, location, capacity, inventory list; capacity-guarded `receive()` |
| `ShipmentLine` | one item + quantity inside a shipment |
| `Shipment` | shipId, destination, carrier, status; `dispatch()`, `updateStatus()`, overloaded `shippingCost()` |
| `LogisticsService` | inbound/outbound processing, tracking, billing, reports |
| `WarehouseAppMain` | runs the full demo with built-in sample data and 17 self-checks |

## OOP concepts
- **Inheritance:** `FragileItem` / `PerishableItem` extend `Item`
- **Overloading:** `Shipment.shippingCost()`, `shippingCost(weight)`, `shippingCost(weight, distance, priority)`
- **Overriding:** `pickPack()`, `handlingCharge()`, `storageRule()` in the subclasses
- **Polymorphism:** `LogisticsService.estimateHandling(List<Item>, qty)` calls the overridden behaviour per item
- **Encapsulation:** stock changes only via `receive()`/`pickPack()`; status only via `dispatch()`/`updateStatus()`

## Run
```
javac -d out src/com/warehouseshipping/*.java
java -cp out com.warehouseshipping.WarehouseAppMain
```
Requires JDK 11 or later. Expected ending: `Self-checks passed: 17 / 17`.
