package com.pms.controller;

import com.pms.dao.SaleDAO;
import com.pms.dao.UserDAO;
import com.pms.model.Sale;
import com.pms.model.SaleItem;
import com.pms.model.User;
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
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.List;

public class SalesController {

    private static final Logger logger = LoggerFactory.getLogger(SalesController.class);

    @FXML private TextField searchField;
    @FXML private ComboBox<User> userFilterCombo;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    
    @FXML private TableView<Sale> salesTable;
    @FXML private TableColumn<Sale, String> colDate;
    @FXML private TableColumn<Sale, String> colRef;
    @FXML private TableColumn<Sale, String> colCustomer;
    @FXML private TableColumn<Sale, String> colCashier;
    @FXML private TableColumn<Sale, String> colMethod;
    @FXML private TableColumn<Sale, String> colTotal;
    @FXML private TableColumn<Sale, Sale> colActions;
    @FXML private Pagination pagination;

    private static final int ITEMS_PER_PAGE = 25;
    private final SaleDAO saleDAO = new SaleDAO();
    private final UserDAO userDAO = new UserDAO();
    private final ObservableList<Sale> tableData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        setupFilters();
        loadUsers();
        pagination.setPageFactory(this::createPage);
        refreshData();
    }

    private void setupTableColumns() {
        colDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSaleDate()));
        colRef.setCellValueFactory(d -> {
            String ref = d.getValue().getPaymentRef();
            return new SimpleStringProperty((ref == null || ref.isEmpty()) ? d.getValue().getId().substring(0, 8) : ref);
        });
        colCustomer.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCustomerName() != null ? d.getValue().getCustomerName() : "Walk-in"));
        colCashier.setCellValueFactory(d -> {
            String name = d.getValue().getCashierName();
            return new SimpleStringProperty(name != null ? name : "Unknown");
        });
        colMethod.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPaymentMethod()));
        colTotal.setCellValueFactory(d -> new SimpleStringProperty(CurrencyUtil.format(d.getValue().getNetTotal())));

        colActions.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colActions.setCellFactory(tc -> new TableCell<>() {
            private final Button viewBtn = new Button("View");
            private final HBox pane = new HBox(viewBtn);
            {
                pane.setAlignment(Pos.CENTER);
                viewBtn.setOnAction(e -> {
                    Sale s = getItem();
                    if (s != null) showSaleDetails(s);
                });
            }
            @Override
            protected void updateItem(Sale s, boolean empty) {
                super.updateItem(s, empty);
                setGraphic((empty || s == null) ? null : pane);
            }
        });

        salesTable.setItems(tableData);
        salesTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && salesTable.getSelectionModel().getSelectedItem() != null) {
                showSaleDetails(salesTable.getSelectionModel().getSelectedItem());
            }
        });
    }

    private void setupFilters() {
        searchField.textProperty().addListener((obs, o, n) -> triggerRefresh());
        userFilterCombo.valueProperty().addListener((obs, o, n) -> triggerRefresh());
        startDatePicker.valueProperty().addListener((obs, o, n) -> triggerRefresh());
        endDatePicker.valueProperty().addListener((obs, o, n) -> triggerRefresh());
    }

    private void triggerRefresh() {
        pagination.setCurrentPageIndex(0);
        refreshData();
    }

    private void loadUsers() {
        new Thread(() -> {
            try {
                List<User> users = userDAO.findAll();
                Platform.runLater(() -> {
                    userFilterCombo.getItems().add(null); // Option for "All"
                    userFilterCombo.getItems().addAll(users);
                });
            } catch (SQLException e) {
                logger.error("Failed to load users", e);
            }
        }).start();
    }

    private javafx.scene.Node createPage(int pageIndex) {
        loadDataForPage(pageIndex);
        return new javafx.scene.layout.StackPane();
    }

    private String getSearch() { return searchField.getText().trim(); }
    private String getUserId() { return userFilterCombo.getValue() != null ? userFilterCombo.getValue().getId() : null; }
    private String getStartDate() { return startDatePicker.getValue() != null ? startDatePicker.getValue().toString() : null; }
    private String getEndDate() { return endDatePicker.getValue() != null ? endDatePicker.getValue().toString() : null; }

    private void refreshData() {
        new Thread(() -> {
            try {
                int totalItems = saleDAO.countFiltered(getSearch(), getUserId(), getStartDate(), getEndDate());
                int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
                
                Platform.runLater(() -> {
                    pagination.setPageCount(totalPages);
                    loadDataForPage(pagination.getCurrentPageIndex());
                });
            } catch (SQLException e) {
                logger.error("Failed to count sales", e);
                Platform.runLater(() -> Notifier.error("Failed to load sales data."));
            }
        }).start();
    }

    private void loadDataForPage(int pageIndex) {
        new Thread(() -> {
            try {
                int offset = pageIndex * ITEMS_PER_PAGE;
                List<Sale> items = saleDAO.findFilteredPaginated(ITEMS_PER_PAGE, offset, getSearch(), getUserId(), getStartDate(), getEndDate());
                Platform.runLater(() -> tableData.setAll(items));
            } catch (SQLException e) {
                logger.error("Failed to fetch sales page", e);
            }
        }).start();
    }

    private void showSaleDetails(Sale sale) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(salesTable.getScene().getWindow());
        dialog.setTitle("Sale Details - " + sale.getId().substring(0,8));
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(15);
        content.setPadding(new Insets(15));
        content.setPrefWidth(400);

        VBox headerBox = new VBox(5);
        headerBox.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 10; -fx-border-color: #e5e7eb; -fx-border-radius: 5; -fx-background-radius: 5;");
        headerBox.getChildren().addAll(
            new Label("Date: " + sale.getSaleDate()),
            new Label("Cashier: " + (sale.getCashierName() != null ? sale.getCashierName() : sale.getCashierId())),
            new Label("Customer: " + (sale.getCustomerName() != null ? sale.getCustomerName() : "Walk-in")),
            new Label("Method: " + sale.getPaymentMethod())
        );
        if (sale.getPaymentRef() != null && !sale.getPaymentRef().isEmpty()) {
            headerBox.getChildren().add(new Label("Reference: " + sale.getPaymentRef()));
        }

        TableView<SaleItem> itemsTable = new TableView<>();
        itemsTable.setPrefHeight(200);
        
        TableColumn<SaleItem, String> colProd = new TableColumn<>("Product");
        colProd.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getProductName()));
        TableColumn<SaleItem, Integer> colQty = new TableColumn<>("Qty");
        colQty.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getQty()));
        TableColumn<SaleItem, String> colSub = new TableColumn<>("Subtotal");
        colSub.setCellValueFactory(d -> new SimpleStringProperty(CurrencyUtil.format(d.getValue().getSubtotal())));
        
        itemsTable.getColumns().addAll(colProd, colQty, colSub);
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        new Thread(() -> {
            try {
                List<SaleItem> items = saleDAO.findItemsBySaleId(sale.getId());
                Platform.runLater(() -> itemsTable.setItems(FXCollections.observableArrayList(items)));
            } catch (SQLException e) {
                logger.error("Failed to load sale items", e);
            }
        }).start();

        VBox summaryBox = new VBox(5);
        summaryBox.setAlignment(Pos.CENTER_RIGHT);
        
        Label subtotalLabel = new Label("Subtotal: " + CurrencyUtil.format(sale.getTotalAmount()));
        subtotalLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #4b5563;");
        summaryBox.getChildren().add(subtotalLabel);

        if (sale.getDiscount() > 0) {
            Label discountLabel = new Label("Discount: -" + CurrencyUtil.format(sale.getDiscount()));
            discountLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #ef4444;");
            summaryBox.getChildren().add(discountLabel);
        }

        if (sale.getTax() > 0) {
            Label taxLabel = new Label("Tax: " + CurrencyUtil.format(sale.getTax()));
            taxLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #4b5563;");
            summaryBox.getChildren().add(taxLabel);
        }

        Label totalLabel = new Label("Net Total: " + CurrencyUtil.format(sale.getNetTotal()));
        totalLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #18181b; -fx-padding: 5 0 0 0;");
        summaryBox.getChildren().add(totalLabel);

        content.getChildren().addAll(headerBox, itemsTable, summaryBox);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    @FXML
    private void handleExport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Sales History");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fileChooser.showSaveDialog(salesTable.getScene().getWindow());
        
        if (file != null) {
            new Thread(() -> {
                try {
                    List<Sale> allSales = saleDAO.findAllFiltered(getSearch(), getUserId(), getStartDate(), getEndDate());
                    try (PrintWriter writer = new PrintWriter(file)) {
                        writer.println("Date,ID,Customer,Cashier,Method,Reference,Subtotal,Discount,Tax,Net Total");
                        for (Sale s : allSales) {
                            String cust = s.getCustomerName() != null ? s.getCustomerName() : "Walk-in";
                            String cash = s.getCashierName() != null ? s.getCashierName() : "Unknown";
                            writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%.2f,%.2f,%.2f,%.2f%n",
                                s.getSaleDate(), s.getId(), cust, cash, s.getPaymentMethod(), 
                                s.getPaymentRef() != null ? s.getPaymentRef() : "", 
                                s.getTotalAmount(), s.getDiscount(), s.getTax(), s.getNetTotal()
                            );
                        }
                    }
                    Platform.runLater(() -> Notifier.success("Export completed successfully."));
                } catch (Exception e) {
                    logger.error("Failed to export sales", e);
                    Platform.runLater(() -> Notifier.error("Export failed: " + e.getMessage()));
                }
            }).start();
        }
    }
}
