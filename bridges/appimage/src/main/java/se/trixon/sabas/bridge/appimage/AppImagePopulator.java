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
package se.trixon.sabas.bridge.appimage;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.Exceptions;
import se.trixon.sabas.api.BridgePopulator;
import se.trixon.sabas.api.DictionarySection;
import se.trixon.sabas.api.Pkg;
import se.trixon.sabas.bridge.appimage.data.AppImageFeed;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class AppImagePopulator extends BridgePopulator {

    @Override
    public List<Pkg> populate(Set<Process> processes) {
        if (!AppImageBridge.FEED_JSON.isFile()) {
            return List.of();
        }

        var rawPackagesList = new ArrayList<Pkg>();
        try {
            var mapper = new ObjectMapper();
            var feed = mapper.readValue(AppImageBridge.FEED_JSON, AppImageFeed.class);

            if (feed == null || feed.getItems() == null) {
                return List.of();
            }

            var groupId = mDictionary.getOrCreateId(DictionarySection.GROUP, "AppImage Applications");
            for (var item : feed.getItems()) {
                if (StringUtils.isBlank(item.getName())) {
                    continue;
                }

                var pkg = new Pkg();
                pkg.setName(item.getName().trim());
                pkg.setVersion("GitHub Release");
                pkg.setRelease("continuous");
                pkg.setGroupId(groupId);

                var licenseText = item.getLicense() != null ? item.getLicense() : "Unknown / Open Source";
                pkg.setLicenseId(mDictionary.getOrCreateId(DictionarySection.LICENSE, licenseText));
                pkg.setRepositoryId(mDictionary.getOrCreateId(DictionarySection.REPOSITORY, "AppImage Hub"));
                var rawDesc = item.getDescription();
                if (StringUtils.isNotBlank(rawDesc)) {
                    if (rawDesc.contains("<p>") || rawDesc.contains("<ul>")) {
                        pkg.setDescription("<html>" + rawDesc.trim() + "</html>");
                    } else {
                        pkg.setDescription("<html><p>" + rawDesc.trim() + "</p></html>");
                    }
                } else {
                    pkg.setDescription("<html><p>No description available.</p></html>");
                }

                if (item.getLinks() != null) {
                    for (var link : item.getLinks()) {
                        if ("Download".equalsIgnoreCase(link.getType()) || "GitHub".equalsIgnoreCase(link.getType())) {
                            pkg.setUrl(link.getUrl());
                            break;
                        }
                    }
                }

                String vendorName = "Unknown";

                if (item.getAuthors() != null && !item.getAuthors().isEmpty()) {
                    var primaryAuthor = item.getAuthors().get(0);
                    if (StringUtils.isNotBlank(primaryAuthor.getName())) {
                        vendorName = primaryAuthor.getName().trim();
                    }
                }
                pkg.setVendorId(mDictionary.getOrCreateId(DictionarySection.VENDOR, vendorName));

                rawPackagesList.add(pkg);
            }
        } catch (IOException e) {
            Exceptions.printStackTrace(e);
        }

        return rawPackagesList.stream()
                .sorted(Comparator.comparing(Pkg::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public Pkg.Details populateDetails(Set<Process> processes, Pkg pkg) {
        var details = new Pkg.Details();

        return details;
    }
}
