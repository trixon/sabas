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
package se.trixon.sabas.labs;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import javax.swing.SwingUtilities;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class Labs {

    public static void main(String[] args) {
        var labs = new Labs();
        labs.loadPackages();
    }

    public void loadPackages() {
//        String[] command = {
//            "dnf", "repoquery", "--available", "--qf",
//            "%{name}%{epoch}%{version}%{release}%{arch}%{group}%{license}%{url}%{summary}%{description}%{downloadsize}%{installsize}%{sourcerpm}%{reponame}\u001E"
//        };

        var command = new ArrayList<String>(List.of("dnf", "repoquery", "--available", "--queryformat"));
        command.add("\u001E");
        new Thread(() -> {
            try {
                Process process = new ProcessBuilder(command).start();

                // Använd \x1E (Record Separator) för att skilja paket från paket
                try (Scanner scanner = new Scanner(new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8")))) {
                    scanner.useDelimiter("\u001E");

                    while (scanner.hasNext()) {
                        String rawPackage = scanner.next();
                        if (rawPackage.trim().isEmpty()) {
                            continue;
                        }
// Skapa den faktiska unicode-strängen för enhetseparatorn (\x1F)
                        String separator = "\u001F";

// Använd Apache Commons Lang för att dela upp fälten helt säkert
                        String[] fields = StringUtils.splitPreserveAllTokens(rawPackage, separator);

                        // Dela upp fälten med \x1F (Unit Separator)
//                        String[] fields = rawPackage.split("", -1);
                        // Kontrollera att vi fick alla 14 fält vi bad om
                        if (fields.length == 14) {
//                            Pkg pkg = new Pkg();
//                            pkg.setName(fields[0]);
//                            pkg.setEpoch(fields[1]);
//                            pkg.setVersion(fields[2]);
//                            pkg.setRelease(fields[3]);
//                            pkg.setArch(fields[4]);
//                            pkg.setSection(fields[5]); // %{group}
//                            pkg.setLicense(fields[6]);
//                            pkg.setUrl(fields[7]);
//                            pkg.setSummary(fields[8]);
//                            pkg.setDescription(fields[9]); // Här är flerraderstexten helt intakt!
//                            pkg.setDownloadSize(fields[10].isEmpty() ? 0 : Long.parseLong(fields[10]));
//                            pkg.setInstallSize(fields[11].isEmpty() ? 0 : Long.parseLong(fields[11]));
//                            pkg.setSourcerpm(fields[12]);
//                            pkg.setRepo(fields[13]);

                            // Uppdatera ditt NetBeans-gränssnitt säkert från EDT
                            SwingUtilities.invokeLater(() -> {
                                // T.ex. minNetBeansMapp.läggTill(pkg);
//                                System.out.println("Hittade paket: " + pkg.getName());
                                System.out.println("Hittade paket: " + fields[0]);
                            });
                        }
                    }
                }
                process.waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
