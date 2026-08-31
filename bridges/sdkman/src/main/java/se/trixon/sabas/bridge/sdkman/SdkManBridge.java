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
package se.trixon.sabas.bridge.sdkman;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
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
public class SdkManBridge extends Bridge {

    private final String SDK_COMMAND = "source $HOME/.sdkman/bin/sdkman-init.sh && sdk ";
    private final PkgDictionary mDictionary = PkgDictionary.getInstance();

    public SdkManBridge() {
        super("sdkman", "in development", "POSIX");
    }

    @Override
    public List<Pkg> onGetPackageAll(Set<Process> processes) {
        var rawPackagesList = new ArrayList<Pkg>();
        var client = HttpClient.newHttpClient();
        try {
            var installedVersions = getPkgInstalledVersions();
            var candidatesFile = new File(getUserHome(), ".sdkman/var/candidates");
            if (!candidatesFile.exists()) {
                return List.of();
            }

            var candidates = FileUtils.readFileToString(candidatesFile, "utf-8").trim();
            var futures = new ArrayList<CompletableFuture<Void>>();

            for (var candidate : StringUtils.split(candidates, ",")) {
                var toolName = candidate.trim();
                if (toolName.isEmpty()) {
                    continue;
                }

                var groupName = StringUtils.capitalize(toolName);
                var groupId = mDictionary.getOrCreateId(DictionarySection.GROUP, groupName);

                var url = "https://api.sdkman.io/2/candidates/%s/linux/versions/list?installed=".formatted(toolName);
                var request = HttpRequest.newBuilder()
                        .uri(java.net.URI.create(url))
                        .header("User-Agent", "SDKMAN")
                        .header("Consumer-Id", "sdkman-cli")
                        .header("Consumer-Version", "5.0.0")
                        .GET()
                        .build();

                var future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            String rawText = response.body();
                            if (rawText != null && !rawText.isEmpty()) {
                                parseVersionsText(rawText, toolName, groupId, installedVersions, rawPackagesList);
                            }
                        }).exceptionally(ex -> {
                    return null;
                });

                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

        return rawPackagesList.stream()
                .sorted(Comparator.comparing(Pkg::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public String onGetVersion(Set<Process> processes) {
        return execute(List.of("bash", "-c", SDK_COMMAND + "version"), processes);
    }

    @Override
    public List<String> onProvideCacheClearCommand() {
        return List.of("bash", "-c", SDK_COMMAND + "flush");
    }

    @Override
    public List<String> onProvideCacheUpdateCommand() {
        return List.of("bash", "-c", SDK_COMMAND + "update");
    }

    @Override
    public List<String> onProvideVersionCommand() {
        return List.of("bash", "-c", SDK_COMMAND + "version");
    }

    private HashSet<String> getPkgInstalledVersions() {
        var installedSet = new HashSet<String>();
        try {
            var candidatesDir = Path.of(SystemUtils.USER_HOME, ".sdkman", "candidates");
            if (Files.isDirectory(candidatesDir)) {
                try (var toolStream = Files.newDirectoryStream(candidatesDir)) {
                    for (var toolDir : toolStream) {
                        if (Files.isDirectory(toolDir)) {
                            String toolName = toolDir.getFileName().toString();
                            try (var versionStream = Files.newDirectoryStream(toolDir)) {
                                for (var versionDir : versionStream) {
                                    var versionName = versionDir.getFileName().toString();
                                    if (!"current".equals(versionName) && !versionName.startsWith(".") && Files.isDirectory(versionDir)) {
                                        installedSet.add(toolName + "/" + versionName);
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

        return installedSet;
    }

    private synchronized void parseVersionsText(String rawText, String toolName, int groupId, java.util.HashSet<String> installedVersions, List<Pkg> outputList) {
        try (var it = IOUtils.lineIterator(new StringReader(rawText))) {
            while (it.hasNext()) {
                String line = it.next().trim();
                if (line.isEmpty() || line.contains("====") || line.contains("----") || line.contains("Vendor")) {
                    continue;
                }

                var tokens = StringUtils.split(line, " \t|");

                if (tokens != null && tokens.length > 0) {
                    String identifier = tokens[tokens.length - 1].trim();
                    if (!identifier.isEmpty() && Character.isDigit(identifier.charAt(0))) {
                        String versionOnly = StringUtils.substringBefore(identifier, "-");
                        if (versionOnly.isEmpty()) {
                            versionOnly = identifier;
                        }

                        var pkg = new Pkg();
                        pkg.setName("%s %s".formatted(toolName, identifier));
                        pkg.setVersion(versionOnly);
                        pkg.setRelease("1");
                        pkg.setGroupId(groupId);

                        String vendorSuffix = StringUtils.substringAfterLast(identifier, "-");
//                        String repoLabel = vendorSuffix.isEmpty() ? "SDKMAN!" : "SDKMAN! (" + vendorSuffix.toUpperCase() + ")";
                        pkg.setVendorId(mDictionary.getOrCreateId(DictionarySection.VENDOR, vendorSuffix.toUpperCase()));
                        pkg.setRepositoryId(mDictionary.getOrCreateId(DictionarySection.REPOSITORY, "SDKMAN"));
                        pkg.setLicenseId(mDictionary.getOrCreateId(DictionarySection.LICENSE, "UNKNOWN LICENSE"));
                        pkg.setSummary(toolName);
                        if (installedVersions.contains(toolName + "/" + identifier)) {
                            pkg.setInstalled(true);
                        }

                        outputList.add(pkg);
                    }
                }
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

}
