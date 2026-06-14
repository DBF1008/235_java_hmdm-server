package com.hmdm.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.hmdm.persistence.domain.ApplicationSetting;

import java.io.Serializable;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Device state information as JSON string */
    private String info;

    /** Timestamp of last IMEI change */
    private Long imeiUpdateTs;

    /** Device public IP address */
    private String publicIp;

    /** Device IMEI */
    private String imei;

    /** Device phone number */
    private String phone;

    /** Options for auto-creating the device on first enrollment */
    private DeviceCreateOptions createOptions;

    /** Device-specific application settings to save during sync */
    private List<ApplicationSetting> applicationSettings;

    /** Device custom properties to update */
    private String custom1;
    private String custom2;
    private String custom3;

    public SyncRequest() {}

    public String getInfo() { return info; }
    public void setInfo(String info) { this.info = info; }

    public Long getImeiUpdateTs() { return imeiUpdateTs; }
    public void setImeiUpdateTs(Long imeiUpdateTs) { this.imeiUpdateTs = imeiUpdateTs; }

    public String getPublicIp() { return publicIp; }
    public void setPublicIp(String publicIp) { this.publicIp = publicIp; }

    public String getImei() { return imei; }
    public void setImei(String imei) { this.imei = imei; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public DeviceCreateOptions getCreateOptions() { return createOptions; }
    public void setCreateOptions(DeviceCreateOptions createOptions) { this.createOptions = createOptions; }

    public List<ApplicationSetting> getApplicationSettings() { return applicationSettings; }
    public void setApplicationSettings(List<ApplicationSetting> applicationSettings) { this.applicationSettings = applicationSettings; }

    public String getCustom1() { return custom1; }
    public void setCustom1(String custom1) { this.custom1 = custom1; }

    public String getCustom2() { return custom2; }
    public void setCustom2(String custom2) { this.custom2 = custom2; }

    public String getCustom3() { return custom3; }
    public void setCustom3(String custom3) { this.custom3 = custom3; }
}
