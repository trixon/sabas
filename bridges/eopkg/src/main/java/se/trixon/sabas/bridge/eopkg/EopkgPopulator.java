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
package se.trixon.sabas.bridge.eopkg;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.openide.util.Exceptions;
import se.trixon.sabas.api.AppStreamPackage;
import se.trixon.sabas.api.AppStreamPopulator;
import se.trixon.sabas.api.Bridge;
import se.trixon.sabas.api.BridgePopulator;
import se.trixon.sabas.api.DictionarySection;
import se.trixon.sabas.api.Pkg;
import se.trixon.sabas.api.PkgCategory;
import static se.trixon.sabas.bridge.eopkg.EopkgBridge.EOPKG;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class EopkgPopulator extends BridgePopulator {

    private final AppStreamPopulator mAppStreamPopulator = new AppStreamPopulator();
    private final AppStreamReader mAppStreamReader = new AppStreamReader();
    private final DateTimeFormatter mFormatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH);

    public EopkgPopulator() {
        initEnricher();
    }

    @Override
    public List<Pkg> populate(Set<Process> processes) {
        var nameToPackageMap = new ConcurrentHashMap<String, Pkg>();
        var command = List.of(EOPKG, "list-available", "--long", "--no-color");

        Process process = null;
        try {
            var pb = Bridge.createProcessBuilder(command);

            process = pb.start();
            processes.add(process);

            String currentRepo = null;
            String currentName = null;
            String currentGroup = null;
            String currentDependencies = null;
            String currentProvides = null;
            String currentVersion = null;
            String currentRelease = null;
            String currentSummary = null;
            String currentDescription = null;
            String currentLicense = null;
            String currentArch = null;

            try (var it = IOUtils.lineIterator(process.getInputStream(), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var line = it.next().trim();
                    if (line.isEmpty()) {
                        if (currentName != null) {
                            var pkg = new Pkg();
                            pkg.setName(currentName);
                            pkg.setVersion(currentVersion != null ? currentVersion : "Available");
                            pkg.setRelease(currentRelease != null ? currentRelease : "1");
                            pkg.setSummary(currentSummary);

                            if (currentDescription != null) {
                                pkg.setDescription(currentDescription);
                            }

                            pkg.setGroupId(mDictionary.getOrCreateId(DictionarySection.GROUP, currentGroup));
                            pkg.setRepositoryId(mDictionary.getOrCreateId(DictionarySection.REPOSITORY, currentRepo));
                            //TODO Vendor
//                            pkg.setVendorId(mDictionary.getOrCreateId(DictionarySection.VENDOR, "Solus Community"));
                            pkg.setArchId(mDictionary.getOrCreateId(DictionarySection.ARCH, currentArch));
                            if (currentLicense != null) {
                                pkg.setLicenseId(mDictionary.getOrCreateId(DictionarySection.LICENSE, currentLicense));
                            }

                            var details = new Pkg.Details();
                            details.setProvides(currentProvides);
                            details.setRequires(Strings.CI.replace(currentDependencies, " ", "\n"));
                            pkg.setDetails(details);
//                            var key = mDictionary.getString(DictionarySection.REPOSITORY, pkg.getRepositoryId()).toLowerCase(Locale.ROOT) + ":" + currentName;
                            nameToPackageMap.put(currentName, pkg);
                        }

                        currentName = null;
                        currentVersion = null;
                        currentRelease = null;
                        currentSummary = null;
                        currentDescription = null;
                        currentLicense = null;
                        currentGroup = null;
                        currentArch = null;
                        currentProvides = null;
                        currentDependencies = null;
                        continue;
                    }

                    if (line.contains(":")) {
                        var key = StringUtils.substringBefore(line, ":").trim();
                        var value = StringUtils.substringAfter(line, ":").trim();

                        if ("Repository".equalsIgnoreCase(key)) {
                            currentRepo = value;
                        } else if ("Name".equalsIgnoreCase(key)) {
                            currentName = StringUtils.substringBefore(value, ",").trim();

                            if (value.contains("version:")) {
                                currentVersion = StringUtils.substringBetween(value, "version:", ",").trim();
                            }
                            if (value.contains("release:")) {
                                currentRelease = StringUtils.substringAfter(value, "release:").trim();
                            }
                        } else if ("Component".equalsIgnoreCase(key)) {
                            currentGroup = value;
                        } else if ("Summary".equalsIgnoreCase(key)) {
                            currentSummary = value;
                        } else if ("Description".equalsIgnoreCase(key)) {
                            currentDescription = value;
                        } else if ("Provides".equalsIgnoreCase(key)) {
                            currentProvides = value;
                        } else if ("Dependencies".equalsIgnoreCase(key)) {
                            currentDependencies = value;
                        } else if ("Licenses".equalsIgnoreCase(key)) {
                            currentLicense = value;
                        } else if ("Architecture".equalsIgnoreCase(key)) {
                            var items = StringUtils.split(value, ",");
                            currentArch = items[0];
                        }
                    }
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println("[SABAS EOPKG ERROR] " + e.getMessage());
            nameToPackageMap.clear();
        } finally {
            if (process != null) {
                processes.remove(process);
            }
        }

        var packageDir = Path.of("/var/lib/eopkg/index");
        if (Files.isDirectory(packageDir)) {
            getActiveRepositories(processes).stream()
                    .map(repository -> new File(packageDir.toFile(), repository + "/eopkg-index.xml"))
                    .filter(f -> f.isFile())
                    .forEach(f -> {
                        try (var fileStream = new FileInputStream(f)) {
                            applyXml(fileStream, nameToPackageMap);
                        } catch (FileNotFoundException ex) {
                            Exceptions.printStackTrace(ex);
                        } catch (IOException | XMLStreamException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    });
        }

        applyInstalled(processes, nameToPackageMap);
        applyOrphaned(processes, nameToPackageMap);
        applyUpgradable(processes, nameToPackageMap);

        var packages = nameToPackageMap.values().stream()
                .sorted(Comparator.comparing(Pkg::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        postAsyncPopulate(() -> {
            mAppStreamReader.read(nameToPackageMap);
            packages.forEach(pkg -> computeCategory(pkg));
        });

        mDictionary.retainOnlyActiveItems(packages);

        return packages;
    }

    @Override
    public Pkg.Details populateDetails(Set<Process> processes, Pkg pkg) {
        var details = new Pkg.Details();
//        details.setFiles(stripDuplicateRowss(files));
//        details.setProvides(stripDuplicateRowss(provides));
//        details.setRequires(stripDuplicateRowss(requires));

        return details;
    }

    private void applyInstalled(Set<Process> processes, Map<String, Pkg> packageMap) {
        var command = List.of(
                EOPKG,
                "--no-color",
                "list-installed",
                "--install-info"
        );

        Process process = null;
        try {
            var pb = Bridge.createProcessBuilder(command);

            process = pb.start();
            processes.add(process);
            try (var it = IOUtils.lineIterator(process.getInputStream(), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var line = it.next().trim();
                    if (!Strings.CI.contains(line, ":")) {
                        continue;
                    }
                    var items = StringUtils.split(line, '|');
                    var pkg = packageMap.get(items[0].trim());
                    if (pkg != null) {
                        pkg.setInstalled(true);
                        var dateStr = items[5].trim();
                        pkg.setTimeInstalled(LocalDateTime.parse(dateStr, mFormatter).atZone(ZoneId.systemDefault()).toEpochSecond());
                        pkg.setVersionNew(pkg.getVersion());
                        pkg.setVersion(StringUtils.trim(items[2]));
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
    }

    private void applyOrphaned(Set<Process> processes, Map<String, Pkg> packageMap) {
        var command = List.of(
                EOPKG,
                "--no-color",
                "list-installed",
                "--automatic"
        );

        Process process = null;
        try {
            var pb = Bridge.createProcessBuilder(command);

            process = pb.start();
            processes.add(process);
            try (var it = IOUtils.lineIterator(process.getInputStream(), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var line = it.next().trim();
                    if (Strings.CI.endsWith(line, "Orphaned package")) {
                        var tokens = StringUtils.split(line);
                        if (tokens != null && tokens.length > 0) {
                            var pkg = packageMap.get(tokens[0]);
                            if (pkg != null) {
                                pkg.setOrphaned(true);
                            }
                        }
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
    }

    private void applyUpgradable(Set<Process> processes, Map<String, Pkg> packageMap) {
        var command = List.of(
                EOPKG,
                "--no-color",
                "list-upgrades",
                "--install-info"
        );

        Process process = null;
        try {
            var pb = Bridge.createProcessBuilder(command);

            process = pb.start();
            processes.add(process);
            try (var it = IOUtils.lineIterator(process.getInputStream(), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var line = it.next().trim();
                    if (!Strings.CI.contains(line, ":")) {
                        continue;
                    }
                    var items = StringUtils.split(line, '|');
                    var pkg = packageMap.get(items[0].trim());
                    if (pkg != null) {
                        pkg.setUpgradable(true);
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
    }

    private void applyXml(FileInputStream fileStream, Map<String, Pkg> packageMap) throws XMLStreamException, IOException {
        var factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        var reader = factory.createXMLStreamReader(fileStream, "UTF-8");
        var xmlMapper = new XmlMapper();

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                var tagName = reader.getLocalName();

                if ("Package".equals(tagName)) {
                    var xmlPackage = xmlMapper.readValue(reader, XmlPackage.class);
                    var currentName = xmlPackage.getName();

                    if (currentName != null && packageMap.containsKey(currentName)) {
                        var pkg = packageMap.get(currentName);

                        pkg.setSizeInstall(xmlPackage.getInstalledSize());
                        pkg.setSizeDownload(xmlPackage.getPackageSize());
                        pkg.setId(StringUtils.substringAfterLast(xmlPackage.getPackageUri(), "/"));

                        if (xmlPackage.getSource() != null) {
                            pkg.setUrl(xmlPackage.getSource().getHomepage());
                            pkg.setVendorId(mDictionary.getOrCreateId(DictionarySection.VENDOR, extractDomain(pkg.getUrl())));
                            if (xmlPackage.getSource().getPackager() != null) {
                                var packagerName = StringUtils.trim(xmlPackage.getSource().getPackager().getName());
                                pkg.setPackagerId(mDictionary.getOrCreateId(DictionarySection.PACKAGER, packagerName));
                            }
                        }

                        if (xmlPackage.getHistory() != null && xmlPackage.getHistory().getUpdates() != null && !xmlPackage.getHistory().getUpdates().isEmpty()) {
                            var latestUpdate = xmlPackage.getHistory().getUpdates().getFirst();
                            var dateStr = latestUpdate.getDate();

                            if (StringUtils.isNotBlank(dateStr)) {
                                try {
                                    var localDate = java.time.LocalDate.parse(dateStr.trim());
                                    long epochSeconds = localDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
                                    pkg.setTimeBuild(epochSeconds);
                                } catch (Exception e) {
                                    //
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void computeCategory(Pkg pkg) {
        if (pkg.getCategory() == null || pkg.getCategory() == PkgCategory.OTHER) {
            var category = PkgCategory.OTHER;
            var name = pkg.getName();
            var ci = Strings.CI;

            if (ci.startsWith(name, "lib") || ci.endsWith(name, "-libs")) {
                category = PkgCategory.LIB;
            } else if (ci.endsWithAny(name, "-devel", "-build")) {
                category = PkgCategory.DEV;
            } else if (ci.endsWithAny(name, "-doc", "-docs")) {
                category = PkgCategory.DOC;
            } else if (ci.contains(name, "font") || ci.endsWith(name, "-fonts")) {
                category = PkgCategory.FONT;
            } else if (ci.containsAny(name, "theme", "-icon-")) {
                category = PkgCategory.THEME;
            } else if (ci.containsAny(name, "systemd", "service", "daemon", "agent")) {
                category = PkgCategory.SERVICE;
            }

            pkg.setCategory(category);
            pkg.setCategoryId(mDictionary.getOrCreateId(DictionarySection.CATEGORY, category.toString()));
        }
    }

    private String extractDomain(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return "Unknown";
        }

        try {
            URI uri = new URI(urlString.trim());
            String host = uri.getHost();

            if (host == null) {
                return "Unknown";
            }

            int lastDot = host.lastIndexOf('.');
            if (lastDot == -1) {
                return host.toLowerCase();
            }

            int secondToLastDot = host.lastIndexOf('.', lastDot - 1);

            if (secondToLastDot == -1) {
                return host.toLowerCase();
            }

            return host.substring(secondToLastDot + 1).toLowerCase();
        } catch (URISyntaxException e) {
            return "Unknown";
        }
    }

    private List<String> getActiveRepositories(Set<Process> processes) {
        var activeRepositories = new ArrayList<String>();
        var command = List.of(EOPKG, "list-repo", "--no-color");

        Process process = null;
        try {
            var pb = Bridge.createProcessBuilder(command);

            process = pb.start();
            processes.add(process);
            try (var it = IOUtils.lineIterator(process.getInputStream(), StandardCharsets.UTF_8)) {
                var active = " [active]";
                while (it.hasNext()) {
                    var line = it.next().trim();
                    if (Strings.CI.endsWith(line, active)) {
                        activeRepositories.add(StringUtils.substringBefore(line, active));
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

        return activeRepositories;
    }

    private void initEnricher() {
        BiConsumer<AppStreamPackage, Pkg> biConsumer = (asp, pkg) -> {
            pkg.setLicenseId(mDictionary.getOrCreateId(DictionarySection.LICENSE, asp.getLicense()));
            pkg.setUrl(asp.getUrl("homepage"));
            var summary = asp.getSummary();
            if (StringUtils.isNotBlank(summary)) {
                pkg.setSummary(summary);
            }
            var name = asp.getName();
            if (StringUtils.isNotBlank(name)) {
//                pkg.setName(name);
            }
            var description = asp.getDescription();
            if (StringUtils.isNotBlank(description)) {
                pkg.setDescription("<html>%s</html>".formatted(description));
            }
//            asp.getLatestReleaseTimestamp();
        };

        mAppStreamPopulator.setEnricher(biConsumer);

    }

    class AppStreamReader {

        public void read(Map<String, Pkg> packageMap) {
            var basePath = Path.of("/usr/share/swcatalog/xml/");

            if (!Files.isDirectory(basePath)) {
                return;
            }

            try (var fileStream = Files.list(basePath)) {
                var xmlFiles = fileStream
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            var name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return name.endsWith(".xml") || name.endsWith(".xml.gz");
                        })
                        .toList();

//                var remappedMap = packageMap.values().parallelStream()
//                        .collect(Collectors.toConcurrentMap(
//                                pkg -> mDictionary.getString(DictionarySection.REPOSITORY, pkg.getRepositoryId()) + ":" + pkg.getName(),
//                                pkg -> pkg,
//                                (existing, replacement) -> existing
//                        ));
                xmlFiles.parallelStream().forEach(xmlPath -> {
                    parseXmlFile(xmlPath, packageMap);
                });
            } catch (Exception e) {
                Exceptions.printStackTrace(e);
            }
        }

        private Function<AppStreamPackage, String> createAppStreamToIdFunction(String repo) {
            return asp -> {
                return asp.getPkgName();
            };
        }

        private void parseXmlFile(Path xmlPath, Map<String, Pkg> packageMap) {
            var filename = xmlPath.getFileName().toString();
            var repo = filename.toLowerCase(Locale.ROOT);
            repo = StringUtils.substringBefore(repo, ".xml");

            var isGz = filename.toLowerCase(Locale.ROOT).endsWith(".gz");

            try {
                mAppStreamPopulator.populate(
                        xmlPath,
                        isGz,
                        packageMap,
                        "",//TODO Adapt to future cganges
                        createAppStreamToIdFunction(repo)
                );
            } catch (Exception e) {
                Exceptions.printStackTrace(e);
            }
        }
    }

}
