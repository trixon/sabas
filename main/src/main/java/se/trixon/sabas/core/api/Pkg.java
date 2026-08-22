/*
 * Copyright 2026 Patrik Karlström <patrik@trixon.se>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.trixon.sabas.core.api;

import java.time.Instant;

public class Pkg {

    private static final PkgDictionary DICTIONARY = PkgDictionary.getInstance();

    private int mArchId;
    private String mDescription;
    private String mEpoch;
    private int mGroupId;
    private String mId;
    private boolean mInstalled;
    private int mLicenseId;
    private String mName;
    private boolean mOrphaned;
    private int mPackagerId;
    private String mRelease;
    private int mRepositoryId;
    private long mSizeDownload;
    private long mSizeInstall;
    private String mSummary;
    private long mTimeBuild;
    private long mTimeInstalled;
    private boolean mUpgradable;
    private String mUrl;
    private int mVendorId;
    private String mVersion;
    private String mVersionNew;

    public Pkg() {
    }

    public String getArch() {
        return DICTIONARY.getString(DictionarySection.ARCH, mArchId);
    }

    public int getArchId() {
        return mArchId;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getEpoch() {
        return mEpoch;
    }

    public String getGroup() {
        return DICTIONARY.getString(DictionarySection.GROUP, mGroupId);
    }

    public int getGroupId() {
        return mGroupId;
    }

    public String getId() {
        return mId;
    }

    public String getLicense() {
        return DICTIONARY.getString(DictionarySection.LICENSE, mLicenseId);
    }

    public int getLicenseId() {
        return mLicenseId;
    }

    public String getName() {
        return mName;
    }

    public String getPackager() {
        return DICTIONARY.getString(DictionarySection.PACKAGER, mPackagerId);
    }

    public int getPackagerId() {
        return mPackagerId;
    }

    public String getRelease() {
        return mRelease;
    }

    public String getRepository() {
        return DICTIONARY.getString(DictionarySection.REPOSITORY, mRepositoryId);
    }

    public int getRepositoryId() {
        return mRepositoryId;
    }

    public long getSizeDownload() {
        return mSizeDownload;
    }

    public long getSizeInstall() {
        return mSizeInstall;
    }

    public String getSummary() {
        return mSummary;
    }

    public long getTimeBuild() {
        return mTimeBuild;
    }

    public Instant getTimeBuildInstant() {
        return Instant.ofEpochSecond(mTimeBuild);
    }

    public long getTimeInstalled() {
        return mTimeInstalled;
    }

    public Instant getTimeInstalledInstant() {
        return Instant.ofEpochSecond(mTimeInstalled);
    }

    public String getUrl() {
        return mUrl;
    }

    public String getVendor() {
        return DICTIONARY.getString(DictionarySection.VENDOR, mVendorId);
    }

    public int getVendorId() {
        return mVendorId;
    }

    public String getVersion() {
        return mVersion;
    }

    public String getVersionNew() {
        return mVersionNew;
    }

    public boolean isInstalled() {
        return mInstalled;
    }

    public boolean isOrphaned() {
        return mOrphaned;
    }

    public boolean isUpgradable() {
        return mUpgradable;
    }

    public void setArchId(int archId) {
        mArchId = archId;
    }

    public void setDescription(String description) {
        mDescription = description;
    }

    public void setEpoch(String epoch) {
        mEpoch = epoch;
    }

    public void setGroupId(int groupId) {
        mGroupId = groupId;
    }

    public void setId(String id) {
        mId = id;
    }

    public void setInstalled(boolean installed) {
        mInstalled = installed;
    }

    public void setLicenseId(int licenseId) {
        mLicenseId = licenseId;
    }

    public void setName(String name) {
        mName = name;
    }

    public void setOrphaned(boolean orphaned) {
        mOrphaned = orphaned;
    }

    public void setPackagerId(int packagerId) {
        mPackagerId = packagerId;
    }

    public void setRelease(String release) {
        mRelease = release;
    }

    public void setRepositoryId(int repositoryId) {
        mRepositoryId = repositoryId;
    }

    public void setSizeDownload(long sizeDownload) {
        mSizeDownload = sizeDownload;
    }

    public void setSizeInstall(long sizeInstall) {
        mSizeInstall = sizeInstall;
    }

    public void setSummary(String summary) {
        mSummary = summary;
    }

    public void setTimeBuild(long timeBuild) {
        mTimeBuild = timeBuild;
    }

    public void setTimeInstalled(long timeInstalled) {
        mTimeInstalled = timeInstalled;
    }

    public void setUpgradable(boolean upgradable) {
        mUpgradable = upgradable;
    }

    public void setUrl(String url) {
        mUrl = url;
    }

    public void setVendorId(int vendorId) {
        mVendorId = vendorId;
    }

    public void setVersion(String version) {
        mVersion = version;
    }

    public void setVersionNew(String versionNew) {
        mVersionNew = versionNew;
    }

}
