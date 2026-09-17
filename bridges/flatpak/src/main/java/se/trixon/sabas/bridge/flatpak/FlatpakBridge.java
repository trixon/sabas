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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
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
public class FlatpakBridge extends Bridge {

    private final String FLATPAK = "flatpak";
    private final Map<String, String> mDefaultEnvironment = new HashMap<>();
    private final PkgDictionary mDictionary = PkgDictionary.getInstance();

    public FlatpakBridge() {
        super("FLATPAK", "1.16", "Linux");
    }

    @Override
    public List<Pkg> onGetPackageAll(Set<Process> processes) {
        final var querytags = List.of(
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

        var rawPackagesList = new ArrayList<Pkg>();
        var installedApps = getInstalled(processes);
        var command = List.of(
                FLATPAK,
                "remote-ls",
                "--app",
                //                "--arch=*",
                "--columns=all"
        );

        Process process = null;
        try {
            var pb = createProcessBuilder(command);
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

            var licenseId = mDictionary.getOrCreateId(DictionarySection.LICENSE, "Open Source / Mixed");

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

                    var pkg = new Pkg();
                    pkg.setId(fields[refIndex]);
                    pkg.setName(fields[nameIndex]);
                    pkg.setNameTransaction(fields[idIndex]);
                    pkg.setSummary(fields[descriptionIndex]);
//                    pkg.setDescription("");
                    pkg.setVersion(fields[versionIndex]);
//                    pkg.setRelease("1");
                    pkg.setLicenseId(licenseId);
                    pkg.setGroupId(mDictionary.getOrCreateId(DictionarySection.GROUP, fields[runtimeIndex]));
                    pkg.setArchId(mDictionary.getOrCreateId(DictionarySection.ARCH, fields[archIndex]));
                    pkg.setRepositoryId(mDictionary.getOrCreateId(DictionarySection.REPOSITORY, fields[originIndex]));
                    pkg.setSizeDownload(getSize(fields[dSizeIndex]));
                    pkg.setSizeInstall(getSize(fields[iSizeIndex]));

                    if (installedApps.contains(fields[idIndex])) {
                        pkg.setInstalled(true);
                    }

                    rawPackagesList.add(pkg);
                }
            }
            process.waitFor();

        } catch (IOException | InterruptedException e) {
            System.err.println("[SABAS FLATPAK ERROR] Misslyckades med att läsa flatpaks: " + e.getMessage());
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
    public String onGetVersion(Set<Process> processes) {
        return execute(List.of(FLATPAK, "--version"), processes);
    }

    @Override
    public BridgeOperation onProvideCacheClearCommand() {
        return new BridgeOperation(
                List.of("true"),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideCacheUpdateCommand() {
        return new BridgeOperation(
                List.of(
                        "flatpak",
                        "update",
                        "--appstream"
                ),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionInstall(String... packages) {
        return new BridgeOperation(
                List.of(
                        FLATPAK,
                        "install",
                        "--noninteractive",
                        String.join(" ", packages)
                ),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionRemove(String... packages) {
        return new BridgeOperation(
                List.of(
                        FLATPAK,
                        "uninstall",
                        "--noninteractive",
                        String.join(" ", packages)
                ),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionUpgrade() {
        return new BridgeOperation(
                List.of(
                        FLATPAK,
                        "update",
                        "--noninteractive"
                ),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideVersionCommand() {
        return new BridgeOperation(
                List.of(FLATPAK, "--version"),
                mDefaultEnvironment
        );
    }

    private Set<String> getInstalled(Set<Process> processes) {
        var installedSet = new HashSet<String>();
        var command = List.of(FLATPAK, "list", "--all", "--columns=application");
        Process p = null;
        try {
            p = createProcessBuilder(command).start();
            processes.add(p);
            try (var it = IOUtils.lineIterator(p.getInputStream(), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var line = it.next().trim();
                    if (!line.isEmpty() && !line.startsWith("Application")) {
                        installedSet.add(line);
                    }
                }
            }
            p.waitFor();
        } catch (Exception e) {
            //
        } finally {
            if (p != null) {
                processes.remove(p);
            }
        }

        return installedSet;
    }

    private long getSize(String sizeString) {
        if (sizeString == null || sizeString.trim().isEmpty() || "Available".equalsIgnoreCase(sizeString)) {
            return 0;
        }
        String[] item = sizeString.trim().split("[^0-9.A-Za-z]+");
        if (item.length < 2) {
            return 0;
        }

        long factor = switch (item[1].trim()) {
            case "kB", "KB" ->
                1024L;
            case "MB" ->
                1024L * 1024L;
            case "GB" ->
                1024L * 1024L * 1024L;
            default ->
                1L;
        };

        try {
            double sizeDouble = Double.parseDouble(item[0].trim());
            return (long) (sizeDouble * factor);

        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
