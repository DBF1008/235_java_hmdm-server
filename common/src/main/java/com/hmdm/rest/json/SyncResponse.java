package com.hmdm.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.hmdm.persistence.domain.Application;
import com.hmdm.persistence.domain.ApplicationSetting;
import com.hmdm.persistence.domain.Configuration;
import com.hmdm.persistence.domain.ConfigurationApplicationParameters;
import com.hmdm.persistence.domain.ConfigurationFile;
import com.hmdm.persistence.domain.Device;
import com.hmdm.persistence.domain.Settings;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncResponse implements SyncResponseInt, Serializable {

    private static final long serialVersionUID = 1L;

    private String backgroundColor;
    private String textColor;
    private String backgroundImageUrl;
    private List<SyncApplicationInt> applications;
    private String password;
    private String imei;
    private String phone;
    private String iconSize;
    private String title;
    private Boolean gps;
    private Boolean bluetooth;
    private Boolean wifi;
    private Boolean mobileData;
    private boolean kioskMode;
    private Boolean kioskHome;
    private Boolean kioskRecents;
    private Boolean kioskNotifications;
    private Boolean kioskSystemInfo;
    private Boolean kioskKeyguard;
    private Boolean kioskLockButtons;
    private Boolean kioskScreenOn;
    private String mainApp;
    private boolean lockStatusBar;
    private int systemUpdateType;
    private String systemUpdateFrom;
    private String systemUpdateTo;
    private Boolean scheduleAppUpdate;
    private String appUpdateFrom;
    private String appUpdateTo;
    private String downloadUpdates;
    private List<SyncApplicationSettingInt> applicationSettings;
    private Boolean usbStorage;
    private String requestUpdates;
    private Boolean disableLocation;
    private String appPermissions;
    private String pushOptions;
    private Integer keepaliveTime;
    private Boolean autoBrightness;
    private Integer brightness;
    private Boolean manageTimeout;
    private Integer timeout;
    private Boolean lockVolume;
    private Boolean manageVolume;
    private Integer volume;
    private String passwordMode;
    private Integer orientation;
    private Boolean displayStatus;
    private Boolean runDefaultLauncher;
    private Boolean disableScreenshots;
    private Boolean autostartForeground;
    private String timeZone;
    private String allowedClasses;
    private String newServerUrl;
    private Boolean lockSafeSettings;
    private Boolean permissive;
    private Boolean kioskExit;
    private Boolean showWifi;
    private List<SyncConfigurationFileInt> files;
    private String newNumber;
    private String restrictions;
    private String custom1;
    private String custom2;
    private String custom3;
    private String appName;
    private String vendor;
    private String description;

    public SyncResponse() {}

    public static SyncResponse fromConfiguration(
            Device device,
            Configuration config,
            List<Application> applications,
            List<ConfigurationFile> configFiles,
            List<ApplicationSetting> appSettings,
            Settings settings,
            String baseUrl) {

        SyncResponse sr = new SyncResponse();

        // Design settings: use configuration values if not using defaults, otherwise fall back to Settings
        if (config.isUseDefaultDesignSettings() && settings != null) {
            sr.backgroundColor = settings.getBackgroundColor();
            sr.textColor = settings.getTextColor();
            sr.backgroundImageUrl = settings.getBackgroundImageUrl();
            sr.iconSize = settings.getIconSize() != null ? settings.getIconSize().getTransmittedValue() : null;
        } else {
            sr.backgroundColor = config.getBackgroundColor();
            sr.textColor = config.getTextColor();
            sr.backgroundImageUrl = config.getBackgroundImageUrl();
            sr.iconSize = config.getIconSize() != null ? config.getIconSize().getTransmittedValue() : null;
        }

        // Build a map of ConfigurationApplicationParameters by application ID for skipVersion lookup
        Map<Integer, ConfigurationApplicationParameters> appParamsMap = new HashMap<>();
        if (config.getApplicationUsageParameters() != null) {
            for (ConfigurationApplicationParameters cap : config.getApplicationUsageParameters()) {
                appParamsMap.put(cap.getApplicationId(), cap);
            }
        }

        // Convert applications
        if (applications != null) {
            sr.applications = new ArrayList<>();
            for (Application app : applications) {
                ConfigurationApplicationParameters params = appParamsMap.get(app.getId());
                sr.applications.add(SyncApplication.fromDomain(app, params));
            }
        }

        // Password
        sr.password = config.getPassword();

        // Device fields
        sr.imei = device.getImei();
        sr.phone = device.getPhone();
        sr.custom1 = device.getCustom1();
        sr.custom2 = device.getCustom2();
        sr.custom3 = device.getCustom3();

        // Title from desktop header
        if (config.getDesktopHeader() != null) {
            switch (config.getDesktopHeader()) {
                case DEVICE_ID:
                    sr.title = device.getNumber();
                    break;
                case DESCRIPTION:
                    sr.title = device.getDescription();
                    break;
                case CUSTOM1:
                    sr.title = device.getCustom1();
                    break;
                case CUSTOM2:
                    sr.title = device.getCustom2();
                    break;
                case CUSTOM3:
                    sr.title = device.getCustom3();
                    break;
                case TEMPLATE:
                    sr.title = config.getDesktopHeaderTemplate();
                    break;
                default:
                    sr.title = null;
                    break;
            }
        }

        // GPS, Bluetooth, WiFi, Mobile Data
        sr.gps = config.getGps();
        sr.bluetooth = config.getBluetooth();
        sr.wifi = config.getWifi();
        sr.mobileData = config.getMobileData();

        // Kiosk settings
        sr.kioskMode = config.isKioskMode();
        sr.kioskHome = config.getKioskHome();
        sr.kioskRecents = config.getKioskRecents();
        sr.kioskNotifications = config.getKioskNotifications();
        sr.kioskSystemInfo = config.getKioskSystemInfo();
        sr.kioskKeyguard = config.getKioskKeyguard();
        sr.kioskLockButtons = config.getKioskLockButtons();
        sr.kioskScreenOn = config.getKioskScreenOn();

        // Main app: resolve package name from mainAppId
        if (config.getMainAppId() != null && applications != null) {
            for (Application app : applications) {
                if (config.getMainAppId().equals(app.getId())) {
                    sr.mainApp = app.getPkg();
                    break;
                }
            }
        }

        // Lock status bar
        sr.lockStatusBar = config.isBlockStatusBar();

        // System update
        sr.systemUpdateType = config.getSystemUpdateType();
        sr.systemUpdateFrom = config.getSystemUpdateFrom();
        sr.systemUpdateTo = config.getSystemUpdateTo();

        // App update scheduling
        sr.scheduleAppUpdate = config.isScheduleAppUpdate();
        sr.appUpdateFrom = config.getAppUpdateFrom();
        sr.appUpdateTo = config.getAppUpdateTo();

        // Download updates
        sr.downloadUpdates = config.getDownloadUpdates() != null
                ? config.getDownloadUpdates().getTransmittedValue() : null;

        // Application settings
        if (appSettings != null) {
            sr.applicationSettings = appSettings.stream()
                    .map(SyncApplicationSetting::fromDomain)
                    .collect(Collectors.toList());
        }

        // USB storage
        sr.usbStorage = config.getUsbStorage();

        // Request updates
        sr.requestUpdates = config.getRequestUpdates() != null
                ? config.getRequestUpdates().getTransmittedValue() : null;

        // Disable location
        sr.disableLocation = config.getDisableLocation();

        // App permissions
        sr.appPermissions = config.getAppPermissions() != null
                ? config.getAppPermissions().getTransmittedValue() : null;

        // Push options
        sr.pushOptions = config.getPushOptions();

        // Keepalive time
        sr.keepaliveTime = config.getKeepaliveTime();

        // Brightness
        sr.autoBrightness = config.getAutoBrightness();
        sr.brightness = config.getBrightness();

        // Timeout
        sr.manageTimeout = config.getManageTimeout();
        sr.timeout = config.getTimeout();

        // Volume
        sr.lockVolume = config.getLockVolume();
        sr.manageVolume = config.getManageVolume();
        sr.volume = config.getVolume();

        // Password mode
        sr.passwordMode = config.getPasswordMode();

        // Orientation
        sr.orientation = config.getOrientation();

        // Display status
        sr.displayStatus = config.isDisplayStatus();

        // Run default launcher
        sr.runDefaultLauncher = config.getRunDefaultLauncher();

        // Disable screenshots
        sr.disableScreenshots = config.getDisableScreenshots();

        // Autostart foreground
        sr.autostartForeground = config.getAutostartForeground();

        // Time zone
        sr.timeZone = config.getTimeZone();

        // Allowed classes
        sr.allowedClasses = config.getAllowedClasses();

        // New server URL
        sr.newServerUrl = config.getNewServerUrl();

        // Lock safe settings
        sr.lockSafeSettings = config.getLockSafeSettings();

        // Permissive
        sr.permissive = config.getPermissive();

        // Kiosk exit
        sr.kioskExit = config.getKioskExit();

        // Show WiFi
        sr.showWifi = config.getShowWifi();

        // Files
        if (configFiles != null) {
            sr.files = new ArrayList<>();
            for (ConfigurationFile cf : configFiles) {
                sr.files.add(SyncConfigurationFile.fromDomain(cf, baseUrl));
            }
        }

        // New number: null (migration handled elsewhere)
        sr.newNumber = null;

        // Restrictions
        sr.restrictions = config.getRestrictions();

        // Constants
        sr.appName = "Headwind MDM";
        sr.vendor = "Headwind Solutions LLC";

        // Description
        sr.description = config.getDescription();

        return sr;
    }

    @Override public String getBackgroundColor() { return backgroundColor; }
    @Override public String getTextColor() { return textColor; }
    @Override public String getBackgroundImageUrl() { return backgroundImageUrl; }
    @Override public List<SyncApplicationInt> getApplications() { return applications; }
    @Override public String getPassword() { return password; }
    @Override public String getImei() { return imei; }
    @Override public String getPhone() { return phone; }
    @Override public String getIconSize() { return iconSize; }
    @Override public String getTitle() { return title; }
    @Override public Boolean getGps() { return gps; }
    @Override public Boolean getBluetooth() { return bluetooth; }
    @Override public Boolean getWifi() { return wifi; }
    @Override public Boolean getMobileData() { return mobileData; }
    @Override public boolean isKioskMode() { return kioskMode; }
    @Override public Boolean getKioskHome() { return kioskHome; }
    @Override public Boolean getKioskRecents() { return kioskRecents; }
    @Override public Boolean getKioskNotifications() { return kioskNotifications; }
    @Override public Boolean getKioskSystemInfo() { return kioskSystemInfo; }
    @Override public Boolean getKioskKeyguard() { return kioskKeyguard; }
    @Override public Boolean getKioskLockButtons() { return kioskLockButtons; }
    @Override public Boolean getKioskScreenOn() { return kioskScreenOn; }
    @Override public String getMainApp() { return mainApp; }
    @Override public boolean isLockStatusBar() { return lockStatusBar; }
    @Override public int getSystemUpdateType() { return systemUpdateType; }
    @Override public String getSystemUpdateFrom() { return systemUpdateFrom; }
    @Override public String getSystemUpdateTo() { return systemUpdateTo; }
    @Override public Boolean getScheduleAppUpdate() { return scheduleAppUpdate; }
    @Override public String getAppUpdateFrom() { return appUpdateFrom; }
    @Override public String getAppUpdateTo() { return appUpdateTo; }
    @Override public String getDownloadUpdates() { return downloadUpdates; }
    @Override public List<SyncApplicationSettingInt> getApplicationSettings() { return applicationSettings; }
    @Override public Boolean getUsbStorage() { return usbStorage; }
    @Override public String getRequestUpdates() { return requestUpdates; }
    @Override public Boolean getDisableLocation() { return disableLocation; }
    @Override public String getAppPermissions() { return appPermissions; }
    @Override public String getPushOptions() { return pushOptions; }
    @Override public Integer getKeepaliveTime() { return keepaliveTime; }
    @Override public Boolean getAutoBrightness() { return autoBrightness; }
    @Override public Integer getBrightness() { return brightness; }
    @Override public Boolean getManageTimeout() { return manageTimeout; }
    @Override public Integer getTimeout() { return timeout; }
    @Override public Boolean getLockVolume() { return lockVolume; }
    @Override public Boolean getManageVolume() { return manageVolume; }
    @Override public Integer getVolume() { return volume; }
    @Override public String getPasswordMode() { return passwordMode; }
    @Override public Integer getOrientation() { return orientation; }
    @Override public Boolean getDisplayStatus() { return displayStatus; }
    @Override public Boolean getRunDefaultLauncher() { return runDefaultLauncher; }
    @Override public Boolean getDisableScreenshots() { return disableScreenshots; }
    @Override public Boolean getAutostartForeground() { return autostartForeground; }
    @Override public String getTimeZone() { return timeZone; }
    @Override public String getAllowedClasses() { return allowedClasses; }
    @Override public String getNewServerUrl() { return newServerUrl; }
    @Override public Boolean getLockSafeSettings() { return lockSafeSettings; }
    @Override public Boolean getPermissive() { return permissive; }
    @Override public Boolean getKioskExit() { return kioskExit; }
    @Override public Boolean getShowWifi() { return showWifi; }
    @Override public List<SyncConfigurationFileInt> getFiles() { return files; }
    @Override public String getNewNumber() { return newNumber; }
    @Override public String getRestrictions() { return restrictions; }
    @Override public String getCustom1() { return custom1; }
    @Override public String getCustom2() { return custom2; }
    @Override public String getCustom3() { return custom3; }
    @Override public String getAppName() { return appName; }
    @Override public String getVendor() { return vendor; }
    @Override public String getDescription() { return description; }

    // Setters
    public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }
    public void setTextColor(String textColor) { this.textColor = textColor; }
    public void setBackgroundImageUrl(String backgroundImageUrl) { this.backgroundImageUrl = backgroundImageUrl; }
    public void setApplications(List<SyncApplicationInt> applications) { this.applications = applications; }
    public void setPassword(String password) { this.password = password; }
    public void setImei(String imei) { this.imei = imei; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setIconSize(String iconSize) { this.iconSize = iconSize; }
    public void setTitle(String title) { this.title = title; }
    public void setGps(Boolean gps) { this.gps = gps; }
    public void setBluetooth(Boolean bluetooth) { this.bluetooth = bluetooth; }
    public void setWifi(Boolean wifi) { this.wifi = wifi; }
    public void setMobileData(Boolean mobileData) { this.mobileData = mobileData; }
    public void setKioskMode(boolean kioskMode) { this.kioskMode = kioskMode; }
    public void setKioskHome(Boolean kioskHome) { this.kioskHome = kioskHome; }
    public void setKioskRecents(Boolean kioskRecents) { this.kioskRecents = kioskRecents; }
    public void setKioskNotifications(Boolean kioskNotifications) { this.kioskNotifications = kioskNotifications; }
    public void setKioskSystemInfo(Boolean kioskSystemInfo) { this.kioskSystemInfo = kioskSystemInfo; }
    public void setKioskKeyguard(Boolean kioskKeyguard) { this.kioskKeyguard = kioskKeyguard; }
    public void setKioskLockButtons(Boolean kioskLockButtons) { this.kioskLockButtons = kioskLockButtons; }
    public void setKioskScreenOn(Boolean kioskScreenOn) { this.kioskScreenOn = kioskScreenOn; }
    public void setMainApp(String mainApp) { this.mainApp = mainApp; }
    public void setLockStatusBar(boolean lockStatusBar) { this.lockStatusBar = lockStatusBar; }
    public void setSystemUpdateType(int systemUpdateType) { this.systemUpdateType = systemUpdateType; }
    public void setSystemUpdateFrom(String systemUpdateFrom) { this.systemUpdateFrom = systemUpdateFrom; }
    public void setSystemUpdateTo(String systemUpdateTo) { this.systemUpdateTo = systemUpdateTo; }
    public void setScheduleAppUpdate(Boolean scheduleAppUpdate) { this.scheduleAppUpdate = scheduleAppUpdate; }
    public void setAppUpdateFrom(String appUpdateFrom) { this.appUpdateFrom = appUpdateFrom; }
    public void setAppUpdateTo(String appUpdateTo) { this.appUpdateTo = appUpdateTo; }
    public void setDownloadUpdates(String downloadUpdates) { this.downloadUpdates = downloadUpdates; }
    public void setApplicationSettings(List<SyncApplicationSettingInt> applicationSettings) { this.applicationSettings = applicationSettings; }
    public void setUsbStorage(Boolean usbStorage) { this.usbStorage = usbStorage; }
    public void setRequestUpdates(String requestUpdates) { this.requestUpdates = requestUpdates; }
    public void setDisableLocation(Boolean disableLocation) { this.disableLocation = disableLocation; }
    public void setAppPermissions(String appPermissions) { this.appPermissions = appPermissions; }
    public void setPushOptions(String pushOptions) { this.pushOptions = pushOptions; }
    public void setKeepaliveTime(Integer keepaliveTime) { this.keepaliveTime = keepaliveTime; }
    public void setAutoBrightness(Boolean autoBrightness) { this.autoBrightness = autoBrightness; }
    public void setBrightness(Integer brightness) { this.brightness = brightness; }
    public void setManageTimeout(Boolean manageTimeout) { this.manageTimeout = manageTimeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public void setLockVolume(Boolean lockVolume) { this.lockVolume = lockVolume; }
    public void setManageVolume(Boolean manageVolume) { this.manageVolume = manageVolume; }
    public void setVolume(Integer volume) { this.volume = volume; }
    public void setPasswordMode(String passwordMode) { this.passwordMode = passwordMode; }
    public void setOrientation(Integer orientation) { this.orientation = orientation; }
    public void setDisplayStatus(Boolean displayStatus) { this.displayStatus = displayStatus; }
    public void setRunDefaultLauncher(Boolean runDefaultLauncher) { this.runDefaultLauncher = runDefaultLauncher; }
    public void setDisableScreenshots(Boolean disableScreenshots) { this.disableScreenshots = disableScreenshots; }
    public void setAutostartForeground(Boolean autostartForeground) { this.autostartForeground = autostartForeground; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public void setAllowedClasses(String allowedClasses) { this.allowedClasses = allowedClasses; }
    public void setNewServerUrl(String newServerUrl) { this.newServerUrl = newServerUrl; }
    public void setLockSafeSettings(Boolean lockSafeSettings) { this.lockSafeSettings = lockSafeSettings; }
    public void setPermissive(Boolean permissive) { this.permissive = permissive; }
    public void setKioskExit(Boolean kioskExit) { this.kioskExit = kioskExit; }
    public void setShowWifi(Boolean showWifi) { this.showWifi = showWifi; }
    public void setFiles(List<SyncConfigurationFileInt> files) { this.files = files; }
    public void setNewNumber(String newNumber) { this.newNumber = newNumber; }
    public void setRestrictions(String restrictions) { this.restrictions = restrictions; }
    public void setCustom1(String custom1) { this.custom1 = custom1; }
    public void setCustom2(String custom2) { this.custom2 = custom2; }
    public void setCustom3(String custom3) { this.custom3 = custom3; }
    public void setAppName(String appName) { this.appName = appName; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public void setDescription(String description) { this.description = description; }
}
