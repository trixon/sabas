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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;
import se.trixon.sabas.core.api.Bridge;
import se.trixon.sabas.core.api.Pkg;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
@ServiceProvider(service = Bridge.class)
public class Dnf0Bridge extends Bridge {

    public Dnf0Bridge() {
        super("dnf", "in development", "Fedora 44");
    }

    @Override
    public List<Pkg> doGetPackagesAll() {
//            "%{name}%{epoch}%{version}%{release}%{arch}%{group}%{license}%{url}%{summary}%{description}%{downloadsize}%{installsize}%{sourcerpm}%{reponame}\u001E"

        var fieldSeparator = "\u001F";
        var recordSeparator = "\u001E";
        var querytags = List.of(
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
        var command = new ArrayList<String>(List.of("dnf", "repoquery", "--queryformat"));
//        var command = new ArrayList<String>(List.of("dnf", "repoquery", "--installed", "--available", "--queryformat"));
        command.add(querytags.stream()
                .map(s -> "%%{%s}".formatted(s))
                .collect(Collectors.joining(fieldSeparator)) + recordSeparator);
        System.out.println(String.join(" ", command));
        var packages = new ArrayList<Pkg>();

        try {
            var process = new ProcessBuilder(command).start();
            try (var scanner = new Scanner(new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8")))) {
                scanner.useDelimiter(recordSeparator);
                int full_nevraIndex = querytags.indexOf("full_nevra");
                int nameIndex = querytags.indexOf("name");
                int groupIndex = querytags.indexOf("group");
                int versionIndex = querytags.indexOf("version");
                int archIndex = querytags.indexOf("arch");
                int summaryIndex = querytags.indexOf("summary");
                int descriptionIndex = querytags.indexOf("description");
                int licenseIndex = querytags.indexOf("license");
                int epochIndex = querytags.indexOf("epoch");
                int downloadsizeIndex = querytags.indexOf("downloadsize");
                int installsizeIndex = querytags.indexOf("installsize");
                int urlIndex = querytags.indexOf("url");
                int vendorIndex = querytags.indexOf("vendor");
                int reponameIndex = querytags.indexOf("reponame");
                int packagerIndex = querytags.indexOf("packager");
                int releaseIndex = querytags.indexOf("release");
                int installtimeIndex = querytags.indexOf("installtime");
                int buildtimeIndex = querytags.indexOf("buildtime");

                while (scanner.hasNext()) {
                    var rawPackage = scanner.next();
                    if (rawPackage.trim().isEmpty()) {
                        continue;
                    }
                    var fields = StringUtils.splitPreserveAllTokens(rawPackage, fieldSeparator);
                    var pkg = new Pkg();
                    pkg.setId(fields[full_nevraIndex]);
                    pkg.setName(fields[nameIndex]);
                    pkg.setGroup(fields[groupIndex]);
                    pkg.setVersion(fields[versionIndex]);
                    pkg.setArch(fields[archIndex]);
                    pkg.setSummary(fields[summaryIndex]);
                    pkg.setDescription(fields[descriptionIndex]);
                    pkg.setLicense(fields[licenseIndex]);
                    pkg.setEpoch(fields[epochIndex]);
                    pkg.setSizeDownload(Long.parseLong(fields[downloadsizeIndex]));
                    pkg.setSizeInstall(Long.parseLong(fields[installsizeIndex]));
                    pkg.setUrl(fields[urlIndex]);
                    pkg.setVendor(fields[vendorIndex]);
                    pkg.setRepository(fields[reponameIndex]);
                    pkg.setPackager(fields[packagerIndex]);
                    pkg.setRelease(fields[releaseIndex]);
                    pkg.setTimeBuild(Instant.ofEpochSecond(Long.parseLong(fields[buildtimeIndex])));
                    pkg.setTimeInstalled(Instant.ofEpochSecond(Long.parseLong(fields[installtimeIndex])));

                    packages.add(pkg);
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            Exceptions.printStackTrace(e);
        }

        return packages;
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
}
/*

--- Phase 2
files

--- Untested
conflicts
debug_name
depends
enhances
evr


obsoletes
prereq_ignoreinst
provides
reason
recommends
regular_requires
requires
requires_pre
source_debug_name
source_name
sourcerpm
suggests

supplements


--- Skip
from_repo
repoid
location
 */
