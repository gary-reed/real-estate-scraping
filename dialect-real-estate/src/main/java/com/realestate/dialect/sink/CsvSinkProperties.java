package com.realestate.dialect.sink;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code realestate.csv-sink.*}. The dialect listens on localhost and writes the listing spreadsheet.
 * Off unless a scraper sets {@code enabled: true}. Compass does, in its {@code application.yaml}.
 */
@ConfigurationProperties(prefix = "realestate.csv-sink")
public class CsvSinkProperties {

    /** Spreadsheet name when {@code realestate.csv-sink.file-name} is omitted or blank. */
    public static final String DEFAULT_FILE_NAME = "listings.csv";

    /** Start the local consumer. The scraper's {@code scraper.sink-consumers.central-db} must point at {@link #port}. */
    private boolean enabled = false;

    /** Localhost port. One scraper process per port. */
    private int port = 8893;

    /**
     * Expected {@code X-Internal-Token}. Blank accepts every caller (localhost only).
     * Must equal {@code scraper.sink-consumers.central-db.token}.
     */
    private String token = "local-csv";

    private String tokenHeader = "X-Internal-Token";

    /**
     * Spreadsheet root. A run writes {@code <directory>/<country>/<site>/<file-name>}.
     * The date is not part of the path: Run Diff always reads this folder, including the next day.
     * Relative to the working directory. Start the jar from the scraper module: {@code ../../outputs}
     * is the repository {@code outputs/} folder.
     */
    private String directory = "../../outputs";

    /**
     * Spreadsheet file name inside the site folder. A file name only, such as {@code listings.csv}.
     * Not a path: the country and site folders stay under {@link #directory}.
     */
    private String fileName = DEFAULT_FILE_NAME;

    /**
     * Hash memory so the next run pushes only what changed. A working file, kept with the scraper's
     * other data ({@code ./data}), not in the date folder.
     */
    private String stateDirectory = "./data/csv-sink";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenHeader() {
        return tokenHeader;
    }

    public void setTokenHeader(String tokenHeader) {
        this.tokenHeader = tokenHeader;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * The file name the sink will write. Blank becomes {@link #DEFAULT_FILE_NAME}.
     * A value that contains a directory separator, or {@code ..}, is rejected: the folder
     * is chosen by {@link #directory}, the country, and the site.
     */
    public static String fileNameOrDefault(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_FILE_NAME;
        }
        String name = raw.trim();
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0
                || ".".equals(name) || "..".equals(name) || name.contains("..")) {
            throw new IllegalArgumentException(
                    "realestate.csv-sink.file-name must be a file name, not a path: " + raw);
        }
        return name;
    }

    public String getStateDirectory() {
        return stateDirectory;
    }

    public void setStateDirectory(String stateDirectory) {
        this.stateDirectory = stateDirectory;
    }
}
