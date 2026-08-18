package com.pms.controller;

import com.pms.dao.CustomerDAO;
import com.pms.dao.ProductDAO;
import com.pms.dao.SaleDAO;
import com.pms.model.Customer;
import com.pms.model.Product;
import com.pms.model.Sale;
import com.pms.model.SaleItem;
import com.pms.util.CurrencyUtil;
import com.pms.util.DateTimeUtil;
import com.pms.util.Notifier;
import com.pms.util.Session;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.geometry.Pos;

import java.util.Optional;
import java.util.List;

public class PosController {

    // Left Panel
    @FXML private TextField searchField;
    @FXML private FlowPane productsFlowPane;

    // Right Panel - Customer & Cart
    @FXML private TextField customerSearchField;
    private Customer selectedCustomer;
    private ContextMenu autocompleteMenu = new ContextMenu();
    private List<Customer> allActiveCustomers;

    @FXML private TableView<SaleItem> cartTable;
    @FXML private TableColumn<SaleItem, String> colCartName;
    @FXML private TableColumn<SaleItem, Integer> colCartQty;
    @FXML private TableColumn<SaleItem, String> colCartPrice;
    @FXML private TableColumn<SaleItem, String> colCartDisc;
    @FXML private TableColumn<SaleItem, String> colCartSub;

    // Checkout Details
    @FXML private TextField cartDiscountField;
    @FXML private TextField cartTaxField;
    @FXML private Label netTotalLabel;
    @FXML private ComboBox<String> paymentMethodCombo;
    @FXML private Label refLabel;
    @FXML private TextField refField;
    @FXML private TextField amountTenderedField;
    @FXML private Label changeLabel;
    @FXML private Button btnCheckout;

    private final ProductDAO productDAO = new ProductDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final SaleDAO saleDAO = new SaleDAO();
    
    private final ObservableList<Product> productsList = FXCollections.observableArrayList();
    private final ObservableList<SaleItem> cartList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        if ("cashier".equalsIgnoreCase(Session.current().getRole())) {
            try {
                com.pms.model.Shift activeShift = new com.pms.dao.ShiftDAO().getActiveShift(Session.current().getId());
                if (activeShift == null) {
                    Notifier.error("You must start a shift from the Dashboard before accessing the POS.");
                    // Disable all interactions
                    searchField.setDisable(true);
                    productsFlowPane.setDisable(true);
                    customerSearchField.setDisable(true);
                    cartTable.setDisable(true);
                    cartDiscountField.setDisable(true);
                    cartTaxField.setDisable(true);
                    amountTenderedField.setDisable(true);
                    paymentMethodCombo.setDisable(true);
                    btnCheckout.setDisable(true);
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        setupCartTable();
        setupPaymentMethods();
        loadCustomers();
        loadProducts("");

        // Listeners
        searchField.textProperty().addListener((obs, oldV, newV) -> loadProducts(newV));

        cartList.addListener((javafx.collections.ListChangeListener.Change<? extends SaleItem> c) -> updateTotals());
        cartDiscountField.textProperty().addListener((obs, oldV, newV) -> updateTotals());
        cartTaxField.textProperty().addListener((obs, oldV, newV) -> updateTotals());
        amountTenderedField.textProperty().addListener((obs, oldV, newV) -> updateTotals());
        
        paymentMethodCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            boolean reqRef = "Mobile Money".equals(newV) || "Card".equals(newV);
            refLabel.setVisible(reqRef);
            refLabel.setManaged(reqRef);
            refField.setVisible(reqRef);
            refField.setManaged(reqRef);
        });

        // Barcode Scanner Integration
        com.pms.util.BarcodeScannerManager.getInstance().setListener(this::onBarcodeScanned);
    }

