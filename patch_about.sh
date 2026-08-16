sed -i '' 's/@FXML private javafx.scene.layout.VBox cloudDbPanel;/@FXML private javafx.scene.layout.VBox cloudDbPanel;\n    @FXML private javafx.scene.layout.VBox aboutPanel;/' src/main/java/com/pms/controller/SettingsController.java

sed -i '' 's/boolean showDb = "database".contains(q) || "cloud".contains(q) || "host".contains(q) || "connection".contains(q) || "port".contains(q) || "sql".contains(q);/boolean showDb = "database".contains(q) || "cloud".contains(q) || "host".contains(q) || "connection".contains(q) || "port".contains(q) || "sql".contains(q);\n            boolean showAbout = "about".contains(q) || "version".contains(q) || "update".contains(q);/' src/main/java/com/pms/controller/SettingsController.java

sed -i '' 's/cloudDbPanel.setManaged(showDb);/cloudDbPanel.setManaged(showDb);\n            aboutPanel.setVisible(showAbout);\n            aboutPanel.setManaged(showAbout);/' src/main/java/com/pms/controller/SettingsController.java

cat << 'INNER_EOF' >> src/main/java/com/pms/controller/SettingsController.java
    @FXML
    public void handleCheckUpdates() {
        Notifier.info("You are using the latest version of PharmSys.");
    }
INNER_EOF

# Fix the extra handleCheckUpdates if we messed up brackets. 
# Oh wait, we just appended it to the bottom. That puts it outside the class!
# Let's fix that.
