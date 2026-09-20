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
package se.trixon.sabas.bridge.appimage.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppImageItem {

    private List<AppImageAuthor> authors;
    private String description;
    private String license;
    private List<AppImageLink> links;
    private String name;

    public List<AppImageAuthor> getAuthors() {
        return authors;
    }

    public String getDescription() {
        return description;
    }

    public String getLicense() {
        return license;
    }

    public List<AppImageLink> getLinks() {
        return links;
    }

    public String getName() {
        return name;
    }

    public void setAuthors(List<AppImageAuthor> authors) {
        this.authors = authors;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public void setLinks(List<AppImageLink> links) {
        this.links = links;
    }

    public void setName(String name) {
        this.name = name;
    }
}
