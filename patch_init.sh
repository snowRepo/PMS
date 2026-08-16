sed -i '' '/currencyCombo.getItems().addAll(currencies);/i\
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {\
            String q = newVal.toLowerCase().trim();\
            boolean showLoc = "localization".contains(q) || "currency".contains(q) || "symbol".contains(q);\
            boolean showSec = "security".contains(q) || "password".contains(q) || "pin".contains(q) || "username".contains(q);\
            boolean showDb = "database".contains(q) || "cloud".contains(q) || "host".contains(q) || "connection".contains(q) || "port".contains(q) || "sql".contains(q);\
\
            localizationPanel.setVisible(showLoc);\
            localizationPanel.setManaged(showLoc);\
            securityPanel.setVisible(showSec);\
            securityPanel.setManaged(showSec);\
            cloudDbPanel.setVisible(showDb);\
            cloudDbPanel.setManaged(showDb);\
        });\
' src/main/java/com/pms/controller/SettingsController.java
