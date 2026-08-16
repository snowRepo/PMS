package com.pms.model;

/** A single line item within a sale. */
public class SaleItem {

    private String id;
    private String saleId;
    private String productId;
    private String productName;   // display only, not persisted separately
    private int qty;
    private double unitPrice;
    private double costPrice;
    private double discount;
    private double subtotal;

    public SaleItem() {}

    public SaleItem(String id, String saleId, String productId, int qty,
                    double unitPrice, double costPrice, double discount) {
        this.id          = id;
        this.saleId      = saleId;
        this.productId  = productId;
        this.qty         = qty;
        this.unitPrice   = unitPrice;
        this.costPrice   = costPrice;
        this.discount    = discount;
        this.subtotal    = (qty * unitPrice) - discount;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public String getId()                          { return id; }
    public void setId(String id)                   { this.id = id; }

    public String getSaleId()                      { return saleId; }
    public void setSaleId(String saleId)           { this.saleId = saleId; }

    public String getProductId()                  { return productId; }
    public void setProductId(String v)            { this.productId = v; }

    public String getProductName()                { return productName; }
    public void setProductName(String v)          { this.productName = v; }

    public int getQty()                            { return qty; }
    public void setQty(int qty)                    { this.qty = qty; }

    public double getUnitPrice()                   { return unitPrice; }
    public void setUnitPrice(double v)             { this.unitPrice = v; }

    public double getCostPrice()                   { return costPrice; }
    public void setCostPrice(double v)             { this.costPrice = v; }

    public double getDiscount()                    { return discount; }
    public void setDiscount(double discount)       { this.discount = discount; }

    public double getSubtotal()                    { return subtotal; }
    public void setSubtotal(double subtotal)       { this.subtotal = subtotal; }

    public void recalculate() {
        this.subtotal = (qty * unitPrice) - discount;
    }
}
