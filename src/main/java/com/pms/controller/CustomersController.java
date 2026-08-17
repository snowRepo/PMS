package com.pms.controller;

import com.pms.dao.CustomerDAO;
import com.pms.model.Customer;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class CustomersController {

    private static final Logger logger = LoggerFactory.getLogger(CustomersController.class);

    @FXML private TextField searchField;
    @FXML private TableView<Customer> customerTable;
    @FXML private TableColumn<Customer, String> colName;
    @FXML private TableColumn<Customer, String> colPhone;
    @FXML private TableColumn<Customer, String> colEmail;
    @FXML private TableColumn<Customer, String> colAddress;
    @FXML private TableColumn<Customer, Customer> colActions;
    @FXML private Pagination pagination;

    private static final int ITEMS_PER_PAGE = 25;
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final com.pms.dao.SaleDAO saleDAO = new com.pms.dao.SaleDAO();
    private final ObservableList<Customer> tableData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        setupSearch();
        pagination.setPageFactory(this::createPage);
        refreshData();
    }

    private void setupTableColumns() {
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colPhone.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPhone()));
        colEmail.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getEmail()));
        colAddress.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAddress()));

        com.pms.util.UIUtil.setTooltipCellFactory(colName, colPhone, colEmail, colAddress);

        colActions.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        colActions.setCellFactory(tc -> new TableCell<>() {
            private final Button purchasesBtn = new Button("Sales");
            private final Button editBtn = new Button("Edit");
            private final Button delBtn = new Button("Delete");
            private final HBox pane = new HBox(8, purchasesBtn, editBtn, delBtn);

            {
                pane.setAlignment(Pos.CENTER);
                
                purchasesBtn.setOnAction(e -> {
                    Customer c = getItem();
                    if (c != null) openPurchasesDialog(c);
                });

                editBtn.setOnAction(e -> {
                    Customer c = getItem();
                    if (c != null) openCustomerDialog(c);
                });
                
                delBtn.setOnAction(e -> {
                    Customer c = getItem();
                    if (c != null) handleDelete(c);
                });
            }

            @Override
            protected void updateItem(Customer c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) {
                    setGraphic(null);
                } else {
                    setGraphic(pane);
                }
            }
        });

        customerTable.setItems(tableData);
        
        customerTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && customerTable.getSelectionModel().getSelectedItem() != null) {
                openCustomerDialog(customerTable.getSelectionModel().getSelectedItem());
            }
        });
    }

    private void setupSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            pagination.setCurrentPageIndex(0);
            refreshData();
        });
    }

    private javafx.scene.Node createPage(int pageIndex) {
        loadDataForPage(pageIndex);
        return new javafx.scene.layout.StackPane();
    }

    private void refreshData() {
        new Thread(() -> {
            try {
                String search = searchField.getText().trim();
                int totalItems = customerDAO.countAll(search);
                int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
                
                Platform.runLater(() -> {
                    pagination.setPageCount(totalPages);
                    loadDataForPage(pagination.getCurrentPageIndex());
                });
            } catch (SQLException e) {
                logger.error("Failed to count customers", e);
                Platform.runLater(() -> Notifier.error("Failed to load customers data."));
            }
        }).start();
    }

    private void loadDataForPage(int pageIndex) {
        new Thread(() -> {
            try {
                String search = searchField.getText().trim();
                int offset = pageIndex * ITEMS_PER_PAGE;
                List<Customer> items = customerDAO.findPaginated(ITEMS_PER_PAGE, offset, search);
                
                Platform.runLater(() -> tableData.setAll(items));
            } catch (SQLException e) {
                logger.error("Failed to fetch paginated customers", e);
                Platform.runLater(() -> Notifier.error("Failed to fetch customers page."));
            }
        }).start();
    }

    @FXML
    private void handleAddCustomer() {
        openCustomerDialog(null);
    }

    private void openCustomerDialog(Customer existingCustomer) {
        Dialog<Customer> dialog = new Dialog<>();
         dialog.initOwner(com.pms.util.Navigator.getStage());
        dialog.initOwner(customerTable.getScene().getWindow());
        dialog.setTitle(existingCustomer == null ? "Add Customer" : "Edit Customer");
        dialog.setHeaderText(null);

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("Customer Name");
        if (existingCustomer != null) nameField.setText(existingCustomer.getName());

        TextField phoneField = new TextField();
        phoneField.setPromptText("Phone Number");
        if (existingCustomer != null) phoneField.setText(existingCustomer.getPhone());

        TextField emailField = new TextField();
        emailField.setPromptText("Email Address");
        if (existingCustomer != null) emailField.setText(existingCustomer.getEmail());

        TextField addressField = new TextField();
        addressField.setPromptText("Address");
        if (existingCustomer != null) addressField.setText(existingCustomer.getAddress());

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        
        grid.add(new Label("Phone:"), 0, 1);
        grid.add(phoneField, 1, 1);
        
        grid.add(new Label("Email:"), 0, 2);
        grid.add(emailField, 1, 2);
        
        grid.add(new Label("Address:"), 0, 3);
        grid.add(addressField, 1, 3);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(nameField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                if (nameField.getText().trim().isEmpty()) {
                    Notifier.error("Customer name cannot be empty.");
                    return null;
                }
                Customer c = existingCustomer != null ? existingCustomer : new Customer();
                c.setName(nameField.getText().trim());
                c.setPhone(phoneField.getText().trim());
                c.setEmail(emailField.getText().trim());
                c.setAddress(addressField.getText().trim());
                return c;
            }
            return null;
        });

        com.pms.util.UIUtil.enableEnterToClick(dialog);

        Optional<Customer> result = dialog.showAndWait();

        result.ifPresent(customer -> {
            try {
                if (customer.getId() == null) {
                    customerDAO.create(customer);
                    Notifier.success("Customer created.");
                } else {
                    customerDAO.update(customer);
                    Notifier.success("Customer updated.");
                }
                refreshData();
            } catch (SQLException e) {
                logger.error("Failed to save customer", e);
                Notifier.error("Failed to save customer.");
            }
        });
        Platform.runLater(customerTable::requestFocus);
    }

    private void handleDelete(Customer c) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(com.pms.util.Navigator.getStage());
        alert.initOwner(customerTable.getScene().getWindow());
        alert.setTitle("Delete Customer");
        alert.setHeaderText("Delete '" + c.getName() + "'?");
        alert.setContentText("Are you sure you want to permanently delete this customer?");
        
        com.pms.util.UIUtil.enableEnterToClick(alert);
        
        alert.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) {
                try {
                    customerDAO.delete(c.getId());
                    Notifier.success("Customer deleted.");
                    refreshData();
                } catch (SQLException e) {
                    logger.error("Failed to delete customer", e);
                    Notifier.error("Database error while deleting customer.");
                }
            }
        });
        Platform.runLater(customerTable::requestFocus);
    }

    private void openPurchasesDialog(Customer c) {
        Dialog<Void> dialog = new Dialog<>();
         dialog.initOwner(com.pms.util.Navigator.getStage());
        dialog.initOwner(customerTable.getScene().getWindow());
        dialog.setTitle("Purchases - " + c.getName());
        dialog.setHeaderText("Purchase History for " + c.getName());

        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TableView<com.pms.model.Sale> salesTable = new TableView<>();
        salesTable.setPrefWidth(600);
        salesTable.setPrefHeight(400);
        salesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<com.pms.model.Sale, String> colDate = new TableColumn<>("Date");
        colDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSaleDate()));

        TableColumn<com.pms.model.Sale, String> colAmount = new TableColumn<>("Total Amount");
        colAmount.setCellValueFactory(data -> new SimpleStringProperty(com.pms.util.CurrencyUtil.format(data.getValue().getNetTotal())));

        TableColumn<com.pms.model.Sale, String> colMethod = new TableColumn<>("Payment");
        colMethod.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPaymentMethod()));

        TableColumn<com.pms.model.Sale, String> colRef = new TableColumn<>("Ref");
        colRef.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPaymentRef()));

        TableColumn<com.pms.model.Sale, com.pms.model.Sale> colPdf = new TableColumn<>("Actions");
        colPdf.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        colPdf.setCellFactory(tc -> new TableCell<>() {
            private final Button pdfBtn = new Button("Save PDF");

            {
                pdfBtn.setOnAction(e -> {
                    com.pms.model.Sale sale = getItem();
                    if (sale != null) {
                        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
                        fileChooser.setTitle("Save Receipt as PDF");
                        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
                        String defaultName = "Receipt_" + sale.getId().substring(0,8).toUpperCase() + ".pdf";
                        fileChooser.setInitialFileName(defaultName);
                        
                        java.io.File file = fileChooser.showSaveDialog(dialog.getOwner());
                        if (file != null) {
                            try {
                                com.pms.util.ReceiptPdfGenerator.generateReceiptPdf(sale, file);
                                Notifier.success("PDF saved successfully!");
                            } catch (Exception ex) {
                                logger.error("Failed to save PDF", ex);
                                Notifier.error("Failed to save PDF: " + ex.getMessage());
                            }
                        }
                    }
                });
            }

            @Override
            protected void updateItem(com.pms.model.Sale sale, boolean empty) {
                super.updateItem(sale, empty);
                if (empty || sale == null) {
                    setGraphic(null);
                } else {
                    setGraphic(pdfBtn);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        salesTable.getColumns().addAll(colDate, colAmount, colMethod, colRef, colPdf);

        new Thread(() -> {
            try {
                List<com.pms.model.Sale> sales = saleDAO.findByCustomer(c.getId());
                Platform.runLater(() -> salesTable.setItems(FXCollections.observableArrayList(sales)));
            } catch (SQLException e) {
                logger.error("Failed to load purchases", e);
                Platform.runLater(() -> Notifier.error("Failed to load purchases."));
            }
        }).start();

        dialog.getDialogPane().setContent(salesTable);
        com.pms.util.UIUtil.enableEnterToClick(dialog);
        dialog.showAndWait();
        Platform.runLater(customerTable::requestFocus);
    }
}
