package com.pms.controller;

import com.pms.dao.ReportDAO;
import com.pms.model.ReportItem;
import com.pms.util.CurrencyUtil;
import com.pms.util.Notifier;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.List;

public class ReportsController {

    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    
    @FXML private Label totalRevenueLabel;
    @FXML private Label totalCostLabel;
    @FXML private Label totalProfitLabel;
    
    @FXML private Button exportCsvBtn;
    
    @FXML private Pagination pagination;
    
    @FXML private TableView<ReportItem> reportsTable;
    @FXML private TableColumn<ReportItem, String> colProduct;
    @FXML private TableColumn<ReportItem, String> colCategory;
    @FXML private TableColumn<ReportItem, Integer> colQty;
    @FXML private TableColumn<ReportItem, String> colRevenue;
    @FXML private TableColumn<ReportItem, String> colProfit;

    private ReportDAO reportDAO;
    private List<ReportItem> allItems;
    private static final int ITEMS_PER_PAGE = 25;

    @FXML
    public void initialize() {
        reportDAO = new ReportDAO();
        
        startDatePicker.setValue(LocalDate.now());
        endDatePicker.setValue(LocalDate.now());

        // Format columns
        colRevenue.setCellValueFactory(cellData -> new SimpleStringProperty(CurrencyUtil.format(cellData.getValue().getTotalRevenue())));
        colProfit.setCellValueFactory(cellData -> new SimpleStringProperty(CurrencyUtil.format(cellData.getValue().getTotalProfit())));

        // Optional: Custom styling for profit (green for positive, red for negative)
        colProfit.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    ReportItem reportItem = getTableView().getItems().get(getIndex());
                    if (reportItem.getTotalProfit() > 0) {
                        setStyle("-fx-text-fill: #15803d; -fx-font-weight: bold;");
                    } else if (reportItem.getTotalProfit() < 0) {
                        setStyle("-fx-text-fill: #b91c1c; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #374151;");
                    }
                }
            }
        });
        
        pagination.setPageFactory(this::createPage);
        handleGenerateReport();
    }

    private javafx.scene.Node createPage(int pageIndex) {
        loadDataForPage(pageIndex);
        return new javafx.scene.layout.StackPane();
    }

    private void loadDataForPage(int pageIndex) {
        if (allItems == null || allItems.isEmpty()) {
            reportsTable.setItems(FXCollections.observableArrayList());
            return;
        }
        int fromIndex = pageIndex * ITEMS_PER_PAGE;
        int toIndex = Math.min(fromIndex + ITEMS_PER_PAGE, allItems.size());
        reportsTable.setItems(FXCollections.observableArrayList(allItems.subList(fromIndex, toIndex)));
    }

    @FXML
    private void handleGenerateReport() {
        if (startDatePicker.getValue() == null || endDatePicker.getValue() == null) {
            Notifier.error("Please select a valid date range.");
            return;
        }

        String startDate = startDatePicker.getValue().toString();
        String endDate = endDatePicker.getValue().toString();

        try {
            allItems = reportDAO.generateSalesReport(startDate, endDate);
            
            int totalPages = Math.max(1, (int) Math.ceil((double) allItems.size() / ITEMS_PER_PAGE));
            pagination.setPageCount(totalPages);
            pagination.setCurrentPageIndex(0);
            loadDataForPage(0);
            
            double sumRevenue = allItems.stream().mapToDouble(ReportItem::getTotalRevenue).sum();
            double sumProfit = allItems.stream().mapToDouble(ReportItem::getTotalProfit).sum();
            double sumCost = sumRevenue - sumProfit;
            
            totalRevenueLabel.setText(CurrencyUtil.format(sumRevenue));
            totalCostLabel.setText(CurrencyUtil.format(sumCost));
            totalProfitLabel.setText(CurrencyUtil.format(sumProfit));
            
            exportCsvBtn.setDisable(allItems.isEmpty());
            
        } catch (Exception e) {
            e.printStackTrace();
            Notifier.error("Failed to generate report.");
        }
    }

    @FXML
    private void handleExportCSV() {
        if (allItems == null || allItems.isEmpty()) {
            Notifier.error("No data to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Report as CSV");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        fileChooser.setInitialFileName("Sales_Report_" + LocalDate.now() + ".csv");

        Stage stage = (Stage) reportsTable.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                // Header
                writer.println("Product Name,Category,Qty Sold,Total Revenue,Total Cost,Total Profit");
                
                // Rows
                for (ReportItem item : allItems) {
                    writer.printf("\"%s\",\"%s\",%d,%.2f,%.2f,%.2f%n",
                            item.getProductName().replace("\"", "\"\""),
                            item.getCategory().replace("\"", "\"\""),
                            item.getQtySold(),
                            item.getTotalRevenue(),
                            item.getTotalRevenue() - item.getTotalProfit(),
                            item.getTotalProfit());
                }
                Notifier.success("Report exported successfully!");
            } catch (Exception e) {
                e.printStackTrace();
                Notifier.error("Failed to export report.");
            }
        }
    }
}
