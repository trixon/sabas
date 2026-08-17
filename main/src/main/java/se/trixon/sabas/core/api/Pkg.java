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

    private String mArch;
    private String mDescription;
    private String mEpoch;
    private String mGroup;
    private String mId;
    private String mLicense;
    private String mName;
    private String mPackager;
    private String mRelease;
    private String mRepository;
    private long mSizeDownload;
    private long mSizeInstall;
    private String mSummary;
    private Instant mTimeBuild;
    private Instant mTimeInstalled;
    private String mUrl;
    private String mVendor;
    private String mVersion;

    public Pkg() {
    }

    public String getArch() {
        return mArch;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getEpoch() {
        return mEpoch;
    }

    public String getGroup() {
        return mGroup;
    }

    public String getId() {
        return mId;
    }

    public String getLicense() {
        return mLicense;
    }

    public String getName() {
        return mName;
    }

    public String getPackager() {
        return mPackager;
    }

    public String getRelease() {
        return mRelease;
    }

    public String getRepository() {
        return mRepository;
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

    public Instant getTimeBuild() {
        return mTimeBuild;
    }

    public Instant getTimeInstalled() {
        return mTimeInstalled;
    }

    public String getUrl() {
        return mUrl;
    }

    public String getVendor() {
        return mVendor;
    }

    public String getVersion() {
        return mVersion;
    }

    public void setArch(String arch) {
        this.mArch = arch;
    }

    public void setDescription(String description) {
        mDescription = description;
    }

    public void setEpoch(String epoch) {
        this.mEpoch = epoch;
    }

    public void setGroup(String group) {
        mGroup = group;
    }

    public void setId(String id) {
        this.mId = id;
    }

    public void setLicense(String license) {
        mLicense = license;
    }

    public void setName(String name) {
        mName = name;
    }

    public void setPackager(String packager) {
        this.mPackager = packager;
    }

    public void setRelease(String release) {
        this.mRelease = release;
    }

    public void setRepository(String repository) {
        this.mRepository = repository;
    }

    public void setSizeDownload(long sizeDownload) {
        mSizeDownload = sizeDownload;
    }

    public void setSizeInstall(long sizeInstall) {
        this.mSizeInstall = sizeInstall;
    }

    public void setSummary(String summary) {
        mSummary = summary;
    }

    public void setTimeBuild(Instant timeBuild) {
        this.mTimeBuild = timeBuild;
    }

    public void setTimeInstalled(Instant timeInstalled) {
        this.mTimeInstalled = timeInstalled;
    }

    public void setUrl(String url) {
        mUrl = url;
    }

    public void setVendor(String vendor) {
        this.mVendor = vendor;
    }

    public void setVersion(String version) {
        this.mVersion = version;
    }

}
