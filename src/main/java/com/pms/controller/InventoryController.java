package com.pms.controller;

import com.pms.dao.ProductDAO;
import com.pms.model.Product;
import com.pms.util.Navigator;
import com.pms.util.Notifier;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class InventoryController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);
    private static final int ITEMS_PER_PAGE = 50;

    @FXML private TextField searchField;
    @FXML private TableView<Product> inventoryTable;
    @FXML private TableColumn<Product, String> colName;
    @FXML private TableColumn<Product, String> colCategory;
    @FXML private TableColumn<Product, Double> colCost;
    @FXML private TableColumn<Product, Double> colSelling;
    @FXML private TableColumn<Product, Integer> colStock;
    @FXML private TableColumn<Product, String> colExpiry;
    @FXML private TableColumn<Product, Product> colActions;
    @FXML private Pagination pagination;

    private final ProductDAO productDAO = new ProductDAO();
    private final ObservableList<Product> tableData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTableColumns();
        setupSearch();
        
        // Let the pagination control load the data when its page changes
        pagination.setPageFactory(this::createPage);
        
        // Initial load
        refreshData();

        // Barcode Scanner Integration — push this screen's listener onto the stack
        com.pms.util.BarcodeScannerManager.getInstance().pushListener(this::onBarcodeScanned);
    }

    private void onBarcodeScanned(String barcode) {
        javafx.application.Platform.runLater(() -> {
            try {
                Product p = productDAO.findByBarcode(barcode);
                if (p != null) {
                    // Scenario A: Product exists, no modal open -> Open edit modal
                    openProductForm(p);
                } else {
                    // Scenario C: Product doesn't exist -> Open new modal and pre-fill
                    Product newP = new Product();
                    newP.setBarcode(barcode);
                    openProductForm(newP);
                }
            } catch (Exception e) {
                logger.error("Barcode scan error on inventory", e);
            }
        });
    }

    private void setupTableColumns() {
        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        colCategory.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory()));
        colStock.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getStockQty()).asObject());
        colExpiry.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getExpiryDate()));
        
        colCost.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getCostPrice()));
        colCost.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                setText((empty || price == null) ? null : com.pms.util.CurrencyUtil.format(price));
            }
        });

        colSelling.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getSellingPrice()));
        colSelling.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                setText((empty || price == null) ? null : com.pms.util.CurrencyUtil.format(price));
            }
        });

        // Stock Indicator Cell Factory
        colStock.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Integer stock, boolean empty) {
                super.updateItem(stock, empty);
                if (empty || stock == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Product med = getTableView().getItems().get(getIndex());
                    Label badge = new Label(String.valueOf(stock));
                    badge.setStyle("-fx-padding: 2 8; -fx-background-radius: 12; -fx-font-weight: bold; -fx-font-size: 11px;");
                    
                    if (med.isLowStock()) {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: #fee2e2; -fx-text-fill: #ef4444;"); // Red warning
                    } else {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: #d1fae5; -fx-text-fill: #065f46;"); // Deep green
                    }
                    
                    StackPane wrapper = new StackPane(badge);
                    wrapper.setAlignment(Pos.CENTER);
                    setGraphic(wrapper);
                    setText(null);
                }
            }
        });

        // Action Buttons Cell Factory
        colActions.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        colActions.setCellFactory(tc -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button delBtn = new Button("Delete");
            private final HBox pane = new HBox(8, editBtn, delBtn);

            {
                // Native buttons, perfectly centered
                pane.setAlignment(Pos.CENTER);
                
                editBtn.setOnAction(e -> {
                    Product med = getItem();
                    if (med != null) openProductForm(med);
                });
                
                delBtn.setOnAction(e -> {
                    Product med = getItem();
                    if (med != null) handleDelete(med);
                });
            }

            @Override
            protected void updateItem(Product med, boolean empty) {
                super.updateItem(med, empty);
                if (empty || med == null) {
                    setGraphic(null);
                } else {
                    setGraphic(pane);
                }
            }
        });

        inventoryTable.setItems(tableData);
        
        // Double click to edit
        inventoryTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && inventoryTable.getSelectionModel().getSelectedItem() != null) {
                openProductForm(inventoryTable.getSelectionModel().getSelectedItem());
            }
        });
    }

    private void setupSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            // Reset to page 0 on search
            pagination.setCurrentPageIndex(0);
            refreshData();
        });
    }

    private javafx.scene.Node createPage(int pageIndex) {
        loadDataForPage(pageIndex);
        return new StackPane(); // Pagination requires a node returned, but we are updating the table view directly
    }

    private void refreshData() {
        new Thread(() -> {
            try {
                String search = searchField.getText().trim();
                int totalItems = productDAO.countAll(search);
                int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
                
                Platform.runLater(() -> {
                    pagination.setPageCount(totalPages);
                    loadDataForPage(pagination.getCurrentPageIndex());
                });
            } catch (SQLException e) {
                logger.error("Failed to count products", e);
                Platform.runLater(() -> Notifier.error("Failed to load inventory data."));
            }
        }).start();
    }

    private void loadDataForPage(int pageIndex) {
        new Thread(() -> {
            try {
                String search = searchField.getText().trim();
                int offset = pageIndex * ITEMS_PER_PAGE;
                List<Product> items = productDAO.findPaginated(ITEMS_PER_PAGE, offset, search);
                
                Platform.runLater(() -> {
                    tableData.setAll(items);
                });
            } catch (SQLException e) {
                logger.error("Failed to fetch paginated products", e);
                Platform.runLater(() -> Notifier.error("Failed to fetch inventory page."));
            }
        }).start();
    }

    @FXML
    private void handleAddItem() {
        openProductForm(null);
    }

    private void openProductForm(Product product) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ProductForm.fxml"));
            Parent root = loader.load();
            ProductFormController ctrl = loader.getController();
            
            // If editing, pass the product object
            if (product != null) {
                ctrl.setProduct(product);
            }

            Stage modal = new Stage();
            modal.initOwner(Navigator.getStage());
            modal.initModality(Modality.WINDOW_MODAL);
            modal.setTitle(product == null ? "Add New Item" : "Edit Item");
            Scene scene = new Scene(root);
            com.pms.util.UIUtil.enableEnterToClick(scene);
            scene.getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
            modal.setScene(scene);
            modal.setResizable(false);
            
            modal.showAndWait();

            // Listener stack automatically restores InventoryController's listener
            // when ProductFormController calls popListener() on close.

            // Refresh table if changes were saved
            if (ctrl.isSaved()) {
                refreshData();
                Notifier.success("Inventory updated successfully.");
            }

        } catch (Exception e) {
            logger.error("Failed to open Product Form", e);
            Notifier.error("Failed to open form.");
        } finally {
            Platform.runLater(inventoryTable::requestFocus);
        }
    }

    private void handleDelete(Product med) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(com.pms.util.Navigator.getStage());
        alert.initOwner(inventoryTable.getScene().getWindow());
        alert.setTitle("Delete Item");
        alert.setHeaderText("Delete " + med.getName() + "?");
        alert.setContentText("This will permanently remove the item from the active inventory list. Are you sure?");
        
        // Wait for user confirmation
        com.pms.util.UIUtil.enableEnterToClick(alert);
        alert.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) {
                try {
                    // Soft delete: set active = false (0)
                    // TODO: add softDelete method to ProductDAO if it doesn't exist, or just use update
                    med.setActive(false);
                    // Wait, let's implement softDelete in ProductDAO to be safe and clean.
                    productDAO.delete(med.getId());
                    com.pms.dao.ActivityLogDAO.log("PRODUCT_DELETED", "Product deleted: " + med.getName());
                    Notifier.success("Item deleted.");
                    refreshData();
                } catch (Exception e) {
                    logger.error("Failed to delete item", e);
                    Notifier.error("Failed to delete item.");
                }
            }
        });
        Platform.runLater(inventoryTable::requestFocus);
    }
}
