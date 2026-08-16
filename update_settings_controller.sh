sed -i '' 's/@FXML private TextField secUsernameField;/@FXML private Label accFullNameLabel;/g' src/main/java/com/pms/controller/SettingsController.java
sed -i '' 's/@FXML private PasswordField secPinField;/@FXML private Label accUsernameLabel;/g' src/main/java/com/pms/controller/SettingsController.java
sed -i '' 's/@FXML private PasswordField secNewPassField;/@FXML private Label accRoleLabel;/g' src/main/java/com/pms/controller/SettingsController.java
sed -i '' '/@FXML private PasswordField secConfirmPassField;/d' src/main/java/com/pms/controller/SettingsController.java
