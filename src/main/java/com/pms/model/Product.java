package com.pms.model;

/** Represents a product / product in inventory. */
public class Product {

    private String id;
    private String name;
    private String genericName;
    private String barcode;
    private String category;
    private String manufacturer;
    private String unit;
    private double costPrice;
    private double sellingPrice;
    private int stockQty;
    private int reorderLevel;
    private String expiryDate;
    private String description;
    private boolean active;
    private String createdAt;
    private String updatedAt;

    public Product() {}

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public String getId()                          { return id; }
    public void setId(String id)                   { this.id = id; }

    public String getName()                        { return name; }
    public void setName(String name)               { this.name = name; }

    public String getGenericName()                 { return genericName; }
    public void setGenericName(String v)           { this.genericName = v; }

    public String getBarcode()                     { return barcode; }
    public void setBarcode(String barcode)         { this.barcode = barcode; }

    public String getCategory()                    { return category; }
    public void setCategory(String category)       { this.category = category; }

    public String getManufacturer()                { return manufacturer; }
    public void setManufacturer(String v)          { this.manufacturer = v; }

    public String getUnit()                        { return unit; }
    public void setUnit(String unit)               { this.unit = unit; }

    public double getCostPrice()                   { return costPrice; }
    public void setCostPrice(double v)             { this.costPrice = v; }

    public double getSellingPrice()                { return sellingPrice; }
    public void setSellingPrice(double v)          { this.sellingPrice = v; }

    public int getStockQty()                       { return stockQty; }
    public void setStockQty(int stockQty)          { this.stockQty = stockQty; }

    public int getReorderLevel()                   { return reorderLevel; }
    public void setReorderLevel(int v)             { this.reorderLevel = v; }

    public String getExpiryDate()                  { return expiryDate; }
    public void setExpiryDate(String expiryDate)   { this.expiryDate = expiryDate; }

    public String getDescription()                 { return description; }
    public void setDescription(String v)           { this.description = v; }

    public boolean isActive()                      { return active; }
    public void setActive(boolean active)          { this.active = active; }

    public String getCreatedAt()                   { return createdAt; }
    public void setCreatedAt(String v)             { this.createdAt = v; }

    public String getUpdatedAt()                   { return updatedAt; }
    public void setUpdatedAt(String v)             { this.updatedAt = v; }

    /** True if stock is at or below the reorder threshold. */
    public boolean isLowStock() {
        return stockQty <= reorderLevel;
    }

    @Override
    public String toString() {
        return name + " [" + stockQty + " " + unit + "]";
    }
}
