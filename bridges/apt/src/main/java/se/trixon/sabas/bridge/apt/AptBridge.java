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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;
import se.trixon.sabas.api.Bridge;
import se.trixon.sabas.api.BridgeOperation;
import se.trixon.sabas.api.DictionarySection;
import se.trixon.sabas.api.Pkg;
import se.trixon.sabas.api.PkgDictionary;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
@ServiceProvider(service = Bridge.class)
public class AptBridge extends Bridge {

    public static final String APT_GET = "apt-get";

    private final Map<String, String> mDefaultEnvironment = new HashMap<>();
    private final List<String> mDefaultEnvironmentList;
    private final PkgDictionary mDictionary = PkgDictionary.getInstance();

    public AptBridge() {
        super("APT", "3.0", "Debian 13");
        mDefaultEnvironmentList = List.of("env",
                "DEBIAN_FRONTEND=noninteractive",
                "DEBCONF_NONINTERACTIVE_SEEN=true",
                "DEBCONF_NOWARNINGS=yes",
                "TERM=dumb"
        );
        initExecutors();
    }

    @Override
    public List<Pkg> onGetPackageAll(Set<Process> processes) {
        var nameToPackageMap = new HashMap<String, Pkg>();
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

            var repoName = nameToRepoMap.getOrDefault(name, "-");
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

            if (!nameToPackageMap.containsKey(name)) {
                nameToPackageMap.put(name, blockPkg);
            } else {
                var existing = nameToPackageMap.get(name);

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
                    nameToPackageMap.put(name, blockPkg);
                }
            }
        }

        return nameToPackageMap.values().stream()
                .sorted(Comparator.comparing(Pkg::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public Pkg.Details onGetPackageDetails(Set<Process> processes, Pkg pkg) {
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

    @Override
    public String onGetVersion(Set<Process> processes) {
        return execute(List.of("apt", "--version"), processes);
    }

    @Override
    public BridgeOperation onProvideCacheClearCommand() {
        return new BridgeOperation(
                createBaseCommand(createShellScript("clear", null)),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideCacheUpdateCommand() {
        return new BridgeOperation(
                createBaseCommand(createShellScript("update", null)),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionInstall(String... packages) {
        return new BridgeOperation(
                createBaseCommand(createShellScript("install", packages)),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionRemove(String... packages) {
        return new BridgeOperation(
                createBaseCommand(createShellScript("remove", packages)),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionUpgrade() {
        return new BridgeOperation(
                createBaseCommand(createShellScript("upgrade", null)),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideVersionCommand() {
        return new BridgeOperation(
                new ArrayList<>(List.of("apt", "--version")),
                mDefaultEnvironment
        );
    }

    private ArrayList<String> createBaseCommand(String shellCommand) {
        var command = new ArrayList<String>();
        command.add(PKEXEC);
        command.addAll(mDefaultEnvironmentList);
        command.add("sh");
        command.add("-c");
        command.add(shellCommand);

        return command;
    }

    private String createShellScript(String command, String[] packages) {
        // -q=2
        if (packages == null) {
            packages = new String[]{""};
        }

        return "%s %s %s %s %s && echo 'SABAS SUCCESS' || { echo 'SABAS ERROR'; exit 1; }"
                .formatted(
                        APT_GET,
                        command,
                        String.join(" ", packages),
                        "-o Dpkg::Use-Pty=0",
                        "-o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold"
                );
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
                                var distPart = org.apache.commons.lang3.StringUtils.substringAfter(filename, "_dists_");
                                repoName = distPart.replace("_binary-amd64_Packages", "")
                                        .replace("_binary-all_Packages", "")
                                        .replace("_main", "/main")
                                        .replace("_contrib", "/contrib")
                                        .replace("_non-free", "/non-free")
                                        .replace("_", "-");
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
                                upgradableMap.put(pkgName, newVersion);
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

    private void initExecutors() {
        mDefaultEnvironment.put("DEBIAN_FRONTEND", "noninteractive");
        mDefaultEnvironment.put("DEBCONF_NONINTERACTIVE_SEEN", "true");
        mDefaultEnvironment.put("DEBCONF_NOWARNINGS", "yes");
        mDefaultEnvironment.put("TERM", "dumb");

//        mExecutorCacheClear = new BridgeOperation(
//                new ArrayList<>(List.of(PKEXEC, APT_GET, "clear")),
//                mDefaultEnvironment
//        );
//
//        mExecutorCacheUpdate = new BridgeOperation(
//                new ArrayList<>(List.of(PKEXEC, APT_GET, "update")),
//                mDefaultEnvironment
//        );
//
//        /*
//        -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold"
//         */
//        mExecutorUpgrade = new BridgeOperation(
//                new ArrayList<>(List.of(PKEXEC, APT_GET, "upgrade")),
//                mDefaultEnvironment
//        );
    }

}
