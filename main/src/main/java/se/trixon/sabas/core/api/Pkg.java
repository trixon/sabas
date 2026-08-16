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

public class Pkg {

    private String mDescription;
    private String mGroup;
    private String mLicense;
    private String mName;
    private long mSize;
    private String mSummary;
    private String mUrl;

    public Pkg() {
    }

    public String getDescription() {
        return mDescription;
    }

    public String getGroup() {
        return mGroup;
    }

    public String getLicense() {
        return mLicense;
    }

    public String getName() {
        return mName;
    }

    public long getSize() {
        return mSize;
    }

    public String getSummary() {
        return mSummary;
    }

    public String getUrl() {
        return mUrl;
    }

    public void setDescription(String description) {
        mDescription = description;
    }

    public void setGroup(String group) {
        mGroup = group;
    }

    public void setLicense(String license) {
        mLicense = license;
    }

    public void setName(String name) {
        mName = name;
    }

    public void setSize(long size) {
        mSize = size;
    }

    public void setSummary(String summary) {
        mSummary = summary;
    }

    public void setUrl(String url) {
        mUrl = url;
    }

}
