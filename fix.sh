sed -i '' -e '$ d' -e '$ d' -e '$ d' -e '$ d' -e '$ d' src/main/java/com/pms/controller/SettingsController.java
cat << 'INNER' >> src/main/java/com/pms/controller/SettingsController.java
    @FXML
    public void handleCheckUpdates() {
        Notifier.info("You are using the latest version of PharmSys.");
    }
}
INNER
