package com.pms.controller;

import com.pms.dao.ProductDAO;
import com.pms.model.Product;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ProductFormController {

    private static final Logger logger = LoggerFactory.getLogger(ProductFormController.class);

    @FXML private Label titleLabel;
    @FXML private TextField nameField;
    @FXML private TextField genericField;
    @FXML private TextField barcodeField;
    @FXML private TextField categoryField;
    @FXML private TextField costField;
    @FXML private TextField sellingField;
    @FXML private TextField stockField;
    @FXML private TextField reorderField;
    @FXML private DatePicker expiryPicker;
    @FXML private Label errorLabel;

    private final ProductDAO productDAO = new ProductDAO();
    private final com.pms.dao.CategoryDAO categoryDAO = new com.pms.dao.CategoryDAO();
    private Product currentProduct;
    private boolean saved = false;
    private ContextMenu autocompleteMenu = new ContextMenu();

    @FXML
    public void initialize() {
        // Autocomplete for Category Field
        categoryField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                autocompleteMenu.hide();
                return;
            }
            try {
                List<com.pms.model.Category> results = categoryDAO.findPaginated(10, 0, newVal.trim());
                autocompleteMenu.getItems().clear();
                if (!results.isEmpty()) {
                    for (com.pms.model.Category c : results) {
                        MenuItem item = new MenuItem(c.getName());
                        item.setOnAction(e -> {
                            categoryField.setText(c.getName());
                            categoryField.positionCaret(c.getName().length());
                        });
                        autocompleteMenu.getItems().add(item);
                    }
                    if (!autocompleteMenu.isShowing()) {
                        autocompleteMenu.show(categoryField, javafx.geometry.Side.BOTTOM, 0, 0);
                    }
                } else {
                    autocompleteMenu.hide();
                }
            } catch (Exception e) {
                logger.error("Failed to search categories", e);
            }
        });

        // Hide menu on focus loss
        categoryField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) autocompleteMenu.hide();
        });

        // Keyboard Shortcuts
        Platform.runLater(() -> {
            if (nameField.getScene() != null) {
                nameField.getScene().setOnKeyPressed(e -> {
                    if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                        handleCancel();
                    } else if (e.getCode() == javafx.scene.input.KeyCode.S && e.isShortcutDown()) {
                        handleSave();
                    }
                });
            }
        });

        // Barcode Scanner Integration — push this modal's listener onto the stack.
        // When this modal closes, popListener() restores the parent screen's listener.
        com.pms.util.BarcodeScannerManager.getInstance().pushListener(this::onBarcodeScanned);
    }

    private void onBarcodeScanned(String barcode) {
        Platform.runLater(() -> {
            String currentBarcode = barcodeField.getText();
            if (currentBarcode != null && currentBarcode.equals(barcode)) {
                // Same product scanned while editing, bump stock
                try {
                    int qty = stockField.getText().isEmpty() ? 0 : Integer.parseInt(stockField.getText());
                    stockField.setText(String.valueOf(qty + 1));
                    com.pms.util.Notifier.success("Quantity +1");
                } catch (NumberFormatException e) {
                    stockField.setText("1");
                }
            } else {
                // Different product or empty barcode field
                barcodeField.setText(barcode);
            }
        });
    }

    public void setProduct(Product product) {
        this.currentProduct = product;
        titleLabel.setText("Edit Item");

        nameField.setText(product.getName());
        genericField.setText(product.getGenericName());
        barcodeField.setText(product.getBarcode());
        categoryField.setText(product.getCategory());
        costField.setText(String.valueOf(product.getCostPrice()));
        sellingField.setText(String.valueOf(product.getSellingPrice()));
        stockField.setText(String.valueOf(product.getStockQty()));
        reorderField.setText(String.valueOf(product.getReorderLevel()));

        if (product.getExpiryDate() != null && !product.getExpiryDate().isBlank()) {
            try {
                expiryPicker.setValue(LocalDate.parse(product.getExpiryDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            } catch (Exception e) {
                logger.warn("Could not parse expiry date: {}", product.getExpiryDate());
            }
        }
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    private void handleSave() {
        try {
            // Validation
            if (nameField.getText().isBlank()) throw new IllegalArgumentException("Name is required.");
            // Category is no longer required
            
            double cost = Double.parseDouble(costField.getText().trim());
            double selling = Double.parseDouble(sellingField.getText().trim());
            int stock = Integer.parseInt(stockField.getText().trim());
            int reorder = Integer.parseInt(reorderField.getText().trim());
            
            if (cost < 0 || selling < 0) throw new IllegalArgumentException("Prices cannot be negative.");
            if (stock < 0 || reorder < 0) throw new IllegalArgumentException("Quantities cannot be negative.");

            // Create or update model
            boolean isNew = (currentProduct == null);
            if (isNew) {
                currentProduct = new Product();
                currentProduct.setActive(true);
            }
            
            String name = nameField.getText().trim();
            String generic = genericField.getText().trim();
            if (generic.isEmpty()) {
                // Generate a unique generic name using part of the product name + unique characters
                String prefix = name.length() > 4 ? name.substring(0, 4).toUpperCase() : name.toUpperCase();
                generic = prefix + "-GEN-" + java.util.UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            }

            currentProduct.setName(name);
            currentProduct.setGenericName(generic);
            currentProduct.setBarcode(barcodeField.getText().trim());
            currentProduct.setCategory(categoryField.getText().trim());
            currentProduct.setCostPrice(cost);
            currentProduct.setSellingPrice(selling);
            currentProduct.setStockQty(stock);
            currentProduct.setReorderLevel(reorder);

            if (expiryPicker.getValue() != null) {
                currentProduct.setExpiryDate(expiryPicker.getValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            } else {
                currentProduct.setExpiryDate(null);
            }

            // Save to DB
            if (isNew) {
                productDAO.create(currentProduct);
                com.pms.dao.ActivityLogDAO.log("PRODUCT_CREATED", "Product added: " + currentProduct.getName());
            } else {
                productDAO.update(currentProduct);
                com.pms.dao.ActivityLogDAO.log("PRODUCT_UPDATED", "Product updated: " + currentProduct.getName());
            }

            saved = true;
            closeModal();

        } catch (NumberFormatException e) {
            showError("Please enter valid numbers for prices and quantities.");
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to save product", e);
            showError("Database error: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        closeModal();
    }

    private void closeModal() {
        // Pop this modal's barcode listener — parent screen resumes automatically
        com.pms.util.BarcodeScannerManager.getInstance().popListener();
        Stage stage = (Stage) nameField.getScene().getWindow();
        stage.close();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
