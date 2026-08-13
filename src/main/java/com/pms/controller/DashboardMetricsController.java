package com.pms.controller;

import com.pms.dao.ProductDAO;
import com.pms.dao.SaleDAO;
import com.pms.model.Product;
import com.pms.util.CurrencyUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;

public class DashboardMetricsController {

    @FXML private Label salesLabel;
    @FXML private Label todaySalesLabel;
    @FXML private Label productsLabel;
    
    @FXML private TableView<Product> lowStockTable;
    @FXML private TableColumn<Product, String> colName;
    @FXML private TableColumn<Product, Integer> colStock;
    @FXML private TableColumn<Product, Integer> colReorder;
    @FXML private Pagination pagination;

    private static final int ITEMS_PER_PAGE = 25;
    private final ProductDAO productDAO = new ProductDAO();
    private final SaleDAO saleDAO = new SaleDAO();
    private final ObservableList<Product> tableData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable();
        loadMetrics();
        
        pagination.setPageFactory(this::createPage);
        refreshLowStockData();
    }

    private void setupTable() {
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colStock.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getStockQty()).asObject());
        colReorder.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getReorderLevel()).asObject());
        
        lowStockTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        lowStockTable.setItems(tableData);
    }

    private javafx.scene.Node createPage(int pageIndex) {
        loadDataForPage(pageIndex);
        return new javafx.scene.layout.StackPane();
    }

    private void refreshLowStockData() {
        new Thread(() -> {
            try {
                int totalItems = productDAO.countLowStock();
                int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
                
                Platform.runLater(() -> {
                    pagination.setPageCount(totalPages);
                    loadDataForPage(pagination.getCurrentPageIndex());
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void loadDataForPage(int pageIndex) {
        new Thread(() -> {
            try {
                int offset = pageIndex * ITEMS_PER_PAGE;
                List<Product> items = productDAO.getPaginatedLowStock(ITEMS_PER_PAGE, offset);
                Platform.runLater(() -> tableData.setAll(items));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void loadMetrics() {
        new Thread(() -> {
            try {
                double totalSales = saleDAO.getTotalRevenue();
                double todaySales = saleDAO.getTodayRevenue();
                int totalProducts = productDAO.countAll("");

                Platform.runLater(() -> {
                    salesLabel.setText(CurrencyUtil.format(totalSales));
                    todaySalesLabel.setText(CurrencyUtil.format(todaySales));
                    productsLabel.setText(String.valueOf(totalProducts));
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
