package com.hmdm.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.hmdm.persistence.domain.ConfigurationFile;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncConfigurationFile implements SyncConfigurationFileInt, Serializable {

    private static final long serialVersionUID = 1L;

    private String description;
    private String path;
    private String checksum;
    private Boolean remove;
    private Long lastUpdate;
    private String url;
    private Boolean varContent;

    public SyncConfigurationFile() {}

    public static SyncConfigurationFile fromDomain(ConfigurationFile cf, String baseUrl) {
        SyncConfigurationFile sf = new SyncConfigurationFile();
        sf.description = cf.getDescription();
        sf.path = cf.getDevicePath();
        sf.checksum = cf.getChecksum();
        sf.remove = cf.isRemove();
        sf.lastUpdate = cf.getLastUpdate();
        sf.varContent = cf.isReplaceVariables();
        // Build URL from baseUrl + filePath if filePath is set, else use externalUrl
        if (cf.getExternalUrl() != null) {
            sf.url = cf.getExternalUrl();
        } else if (cf.getFilePath() != null && baseUrl != null) {
            sf.url = baseUrl + "/files/" + cf.getFilePath();
        } else {
            sf.url = cf.getUrl();
        }
        return sf;
    }

    @Override public String getDescription() { return description; }
    @Override public String getPath() { return path; }
    @Override public String getChecksum() { return checksum; }
    @Override public Boolean getRemove() { return remove; }
    @Override public Long getLastUpdate() { return lastUpdate; }
    @Override public String getUrl() { return url; }
    @Override public Boolean getVarContent() { return varContent; }

    public void setDescription(String description) { this.description = description; }
    public void setPath(String path) { this.path = path; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public void setRemove(Boolean remove) { this.remove = remove; }
    public void setLastUpdate(Long lastUpdate) { this.lastUpdate = lastUpdate; }
    public void setUrl(String url) { this.url = url; }
    public void setVarContent(Boolean varContent) { this.varContent = varContent; }
}
