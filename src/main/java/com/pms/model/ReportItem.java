package com.pms.model;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.DoubleProperty;

public class ReportItem {
    private final StringProperty productName = new SimpleStringProperty();
    private final StringProperty category = new SimpleStringProperty();
    private final IntegerProperty qtySold = new SimpleIntegerProperty();
    private final DoubleProperty totalRevenue = new SimpleDoubleProperty();
    private final DoubleProperty totalProfit = new SimpleDoubleProperty();

    public ReportItem(String productName, String category, int qtySold, double totalRevenue, double totalProfit) {
        this.productName.set(productName);
        this.category.set(category);
        this.qtySold.set(qtySold);
        this.totalRevenue.set(totalRevenue);
        this.totalProfit.set(totalProfit);
    }

    public String getProductName() { return productName.get(); }
    public StringProperty productNameProperty() { return productName; }

    public String getCategory() { return category.get(); }
    public StringProperty categoryProperty() { return category; }

    public int getQtySold() { return qtySold.get(); }
    public IntegerProperty qtySoldProperty() { return qtySold; }

    public double getTotalRevenue() { return totalRevenue.get(); }
    public DoubleProperty totalRevenueProperty() { return totalRevenue; }

    public double getTotalProfit() { return totalProfit.get(); }
    public DoubleProperty totalProfitProperty() { return totalProfit; }
}
