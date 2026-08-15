package se.trixon.sabas.labs;
// Ändra till ditt faktiska paket i NetBeans

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LinuxPackage {

    @JsonProperty("package_id")
    private String packageId;
    private String summary;
    private String name;
    private String description;
    private String group;
    private String license;
    private String url;
    private long size;

    // Standardkonstruktor (krävs av Jackson)
    public LinuxPackage() {
    }

    public String getName() {
        return name;
    }

    // Getters och Setters
    public String getPackageId() {
        return packageId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    // En hjälpmetod för att plocka ut bara det rena namnet ur ett package_id
    // (Eftersom package_id ofta ser ut som "nano;7.2-1.fc40;x86_64;fedora")
    public String getNameSimple() {
        if (packageId != null && packageId.contains(";")) {
            return packageId.split(";")[0];
        }
        return packageId;
    }
}
