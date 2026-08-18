package com.pms.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateManager {

    private static final Logger logger = LoggerFactory.getLogger(UpdateManager.class);
    
    // GitHub repository details for checking releases
    private static final String GITHUB_OWNER = "snowRepo";
    private static final String GITHUB_REPO = "PMS";
    
    public static final String CURRENT_VERSION = "v1.0.0";
    
    public static class UpdateInfo {
        public String version;
        public String downloadUrl;
        public String fileName;
        
        public UpdateInfo(String version, String downloadUrl, String fileName) {
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.fileName = fileName;
        }
    }

    /**
     * Checks the GitHub Releases API for a newer version.
     * Returns UpdateInfo if an update is available for this OS, otherwise null.
     */
    public static UpdateInfo checkForUpdates() throws Exception {
        String apiUrl = String.format("https://api.github.com/repos/%s/%s/releases/latest", GITHUB_OWNER, GITHUB_REPO);
        
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        if (conn.getResponseCode() != 200) {
            // Probably private repo or rate limit, just return null silently
            logger.warn("Update check failed with HTTP {}", conn.getResponseCode());
            return null;
        }

        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
            JsonObject release = JsonParser.parseReader(reader).getAsJsonObject();
            String latestVersion = release.get("tag_name").getAsString();
            
            // Compare versions (simple string check for now, can be improved)
            if (latestVersion.equals(CURRENT_VERSION) || latestVersion.equals(CURRENT_VERSION.replace("v", ""))) {
                return null;
            }

            // Find the correct asset for this OS
            String os = System.getProperty("os.name").toLowerCase();
            String targetExtension = os.contains("win") ? ".exe" : (os.contains("mac") ? ".dmg" : ".deb");
            
            JsonArray assets = release.getAsJsonArray("assets");
            for (JsonElement element : assets) {
                JsonObject asset = element.getAsJsonObject();
                String fileName = asset.get("name").getAsString();
                
                if (fileName.toLowerCase().endsWith(targetExtension)) {
                    String downloadUrl = asset.get("browser_download_url").getAsString();
                    return new UpdateInfo(latestVersion, downloadUrl, fileName);
                }
            }
        }
        
        return null; // No matching asset found
    }
}
