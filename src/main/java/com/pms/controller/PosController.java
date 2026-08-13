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
import javafx.geometry.Pos;

import java.util.Optional;
import java.util.List;

public class PosController {

    // Left Panel
    @FXML private TextField searchField;
    @FXML private FlowPane productsFlowPane;

    // Right Panel - Customer & Cart
    @FXML private ComboBox<Customer> customerCombo;
    @FXML private TableView<SaleItem> cartTable;
    @FXML private TableColumn<SaleItem, String> colCartName;
    @FXML private TableColumn<SaleItem, Integer> colCartQty;
    @FXML private TableColumn<SaleItem, String> colCartPrice;
    @FXML private TableColumn<SaleItem, String> colCartDisc;
    @FXML private TableColumn<SaleItem, String> colCartSub;

    // Checkout Details
    @FXML private TextField cartDiscountField;
    @FXML private Label netTotalLabel;
    @FXML private ComboBox<String> paymentMethodCombo;
    @FXML private Label refLabel;
    @FXML private TextField refField;
    @FXML private TextField amountTenderedField;
    @FXML private Label changeLabel;

    private final ProductDAO productDAO = new ProductDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final SaleDAO saleDAO = new SaleDAO();
    
    private final ObservableList<Product> productsList = FXCollections.observableArrayList();
    private final ObservableList<SaleItem> cartList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupCartTable();
        setupPaymentMethods();
        loadCustomers();
        loadProducts("");

        // Listeners
        searchField.textProperty().addListener((obs, oldV, newV) -> loadProducts(newV));

        cartList.addListener((javafx.collections.ListChangeListener.Change<? extends SaleItem> c) -> updateTotals());
        cartDiscountField.textProperty().addListener((obs, oldV, newV) -> updateTotals());
        amountTenderedField.textProperty().addListener((obs, oldV, newV) -> updateTotals());
        
        paymentMethodCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            boolean reqRef = "Mobile Money".equals(newV) || "Card".equals(newV);
            refLabel.setVisible(reqRef);
            refLabel.setManaged(reqRef);
            refField.setVisible(reqRef);
            refField.setManaged(reqRef);
        });
    }

    private void renderProductCards(List<Product> products) {
        productsFlowPane.getChildren().clear();
        for (Product p : products) {
            VBox card = new VBox(4);
            card.setAlignment(Pos.CENTER);
            card.setPrefSize(120, 100);
            card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e4e4e7; -fx-border-radius: 8; -fx-cursor: hand;");
            
            // Hover effect
            card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #f4f4f5; -fx-background-radius: 8; -fx-border-color: #039ED3; -fx-border-radius: 8; -fx-cursor: hand;"));
            card.setOnMouseExited(e -> card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e4e4e7; -fx-border-radius: 8; -fx-cursor: hand;"));

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

            card.setOnMouseClicked(e -> {
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
            customerCombo.getItems().setAll(customerDAO.findAllActive());
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
        newItem.setDiscount(0);
        newItem.recalculate();
        cartList.add(newItem);
    }

    @FXML
    public void handleRemoveItem() {
        SaleItem selected = cartTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            cartList.remove(selected);
        }
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
        dialog.setTitle("Item Discount");
        dialog.setHeaderText("Enter discount for " + selected.getProductName());
        dialog.setContentText("Discount Amount (" + CurrencyUtil.getSymbol() + "):");
        
        // Attach to main window to prevent full-screen takeover issues on macOS
        if (cartTable.getScene() != null && cartTable.getScene().getWindow() != null) {
            dialog.initOwner(cartTable.getScene().getWindow());
        }

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(val -> {
            try {
                double disc = Double.parseDouble(val);
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
    }

    @FXML
    public void handleNewCustomer() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Customer");
        dialog.setHeaderText("Quick Add Customer");
        dialog.setContentText("Customer Name:");

        if (cartTable.getScene() != null && cartTable.getScene().getWindow() != null) {
            dialog.initOwner(cartTable.getScene().getWindow());
        }

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (name.trim().isEmpty()) return;
            try {
                Customer c = new Customer();
                c.setName(name.trim());
                c.setPhone("");
                c.setEmail("");
                c.setAddress("");
                customerDAO.create(c);
                loadCustomers();
                customerCombo.setValue(c); // select it
            } catch (Exception e) {
                Notifier.error("Failed to add customer: " + e.getMessage());
            }
        });
    }

    private void updateTotals() {
        double subtotal = 0;
        for (SaleItem item : cartList) {
            subtotal += item.getSubtotal();
        }

        double cartDisc = parseDouble(cartDiscountField.getText());
        double net = subtotal - cartDisc;
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

    @FXML
    public void handleCheckout() {
        if (cartList.isEmpty()) {
            Notifier.error("Cart is empty.");
            return;
        }

        double subtotal = cartList.stream().mapToDouble(SaleItem::getSubtotal).sum();
        double cartDisc = parseDouble(cartDiscountField.getText());
        double netTotal = subtotal - cartDisc;
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
            sale.setTax(0); // Not implemented yet
            sale.setAmountPaid(tendered);
            sale.setChangeAmount(tendered > netTotal ? tendered - netTotal : 0);
            sale.setPaymentMethod(pm);
            sale.setPaymentRef(ref);
            sale.setCashierId(Session.current().getId());

            Customer cust = customerCombo.getValue();
            if (cust != null) {
                sale.setCustomerId(cust.getId());
                sale.setCustomerName(cust.getName());
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
            amountTenderedField.setText("0.00");
            refField.clear();
            customerCombo.getSelectionModel().clearSelection();
            loadProducts(""); // Refresh stock
            
        } catch (Exception e) {
            e.printStackTrace();
            Notifier.error("Failed to complete sale: " + e.getMessage());
        }
    }

    private void printReceipt(Sale sale) {
        StringBuilder sb = new StringBuilder();
        sb.append("====================================\n");
        sb.append("          SALES RECEIPT\n");
        sb.append("====================================\n");
        sb.append("Receipt No: ").append(sale.getId().substring(0,8).toUpperCase()).append("\n");
        sb.append("Date: ").append(sale.getSaleDate()).append("\n");
        if (sale.getCustomerName() != null) {
            sb.append("Customer: ").append(sale.getCustomerName()).append("\n");
        }
        sb.append("------------------------------------\n");
        sb.append(String.format("%-20s %-5s %s\n", "Item", "Qty", "Total"));
        sb.append("------------------------------------\n");
        
        for (SaleItem item : sale.getItems()) {
            String name = item.getProductName();
            if (name.length() > 18) name = name.substring(0, 18) + "..";
            sb.append(String.format("%-20s %-5d %s\n", name, item.getQty(), CurrencyUtil.format(item.getSubtotal())));
        }
        
        sb.append("------------------------------------\n");
        sb.append(String.format("%-26s %s\n", "Subtotal:", CurrencyUtil.format(sale.getTotalAmount())));
        if (sale.getDiscount() > 0) {
            sb.append(String.format("%-26s %s\n", "Cart Discount:", "-" + CurrencyUtil.format(sale.getDiscount())));
        }
        sb.append(String.format("%-26s %s\n", "NET TOTAL:", CurrencyUtil.format(sale.getNetTotal())));
        sb.append("------------------------------------\n");
        sb.append(String.format("%-26s %s\n", "Amount Tendered:", CurrencyUtil.format(sale.getAmountPaid())));
        sb.append(String.format("%-26s %s\n", "Change:", CurrencyUtil.format(sale.getChangeAmount())));
        sb.append("Payment: ").append(sale.getPaymentMethod());
        if (sale.getPaymentRef() != null && !sale.getPaymentRef().isEmpty()) {
            sb.append(" (Ref: ").append(sale.getPaymentRef()).append(")");
        }
        sb.append("\n====================================\n");
        sb.append("       THANK YOU FOR SHOPPING!\n");
        sb.append("====================================\n");

        TextArea textArea = new TextArea(sb.toString());
        textArea.setEditable(false);
        textArea.setStyle("-fx-font-family: monospace;");
        textArea.setPrefRowCount(25);
        textArea.setPrefColumnCount(40);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(cartTable.getScene().getWindow());
        alert.setTitle("Receipt");
        alert.setHeaderText("Transaction Successful");
        alert.getDialogPane().setContent(textArea);
        
        ButtonType printBtn = new ButtonType("Print", ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(printBtn, ButtonType.CLOSE);
        
        alert.showAndWait().ifPresent(type -> {
            if (type == printBtn) {
                // In a real app we'd use PrinterJob here
                Notifier.info("Receipt sent to printer!");
            }
        });
    }
}
