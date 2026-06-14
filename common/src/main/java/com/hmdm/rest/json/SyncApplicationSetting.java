package com.hmdm.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.hmdm.persistence.domain.ApplicationSetting;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncApplicationSetting implements SyncApplicationSettingInt, Serializable {

    private static final long serialVersionUID = 1L;

    private String packageId;
    private String name;
    private int type;
    private String value;
    private Boolean readonly;
    private long lastUpdate;
    private Boolean variable;

    public SyncApplicationSetting() {}

    public static SyncApplicationSetting fromDomain(ApplicationSetting as) {
        SyncApplicationSetting sas = new SyncApplicationSetting();
        sas.packageId = as.getApplicationPkg();
        sas.name = as.getName();
        sas.type = as.getType() != null ? as.getType().getId() : 0;
        sas.value = as.getValue();
        sas.readonly = as.isReadonly();
        sas.lastUpdate = as.getLastUpdate();
        sas.variable = as.isVariable();
        return sas;
    }

    @Override public String getPackageId() { return packageId; }
    @Override public String getName() { return name; }
    @Override public int getType() { return type; }
    @Override public String getValue() { return value; }
    @Override public Boolean isReadonly() { return readonly; }
    @Override public long getLastUpdate() { return lastUpdate; }
    @Override public Boolean isVariable() { return variable; }

    public void setPackageId(String packageId) { this.packageId = packageId; }
    public void setName(String name) { this.name = name; }
    public void setType(int type) { this.type = type; }
    public void setValue(String value) { this.value = value; }
    public void setReadonly(Boolean readonly) { this.readonly = readonly; }
    public void setLastUpdate(long lastUpdate) { this.lastUpdate = lastUpdate; }
    public void setVariable(Boolean variable) { this.variable = variable; }
}
