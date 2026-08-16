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
        var querytags = List.of("name", "epoch");
        var command = new ArrayList<String>(List.of("dnf", "repoquery", "--available", "--queryformat"));
        command.add(querytags.stream()
                .map(s -> "%%{%s}".formatted(s))
                .collect(Collectors.joining(fieldSeparator)) + recordSeparator);
//        System.out.println(String.join(" ", command));
        var packages = new ArrayList<Pkg>();

        try {
            var process = new ProcessBuilder(command).start();
            try (var scanner = new Scanner(new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8")))) {
                scanner.useDelimiter(recordSeparator);

                while (scanner.hasNext()) {
                    var rawPackage = scanner.next();
                    if (rawPackage.trim().isEmpty()) {
                        continue;
                    }
                    var fields = StringUtils.splitPreserveAllTokens(rawPackage, fieldSeparator);
                    var pkg = new Pkg();
                    pkg.setName(fields[0]);
//                    pkg.setEpoch(fields[1]);

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
