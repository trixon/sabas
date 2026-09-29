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
package se.trixon.sabas.bridge.flatpak;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.openide.util.Exceptions;
import se.trixon.sabas.api.Bridge;
import se.trixon.sabas.api.BridgePopulator;
import se.trixon.sabas.api.DictionarySection;
import se.trixon.sabas.api.Pkg;
import static se.trixon.sabas.bridge.flatpak.FlatpakBridge.FLATPAK;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class FlatpakPopulator extends BridgePopulator {

    private final AppStreamPopulator mAppStreamPopulator = new AppStreamPopulator();

    public FlatpakPopulator() {
        initEnricher();
    }

    @Override
    public List<Pkg> populate(Set<Process> processes) {
        var rawPackagesList = new ArrayList<Pkg>();
        var idToPkgMap = new HashMap<String, Pkg>();
        var idToInstallTimeMap = new HashMap< String, Long>();
        var installedApps = getInstalled(idToInstallTimeMap);
        var upgradableApps = getUpgradable(processes);
        var command = List.of(
                FLATPAK,
                "remote-ls",
                "--app",
                //                "--arch=*",
                "--columns=all"
        );

        var querytags = List.of(
                "name",
                "description",
                "id",
                "version",
                "bransch",
                "arch",
                "origin",
                "ref",
                "commit",
                "runtime",
                "isize",
                "dsize"
        );

        Process process = null;
        try {
            var pb = Bridge.createProcessBuilder(command);
            pb.environment().put("LANGUAGE", "en_US");
            process = pb.start();
            processes.add(process);
            final int nameIndex = querytags.indexOf("name");
            final int descriptionIndex = querytags.indexOf("description");
            final int idIndex = querytags.indexOf("id");
            final int versionIndex = querytags.indexOf("version");
            final int branschIndex = querytags.indexOf("bransch");
            final int archIndex = querytags.indexOf("arch");
            final int originIndex = querytags.indexOf("origin");
            final int refIndex = querytags.indexOf("ref");
            final int commitIndex = querytags.indexOf("commit");
            final int runtimeIndex = querytags.indexOf("runtime");
            final int iSizeIndex = querytags.indexOf("isize");
            final int dSizeIndex = querytags.indexOf("dsize");

            try (var it = IOUtils.lineIterator(process.getInputStream(), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var line = it.next().trim();
                    if (line.isEmpty() || Strings.CI.containsAny(line, ".BaseApp", ".BaseExtension")) {
                        continue;
                    }

                    String[] fields = StringUtils.splitPreserveAllTokens(line, "\t");
                    if (fields.length < querytags.size()) {
                        break;
                    }

                    for (int i = 0; i < fields.length; i++) {
                        fields[i] = StringUtils.trimToEmpty(fields[i]);

                    }

                    var id = fields[idIndex];
                    var pkg = new Pkg();
                    pkg.setId(fields[refIndex]);
                    pkg.setName(fields[nameIndex]);
                    pkg.setNameTransaction(id);
                    pkg.setSummary(fields[descriptionIndex]);
                    pkg.setVersion(fields[versionIndex]);
                    pkg.setRelease(fields[branschIndex]);
                    pkg.setGroupId(mDictionary.getOrCreateId(DictionarySection.GROUP, fields[runtimeIndex]));
                    pkg.setArchId(mDictionary.getOrCreateId(DictionarySection.ARCH, fields[archIndex]));
                    pkg.setRepositoryId(mDictionary.getOrCreateId(DictionarySection.REPOSITORY, fields[originIndex]));
                    pkg.setSizeDownload(getSize(fields[dSizeIndex]));
                    pkg.setSizeInstall(getSize(fields[iSizeIndex]));

                    if (installedApps.contains(id)) {
                        pkg.setInstalled(true);
                        pkg.setTimeInstalled(idToInstallTimeMap.get(id));
                        pkg.setUpgradable(upgradableApps.contains(id));
                    }

                    rawPackagesList.add(pkg);
                    idToPkgMap.put(id, pkg);
                }
            }
            mAppStreamPopulator.populateAppStream(idToPkgMap);
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            Exceptions.printStackTrace(e);
            rawPackagesList.clear();
        } finally {
            if (process != null) {
                processes.remove(process);
            }
        }

        return rawPackagesList.stream()
                .sorted(Comparator.comparing(Pkg::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public Pkg.Details populateDetails(Set<Process> processes, Pkg pkg) {
        var details = new Pkg.Details();
        details.setRequires(pkg.getGroup());
        pkg.setDetails(details);

        return details;
    }

    private Set<String> getInstalled(HashMap<String, Long> idToInstallTimeMap) {
        var installedSet = new HashSet<String>();
        var basePaths = List.of("/var/lib", FileUtils.getUserDirectoryPath() + "/.local/share");

        for (var basePath : basePaths) {
            var path = Path.of("%s/flatpak/app".formatted(basePath));
            if (Files.isDirectory(path)) {
                try (var stream = Files.newDirectoryStream(path)) {
                    for (var versionDir : stream) {
                        if (Files.isDirectory(versionDir)) {
                            String appId = versionDir.getFileName().toString();
                            installedSet.add(appId);
                            try {
                                var fileTime = Files.getLastModifiedTime(versionDir);
                                long installEpochSeconds = fileTime.toInstant().getEpochSecond();
                                idToInstallTimeMap.put(appId, installEpochSeconds);
                            } catch (Exception e) {
                                //
                            }
                        }
                    }
                } catch (Exception e) {
                    //
                }
            }
        }

        return installedSet;
    }

    private Set<String> getUpgradable(Set<Process> processes) {
        var upgradeableSet = new HashSet<String>();
        var command = List.of(FLATPAK, "remote-ls", "--app", "--updates", "--columns=application");

        Process process = null;
        try {
            process = Bridge.createProcessBuilder(command).start();
            processes.add(process);

            try (var it = IOUtils.lineIterator(process.getInputStream(), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    String line = it.next().trim();
                    if (!line.isEmpty() && !line.startsWith("Application")) {
                        upgradeableSet.add(line);
                    }
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            Exceptions.printStackTrace(e);
        } finally {
            if (process != null) {
                processes.remove(process);
            }
        }

        return upgradeableSet;
    }

    /*

    private void parseSingleAppStreamFileOLD(FileInputStream fileStream, Map<String, Pkg> packageMap) throws Exception {
        String currentAppId = null;
        String currentUrl = null;
        String currentBuildTime = null;
        String currentPackager = null;
        String bestName = null;
        String bestSummary = null;
        String bestHtmlDescription = null;
        int namePriority = -1;
        int summaryPriority = -1;
        int currentLangPriority = -1;
        boolean insideDescription = false;
        var htmlBuilder = new StringBuilder();

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                var tagName = reader.getLocalName();

                if ("component".equals(tagName)) {
                    currentAppId = null;
                    currentPackager = "* Community Packager *";
                    bestName = null;
                    namePriority = -1;
                    bestSummary = null;
                    summaryPriority = -1;
                    bestHtmlDescription = null;
                    currentLangPriority = -1;
                    htmlBuilder.setLength(0);
                    insideDescription = false;
                } else if ("id".equals(tagName) && currentAppId == null) {
                    currentAppId = reader.getElementText().trim();
                } else if ("name".equals(tagName)) {
                    String lang = reader.getAttributeValue("http://w3.org", "lang");
                    if (lang == null) {
                        lang = reader.getAttributeValue(null, "lang");
                    }
                    String text = reader.getElementText().trim();
                    if (lang == null || "en".equalsIgnoreCase(lang)) {
                        if (namePriority < 0) {
                            bestName = text;
                            namePriority = 0;
                        }
                    } else if (systemLang.equalsIgnoreCase(lang)) {
                        bestName = text;
                        namePriority = 1;
                    }
                } else if ("summary".equals(tagName)) {
                    String lang = reader.getAttributeValue("http://w3.org", "lang");
                    if (lang == null) {
                        lang = reader.getAttributeValue(null, "lang");
                    }
                    String text = reader.getElementText().trim();
                    if (lang == null || "en".equalsIgnoreCase(lang)) {
                        if (summaryPriority < 0) {
                            bestSummary = text;
                            summaryPriority = 0;
                        }
                    } else if (systemLang.equalsIgnoreCase(lang)) {
                        bestSummary = text;
                        summaryPriority = 1;
                    }

                } else if ("release".equals(tagName)) {
                    String releaseTimestamp = reader.getAttributeValue(null, "timestamp");
                    if (releaseTimestamp != null && currentBuildTime == null) {
                        currentBuildTime = releaseTimestamp;
                    }
                } else if ("value".equals(tagName)) {
                    String key = reader.getAttributeValue(null, "key");
                    String valueText = reader.getElementText().trim();
                    if ("flathub::verification::website".equals(key)) {
                        currentPackager = valueText;
                    }
                } else if ("description".equals(tagName)) {
                    String lang = reader.getAttributeValue("http://w3.org", "lang");
                    if (lang == null) {
                        lang = reader.getAttributeValue(null, "lang");
                    }
                    if (lang == null || "en".equalsIgnoreCase(lang)) {
                        if (currentLangPriority < 0) {
                            insideDescription = true;
                            currentLangPriority = 0;
                            htmlBuilder.setLength(0);
                        }
                    } else if (systemLang.equalsIgnoreCase(lang)) {
                        insideDescription = true;
                        currentLangPriority = 1;
                        htmlBuilder.setLength(0);
                    } else {
                        insideDescription = false;
                    }

                } else if (insideDescription) {
                    htmlBuilder.append("<").append(tagName);
                    int attributeCount = reader.getAttributeCount();
                    for (int i = 0; i < attributeCount; i++) {
                        htmlBuilder.append(" ").append(reader.getAttributeLocalName(i)).append("=\"").append(reader.getAttributeValue(i)).append("\"");
                    }
                    htmlBuilder.append(">");
                }
            }

            if (event == XMLStreamConstants.CHARACTERS && insideDescription) {
                String text = reader.getText();
                if (text != null) {
                    htmlBuilder.append(text);
                }
            }

            if (event == XMLStreamConstants.END_ELEMENT) {
                String tagName = reader.getLocalName();

                if ("description".equals(tagName)) {
                    if (insideDescription) {
                        bestHtmlDescription = htmlBuilder.toString().trim();
                    }
                    insideDescription = false;
                } else if (insideDescription) {
                    htmlBuilder.append("</").append(tagName).append(">");
                } else if ("component".equals(tagName)) {
                    if (currentAppId != null && packageMap.containsKey(currentAppId)) {
                        var pkg = packageMap.get(currentAppId);
                        if (bestName != null) {
                            pkg.setName(bestName);
                        }
                        if (currentPackager != null) {
                            pkg.setPackagerId(mDictionary.getOrCreateId(DictionarySection.PACKAGER, currentPackager));
                        }
                        if (bestHtmlDescription != null && !bestHtmlDescription.isEmpty()) {
                            pkg.setDescription("<html>" + bestHtmlDescription + "</html>");
                        }
                        if (currentBuildTime != null) {
                            long timestamp = Long.parseLong(currentBuildTime);
                            long localMidnightSeconds = Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() / 1000;
                            pkg.setTimeBuild(localMidnightSeconds);
                        }
                    }
                }
            }
        }
    }
     */
    private void initEnricher() {
        BiConsumer<AppStreamPackage, Pkg> biConsumer = (asp, pkg) -> {
            pkg.setLicenseId(mDictionary.getOrCreateId(DictionarySection.LICENSE, asp.getLicense()));
            pkg.setVendorId(mDictionary.getOrCreateId(DictionarySection.VENDOR, asp.getDeveloperName()));
            pkg.setUrl(asp.getUrl("homepage"));
            var summary = asp.getSummary();
            if (StringUtils.isNotBlank(summary)) {
                pkg.setSummary(summary);
            }
            var name = asp.getName();
            if (StringUtils.isNotBlank(name)) {
                pkg.setName(name);
            }
            var description = asp.getDescription();
            if (StringUtils.isNotBlank(description)) {
                pkg.setDescription(description);
            }
        };

        mAppStreamPopulator.setEnricher(biConsumer);
    }

}