    private void onBarcodeScanned(String barcode) {
        javafx.application.Platform.runLater(() -> {
            try {
                Product p = productDAO.findByBarcode(barcode);
                if (p != null) {
                    if (p.getStockQty() <= 0) {
                        Notifier.warning("Cannot add '" + p.getName() + "'. Out of stock.");
                    } else {
                        addToCart(p);
                    }
                } else {
                    Notifier.error("Product with barcode '" + barcode + "' not found.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void renderProductCards(List<Product> products) {
        productsFlowPane.getChildren().clear();
        for (Product p : products) {
            boolean isExpired = false;
            if (p.getExpiryDate() != null && !p.getExpiryDate().isEmpty()) {
                try {
                    java.time.LocalDate expDate = java.time.LocalDate.parse(p.getExpiryDate());
                    if (expDate.isBefore(java.time.LocalDate.now())) {
                        isExpired = true;
                    }
                } catch (Exception ex) {
                    // Ignore parsing errors
                }
            }

            VBox card = new VBox(4);
            card.setAlignment(Pos.CENTER);
            card.setPrefSize(120, 100);
            
            if (isExpired) {
                card.setStyle("-fx-background-color: #fef2f2; -fx-background-radius: 8; -fx-border-color: #ef4444; -fx-border-radius: 8; -fx-cursor: hand;");
            } else {
                card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e4e4e7; -fx-border-radius: 8; -fx-cursor: hand;");
                card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #f4f4f5; -fx-background-radius: 8; -fx-border-color: #039ED3; -fx-border-radius: 8; -fx-cursor: hand;"));
                card.setOnMouseExited(e -> card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e4e4e7; -fx-border-radius: 8; -fx-cursor: hand;"));
            }

            Label nameLbl = new Label(p.getName());
            nameLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #18181b;");
            nameLbl.setWrapText(true);
            nameLbl.setAlignment(Pos.CENTER);
            nameLbl.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

            Label priceLbl = new Label(CurrencyUtil.format(p.getSellingPrice()));
            priceLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #039ED3; -fx-font-weight: bold;");

            Label stockLbl = new Label(p.getStockQty() + " in stock");
            stockLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #71717a;");

            card.getChildren().addAll(nameLbl, priceLbl, stockLbl);
            
            if (isExpired) {
                Label expiredLbl = new Label("EXPIRED");
                expiredLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: white; -fx-background-color: #ef4444; -fx-padding: 2 6; -fx-background-radius: 4;");
                card.getChildren().add(expiredLbl);
            }

            final boolean expiredFinal = isExpired;
            card.setOnMouseClicked(e -> {
                if (expiredFinal) {
                    Notifier.error("Product has expired and cannot be sold.");
                    return;
                }
                if (p.getStockQty() <= 0) {
                    Notifier.error("Product is out of stock!");
                    return;
                }
                addToCart(p);
            });

            productsFlowPane.getChildren().add(card);
        }
    }

    private void setupCartTable() {
        colCartName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProductName()));
        colCartQty.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getQty()).asObject());
        colCartPrice.setCellValueFactory(data -> new SimpleStringProperty(CurrencyUtil.format(data.getValue().getUnitPrice())));
        colCartDisc.setCellValueFactory(data -> new SimpleStringProperty(CurrencyUtil.format(data.getValue().getDiscount())));
        colCartSub.setCellValueFactory(data -> new SimpleStringProperty(CurrencyUtil.format(data.getValue().getSubtotal())));
        cartTable.setItems(cartList);
    }

    private void setupPaymentMethods() {
        paymentMethodCombo.getItems().addAll("Cash", "Mobile Money", "Card");
        paymentMethodCombo.getSelectionModel().selectFirst();
    }

    private void loadCustomers() {
        try {
            allActiveCustomers = customerDAO.findAllActive();
            
            customerSearchField.textProperty().addListener((obs, oldV, newV) -> {
                if (selectedCustomer != null && !selectedCustomer.toString().equals(newV)) {
                    selectedCustomer = null;
                }
                
                if (newV == null || newV.trim().isEmpty()) {
                    autocompleteMenu.hide();
                    return;
                }
                
                if (selectedCustomer != null && selectedCustomer.toString().equals(newV)) {
                    return; // user just selected from menu
                }

                String query = newV.toLowerCase();
                List<Customer> filtered = allActiveCustomers.stream()
                        .filter(c -> c.getName().toLowerCase().contains(query) || (c.getPhone() != null && c.getPhone().toLowerCase().contains(query)))
                        .toList();
                
                if (filtered.isEmpty()) {
                    autocompleteMenu.hide();
                } else {
                    autocompleteMenu.getItems().clear();
                    for (Customer c : filtered) {
                        MenuItem item = new MenuItem(c.toString());
                        item.setOnAction(e -> {
                            selectedCustomer = c;
                            customerSearchField.setText(c.toString());
                            autocompleteMenu.hide();
                        });
                        autocompleteMenu.getItems().add(item);
                    }
                    if (!autocompleteMenu.isShowing()) {
                        autocompleteMenu.show(customerSearchField, javafx.geometry.Side.BOTTOM, 0, 0);
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadProducts(String query) {
        try {
            List<Product> products = productDAO.search(query);
            productsList.setAll(products);
            renderProductCards(products);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private void addToCart(Product p) {
        // Check if already in cart
        for (SaleItem item : cartList) {
            if (item.getProductId().equals(p.getId())) {
                if (item.getQty() >= p.getStockQty()) {
                    Notifier.error("Cannot add more than available stock.");
                    return;
                }
                item.setQty(item.getQty() + 1);
                item.recalculate();
                cartTable.refresh();
                updateTotals();
                return;
            }
        }

        SaleItem newItem = new SaleItem();
        newItem.setProductId(p.getId());
        newItem.setProductName(p.getName());
        newItem.setQty(1);
        newItem.setUnitPrice(p.getSellingPrice());
        newItem.setCostPrice(p.getCostPrice());
        newItem.setDiscount(0);
        newItem.recalculate();
        cartList.add(newItem);
    }

    @FXML
    public void handleRemoveItem() {
        SaleItem selected = cartTable.getSelectionModel().getSelectedItem();
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
    public void handleEditItemDiscount() {
        SaleItem selected = cartTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Notifier.error("Select an item to edit discount.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(String.valueOf(selected.getDiscount()));
        dialog.initOwner(com.pms.util.Navigator.getStage());
        dialog.setTitle("Edit Discount");
        dialog.setHeaderText("Enter discount for " + selected.getProductName());
        dialog.setContentText("Discount Amount (" + CurrencyUtil.getSymbol() + " | %):");
        
        // Attach to main window to prevent full-screen takeover issues on macOS
        if (cartTable.getScene() != null && cartTable.getScene().getWindow() != null) {
            dialog.initOwner(cartTable.getScene().getWindow());
        }

        com.pms.util.UIUtil.enableEnterToClick(dialog);

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(val -> {
            try {
                double disc = parseDiscount(val, selected.getUnitPrice());
                if (disc < 0) throw new NumberFormatException();
                if (disc > selected.getUnitPrice()) {
                    Notifier.error("Discount cannot exceed item price.");
                    return;
                }
                selected.setDiscount(disc);
                selected.recalculate();
                cartTable.refresh();
                updateTotals();
            } catch (NumberFormatException e) {
                Notifier.error("Invalid discount amount.");
            }
        });
        javafx.application.Platform.runLater(searchField::requestFocus);
    }

    @FXML
    public void handleNewCustomer() {
        Dialog<Customer> dialog = new Dialog<>();
        dialog.initOwner(com.pms.util.Navigator.getStage());
        dialog.setTitle("New Customer");
        dialog.setHeaderText("Add Customer Details");

        if (cartTable.getScene() != null && cartTable.getScene().getWindow() != null) {
            dialog.initOwner(cartTable.getScene().getWindow());
        }

        ButtonType saveBtnType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("Customer Name");
        TextField phoneField = new TextField();
        phoneField.setPromptText("Phone Number");
        TextField emailField = new TextField();
        emailField.setPromptText("Email Address");
        TextField addressField = new TextField();
        addressField.setPromptText("Address");

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Phone:"), 0, 1);
        grid.add(phoneField, 1, 1);
        grid.add(new Label("Email:"), 0, 2);
        grid.add(emailField, 1, 2);
        grid.add(new Label("Address:"), 0, 3);
        grid.add(addressField, 1, 3);

        dialog.getDialogPane().setContent(grid);
        javafx.application.Platform.runLater(nameField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveBtnType) {
                if (nameField.getText().trim().isEmpty()) {
                    Notifier.error("Customer name cannot be empty.");
                    return null;
                }
                Customer c = new Customer();
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
        result.ifPresent(c -> {
            try {
                customerDAO.create(c);
                Notifier.success("Customer added successfully.");
                loadCustomers();
                selectedCustomer = c;
                customerSearchField.setText(c.toString());
            } catch (Exception e) {
                Notifier.error("Failed to add customer: " + e.getMessage());
            }
        });
        javafx.application.Platform.runLater(searchField::requestFocus);
    }

    private void updateTotals() {
        double subtotal = 0;
        for (SaleItem item : cartList) {
            subtotal += item.getSubtotal();
        }

        double cartDisc = parseDiscount(cartDiscountField.getText(), subtotal);
        double tax = parseDiscount(cartTaxField.getText(), subtotal - cartDisc);
        double net = subtotal - cartDisc + tax;
        if (net < 0) net = 0;

        netTotalLabel.setText(CurrencyUtil.format(net));

        double tendered = parseDouble(amountTenderedField.getText());
        double change = tendered - net;
        changeLabel.setText(CurrencyUtil.format(change < 0 ? 0 : change));
    }

    private double parseDouble(String val) {
        try {
            return val == null || val.trim().isEmpty() ? 0 : Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double parseDiscount(String val, double baseAmount) {
        if (val == null || val.trim().isEmpty()) return 0;
        val = val.trim();
        try {
            if (val.endsWith("%")) {
                double pct = Double.parseDouble(val.substring(0, val.length() - 1));
                return baseAmount * (pct / 100.0);
            }
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @FXML
    public void handleCheckout() {
        if (cartList.isEmpty()) {
            Notifier.error("Cart is empty.");
            return;
        }

        double subtotal = cartList.stream().mapToDouble(SaleItem::getSubtotal).sum();
        double cartDisc = parseDiscount(cartDiscountField.getText(), subtotal);
        double tax = parseDiscount(cartTaxField.getText(), subtotal - cartDisc);
        double netTotal = subtotal - cartDisc + tax;
        double tendered = parseDouble(amountTenderedField.getText());

        if (tendered == 0) {
            Notifier.error("Please enter the amount tendered.");
            return;
        }
        if (tendered < netTotal) {
            Notifier.error("Amount tendered is less than net total.");
            return;
        }

        String pm = paymentMethodCombo.getValue();
        String ref = refField.getText().trim();
        if (("Mobile Money".equals(pm) || "Card".equals(pm)) && ref.isEmpty()) {
            Notifier.error("Transaction Reference is required for " + pm + ".");
            return;
        }

        try {
            Sale sale = new Sale();
            sale.setSaleDate(DateTimeUtil.now());
            sale.setTotalAmount(subtotal);
            sale.setDiscount(cartDisc);
            sale.setTax(tax);
            sale.setAmountPaid(tendered);
            sale.setChangeAmount(tendered > netTotal ? tendered - netTotal : 0);
            sale.setPaymentMethod(pm);
            sale.setPaymentRef(ref);
            sale.setCashierId(Session.current().getId());

            if (selectedCustomer != null) {
                sale.setCustomerId(selectedCustomer.getId());
                sale.setCustomerName(selectedCustomer.getName());
            }

            sale.setItems(cartList);
            
            // Save to DB
            Sale savedSale = saleDAO.create(sale);
            
            Notifier.success("Sale completed successfully!");
            
            // Print Receipt
            printReceipt(savedSale);

            // Reset UI
            cartList.clear();
            cartDiscountField.setText("0.00");
            cartTaxField.setText("0.00");
            amountTenderedField.setText("0.00");
            refField.clear();
            customerSearchField.clear();
            selectedCustomer = null;
            loadProducts(""); // Refresh stock
            
        } catch (Exception e) {
            e.printStackTrace();
            Notifier.error("Failed to complete sale: " + e.getMessage());
        }
    }

    private void printReceipt(Sale sale) {
        String receiptText = com.pms.util.ReceiptPdfGenerator.generateReceiptText(sale);

        TextArea textArea = new TextArea(receiptText);
        textArea.setEditable(false);
        textArea.setStyle("-fx-font-family: monospace;");
        textArea.setPrefRowCount(25);
        textArea.setPrefColumnCount(40);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(com.pms.util.Navigator.getStage());
        alert.initOwner(cartTable.getScene().getWindow());
        alert.setTitle("Receipt");
        alert.setHeaderText("Transaction Successful");
        alert.getDialogPane().setContent(textArea);
        
        ButtonType printBtn = new ButtonType("Print", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(printBtn, ButtonType.CLOSE);
        
        com.pms.util.UIUtil.enableEnterToClick(alert);
        
        alert.showAndWait().ifPresent(type -> {
            if (type == printBtn) {
                // In a real app we'd use PrinterJob here
                Notifier.success("Printing receipt...");
            }
        });
        javafx.application.Platform.runLater(searchField::requestFocus);
    }
}
