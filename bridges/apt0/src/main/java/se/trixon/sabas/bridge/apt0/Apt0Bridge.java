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
package se.trixon.sabas.bridge.apt0;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;
import se.trixon.sabas.api.Bridge;
import se.trixon.sabas.api.DictionarySection;
import se.trixon.sabas.api.Pkg;
import se.trixon.sabas.api.PkgDictionary;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
@ServiceProvider(service = Bridge.class)
public class Apt0Bridge extends Bridge {

    public Apt0Bridge() {
        super("apt", "in development", "Ubuntu 26.04");
    }

    @Override
    public List<Pkg> onGetPackagesAll(Set<Process> processes) {
        var rawBlocksList = new ArrayList<Pkg>();

        var installedPackages = new HashSet<String>();
        try {
            var dpkgProcess = new ProcessBuilder("dpkg-query", "-W", "-f=${Package}\n").start();
            try (var it = IOUtils.lineIterator(dpkgProcess.getInputStream(), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var line = it.next();
                    if (!line.isBlank()) {
                        installedPackages.add(line.trim());
                    }
                }
            }
            dpkgProcess.waitFor();
        } catch (IOException | InterruptedException ex) {
            Exceptions.printStackTrace(ex);
        }

        var command = List.of("apt-cache", "show", ".*");
        System.out.println(String.join(" ", command));

        var dict = PkgDictionary.getInstance();
        Process process = null;

        String currentName = null;
        Pkg currentPkg = null;
        StringBuilder descriptionBuilder = null;
        boolean parsingDescription = false;

        try {
            process = new ProcessBuilder(command).start();
            processes.add(process);

            try (var it = IOUtils.lineIterator(process.getInputStream(), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var line = it.next();

                    if (line.isBlank()) {
                        if (currentPkg != null && currentName != null) {
                            if (descriptionBuilder != null) {
                                currentPkg.setDescription(descriptionBuilder.toString().trim());
                            }
                            rawBlocksList.add(currentPkg);
                        }
                        currentName = null;
                        currentPkg = null;
                        descriptionBuilder = null;
                        parsingDescription = false;
                        continue;
                    }

                    if (currentPkg == null) {
                        currentPkg = new Pkg();
                        currentPkg.setLicenseId(dict.getOrCreateId(DictionarySection.LICENSE, "Debian FOSS"));
                    }

                    if (parsingDescription) {
                        if (line.startsWith(" ") || line.startsWith("\t")) {
                            String cleanLine = line.trim();
                            if (".".equals(cleanLine)) {
                                descriptionBuilder.append("\n");
                            } else {
                                descriptionBuilder.append("\n").append(cleanLine);
                            }
                            continue;
                        } else {
                            parsingDescription = false;
                        }
                    }

                    if (line.startsWith("Package: ")) {
                        currentName = line.substring(9).trim();
                        currentPkg.setName(currentName);
                    } else {
                        if (line.startsWith("Version: ")) {
                            currentPkg.setVersion(line.substring(9).trim());
                            currentPkg.setRelease(line.substring(9).trim());
                        } else if (line.startsWith("Architecture: ")) {
                            currentPkg.setArchId(dict.getOrCreateId(DictionarySection.ARCH, line.substring(14).trim()));
                        } else if (line.startsWith("Section: ")) {
                            currentPkg.setRepositoryId(dict.getOrCreateId(DictionarySection.REPOSITORY, line.substring(9).trim()));
                            currentPkg.setGroupId(dict.getOrCreateId(DictionarySection.GROUP, line.substring(9).trim()));
                        } else if (line.startsWith("Maintainer: ")) {
                            var cleanMaintainer = org.apache.commons.lang3.StringUtils.substringBefore(line.substring(12).trim(), " <");
                            currentPkg.setPackagerId(dict.getOrCreateId(DictionarySection.PACKAGER, cleanMaintainer));
                            currentPkg.setVendorId(dict.getOrCreateId(DictionarySection.VENDOR, cleanMaintainer));
                        } else if (line.startsWith("Homepage: ")) {
                            currentPkg.setUrl(line.substring(10).trim());
                        } else if (line.startsWith("Size: ")) {
                            try {
                                currentPkg.setSizeDownload(Long.parseLong(line.substring(6).trim()));
                            } catch (Exception e) {
                            }
                        } else if (line.startsWith("Installed-Size: ")) {
                            try {
                                currentPkg.setSizeInstall(Long.parseLong(line.substring(16).trim()) * 1024);
                            } catch (Exception e) {
                            }
                        } else if (line.startsWith("Description-md5:")) {
                            continue;
                        } else if (line.startsWith("Description")) {
                            int colonIdx = line.indexOf(":");
                            if (colonIdx != -1) {
                                String summaryText = line.substring(colonIdx + 1).trim();
                                currentPkg.setSummary(summaryText);
                                descriptionBuilder = new StringBuilder(summaryText);
                                parsingDescription = true;
                            }
                        }
                    }
                }

                if (currentPkg != null && currentPkg.getName() != null) {
                    if (descriptionBuilder != null) {
                        currentPkg.setDescription(descriptionBuilder.toString().trim());
                    }
                    rawBlocksList.add(currentPkg);
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            rawBlocksList.clear();
        } finally {
            if (process != null) {
                processes.remove(process);
            }
        }

        var packagesMap = new HashMap<String, Pkg>();

        for (var blockPkg : rawBlocksList) {
            var name = blockPkg.getName();

            if (installedPackages.contains(name)) {
                blockPkg.setInstalled(true);
            }

            if (!packagesMap.containsKey(name)) {
                packagesMap.put(name, blockPkg);
            } else {
                var existing = packagesMap.get(name);

                var existingDesc = existing.getDescription() != null ? existing.getDescription() : "";
                var blockDesc = blockPkg.getDescription() != null ? blockPkg.getDescription() : "";

                if (blockDesc.length() > existingDesc.length()) {
                    existing.setDescription(blockDesc);
                    existing.setSummary(blockPkg.getSummary());
                }

                if ((existing.getUrl() == null || existing.getUrl().isEmpty()) && blockPkg.getUrl() != null) {
                    existing.setUrl(blockPkg.getUrl());
                }

                if (blockPkg.isInstalled()) {
                    blockPkg.setDescription(existing.getDescription());
                    packagesMap.put(name, blockPkg);
                }
            }
        }

        return packagesMap.values().stream()
                .sorted(Comparator.comparing(Pkg::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public String onGetVersion(Set<Process> processes) {
        return execute(List.of("apt", "--version"), processes);
    }

    @Override
    public List<String> onProvideCacheClearCommand() {
        return List.of("pkexec", "apt-get", "clean");
    }

    @Override
    public List<String> onProvideCacheUpdateCommand() {
        return List.of("pkexec", "apt-get", "update");
    }

    @Override
    public List<String> onProvideVersionCommand() {
        return List.of("apt", "--version");
    }
}
