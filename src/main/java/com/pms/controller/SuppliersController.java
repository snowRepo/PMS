package com.pms.controller;

import com.pms.dao.SupplierDAO;
import com.pms.model.Supplier;
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

public class SuppliersController {

    private static final Logger logger = LoggerFactory.getLogger(SuppliersController.class);

    @FXML private TextField searchField;
    @FXML private TableView<Supplier> supplierTable;
    @FXML private TableColumn<Supplier, String> colName;
    @FXML private TableColumn<Supplier, String> colContact;
    @FXML private TableColumn<Supplier, String> colPhone;
    @FXML private TableColumn<Supplier, String> colEmail;
    @FXML private TableColumn<Supplier, String> colAddress;
    @FXML private TableColumn<Supplier, Supplier> colActions;
    @FXML private Pagination pagination;

    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final com.pms.dao.PurchaseDAO purchaseDAO = new com.pms.dao.PurchaseDAO();
    private final ObservableList<Supplier> supplierData = FXCollections.observableArrayList();
    private static final int ITEMS_PER_PAGE = 25;

    @FXML
    public void initialize() {
        setupSupplierTable();
        pagination.setPageFactory(this::createPage);
        searchField.textProperty().addListener((obs, o, n) -> triggerRefresh());
        refreshData();
    }

    private void setupSupplierTable() {
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        colContact.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getContact()));
        colPhone.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPhone()));
        colEmail.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmail()));
        colAddress.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAddress()));

        com.pms.util.UIUtil.setTooltipCellFactory(colName, colContact, colPhone, colEmail, colAddress);

        colActions.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colActions.setCellFactory(tc -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button delBtn = new Button("Delete");
            private final HBox pane = new HBox(8, editBtn, delBtn);
            {
                pane.setAlignment(Pos.CENTER);
                editBtn.setOnAction(e -> {
                    Supplier s = getItem();
                    if (s != null) showSupplierDialog(s);
                });
                delBtn.setOnAction(e -> {
                    Supplier s = getItem();
                    if (s != null) handleSupplierDelete(s);
                });
            }
            @Override
            protected void updateItem(Supplier s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setGraphic(null);
                } else {
                    setGraphic(pane);
                }
            }
        });

        supplierTable.setItems(supplierData);
    }

    @FXML
    public void handleAddSupplier() {
        showSupplierDialog(null);
    }

    private void showSupplierDialog(Supplier supplier) {
        boolean isEdit = supplier != null;
        Dialog<Supplier> dialog = new Dialog<>();
         dialog.initOwner(com.pms.util.Navigator.getStage());
        dialog.setTitle(isEdit ? "Edit Supplier" : "New Supplier");
        dialog.setHeaderText(isEdit ? "Update Supplier Details" : "Add Supplier Details");

        if (supplierTable.getScene() != null && supplierTable.getScene().getWindow() != null) {
            dialog.initOwner(supplierTable.getScene().getWindow());
        }

        ButtonType saveBtnType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField(isEdit ? supplier.getName() : "");
        TextField contactField = new TextField(isEdit ? supplier.getContact() : "");
        TextField phoneField = new TextField(isEdit ? supplier.getPhone() : "");
        TextField emailField = new TextField(isEdit ? supplier.getEmail() : "");
        TextField addressField = new TextField(isEdit ? supplier.getAddress() : "");

        grid.add(new Label("Company Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Contact Person:"), 0, 1);
        grid.add(contactField, 1, 1);
        grid.add(new Label("Phone:"), 0, 2);
        grid.add(phoneField, 1, 2);
        grid.add(new Label("Email:"), 0, 3);
        grid.add(emailField, 1, 3);
        grid.add(new Label("Address:"), 0, 4);
        grid.add(addressField, 1, 4);

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(nameField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveBtnType) {
                if (nameField.getText().trim().isEmpty()) {
                    Notifier.error("Supplier name cannot be empty.");
                    return null;
                }
                Supplier s = isEdit ? supplier : new Supplier();
                s.setName(nameField.getText().trim());
                s.setContact(contactField.getText().trim());
                s.setPhone(phoneField.getText().trim());
                s.setEmail(emailField.getText().trim());
                s.setAddress(addressField.getText().trim());
                return s;
            }
            return null;
        });

        com.pms.util.UIUtil.enableEnterToClick(dialog);

        dialog.showAndWait().ifPresent(s -> {
            try {
                if (isEdit) {
                    supplierDAO.update(s);
                    com.pms.dao.ActivityLogDAO.log("SUPPLIER_UPDATED", "Supplier updated: " + s.getName());
                    Notifier.success("Supplier updated.");
                } else {
                    supplierDAO.create(s);
                    com.pms.dao.ActivityLogDAO.log("SUPPLIER_CREATED", "Supplier added: " + s.getName());
                    Notifier.success("Supplier added.");
                }
                triggerRefresh();
            } catch (Exception e) {
                Notifier.error("Failed to save supplier: " + e.getMessage());
            } finally {
                Platform.runLater(supplierTable::requestFocus);
            }
        });
    }

    private void handleSupplierDelete(Supplier s) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(com.pms.util.Navigator.getStage());
        alert.setTitle("Delete Supplier");
        alert.setHeaderText("Delete " + s.getName() + "?");
        alert.setContentText("This will mark the supplier as inactive.");
        if (supplierTable.getScene() != null && supplierTable.getScene().getWindow() != null) {
            alert.initOwner(supplierTable.getScene().getWindow());
        }

        com.pms.util.UIUtil.enableEnterToClick(alert);

        alert.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) {
                try {
                    int pendingCount = purchaseDAO.countBySupplierAndStatus(s.getId(), "PENDING");
                    if (pendingCount > 0) {
                        Notifier.error("Cannot delete supplier with pending orders.");
                        return;
                    }
                    supplierDAO.delete(s.getId());
                    com.pms.dao.ActivityLogDAO.log("SUPPLIER_DELETED", "Supplier deleted: " + s.getName());
                    Notifier.success("Supplier deleted.");
                    triggerRefresh();
                } catch (Exception e) {
                    Notifier.error("Failed to delete supplier: " + e.getMessage());
                } finally {
                    Platform.runLater(supplierTable::requestFocus);
                }
            }
        });
    }

    private void triggerRefresh() {
        pagination.setCurrentPageIndex(0);
        refreshData();
    }

    private javafx.scene.Node createPage(int pageIndex) {
        loadPage(pageIndex);
        return new javafx.scene.layout.StackPane();
    }

    private void refreshData() {
        new Thread(() -> {
            try {
                String search = searchField.getText().trim();
                int totalItems = supplierDAO.countAll(search);
                int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
                
                Platform.runLater(() -> {
                    pagination.setPageCount(totalPages);
                    loadPage(pagination.getCurrentPageIndex());
                });
            } catch (SQLException e) {
                logger.error("Failed to count suppliers", e);
            }
        }).start();
    }

    private void loadPage(int pageIndex) {
        new Thread(() -> {
            try {
                String search = searchField.getText().trim();
                int offset = pageIndex * ITEMS_PER_PAGE;
                List<Supplier> items = supplierDAO.findPaginated(ITEMS_PER_PAGE, offset, search);
                Platform.runLater(() -> supplierData.setAll(items));
            } catch (SQLException e) {
                logger.error("Failed to fetch supplier page", e);
            }
        }).start();
    }
}
