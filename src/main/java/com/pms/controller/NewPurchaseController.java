package com.pms.controller;

import com.pms.dao.ProductDAO;
import com.pms.dao.PurchaseDAO;
import com.pms.dao.SupplierDAO;
import com.pms.model.Product;
import com.pms.model.Purchase;
import com.pms.model.PurchaseItem;
import com.pms.model.Supplier;
import com.pms.util.CurrencyUtil;
import com.pms.util.DateTimeUtil;
import com.pms.util.Notifier;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NewPurchaseController {

    private static final Logger logger = LoggerFactory.getLogger(NewPurchaseController.class);

    @FXML private TextField searchField;
    @FXML private FlowPane productsFlowPane;
    @FXML private TextField supplierSearchField;
    @FXML private TableView<PurchaseItem> cartTable;
    @FXML private TableColumn<PurchaseItem, String> colCartName;
    @FXML private TableColumn<PurchaseItem, Integer> colCartQty;
    @FXML private TableColumn<PurchaseItem, String> colCartCost;
    @FXML private TableColumn<PurchaseItem, String> colCartSub;
    @FXML private TextArea notesArea;
    @FXML private Label netTotalLabel;

    private Supplier selectedSupplier;
    private ContextMenu autocompleteMenu = new ContextMenu();
    private List<Supplier> allActiveSuppliers;
    
    private final ProductDAO productDAO = new ProductDAO();
    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final PurchaseDAO purchaseDAO = new PurchaseDAO();
    
    private final ObservableList<Product> productsList = FXCollections.observableArrayList();
    private final ObservableList<PurchaseItem> cartList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupCartTable();
        loadSuppliers();
        loadProducts("");
        searchField.textProperty().addListener((obs, o, n) -> loadProducts(n));
        cartList.addListener((javafx.collections.ListChangeListener.Change<? extends PurchaseItem> c) -> updateTotals());
    }

    private void renderProductCards(List<Product> products) {
        productsFlowPane.getChildren().clear();
        for (Product p : products) {
            VBox card = new VBox(4);
            card.setAlignment(Pos.CENTER);
            card.setPrefSize(120, 100);
            
            card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e4e4e7; -fx-border-radius: 8; -fx-cursor: hand;");
            card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #f4f4f5; -fx-background-radius: 8; -fx-border-color: #039ED3; -fx-border-radius: 8; -fx-cursor: hand;"));
            card.setOnMouseExited(e -> card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e4e4e7; -fx-border-radius: 8; -fx-cursor: hand;"));

            Label nameLbl = new Label(p.getName());
            nameLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #18181b;");
            nameLbl.setWrapText(true);
            nameLbl.setAlignment(Pos.CENTER);
            nameLbl.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

            Label costLbl = new Label(CurrencyUtil.format(p.getCostPrice()));
            costLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #039ED3; -fx-font-weight: bold;");

            Label stockLbl = new Label(p.getStockQty() + " in stock");
            stockLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #71717a;");

            card.getChildren().addAll(nameLbl, costLbl, stockLbl);
            
            card.setOnMouseClicked(e -> addToCart(p));
            productsFlowPane.getChildren().add(card);
        }
    }

    private void setupCartTable() {
        colCartName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProductName()));
        colCartQty.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getQty()).asObject());
        colCartCost.setCellValueFactory(data -> new SimpleStringProperty(CurrencyUtil.format(data.getValue().getUnitCost())));
        colCartSub.setCellValueFactory(data -> new SimpleStringProperty(CurrencyUtil.format(data.getValue().getSubtotal())));
        cartTable.setItems(cartList);
        
        cartTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && cartTable.getSelectionModel().getSelectedItem() != null) {
                editCartItem(cartTable.getSelectionModel().getSelectedItem());
            }
        });
    }
    
    private void editCartItem(PurchaseItem item) {
        Dialog<PurchaseItem> dialog = new Dialog<>();
         dialog.initOwner(com.pms.util.Navigator.getStage());
        dialog.setTitle("Edit Item");
        dialog.setHeaderText("Edit Quantity and Unit Cost for " + item.getProductName());
        if (cartTable.getScene() != null && cartTable.getScene().getWindow() != null) {
            dialog.initOwner(cartTable.getScene().getWindow());
        }
        
        ButtonType saveBtnType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField qtyField = new TextField(String.valueOf(item.getQty()));
        TextField costField = new TextField(String.valueOf(item.getUnitCost()));
        
        grid.add(new Label("Quantity:"), 0, 0);
        grid.add(qtyField, 1, 0);
        grid.add(new Label("Unit Cost:"), 0, 1);
        grid.add(costField, 1, 1);
        
        dialog.getDialogPane().setContent(grid);
        Platform.runLater(qtyField::requestFocus);
        
        dialog.setResultConverter(b -> {
            if (b == saveBtnType) {
                try {
                    int q = Integer.parseInt(qtyField.getText().trim());
                    double c = Double.parseDouble(costField.getText().trim());
                    if (q > 0 && c >= 0) {
                        item.setQty(q);
                        item.setUnitCost(c);
                        item.recalculate();
                        return item;
                    }
                } catch (NumberFormatException ignored) {}
                Notifier.error("Invalid input. Please enter valid numbers.");
            }
            return null;
        });
        
        com.pms.util.UIUtil.enableEnterToClick(dialog);
        
        dialog.showAndWait().ifPresent(updatedItem -> {
            cartTable.refresh();
            updateTotals();
        });
        Platform.runLater(searchField::requestFocus);
    }

    private void loadSuppliers() {
        try {
            allActiveSuppliers = supplierDAO.findAllActive();
            
            supplierSearchField.textProperty().addListener((obs, oldV, newV) -> {
                if (selectedSupplier != null && !selectedSupplier.toString().equals(newV)) {
                    selectedSupplier = null;
                }
                
                if (newV == null || newV.trim().isEmpty()) {
                    autocompleteMenu.hide();
                    return;
                }
                
                if (selectedSupplier != null && selectedSupplier.toString().equals(newV)) {
                    return; 
                }

                String query = newV.toLowerCase();
                List<Supplier> filtered = allActiveSuppliers.stream()
                        .filter(s -> s.getName().toLowerCase().contains(query) || (s.getContact() != null && s.getContact().toLowerCase().contains(query)))
                        .toList();
                
                if (filtered.isEmpty()) {
                    autocompleteMenu.hide();
                } else {
                    autocompleteMenu.getItems().clear();
                    for (Supplier s : filtered) {
                        MenuItem item = new MenuItem(s.toString());
                        item.setOnAction(e -> {
                            selectedSupplier = s;
                            supplierSearchField.setText(s.toString());
                            autocompleteMenu.hide();
                        });
                        autocompleteMenu.getItems().add(item);
                    }
                    if (!autocompleteMenu.isShowing()) {
                        autocompleteMenu.show(supplierSearchField, javafx.geometry.Side.BOTTOM, 0, 0);
                    }
                }
            });
        } catch (Exception e) {
            logger.error("Failed to load suppliers", e);
        }
    }

    private void loadProducts(String query) {
        try {
            List<Product> products = productDAO.search(query);
            productsList.setAll(products);
            renderProductCards(products);
        } catch (Exception e) {
            logger.error("Failed to load products", e);
        }
    }

    private void addToCart(Product p) {
        for (PurchaseItem item : cartList) {
            if (item.getProductId().equals(p.getId())) {
                item.setQty(item.getQty() + 1);
                item.recalculate();
                cartTable.refresh();
                updateTotals();
                return;
            }
        }

        PurchaseItem newItem = new PurchaseItem();
        newItem.setProductId(p.getId());
        newItem.setProductName(p.getName());
        newItem.setQty(1);
        newItem.setUnitCost(p.getCostPrice());
        newItem.recalculate();
        cartList.add(newItem);
    }

    @FXML
    public void handleRemoveItem() {
        PurchaseItem selected = cartTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Notifier.error("Select an item to remove.");
            return;
        }
        cartList.remove(selected);
    }

    @FXML
    public void handleClearCart() {
        cartList.clear();
        updateTotals();
    }

    @FXML
    public void handleEditItemAction() {
        PurchaseItem selected = cartTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Notifier.error("Select an item to edit.");
            return;
        }
        editCartItem(selected);
    }

    private void updateTotals() {
        double subtotal = 0;
        for (PurchaseItem item : cartList) {
            subtotal += item.getSubtotal();
        }
        netTotalLabel.setText(CurrencyUtil.format(subtotal));
    }

    @FXML
    public void handleNewSupplier() {
        Dialog<Supplier> dialog = new Dialog<>();
         dialog.initOwner(com.pms.util.Navigator.getStage());
        dialog.setTitle("New Supplier");
        dialog.setHeaderText("Add Supplier Details");

        if (cartTable.getScene() != null && cartTable.getScene().getWindow() != null) {
            dialog.initOwner(cartTable.getScene().getWindow());
        }

        ButtonType saveBtnType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nameField = new TextField("");
        TextField contactField = new TextField("");
        TextField phoneField = new TextField("");
        TextField emailField = new TextField("");
        TextField addressField = new TextField("");

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
                Supplier s = new Supplier();
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
                supplierDAO.create(s);
                Notifier.success("Supplier added.");
                loadSuppliers();
                selectedSupplier = s;
                supplierSearchField.setText(s.toString());
            } catch (Exception e) {
                Notifier.error("Failed to save supplier: " + e.getMessage());
            }
        });
        Platform.runLater(searchField::requestFocus);
    }

    @FXML
    public void handlePlaceOrder() {
        if (cartList.isEmpty()) {
            Notifier.error("Purchase list is empty.");
            return;
        }
        
        if (selectedSupplier == null) {
            Notifier.error("Please select a supplier.");
            return;
        }

        double subtotal = cartList.stream().mapToDouble(PurchaseItem::getSubtotal).sum();

        try {
            Purchase purchase = new Purchase();
            purchase.setPurchaseDate(DateTimeUtil.now());
            purchase.setTotalAmount(subtotal);
            purchase.setNotes(notesArea.getText());
            purchase.setSupplierId(selectedSupplier.getId());
            purchase.setSupplierName(selectedSupplier.getName());
            purchase.setItems(cartList);
            
            // Save to DB (status becomes PENDING)
            purchaseDAO.create(purchase);
            
            com.pms.dao.ActivityLogDAO.log("ORDER_CREATED", "Purchase order placed to supplier: " + selectedSupplier.getName());
            Notifier.success("Purchase order placed successfully!");
            
            // Reset UI
            cartList.clear();
            notesArea.clear();
            supplierSearchField.clear();
            selectedSupplier = null;
            
        } catch (Exception e) {
            logger.error("Failed to place purchase order", e);
            Notifier.error("Failed to place purchase order: " + e.getMessage());
        }
    }
}
