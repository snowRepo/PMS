package com.pms.controller.auth;

import com.pms.util.UpdateManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateProgressController {

    private static final Logger logger = LoggerFactory.getLogger(UpdateProgressController.class);

    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Label bytesLabel;
    @FXML private Button btnCancel;
    @FXML private Button btnInstall;

    private UpdateManager.UpdateInfo updateInfo;
    private Thread downloadThread;
    private volatile boolean cancelled = false;
    private File downloadedFile;

    public void setUpdateInfo(UpdateManager.UpdateInfo updateInfo) {
        this.updateInfo = updateInfo;
        startDownload();
    }

    private void startDownload() {
        downloadThread = new Thread(() -> {
            try {
                URL url = new URL(updateInfo.downloadUrl);
                
                // Note: GitHub Releases browser_download_url might issue a 302 Redirect
                // HttpURLConnection follows redirects automatically by default
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.connect();

                // If redirected, might need to manually follow if crossing domains,
                // but standard HttpURLConnection handles same-protocol redirects.
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    
                    String newUrl = conn.getHeaderField("Location");
                    conn = (HttpURLConnection) new URL(newUrl).openConnection();
                    conn.setRequestMethod("GET");
                    conn.connect();
                }

                int contentLength = conn.getContentLength();
                
                downloadedFile = new File(System.getProperty("java.io.tmpdir"), updateInfo.fileName);
                
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(downloadedFile)) {
                     
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalRead = 0;
                    
                    while ((bytesRead = in.read(buffer)) != -1) {
                        if (cancelled) {
                            out.close();
                            downloadedFile.delete();
                            return;
                        }
                        
                        out.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        
                        final long current = totalRead;
                        Platform.runLater(() -> {
                            if (contentLength > 0) {
                                double progress = (double) current / contentLength;
                                progressBar.setProgress(progress);
                                bytesLabel.setText(formatBytes(current) + " / " + formatBytes(contentLength));
                            } else {
                                progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                                bytesLabel.setText(formatBytes(current) + " downloaded");
                            }
                        });
                    }
                }
                
                if (!cancelled) {
                    Platform.runLater(() -> {
                        progressBar.setProgress(1.0);
                        statusLabel.setText("Download complete! Ready to install.");
                        btnCancel.setVisible(false);
                        btnCancel.setManaged(false);
                        btnInstall.setVisible(true);
                        btnInstall.setManaged(true);
                    });
                }
                
            } catch (Exception e) {
                logger.error("Failed to download update", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Download failed: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 13px;");
                    progressBar.setProgress(0);
                });
            }
        }, "Update-Downloader");
        
        downloadThread.start();
    }
    
    private String formatBytes(long bytes) {
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    @FXML
    private void handleInstall() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            
            if (os.contains("win")) {
                // Windows: Run the exe
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", downloadedFile.getAbsolutePath()});
            } else if (os.contains("mac")) {
                // Mac: Open the dmg
                Runtime.getRuntime().exec(new String[]{"open", downloadedFile.getAbsolutePath()});
            } else {
                // Linux: Try xdg-open or dpkg? For now just try open
                Runtime.getRuntime().exec(new String[]{"xdg-open", downloadedFile.getAbsolutePath()});
            }
            
            // Shut down this app so the installer can overwrite files if needed
            System.exit(0);
            
        } catch (Exception e) {
            logger.error("Failed to launch installer", e);
            com.pms.util.Notifier.error("Failed to launch installer. File is at: " + downloadedFile.getAbsolutePath());
        }
    }

    @FXML
    private void handleCancel() {
        cancelled = true;
        closeModal();
    }
    
    private void closeModal() {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }
}
