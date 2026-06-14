package com.hmdm.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.hmdm.persistence.domain.Application;
import com.hmdm.persistence.domain.ApplicationType;
import com.hmdm.persistence.domain.ConfigurationApplicationParameters;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncApplication implements SyncApplicationInt, Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String icon;
    private String name;
    private String pkg;
    private String version;
    private Integer code;
    private String url;
    private Boolean showIcon;
    private Boolean useKiosk;
    private Boolean remove;
    private Boolean system;
    private Boolean runAfterInstall;
    private Boolean runAtBoot;
    private Boolean skipVersion;
    private String iconText;
    private ApplicationType type;
    private Integer screenOrder;
    private Integer keyCode;
    private Boolean bottom;
    private Boolean longTap;
    private String intent;

    public SyncApplication() {}

    public static SyncApplication fromDomain(Application app, ConfigurationApplicationParameters params) {
        SyncApplication sa = new SyncApplication();
        sa.id = app.getId();
        sa.icon = app.getIcon();
        sa.name = app.getName();
        sa.pkg = app.getPkg();
        sa.version = app.getVersion();
        sa.code = app.getVersionCode();
        sa.url = app.getUrl();
        sa.showIcon = app.getShowIcon();
        sa.useKiosk = app.getUseKiosk();
        sa.remove = app.getAction() == 2;
        sa.system = app.isSystem();
        sa.runAfterInstall = app.isRunAfterInstall();
        sa.runAtBoot = app.isRunAtBoot();
        sa.iconText = app.getIconText();
        sa.type = app.getType();
        sa.screenOrder = app.getScreenOrder();
        sa.keyCode = app.getKeyCode();
        sa.bottom = app.isBottom();
        sa.longTap = app.isLongTap();
        sa.intent = app.getIntent();
        if (params != null) {
            sa.skipVersion = params.isSkipVersionCheck();
        } else {
            sa.skipVersion = app.isSkipVersion();
        }
        return sa;
    }

    @Override public Integer getId() { return id; }
    @Override public String getIcon() { return icon; }
    @Override public String getName() { return name; }
    @Override public String getPkg() { return pkg; }
    @Override public String getVersion() { return version; }
    @Override public Integer getCode() { return code; }
    @Override public String getUrl() { return url; }
    @Override public Boolean getShowIcon() { return showIcon; }
    @Override public Boolean getUseKiosk() { return useKiosk; }
    @Override public Boolean isRemove() { return remove; }
    @Override public Boolean isSystem() { return system; }
    @Override public Boolean isRunAfterInstall() { return runAfterInstall; }
    @Override public Boolean isRunAtBoot() { return runAtBoot; }
    @Override public Boolean isSkipVersion() { return skipVersion; }
    @Override public String getIconText() { return iconText; }
    @Override public ApplicationType getType() { return type; }
    @Override public Integer getScreenOrder() { return screenOrder; }
    @Override public Integer getKeyCode() { return keyCode; }
    @Override public Boolean getBottom() { return bottom; }
    @Override public Boolean getLongTap() { return longTap; }
    @Override public String getIntent() { return intent; }

    public void setId(Integer id) { this.id = id; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setName(String name) { this.name = name; }
    public void setPkg(String pkg) { this.pkg = pkg; }
    public void setVersion(String version) { this.version = version; }
    public void setCode(Integer code) { this.code = code; }
    public void setUrl(String url) { this.url = url; }
    public void setShowIcon(Boolean showIcon) { this.showIcon = showIcon; }
    public void setUseKiosk(Boolean useKiosk) { this.useKiosk = useKiosk; }
    public void setRemove(Boolean remove) { this.remove = remove; }
    public void setSystem(Boolean system) { this.system = system; }
    public void setRunAfterInstall(Boolean runAfterInstall) { this.runAfterInstall = runAfterInstall; }
    public void setRunAtBoot(Boolean runAtBoot) { this.runAtBoot = runAtBoot; }
    public void setSkipVersion(Boolean skipVersion) { this.skipVersion = skipVersion; }
    public void setIconText(String iconText) { this.iconText = iconText; }
    public void setType(ApplicationType type) { this.type = type; }
    public void setScreenOrder(Integer screenOrder) { this.screenOrder = screenOrder; }
    public void setKeyCode(Integer keyCode) { this.keyCode = keyCode; }
    public void setBottom(Boolean bottom) { this.bottom = bottom; }
    public void setLongTap(Boolean longTap) { this.longTap = longTap; }
    public void setIntent(String intent) { this.intent = intent; }
}
