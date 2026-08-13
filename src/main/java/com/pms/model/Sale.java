package com.pms.model;

import java.util.ArrayList;
import java.util.List;

/** Represents a sales transaction (header record). */
public class Sale {

    private String id;
    private String saleDate;
    private double totalAmount;
    private double discount;
    private double tax;
    private double amountPaid;
    private double changeAmount;
    private String paymentMethod;  // cash, card, mobile
    private String paymentRef;
    private String cashierId;
    private String cashierName;
    private String customerId;
    private String customerName;
    private String notes;
    private String createdAt;
    private List<SaleItem> items = new ArrayList<>();

    public Sale() {}

    // ─── Computed ─────────────────────────────────────────────────────────────

    public double getNetTotal() {
        return totalAmount - discount + tax;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public String getId()                          { return id; }
    public void setId(String id)                   { this.id = id; }

    public String getSaleDate()                    { return saleDate; }
    public void setSaleDate(String saleDate)       { this.saleDate = saleDate; }

    public double getTotalAmount()                 { return totalAmount; }
    public void setTotalAmount(double v)           { this.totalAmount = v; }

    public double getDiscount()                    { return discount; }
    public void setDiscount(double discount)       { this.discount = discount; }

    public double getTax()                         { return tax; }
    public void setTax(double tax)                 { this.tax = tax; }

    public double getAmountPaid()                  { return amountPaid; }
    public void setAmountPaid(double v)            { this.amountPaid = v; }

    public double getChangeAmount()                { return changeAmount; }
    public void setChangeAmount(double v)          { this.changeAmount = v; }

    public String getPaymentMethod()               { return paymentMethod; }
    public void setPaymentMethod(String v)         { this.paymentMethod = v; }
    
    public String getPaymentRef()                  { return paymentRef; }
    public void setPaymentRef(String v)            { this.paymentRef = v; }

    public String getCashierId()                   { return cashierId; }
    public void setCashierId(String cashierId)     { this.cashierId = cashierId; }
    
    public String getCashierName()                 { return cashierName; }
    public void setCashierName(String cashierName) { this.cashierName = cashierName; }
    
    public String getCustomerId()                  { return customerId; }
    public void setCustomerId(String v)            { this.customerId = v; }

    public String getCustomerName()                { return customerName; }
    public void setCustomerName(String v)          { this.customerName = v; }

    public String getNotes()                       { return notes; }
    public void setNotes(String notes)             { this.notes = notes; }

    public String getCreatedAt()                   { return createdAt; }
    public void setCreatedAt(String createdAt)     { this.createdAt = createdAt; }

    public List<SaleItem> getItems()               { return items; }
    public void setItems(List<SaleItem> items)     { this.items = items; }
    public void addItem(SaleItem item)             { this.items.add(item); }
}
