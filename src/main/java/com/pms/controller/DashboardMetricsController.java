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
    
    @FXML private javafx.scene.layout.VBox shiftWidget;
    @FXML private Label shiftStatusLabel;
    @FXML private javafx.scene.control.Button btnShiftAction;

    private static final int ITEMS_PER_PAGE = 25;
    private final ProductDAO productDAO = new ProductDAO();
    private final SaleDAO saleDAO = new SaleDAO();
    private final com.pms.dao.ShiftDAO shiftDAO = new com.pms.dao.ShiftDAO();
    private final ObservableList<Product> tableData = FXCollections.observableArrayList();
    private com.pms.model.Shift activeShift;

    @FXML
    public void initialize() {
        setupTable();
        loadMetrics();
        
        pagination.setPageFactory(this::createPage);
        refreshLowStockData();
        
        String role = com.pms.util.Session.current().getRole();
        if ("cashier".equalsIgnoreCase(role)) {
            shiftWidget.setVisible(true);
            shiftWidget.setManaged(true);
            checkShiftStatus();
        } else {
            shiftWidget.setVisible(false);
            shiftWidget.setManaged(false);
        }
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
                String role = com.pms.util.Session.current().getRole();
                String userId = com.pms.util.Session.current().getId();
                
                double totalSales;
                double todaySales;
                
                if ("cashier".equalsIgnoreCase(role)) {
                    totalSales = saleDAO.getTotalRevenueByCashier(userId);
                    todaySales = saleDAO.getTodayRevenueByCashier(userId);
                } else {
                    totalSales = saleDAO.getTotalRevenue();
                    todaySales = saleDAO.getTodayRevenue();
                }
                
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
    
    private void checkShiftStatus() {
        new Thread(() -> {
            try {
                String userId = com.pms.util.Session.current().getId();
                activeShift = shiftDAO.getActiveShift(userId);
                Platform.runLater(() -> updateShiftUI());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void updateShiftUI() {
        if (activeShift != null) {
            shiftStatusLabel.setText("Active Shift: Started at " + activeShift.getStartTime());
            shiftStatusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
            btnShiftAction.setText("Close Shift");
        } else {
            shiftStatusLabel.setText("No Active Shift");
            shiftStatusLabel.setStyle("-fx-text-fill: #71717a; -fx-font-weight: bold;");
            btnShiftAction.setText("Start Shift");
        }
    }
    
    @FXML
    public void handleShiftAction() {
        if (activeShift != null) {
            closeShift();
        } else {
            startShift();
        }
    }
    
    private void startShift() {
        try {
            com.pms.model.Shift lastShift = shiftDAO.getLastClosedShift();
            double expectedCash = lastShift != null ? lastShift.getDeclaredEndingCash() : 0.0;
            
            javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
            dialog.initOwner(com.pms.util.Navigator.getStage());
            dialog.setTitle("Start Shift");
            dialog.setHeaderText("Expected cash in drawer: " + CurrencyUtil.format(expectedCash) + "\n\nAny discrepancy will be reported.");
            dialog.setContentText("Declare actual starting cash:");
            
            com.pms.util.UIUtil.enableEnterToClick(dialog);
            
            java.util.Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                try {
                    double declared = Double.parseDouble(result.get().trim());
                    com.pms.model.Shift newShift = new com.pms.model.Shift();
                    newShift.setCashierId(com.pms.util.Session.current().getId());
                    newShift.setStartingCash(declared);
                    
                    if (Math.abs(expectedCash - declared) > 0.01) {
                        newShift.setNotes("Starting cash discrepancy! Expected " + CurrencyUtil.format(expectedCash) + " but got " + CurrencyUtil.format(declared));
                    }
                    
                    shiftDAO.startShift(newShift);
                    com.pms.dao.ActivityLogDAO.log("SHIFT_STARTED", "Cashier started shift with starting cash: " + CurrencyUtil.format(declared));
                    com.pms.util.Notifier.success("Shift started successfully.");
                    checkShiftStatus();
                } catch (NumberFormatException e) {
                    com.pms.util.Notifier.error("Invalid amount.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            com.pms.util.Notifier.error("Failed to start shift.");
        }
    }
    
    private void closeShift() {
        try {
            activeShift = shiftDAO.getActiveShift(com.pms.util.Session.current().getId());
            if (activeShift == null) {
                com.pms.util.Notifier.error("No active shift found to close.");
                return;
            }
            double cashSales = activeShift.getCashSales();
            double momoSales = activeShift.getMomoSales();
            double cardSales = activeShift.getCardSales();
            double totalSales = cashSales + momoSales + cardSales;
            double expectedEnd = activeShift.getStartingCash() + cashSales;
            
            javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
            dialog.initOwner(com.pms.util.Navigator.getStage());
            dialog.setTitle("Close Shift");
            dialog.setHeaderText("Shift Sales Breakdown & Closure");
            
            javafx.scene.control.ButtonType closeBtn = new javafx.scene.control.ButtonType("Close Shift", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(closeBtn, javafx.scene.control.ButtonType.CANCEL);
            
            javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(15);
            root.setStyle("-fx-padding: 10;");

            javafx.scene.layout.HBox cardsBox = new javafx.scene.layout.HBox(15);
            cardsBox.setAlignment(javafx.geometry.Pos.CENTER);
            
            cardsBox.getChildren().addAll(
                createStatCard("Cash", cashSales),
                createStatCard("Mobile Money", momoSales),
                createStatCard("Card", cardSales)
            );
            
            Label lblExpected = new Label("Expected in Drawer: " + CurrencyUtil.format(expectedEnd));
            lblExpected.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1d4ed8;");
            
            javafx.scene.layout.HBox expectedBox = new javafx.scene.layout.HBox(lblExpected);
            expectedBox.setAlignment(javafx.geometry.Pos.CENTER);
            expectedBox.setStyle("-fx-padding: 10 0;");
            
            javafx.scene.control.TextField amountField = new javafx.scene.control.TextField();
            amountField.setPromptText("Declared Cash");
            amountField.setStyle("-fx-font-size: 14px; -fx-padding: 8;");
            
            javafx.scene.control.TextArea notesField = new javafx.scene.control.TextArea();
            notesField.setPromptText("Notes (Required if discrepancy exists)");
            notesField.setPrefRowCount(3);
            notesField.setWrapText(true);
            
            root.getChildren().addAll(
                cardsBox,
                new javafx.scene.control.Separator(),
                expectedBox,
                new javafx.scene.control.Separator(),
                new Label("Declare Actual Cash:"),
                amountField,
                new Label("Notes:"),
                notesField
            );
            
            dialog.getDialogPane().setContent(root);
            Platform.runLater(amountField::requestFocus);
            
            final javafx.scene.control.Button btn = (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(closeBtn);
            btn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                try {
                    double declared = Double.parseDouble(amountField.getText().trim());
                    if (Math.abs(declared - expectedEnd) > 0.01 && notesField.getText().trim().isEmpty()) {
                        com.pms.util.Notifier.error("A discrepancy exists! You must provide a note.");
                        event.consume();
                    }
                } catch (NumberFormatException e) {
                    com.pms.util.Notifier.error("Invalid cash amount.");
                    event.consume();
                }
            });
            
            dialog.setResultConverter(b -> {
                if (b == closeBtn) {
                    return amountField.getText() + "|||" + notesField.getText();
                }
                return null;
            });
            
            com.pms.util.UIUtil.enableEnterToClick(dialog);
            
            java.util.Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                String[] parts = result.get().split("\\|\\|\\|");
                double declared = Double.parseDouble(parts[0].trim());
                String notes = parts.length > 1 ? parts[1].trim() : "";
                
                activeShift.setExpectedEndingCash(expectedEnd);
                activeShift.setDeclaredEndingCash(declared);
                activeShift.setDiscrepancy(declared - expectedEnd);
                
                String finalNotes = notes;
                activeShift.setNotes(finalNotes.trim());
                
                shiftDAO.closeShift(activeShift);
                com.pms.dao.ActivityLogDAO.log("SHIFT_CLOSED", "Cashier closed shift. Declared: " + CurrencyUtil.format(declared) + " Expected: " + CurrencyUtil.format(expectedEnd));
                com.pms.util.Notifier.success("Shift closed.");
                checkShiftStatus();
            }
        } catch (Exception e) {
            e.printStackTrace();
            com.pms.util.Notifier.error("Failed to close shift.");
        }
    }
    private javafx.scene.layout.VBox createStatCard(String title, double amount) {
        javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(5);
        card.setAlignment(javafx.geometry.Pos.CENTER);
        card.setPrefSize(120, 80);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e4e4e7; -fx-border-radius: 8; -fx-padding: 10;");
        
        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #71717a;");
        
        Label lblAmount = new Label(CurrencyUtil.format(amount));
        lblAmount.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #18181b;");
        
        card.getChildren().addAll(lblTitle, lblAmount);
        return card;
    }
}
