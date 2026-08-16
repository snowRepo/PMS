package com.pms.model;

public class PurchaseItem {
    private String id;
    private String purchaseId;
    private String productId;
    private String productName; // Optional display field
    private int qty;
    private double unitCost;
    private double subtotal;

    public PurchaseItem() {}

    public void recalculate() {
        this.subtotal = qty * unitCost;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPurchaseId() { return purchaseId; }
    public void setPurchaseId(String purchaseId) { this.purchaseId = purchaseId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }

    public double getUnitCost() { return unitCost; }
    public void setUnitCost(double unitCost) { this.unitCost = unitCost; }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
}
