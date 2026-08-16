package com.pms.model;

public class Shift {
    private String id;
    private String cashierId;
    private String cashierName; // Joined from users table
    private String startTime;
    private String endTime;
    
    private double startingCash;
    private double cashSales;
    private double momoSales;
    private double cardSales;
    
    private double expectedEndingCash;
    private double declaredEndingCash;
    private double discrepancy;
    private boolean discrepancyResolved;
    
    private String status; // 'ACTIVE', 'CLOSED'
    private String notes;

    public Shift() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCashierId() { return cashierId; }
    public void setCashierId(String cashierId) { this.cashierId = cashierId; }

    public String getCashierName() { return cashierName; }
    public void setCashierName(String cashierName) { this.cashierName = cashierName; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public double getStartingCash() { return startingCash; }
    public void setStartingCash(double startingCash) { this.startingCash = startingCash; }

    public double getCashSales() { return cashSales; }
    public void setCashSales(double cashSales) { this.cashSales = cashSales; }

    public double getMomoSales() { return momoSales; }
    public void setMomoSales(double momoSales) { this.momoSales = momoSales; }

    public double getCardSales() { return cardSales; }
    public void setCardSales(double cardSales) { this.cardSales = cardSales; }

    public double getExpectedEndingCash() { return expectedEndingCash; }
    public void setExpectedEndingCash(double expectedEndingCash) { this.expectedEndingCash = expectedEndingCash; }

    public double getDeclaredEndingCash() { return declaredEndingCash; }
    public void setDeclaredEndingCash(double declaredEndingCash) { this.declaredEndingCash = declaredEndingCash; }

    public double getDiscrepancy() { return discrepancy; }
    public void setDiscrepancy(double discrepancy) { this.discrepancy = discrepancy; }

    public boolean isDiscrepancyResolved() { return discrepancyResolved; }
    public void setDiscrepancyResolved(boolean discrepancyResolved) { this.discrepancyResolved = discrepancyResolved; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
