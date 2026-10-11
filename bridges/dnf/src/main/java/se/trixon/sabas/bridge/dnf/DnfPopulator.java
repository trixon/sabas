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
package se.trixon.sabas.bridge.dnf;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.openide.util.Exceptions;
import se.trixon.sabas.api.AppStreamPackage;
import se.trixon.sabas.api.AppStreamPopulator;
import se.trixon.sabas.api.BridgePopulator;
import se.trixon.sabas.api.DictionarySection;
import se.trixon.sabas.api.Pkg;
import se.trixon.sabas.api.PkgCategory;
import static se.trixon.sabas.bridge.dnf.DnfBridge.DNF_COMMAND;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class DnfPopulator extends BridgePopulator {

    private final AppStreamPopulator mAppStreamPopulator = new AppStreamPopulator();
    private final AppStreamReader mAppStreamReader = new AppStreamReader();
    private final ExecutorService mDnfExecutor = Executors.newFixedThreadPool(3);

    public DnfPopulator() {
        initEnricher();
    }

    @Override
    public List<Pkg> populate(Set<Process> processes) {
        CompletableFuture<HashMap<String, Pkg>> installedFuture
                = CompletableFuture.supplyAsync(() -> getPackages(processes, List.of(DNF_COMMAND, "repoquery", "--installed", "--queryformat")), mDnfExecutor);

        CompletableFuture<HashMap<String, Pkg>> availableFuture
                = CompletableFuture.supplyAsync(() -> getPackages(processes, List.of(DNF_COMMAND, "repoquery", "--available", "--queryformat")), mDnfExecutor);

        CompletableFuture<HashMap<String, Pkg>> upgradableFuture
                = CompletableFuture.supplyAsync(() -> getPackages(processes, List.of(DNF_COMMAND, "repoquery", "--upgrades", "--queryformat")), mDnfExecutor);

        try {
            CompletableFuture.allOf(installedFuture, availableFuture, upgradableFuture).join();

            var installedPackages = installedFuture.get();
            var availablePackages = availableFuture.get();
            var upgradablePackages = upgradableFuture.get();
            var onlineByName = new HashMap<String, Pkg>();

            for (var pkg : availablePackages.values()) {
                var uniqueKey = pkg.getName() + ":" + pkg.getArchId();
                var existingPkg = onlineByName.get(uniqueKey);

                if (existingPkg == null) {
                    onlineByName.put(uniqueKey, pkg);
                } else {
                    if (pkg.getTimeBuild() > existingPkg.getTimeBuild()) {
                        onlineByName.put(uniqueKey, pkg);
                    }
                }
            }

            for (var entry : installedPackages.entrySet()) {
                var installedPkg = entry.getValue();
                var uniqueKey = installedPkg.getName() + ":" + installedPkg.getArchId();
                var mainPkg = onlineByName.get(uniqueKey);

                if (mainPkg != null) {
                    mainPkg.setInstalled(true);
                    mainPkg.setTimeInstalled(installedPkg.getTimeInstalled());
                    mainPkg.setOrphaned(false);
                } else {
                    installedPkg.setInstalled(true);
                    installedPkg.setOrphaned(true);
                    availablePackages.put(entry.getKey(), installedPkg);
                    onlineByName.put(uniqueKey, installedPkg);
                }
            }

            for (var entry : upgradablePackages.entrySet()) {
                var upgradePkg = entry.getValue();
                var uniqueKey = upgradePkg.getName() + ":" + upgradePkg.getArchId();
                var mainPkg = onlineByName.get(uniqueKey);
                if (mainPkg != null && mainPkg.isInstalled()) {
                    mainPkg.setUpgradable(true);
                    mainPkg.setVersionNew(upgradePkg.getVersion());
                }
            }

            var packages = onlineByName.values().stream()
                    .sorted(Comparator.comparing(Pkg::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            postAsyncPopulate(() -> {
                mAppStreamReader.read(onlineByName);
                packages.forEach(pkg -> computeCategory(pkg));
            });

            mDictionary.retainOnlyActiveItems(packages);

            return packages;
        } catch (InterruptedException | ExecutionException e) {
            Exceptions.printStackTrace(e);
            return List.of();
        }
    }

    @Override
    public Pkg.Details populateDetails(Set<Process> processes, Pkg pkg) {
//        System.out.println(pkg.getId());
        final var querytags = List.of(
                "files",
                "requires",
                "provides"
        );

        var command = new ArrayList<>(List.of(DNF_COMMAND, "repoquery", "--available", "--installed", pkg.getName(), "--queryformat"));
        command.add(querytags.stream()
                .map(s -> "%%{%s}".formatted(s))
                .collect(Collectors.joining(FIELD_SEPARATOR)) + RECORD_SEPARATOR);
//        System.out.println(String.join(" ", command));
//        System.out.println(System.currentTimeMillis());
        String files = "";
        String requires = "";
        String provides = "";

        Process process = null;
        try {
            process = new ProcessBuilder(command).start();
            processes.add(process);

            try (var scanner = new Scanner(new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8")))) {
                scanner.useDelimiter(RECORD_SEPARATOR);
//                Thread.sleep(25_000);
                if (scanner.hasNext()) {
                    var rawRecord = scanner.next();
                    var fields = StringUtils.splitPreserveAllTokens(rawRecord, FIELD_SEPARATOR);
                    if (fields.length >= querytags.size()) {
                        files = fields[0];
                        requires = fields[1];
                        provides = fields[2];
                    }
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            //
        } finally {
            if (process != null) {
                processes.remove(process);
            }
        }

        var details = new Pkg.Details();
        details.setFiles(stripDuplicateRowss(files));
        details.setProvides(stripDuplicateRowss(provides));
        details.setRequires(stripDuplicateRowss(requires));

        return details;
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
            } else if (ci.endsWithAny(name, "-doc", "-manual")) {
                category = PkgCategory.DOC;
            } else if (ci.contains(name, "font") || ci.endsWith(name, "-fonts")) {
                category = PkgCategory.FONT;
            } else if (ci.containsAny(name, "theme", "-icon-")) {
                category = PkgCategory.THEME;
            } else if (ci.containsAny(name, "systemd", "service", "daemon", "agent")) {
                category = PkgCategory.SERVICE;
            }

            pkg.setCategory(category);
        }
    }

    private HashMap<String, Pkg> getPackages(Set<Process> processes, List<String> args) {
        final var querytags = List.of(
                "full_nevra",
                "name",
                "group",
                "version",
                "arch",
                "summary",
                "description",
                "license",
                "downloadsize",
                "installsize",
                "url",
                "vendor",
                "repoid",
                "packager",
                "release",
                "installtime",
                "buildtime",
                "epoch"
        );
        var command = new ArrayList<String>(args);
        command.add(querytags.stream()
                .map(s -> "%%{%s}".formatted(s))
                .collect(Collectors.joining(FIELD_SEPARATOR)) + RECORD_SEPARATOR);
//        System.out.println(String.join(" ", command));
        var packages = new HashMap<String, Pkg>();
        Process process = null;
        try {
            process = new ProcessBuilder(command).start();
            processes.add(process);
            try (var scanner = new Scanner(new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8")))) {
                scanner.useDelimiter(RECORD_SEPARATOR);
                final int full_nevraIndex = querytags.indexOf("full_nevra");
                final int nameIndex = querytags.indexOf("name");
                final int groupIndex = querytags.indexOf("group");
                final int versionIndex = querytags.indexOf("version");
                final int archIndex = querytags.indexOf("arch");
                final int summaryIndex = querytags.indexOf("summary");
                final int descriptionIndex = querytags.indexOf("description");
                final int licenseIndex = querytags.indexOf("license");
                final int epochIndex = querytags.indexOf("epoch");
                final int downloadsizeIndex = querytags.indexOf("downloadsize");
                final int installsizeIndex = querytags.indexOf("installsize");
                final int urlIndex = querytags.indexOf("url");
                final int vendorIndex = querytags.indexOf("vendor");
                final int repoidIndex = querytags.indexOf("repoid");
                final int packagerIndex = querytags.indexOf("packager");
                final int releaseIndex = querytags.indexOf("release");
                final int installtimeIndex = querytags.indexOf("installtime");
                final int buildtimeIndex = querytags.indexOf("buildtime");

                while (scanner.hasNext()) {
//                    Thread.sleep(1);
                    var rawPackage = scanner.next();
                    if (rawPackage.trim().isEmpty()) {
                        continue;
                    }
                    var fields = StringUtils.splitPreserveAllTokens(rawPackage, FIELD_SEPARATOR);
                    if (fields.length < querytags.size()) {
                        break;
                    }

                    var pkg = new Pkg();
                    pkg.setId(fields[full_nevraIndex]);
                    pkg.setName(fields[nameIndex]);
                    pkg.setGroupId(mDictionary.getOrCreateId(DictionarySection.GROUP, fields[groupIndex]));
                    pkg.setVersion(fields[versionIndex]);
                    pkg.setArchId(mDictionary.getOrCreateId(DictionarySection.ARCH, fields[archIndex]));
                    pkg.setSummary(fields[summaryIndex]);
                    pkg.setDescription(fields[descriptionIndex]);
                    pkg.setLicenseId(mDictionary.getOrCreateId(DictionarySection.LICENSE, fields[licenseIndex]));
                    var epoch = -1;
                    try {
                        epoch = Integer.parseInt(fields[epochIndex]);
                    } catch (NumberFormatException e) {
                        //
                    }
                    pkg.setEpoch(epoch);
                    pkg.setSizeDownload(Long.parseLong(fields[downloadsizeIndex]));
                    pkg.setSizeInstall(Long.parseLong(fields[installsizeIndex]));
                    pkg.setUrl(fields[urlIndex]);
                    pkg.setVendorId(mDictionary.getOrCreateId(DictionarySection.VENDOR, fields[vendorIndex]));
                    pkg.setRepositoryId(mDictionary.getOrCreateId(DictionarySection.REPOSITORY, fields[repoidIndex]));
                    pkg.setPackagerId(mDictionary.getOrCreateId(DictionarySection.PACKAGER, fields[packagerIndex]));
                    pkg.setRelease(fields[releaseIndex]);
                    pkg.setTimeBuild(Long.parseLong(fields[buildtimeIndex]));
                    pkg.setTimeInstalled(Long.parseLong(fields[installtimeIndex]));

                    packages.put(pkg.getId(), pkg);
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            Exceptions.printStackTrace(e);
        } finally {
            processes.remove(process);
        }

        return packages;
    }

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
//                pkg.setName(name);
            }

            var description = asp.getDescription();
            if (StringUtils.isNotBlank(description)) {
                var cleanText = description
                        .replaceAll("<[^>]*>", "")
                        .replace("\n", "")
                        .trim();
                boolean isOnlyNull = cleanText.matches("^(\\s*\\(null\\)\\s*)+$");

                if (!cleanText.isEmpty() && !isOnlyNull) {
                    pkg.setDescription("<html>%s</html>".formatted(description));
                }
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

                var remappedMap = packageMap.values().parallelStream()
                        .collect(Collectors.toConcurrentMap(
                                pkg -> mDictionary.getString(DictionarySection.REPOSITORY, pkg.getRepositoryId()) + ":" + pkg.getName(),
                                pkg -> pkg,
                                (existing, replacement) -> existing
                        ));
                xmlFiles.parallelStream().forEach(xmlPath -> {
                    parseXmlFile(xmlPath, remappedMap);
                });
            } catch (Exception e) {
                Exceptions.printStackTrace(e);
            }
        }

        private Function<AppStreamPackage, String> createAppStreamToIdFunction(String repo) {
            return asp -> {
                return repo + ":" + asp.getPkgName();
            };
        }

        private void parseXmlFile(Path xmlPath, Map<String, Pkg> packageMap) {
            var filename = xmlPath.getFileName().toString();
            var repo = filename.toLowerCase(Locale.ROOT);
            repo = Strings.CI.removeEnd(repo, ".xml.gz");
            repo = Strings.CI.removeEnd(repo, ".xml");

            var isGz = filename.toLowerCase(Locale.ROOT).endsWith(".gz");

            try {
                mAppStreamPopulator.populate(
                        xmlPath,
                        isGz,
                        packageMap,
                        repo,
                        createAppStreamToIdFunction(repo)
                );
            } catch (Exception e) {
                Exceptions.printStackTrace(e);
            }
        }
    }

}
