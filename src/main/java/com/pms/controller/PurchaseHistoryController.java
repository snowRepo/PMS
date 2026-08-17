package com.pms.controller;

import com.pms.dao.PurchaseDAO;
import com.pms.model.Purchase;
import com.pms.model.PurchaseItem;
import com.pms.util.CurrencyUtil;
import com.pms.util.Notifier;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class PurchaseHistoryController {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseHistoryController.class);

    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private ComboBox<String> statusFilter;
    @FXML private TextField historySearchField;
    @FXML private TableView<Purchase> historyTable;
    @FXML private TableColumn<Purchase, String> colHistDate;
    @FXML private TableColumn<Purchase, String> colHistId;
    @FXML private TableColumn<Purchase, String> colHistSupplier;
    @FXML private TableColumn<Purchase, String> colHistTotal;
    @FXML private TableColumn<Purchase, String> colHistStatus;
    @FXML private TableColumn<Purchase, Purchase> colHistActions;
    @FXML private Pagination historyPagination;

    private final PurchaseDAO purchaseDAO = new PurchaseDAO();
    private final ObservableList<Purchase> historyData = FXCollections.observableArrayList();
    private static final int HIST_ITEMS_PER_PAGE = 25;

    @FXML
    public void initialize() {
        statusFilter.setItems(FXCollections.observableArrayList("All", "PENDING", "RECEIVED", "CANCELLED"));
        statusFilter.getSelectionModel().select("All");
        
        setupHistoryTable();
        historyPagination.setPageFactory(this::createHistoryPage);
        historySearchField.textProperty().addListener((obs, o, n) -> triggerHistoryRefresh());
        startDatePicker.valueProperty().addListener((obs, o, n) -> triggerHistoryRefresh());
        endDatePicker.valueProperty().addListener((obs, o, n) -> triggerHistoryRefresh());
        statusFilter.valueProperty().addListener((obs, o, n) -> triggerHistoryRefresh());
        refreshHistoryData();
    }

    private void setupHistoryTable() {
        colHistDate.setCellValueFactory(d -> new SimpleStringProperty(com.pms.util.DateTimeUtil.formatForDisplay(d.getValue().getPurchaseDate())));
        colHistId.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getId().substring(0, 8)));
        colHistSupplier.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSupplierName() != null ? d.getValue().getSupplierName() : "Unknown"));
        colHistTotal.setCellValueFactory(d -> new SimpleStringProperty(CurrencyUtil.format(d.getValue().getTotalAmount())));
        colHistStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));

        com.pms.util.UIUtil.setTooltipCellFactory(colHistDate, colHistId, colHistSupplier, colHistTotal, colHistStatus);

        colHistActions.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colHistActions.setCellFactory(tc -> new TableCell<>() {
            private final Button viewBtn = new Button("View");
            private final Button receiveBtn = new Button("Receive");
            private final Button cancelBtn = new Button("Cancel");
            private final HBox pane = new HBox(8, viewBtn, receiveBtn, cancelBtn);
            {
                pane.setAlignment(Pos.CENTER);
                viewBtn.setOnAction(e -> {
                    Purchase p = getItem();
                    if (p != null) showPurchaseDetails(p);
                });
                receiveBtn.setOnAction(e -> {
                    Purchase p = getItem();
                    if (p != null) handleReceiveStock(p);
                });
                cancelBtn.setOnAction(e -> {
                    Purchase p = getItem();
                    if (p != null) handleCancelOrder(p);
                });
            }
            @Override
            protected void updateItem(Purchase p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setGraphic(null);
                } else {
                    boolean isPending = "PENDING".equals(p.getStatus());
                    receiveBtn.setVisible(isPending);
                    receiveBtn.setManaged(isPending);
                    cancelBtn.setVisible(isPending);
                    cancelBtn.setManaged(isPending);
                    setGraphic(pane);
                }
            }
        });

        historyTable.setItems(historyData);
    }

    private void handleReceiveStock(Purchase p) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(com.pms.util.Navigator.getStage());
        alert.setTitle("Receive Stock");
        alert.setHeaderText("Receive stock for Order " + p.getId().substring(0,8) + "?");
        alert.setContentText("This will increase the inventory quantities for all items in this order. This action cannot be undone.");
        if (historyTable.getScene() != null && historyTable.getScene().getWindow() != null) {
            alert.initOwner(historyTable.getScene().getWindow());
        }

        com.pms.util.UIUtil.enableEnterToClick(alert);

        alert.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) {
                try {
                    purchaseDAO.receiveStock(p.getId());
                    com.pms.dao.ActivityLogDAO.log("ORDER_RECEIVED", "Received stock for order: " + p.getId().substring(0,8));
                    Notifier.success("Stock received successfully.");
                    triggerHistoryRefresh();
                } catch (Exception e) {
                    Notifier.error("Failed to receive stock: " + e.getMessage());
                } finally {
                    Platform.runLater(historyTable::requestFocus);
                }
            } else {
                Platform.runLater(historyTable::requestFocus);
            }
        });
    }

    private void handleCancelOrder(Purchase p) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(com.pms.util.Navigator.getStage());
        alert.setTitle("Cancel Order");
        alert.setHeaderText("Cancel Order " + p.getId().substring(0,8) + "?");
        alert.setContentText("This will mark the order as cancelled. This action cannot be undone.");
        if (historyTable.getScene() != null && historyTable.getScene().getWindow() != null) {
            alert.initOwner(historyTable.getScene().getWindow());
        }

        com.pms.util.UIUtil.enableEnterToClick(alert);

        alert.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) {
                try {
                    purchaseDAO.cancelOrder(p.getId());
                    com.pms.dao.ActivityLogDAO.log("ORDER_CANCELLED", "Cancelled purchase order: " + p.getId().substring(0,8));
                    Notifier.success("Order cancelled.");
                    triggerHistoryRefresh();
                } catch (Exception e) {
                    Notifier.error("Failed to cancel order: " + e.getMessage());
                } finally {
                    Platform.runLater(historyTable::requestFocus);
                }
            } else {
                Platform.runLater(historyTable::requestFocus);
            }
        });
    }

    private void showPurchaseDetails(Purchase p) {
        Dialog<Void> dialog = new Dialog<>();
         dialog.initOwner(com.pms.util.Navigator.getStage());
        if (historyTable.getScene() != null && historyTable.getScene().getWindow() != null) {
            dialog.initOwner(historyTable.getScene().getWindow());
        }
        dialog.setTitle("Purchase Details - " + p.getId().substring(0,8));
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(15);
        content.setPadding(new Insets(15));
        content.setPrefWidth(500);

        VBox headerBox = new VBox(5);
        headerBox.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 10; -fx-border-color: #e5e7eb; -fx-border-radius: 5;");
        headerBox.getChildren().addAll(
            new Label("Date: " + com.pms.util.DateTimeUtil.formatForDisplay(p.getPurchaseDate())),
            new Label("Supplier: " + (p.getSupplierName() != null ? p.getSupplierName() : p.getSupplierId())),
            new Label("Status: " + p.getStatus())
        );

        VBox notesBox = new VBox(5);
        if (p.getNotes() != null && !p.getNotes().trim().isEmpty()) {
            notesBox.setStyle("-fx-background-color: #fffbeb; -fx-padding: 10; -fx-border-color: #fde68a; -fx-border-radius: 5;");
            Label notesLabel = new Label("Notes:");
            notesLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #92400e;");
            Label notesContent = new Label(p.getNotes());
            notesContent.setWrapText(true);
            notesContent.setStyle("-fx-text-fill: #92400e;");
            notesBox.getChildren().addAll(notesLabel, notesContent);
        }

        TableView<PurchaseItem> itemsTable = new TableView<>();
        itemsTable.setPrefHeight(200);
        
        TableColumn<PurchaseItem, String> colProd = new TableColumn<>("Product");
        colProd.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getProductName()));
        TableColumn<PurchaseItem, Integer> colQty = new TableColumn<>("Qty");
        colQty.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getQty()));
        TableColumn<PurchaseItem, String> colSub = new TableColumn<>("Subtotal");
        colSub.setCellValueFactory(d -> new SimpleStringProperty(CurrencyUtil.format(d.getValue().getSubtotal())));
        
        itemsTable.getColumns().addAll(colProd, colQty, colSub);
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        new Thread(() -> {
            try {
                List<PurchaseItem> items = purchaseDAO.findItemsByPurchaseId(p.getId());
                Platform.runLater(() -> itemsTable.setItems(FXCollections.observableArrayList(items)));
            } catch (SQLException e) {
                logger.error("Failed to load purchase items", e);
            }
        }).start();

        HBox totalBox = new HBox();
        totalBox.setAlignment(Pos.CENTER_RIGHT);
        Label totalLabel = new Label("Total: " + CurrencyUtil.format(p.getTotalAmount()));
        totalLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        totalBox.getChildren().add(totalLabel);

        content.getChildren().addAll(headerBox, notesBox, itemsTable, totalBox);
        dialog.getDialogPane().setContent(content);
        com.pms.util.UIUtil.enableEnterToClick(dialog);
        dialog.showAndWait();
        Platform.runLater(historyTable::requestFocus);
    }

    private void triggerHistoryRefresh() {
        historyPagination.setCurrentPageIndex(0);
        refreshHistoryData();
    }

    private String getHistSearch() { return historySearchField.getText().trim(); }
    private String getHistStartDate() { return startDatePicker.getValue() != null ? startDatePicker.getValue().toString() : null; }
    private String getHistEndDate() { return endDatePicker.getValue() != null ? endDatePicker.getValue().toString() : null; }
    private String getHistStatus() { return statusFilter.getValue(); }

    private javafx.scene.Node createHistoryPage(int pageIndex) {
        loadHistoryPage(pageIndex);
        return new javafx.scene.layout.StackPane();
    }

    private void refreshHistoryData() {
        new Thread(() -> {
            try {
                int totalItems = purchaseDAO.countFiltered(getHistSearch(), getHistStartDate(), getHistEndDate(), getHistStatus());
                int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / HIST_ITEMS_PER_PAGE));
                
                Platform.runLater(() -> {
                    historyPagination.setPageCount(totalPages);
                    loadHistoryPage(historyPagination.getCurrentPageIndex());
                });
            } catch (SQLException e) {
                logger.error("Failed to count purchases", e);
            }
        }).start();
    }

    private void loadHistoryPage(int pageIndex) {
        new Thread(() -> {
            try {
                int offset = pageIndex * HIST_ITEMS_PER_PAGE;
                List<Purchase> items = purchaseDAO.findFilteredPaginated(HIST_ITEMS_PER_PAGE, offset, getHistSearch(), getHistStartDate(), getHistEndDate(), getHistStatus());
                Platform.runLater(() -> historyData.setAll(items));
            } catch (SQLException e) {
                logger.error("Failed to fetch purchases page", e);
            }
        }).start();
    }
}
