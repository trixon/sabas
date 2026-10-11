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
package se.trixon.sabas.bridge.apt;

import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.openide.util.Exceptions;
import se.trixon.sabas.api.AppStreamPackage;
import se.trixon.sabas.api.AppStreamPopulator;
import static se.trixon.sabas.api.Bridge.createProcessBuilder;
import se.trixon.sabas.api.BridgePopulator;
import se.trixon.sabas.api.DictionarySection;
import se.trixon.sabas.api.Pkg;
import se.trixon.sabas.api.PkgCategory;
import static se.trixon.sabas.bridge.apt.AptBridge.APT_GET;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class AptPopulator extends BridgePopulator {

    private final AppStreamPopulator mAppStreamPopulator = new AppStreamPopulator();
    private final AppStreamReader mAppStreamReader = new AppStreamReader();

    public AptPopulator() {
        initEnricher();
    }

    @Override
    public List<Pkg> populate(Set<Process> processes) {
        var nameToPackageMap = new ConcurrentHashMap<String, Pkg>(80_000);
        var nameToRepoMap = getPkgRepoMap();
        var installedPackages = getPkgInstalled();
        var orphanedPackages = getPkgOrphaned();
        var upgradablePackages = getPkgUpgradableMap();
        var rawBlocksList = getPkgRawList(processes);

        for (var blockPkg : rawBlocksList) {
            var name = blockPkg.getName();

            if (upgradablePackages.keySet().contains(name)) {
                blockPkg.setUpgradable(true);
                blockPkg.setVersionNew(upgradablePackages.get(name));
            }

            var repoName = nameToRepoMap.getOrDefault(name, "LOCAL");
            blockPkg.setRepositoryId(mDictionary.getOrCreateId(DictionarySection.REPOSITORY, repoName));

            if (installedPackages.contains(name)) {
                blockPkg.setInstalled(true);
                //blockPkg.setRepositoryId(mDictionary.getOrCreateId(DictionarySection.REPOSITORY, "Installed"));

                if (orphanedPackages.contains(name)) {
                    blockPkg.setOrphaned(true);
                }
                String arch = blockPkg.getArch();

                var dpkgListPath = Path.of("/var/lib/dpkg/info/" + name + ".list");
                if (!Files.exists(dpkgListPath) && arch != null) {
                    dpkgListPath = Path.of("/var/lib/dpkg/info/" + name + ":" + arch + ".list");
                }

                if (Files.exists(dpkgListPath)) {
                    try {
                        blockPkg.setTimeInstalled(Files.getLastModifiedTime(dpkgListPath).to(TimeUnit.SECONDS));
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
            String keyRepo = mDictionary.getString(DictionarySection.REPOSITORY, blockPkg.getRepositoryId());
            String keyName = blockPkg.getName();
            var key = StringUtils.substringAfter(keyRepo, "-") + ":" + keyName;
            if (!nameToPackageMap.containsKey(key)) {
                nameToPackageMap.put(key, blockPkg);
            } else {
                var existing = nameToPackageMap.get(key);

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
                    nameToPackageMap.put(key, blockPkg);
                }
            }
        }

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
        var name = StringUtils.substringBefore(pkg.getName(), ":");

        details.setRequired(getPkgDetailsRequired(processes, name));
        details.setRequires(getPkgDetailsRequires(processes, name));
        details.setProvides(getPkgDetailsProvides(processes, name));

        if (pkg.isInstalled()) {
            var changelogPath = Path.of("/usr/share/doc/" + name + "/changelog.Debian.gz");
            if (Files.exists(changelogPath)) {
                try {
                    var buildTime = Files.getLastModifiedTime(changelogPath).to(TimeUnit.SECONDS);
                    pkg.setTimeBuild(buildTime);
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }

            details.setFiles(getPkgDetailsFiles(processes, name));
        } else {
            details.setFiles("Listan över installerade filer är endast tillgänglig för installerade paket");
        }

        return details;
    }

    private void computeCategory(Pkg pkg) {
        if (pkg.getCategory() == null || pkg.getCategory() == PkgCategory.OTHER) {
            var category = PkgCategory.OTHER;
            var name = pkg.getName();
            var ci = Strings.CI;

            if (ci.startsWithAny(name, "lib")) {
                category = PkgCategory.LIB;
            } else if (ci.endsWithAny(name, "-dev", "-build")) {
                category = PkgCategory.DEV;
            } else if (ci.endsWithAny(name, "-doc", "-manual")) {
                category = PkgCategory.DOC;
            } else if (ci.containsAny(name, "font", "fonts-")) {
                category = PkgCategory.FONT;
            } else if (ci.containsAny(name, "theme", "-icon-")) {
                category = PkgCategory.THEME;
            } else if (ci.containsAny(name, "systemd", "service", "daemon", "agent")) {
                category = PkgCategory.SERVICE;
            }

            pkg.setCategory(category);
        }
    }

    private String getPkgDetailsFiles(Set<Process> processes, String name) {
        Process p = null;
        var sb = new StringBuilder();
        try {
            p = createProcessBuilder(List.of("dpkg", "-L", name)).start();
            processes.add(p);

            try (var it = IOUtils.lineIterator(p.getInputStream(), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var fileRoute = it.next().trim();
                    if (!fileRoute.isEmpty() && !fileRoute.endsWith("/.")) {
                        var fileCheck = Path.of(fileRoute);
                        if (Files.isRegularFile(fileCheck)) {
                            sb.append(fileRoute).append("\n");
                        }
                    }
                }
            }
            p.waitFor();

            if (sb.isEmpty()) {
                sb.append("Paketet innehåller inga filer.");
            }
        } catch (IOException | InterruptedException e) {
            sb.append("Kunde inte hämta filstrukturen.");
        } finally {
            if (p != null) {
                processes.remove(p);
            }
        }

        return sb.toString();
    }

    private String getPkgDetailsProvides(Set<Process> processes, String name) {
        Process p = null;
        var sb = new StringBuilder();
        try {
            p = createProcessBuilder(List.of("apt-cache", "show", name)).start();
            processes.add(p);

            try (var it = IOUtils.lineIterator(p.getInputStream(), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var line = it.next();
                    if (line.isBlank() && sb.length() > 0) {
                        break;
                    }
                    if (line.startsWith("Provides:")) {
                        String prov = line.substring(9).trim();
                        sb.append(prov).append("\n");
                    }
                }
            }
            p.destroy();
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            //
        } finally {
            if (p != null) {
                processes.remove(p);
            }
        }

        return sb.toString().trim();
    }

    private String getPkgDetailsRequired(Set<Process> processes, String name) {
        Process p = null;
        var sb = new StringBuilder();
        try {
            p = createProcessBuilder(List.of("apt-cache", "rdepends", name)).start();
            processes.add(p);

            try (var it = IOUtils.lineIterator(p.getInputStream(), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    String line = it.next();

                    if (line.startsWith(name) || line.contains("Reverse Depends:")) {
                        continue;
                    }

                    if (line.startsWith(" ") || line.startsWith("\t")) {
                        String dependentPackage = line.trim();
                        if (!dependentPackage.isEmpty()) {
                            sb.append(dependentPackage).append("\n");
                        }
                    }
                }
            }
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            //
        } finally {
            if (p != null) {
                processes.remove(p);
            }
        }

        return sb.toString().trim();
    }

    private String getPkgDetailsRequires(Set<Process> processes, String name) {
        Process p = null;
        var sb = new StringBuilder();
        try {
            p = createProcessBuilder(List.of("apt-cache", "depends", name)).start();
            processes.add(p);

            try (var it = IOUtils.lineIterator(p.getInputStream(), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var line = it.next();
                    if (line.contains("Depends:") || line.contains("Pre-Depends:")) {
                        int colonIdx = line.indexOf(":");
                        if (colonIdx != -1) {
                            String dep = line.substring(colonIdx + 1).trim();
                            sb.append(dep).append("\n");
                        }
                    }
                }
            }
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            //
        } finally {
            if (p != null) {
                processes.remove(p);
            }
        }

        return sb.toString().trim();
    }

    private HashSet<String> getPkgInstalled() {
        var packageNames = new HashSet<String>();
        try {
            var p = createProcessBuilder(List.of("dpkg-query", "-W", "-f=${Package}\n")).start();
            try (var it = IOUtils.lineIterator(p.getInputStream(), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var line = it.next();
                    if (!line.isBlank()) {
                        packageNames.add(line.trim());
                    }
                }
            }
            p.waitFor();
        } catch (IOException | InterruptedException ex) {
            Exceptions.printStackTrace(ex);
        }

        return packageNames;
    }

    private HashSet<String> getPkgOrphaned() {
        var packageNames = new HashSet<String>();
        try {
            var p = createProcessBuilder(List.of(APT_GET, "autoremove", "--simulate")).start();
            try (var it = IOUtils.lineIterator(p.getInputStream(), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var line = it.next();
                    if (line.startsWith("Remv ")) {
                        var tokens = StringUtils.split(line);
                        if (tokens != null && tokens.length > 1) {
                            var pkgName = tokens[1].trim();
                            packageNames.add(pkgName);
                        }
                    }
                }
            }
            p.waitFor();
        } catch (IOException | InterruptedException ex) {
            Exceptions.printStackTrace(ex);
        }

        return packageNames;
    }

    private ArrayList<Pkg> getPkgRawList(Set<Process> processes) {
        var rawBlocksList = new ArrayList<Pkg>();
        var command = List.of("apt-cache", "show", ".*");
        System.out.println(String.join(" ", command));

        Process p = null;
        String currentName = null;
        Pkg currentPkg = null;
        StringBuilder descriptionBuilder = null;
        boolean parsingDescription = false;

        try {
            p = createProcessBuilder(command).start();
            processes.add(p);

            try (var it = IOUtils.lineIterator(p.getInputStream(), StandardCharsets.UTF_8)) {
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
                        currentPkg.setLicenseId(mDictionary.getOrCreateId(DictionarySection.LICENSE, "Debian FOSS"));
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
                            String rawVersion = line.substring(9).trim();

                            String epoch = "0";
                            String versionAndRelease = rawVersion;

                            if (rawVersion.contains(":")) {
                                epoch = StringUtils.substringBefore(rawVersion, ":");
                                versionAndRelease = StringUtils.substringAfter(rawVersion, ":");
                            }

                            String version = versionAndRelease;
                            String release = "1";

                            if (versionAndRelease.contains("-")) {
                                version = StringUtils.substringBeforeLast(versionAndRelease, "-");
                                release = StringUtils.substringAfterLast(versionAndRelease, "-");
                            }

                            var epochInt = -1;
                            try {
                                epochInt = Integer.parseInt(epoch);
                            } catch (NumberFormatException e) {
                                //
                            }
                            currentPkg.setEpoch(epochInt);
                            currentPkg.setVersion(version);
                            currentPkg.setRelease(release);
                        } else if (line.startsWith("Architecture: ")) {
                            currentPkg.setArchId(mDictionary.getOrCreateId(DictionarySection.ARCH, line.substring(14).trim()));
                        } else if (line.startsWith("Section: ")) {
                            currentPkg.setGroupId(mDictionary.getOrCreateId(DictionarySection.GROUP, line.substring(9).trim()));
                        } else if (line.startsWith("Maintainer: ")) {
                            String cleanMaintainer = StringUtils.substringBefore(line.substring(12).trim(), " <");
                            currentPkg.setPackagerId(mDictionary.getOrCreateId(DictionarySection.PACKAGER, cleanMaintainer));
                            if (currentPkg.getVendorId() == 0) {
                                currentPkg.setVendorId(mDictionary.getOrCreateId(DictionarySection.VENDOR, cleanMaintainer));
                            }
                        } else if (line.startsWith("Vendor: ")) {
                            String explicitVendor = line.substring(8).trim();
                            currentPkg.setVendorId(mDictionary.getOrCreateId(DictionarySection.VENDOR, explicitVendor));
                        } else if (line.startsWith("Homepage: ")) {
                            currentPkg.setUrl(line.substring(10).trim());
                        } else if (line.startsWith("Size: ")) {
                            try {
                                currentPkg.setSizeDownload(Long.parseLong(line.substring(6).trim()));
                            } catch (NumberFormatException e) {
                            }
                        } else if (line.startsWith("Installed-Size: ")) {
                            try {
                                currentPkg.setSizeInstall(Long.parseLong(line.substring(16).trim()) * 1024);
                            } catch (NumberFormatException e) {
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
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            rawBlocksList.clear();
        } finally {
            if (p != null) {
                processes.remove(p);
            }
        }

        return rawBlocksList;
    }

    private HashMap<String, String> getPkgRepoMap() {
        var packageToRepoMap = new HashMap<String, String>();
        try {
            var aptListsPath = Path.of("/var/lib/apt/lists/");
            if (Files.isDirectory(aptListsPath)) {
                try (var stream = Files.newDirectoryStream(aptListsPath)) {
                    for (var path : stream) {
                        var filename = path.getFileName().toString();

                        if (filename.endsWith("_Packages")) {
                            var repoName = "Online Components";
                            if (filename.contains("_dists_")) {
                                var rawBase = StringUtils.substringBefore(filename, "_dists_").toLowerCase(Locale.ROOT);
                                var distPart = StringUtils.substringAfter(filename, "_dists_").toLowerCase(Locale.ROOT);
                                String[] distParts = StringUtils.split(distPart, "_");
                                if (distParts != null && distParts.length > 1) {
                                    var suite = distParts[0];
                                    var component = distParts[1];

                                    if (component.equals("non-free") && distParts.length > 2 && distParts[2].equals("firmware")) {
                                        component = "non-free-firmware";
                                    }
                                    var domain = StringUtils.substringBefore(rawBase, "_");
                                    String[] domainSegments = StringUtils.split(domain, ".");

                                    String osBase = "unknown";
                                    if (domainSegments != null && domainSegments.length > 1) {
                                        if (domainSegments[0].equals("deb") || domainSegments[0].equals("security") || domainSegments[0].equals("download") || domainSegments[0].equals("dl")) {
                                            osBase = domainSegments[1];
                                        } else {
                                            osBase = domainSegments[0];
                                        }
                                    }

                                    if (domain.contains("opensuse")) {
                                        osBase = "debian";
                                    }

                                    repoName = osBase + "-" + suite + "-" + component;
                                }
                            } else {
                                if (Strings.CI.contains(filename, "opensuse.org")) {
                                    var cleanPath = StringUtils.substringAfter(filename.toLowerCase(Locale.ROOT), "_home:_");
                                    cleanPath = StringUtils.substringBeforeLast(cleanPath, "_packages");

                                    cleanPath = StringUtils.replace(cleanPath, ":_", "-");
                                    cleanPath = StringUtils.replace(cleanPath, "%5f", "_");

                                    repoName = "opensuse.org-" + cleanPath;
                                } else {
                                    var domain = StringUtils.substringBefore(filename.toLowerCase(Locale.ROOT), "_");
                                    if (domain.contains(".")) {
                                        repoName = StringUtils.substringAfter(domain, ".");
                                    } else {
                                        repoName = domain;
                                    }
                                }
                            }

                            try (var lineIt = IOUtils.lineIterator(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                                while (lineIt.hasNext()) {
                                    var line = lineIt.next();
                                    if (line.startsWith("Package: ")) {
                                        packageToRepoMap.put(line.substring(9).trim(), repoName);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

        return packageToRepoMap;
    }

    private HashMap<String, String> getPkgUpgradableMap() {
        var upgradableMap = new HashMap<String, String>();
        try {
            var p = createProcessBuilder(List.of("apt", "list", "--upgradable")).start();
            try (var it = IOUtils.lineIterator(p.getInputStream(), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var line = it.next();
                    if (!line.isBlank() && !line.startsWith("Listing")) {
                        var pkgName = StringUtils.substringBefore(line, "/").trim();

                        if (!pkgName.isEmpty()) {
                            var afterNameBlock = StringUtils.substringAfter(line, " ");
                            var tokens = StringUtils.split(afterNameBlock);
                            if (tokens != null && tokens.length > 0) {
                                String newVersion = tokens[0].trim();
                                upgradableMap.put(pkgName, StringUtils.substringBefore(newVersion, "~"));
                            }
                        }
                    }
                }
            }
            p.waitFor();
        } catch (IOException | InterruptedException ex) {
            Exceptions.printStackTrace(ex);
        }

        return upgradableMap;
    }

    private void initEnricher() {
        BiConsumer<AppStreamPackage, Pkg> biConsumer = (asp, pkg) -> {
//            System.out.println("enriching " + pkg.getName());
//            System.out.println("  with " + asp.getPkgName());
//            if ("caja-dropbox".equalsIgnoreCase(pkg.getName())) {
//                System.out.println("PAUSE");
//            }
            pkg.setLicenseId(mDictionary.getOrCreateId(DictionarySection.LICENSE, asp.getLicense()));
            pkg.setVendorId(mDictionary.getOrCreateId(DictionarySection.VENDOR, asp.getDeveloperName()));
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

        public void read(Map<String, Pkg> nameToPackageMap) {
            var yamlBaseDir = Path.of("/var/lib/swcatalog/yaml");
            if (!Files.isDirectory(yamlBaseDir)) {
                return;
            }

            var yamlFiles = new ArrayList<Path>();
            try (var fileStream = Files.walk(yamlBaseDir, 1)) {
                fileStream
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            var name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return name.endsWith(".yml.gz") && name.contains("components");
                        })
                        .forEach(yamlFiles::add);
            } catch (IOException e) {
                Exceptions.printStackTrace(e);
            }

            yamlFiles.parallelStream().forEach(yamlPath -> {
                var filename = yamlPath.getFileName().toString();
                var repo = filename;
                if (repo.contains("_dists_")) {
                    repo = StringUtils.substringAfter(repo, "_dists_");
                    repo = StringUtils.substringBefore(repo, "_dep11");
                    repo = Strings.CI.replace(repo, "_", "-");
                }

                var pb = new ProcessBuilder(
                        "appstreamcli",
                        "convert",
                        "--format",
                        "xml",
                        yamlPath.toString(),
                        "-");

                try {
                    var process = pb.start();
                    try (var processInputStream = process.getInputStream()) {
                        var finalRepo = repo;
                        mAppStreamPopulator.populate(processInputStream, nameToPackageMap, repo, asp -> {
                            return finalRepo + ":" + asp.getPkgName();
                        });
                    }

                    process.waitFor();
                } catch (IOException | InterruptedException | XMLStreamException | JAXBException e) {
                    Exceptions.printStackTrace(e);
                }
            });
        }
    }
}
