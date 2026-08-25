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
package se.trixon.sabas.bridge.dnf0;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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
public class Dnf0Bridge extends Bridge {

    private final ExecutorService mDnfExecutor = Executors.newFixedThreadPool(3);

    public Dnf0Bridge() {
        super("dnf5", "in development", "Fedora 44");
    }

    @Override
    public Pkg.Details doGetPackageDetails(Pkg pkg) {
//        System.out.println(pkg.getId());
        final var querytags = List.of(
                "files",
                "requires",
                "provides"
        );

        var command = new ArrayList<>(List.of("dnf", "repoquery", "--available", "--installed", pkg.getName(), "--queryformat"));
        command.add(querytags.stream()
                .map(s -> "%%{%s}".formatted(s))
                .collect(Collectors.joining(mFieldSeparator)) + mRecordSeparator);
        System.out.println(String.join(" ", command));

        String files = "";
        String requires = "";
        String provides = "";

        Process process = null;
        try {
            process = new ProcessBuilder(command).start();
            mActiveProcesses.add(process);

            try (var scanner = new Scanner(new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8")))) {
                scanner.useDelimiter(mRecordSeparator);
//                Thread.sleep(5_000);
                if (scanner.hasNext()) {
                    var rawRecord = scanner.next();
                    var fields = StringUtils.splitPreserveAllTokens(rawRecord, mFieldSeparator);
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
                mActiveProcesses.remove(process);
            }
        }

        var details = new Pkg.Details();
        details.setFiles(stripDuplicateRowss(files));
        details.setProvides(stripDuplicateRowss(provides));
        details.setRequires(stripDuplicateRowss(requires));

        return details;
    }

    @Override
    public List<Pkg> doGetPackagesAll() {
        CompletableFuture<HashMap<String, Pkg>> installedFuture
                = CompletableFuture.supplyAsync(() -> getPackages(List.of("dnf", "repoquery", "--installed", "--queryformat")), mDnfExecutor);

        CompletableFuture<HashMap<String, Pkg>> availableFuture
                = CompletableFuture.supplyAsync(() -> getPackages(List.of("dnf", "repoquery", "--available", "--queryformat")), mDnfExecutor);

        CompletableFuture<HashMap<String, Pkg>> upgradableFuture
                = CompletableFuture.supplyAsync(() -> getPackages(List.of("dnf", "repoquery", "--upgrades", "--queryformat")), mDnfExecutor);

        try {
            CompletableFuture.allOf(installedFuture, availableFuture, upgradableFuture).join();

            var installedPackages = installedFuture.get();
            var availablePackages = availableFuture.get();
            var upgradablePackages = upgradableFuture.get();

            var onlineByName = new HashMap<String, Pkg>();
            for (var pkg : availablePackages.values()) {
                onlineByName.put(pkg.getName(), pkg);
            }

            for (var entry : installedPackages.entrySet()) {
                var installedPkg = entry.getValue();
                var mainPkg = onlineByName.get(installedPkg.getName());

                if (mainPkg != null) {
                    mainPkg.setInstalled(true);
                    mainPkg.setTimeInstalled(installedPkg.getTimeInstalled());
                    mainPkg.setOrphaned(false);
                } else {
                    installedPkg.setInstalled(true);
                    installedPkg.setOrphaned(true);
                    availablePackages.put(entry.getKey(), installedPkg);
                    onlineByName.put(installedPkg.getName(), installedPkg);
                }
            }

            for (var entry : upgradablePackages.entrySet()) {
                var upgradePkg = entry.getValue();
                var mainPkg = onlineByName.get(upgradePkg.getName());

                if (mainPkg != null && mainPkg.isInstalled()) {
                    mainPkg.setUpgradable(true);
                    mainPkg.setVersionNew(upgradePkg.getVersion());
                }
            }

            return availablePackages.values().stream()
                    .sorted(Comparator.comparing(Pkg::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (InterruptedException | ExecutionException e) {
            Exceptions.printStackTrace(e);
            return List.of();
        }
    }

    @Override
    public String doGetVersion() {
        try {
            String[] command = {"dnf", "--version"};
            var process = new ProcessBuilder(command).start();
            var result = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
            process.waitFor();

            return StringUtils.substringBefore(result, "\n\n");
        } catch (IOException | InterruptedException ex) {
            Exceptions.printStackTrace(ex);
        }

        return "?";
    }

    private HashMap<String, Pkg> getPackages(List<String> args) {
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
                "reponame",
                "packager",
                "release",
                "installtime",
                "buildtime",
                "epoch"
        );
        var command = new ArrayList<String>(args);
        command.add(querytags.stream()
                .map(s -> "%%{%s}".formatted(s))
                .collect(Collectors.joining(mFieldSeparator)) + mRecordSeparator);
        System.out.println(String.join(" ", command));
        var packages = new HashMap<String, Pkg>();
        PkgDictionary dict = PkgDictionary.getInstance();
        Process process = null;
        try {
            process = new ProcessBuilder(command).start();
            mActiveProcesses.add(process);
            try (var scanner = new Scanner(new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8")))) {
                scanner.useDelimiter(mRecordSeparator);
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
                final int reponameIndex = querytags.indexOf("reponame");
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
                    var fields = StringUtils.splitPreserveAllTokens(rawPackage, mFieldSeparator);
                    if (fields.length < querytags.size()) {
                        break;
                    }

                    var pkg = new Pkg();
                    pkg.setId(fields[full_nevraIndex]);
                    pkg.setName(fields[nameIndex]);
                    pkg.setGroupId(dict.getOrCreateId(DictionarySection.GROUP, fields[groupIndex]));
                    pkg.setVersion(fields[versionIndex]);
                    pkg.setArchId(dict.getOrCreateId(DictionarySection.ARCH, fields[archIndex]));
                    pkg.setSummary(fields[summaryIndex]);
                    pkg.setDescription(fields[descriptionIndex]);
                    pkg.setLicenseId(dict.getOrCreateId(DictionarySection.LICENSE, fields[licenseIndex]));
                    pkg.setEpoch(fields[epochIndex]);
                    pkg.setSizeDownload(Long.parseLong(fields[downloadsizeIndex]));
                    pkg.setSizeInstall(Long.parseLong(fields[installsizeIndex]));
                    pkg.setUrl(fields[urlIndex]);
                    pkg.setVendorId(dict.getOrCreateId(DictionarySection.VENDOR, fields[vendorIndex]));
                    pkg.setRepositoryId(dict.getOrCreateId(DictionarySection.REPOSITORY, fields[reponameIndex]));
                    pkg.setPackagerId(dict.getOrCreateId(DictionarySection.PACKAGER, fields[packagerIndex]));
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
            mActiveProcesses.remove(process);
        }

        return packages;
    }
}
/*

--- Phase 2
depends
recommends
conflicts
enhances
obsoletes
suggests

--- Untested
debug_name
evr


prereq_ignoreinst
reason
regular_requires
requires_pre
source_debug_name
source_name
sourcerpm

supplements


--- Skip
from_repo
repoid
location
 */
