package com.pms.controller;

import com.pms.util.AppPrefs;
import com.pms.util.CurrencyUtil;
import com.pms.util.Notifier;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;

public class SettingsController {

    @FXML private ComboBox<String> currencyCombo;

    private final String[] currencies = {
        "$ (US Dollar)",
        "£ (British Pound)",
        "€ (Euro)",
        "GHS",
        "₦ (Nigerian Naira)",
        "¥ (Chinese Yen)",
        "A$ (Australian Dollar)",
        "C$ (Canadian Dollar)"
    };

    @FXML
    public void initialize() {
        currencyCombo.getItems().addAll(currencies);
        
        String currentSymbol = CurrencyUtil.getSymbol();
        // Try to select the one that matches the current symbol
        for (String c : currencies) {
            if (c.startsWith(currentSymbol)) {
                currencyCombo.setValue(c);
                break;
            }
        }
    }

    @FXML
    public void handleSave() {
        String selected = currencyCombo.getValue();
        if (selected == null || selected.isEmpty()) {
            Notifier.error("Please select a currency.");
            return;
        }

        // Extract just the symbol (everything before the first space, unless it's just "GHS")
        String newSymbol = selected.contains(" ") ? selected.split(" ")[0] : selected;

        AppPrefs.set("currency_symbol", newSymbol);
        Notifier.success("Preferences saved successfully!");
    }
}
