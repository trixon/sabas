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

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.openide.util.Exceptions;
import se.trixon.sabas.api.Bridge;
import se.trixon.sabas.api.BridgePopulator;
import se.trixon.sabas.api.Pkg;
import static se.trixon.sabas.bridge.flatpak.FlatpakBridge.FLATPAK;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class AppStreamPopulator extends BridgePopulator {

    private BiConsumer<AppStreamPackage, Pkg> mEnricher;
    private Unmarshaller mUnmarshaller;

    public AppStreamPopulator() {
        var originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(AppStreamPopulator.class.getClassLoader());
            var jaxbContext = JAXBContext.newInstance(AppStreamPackage.class);
            mUnmarshaller = jaxbContext.createUnmarshaller();
        } catch (JAXBException e) {
            Exceptions.printStackTrace(e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }

//        mUnmarshaller.setListener(new Unmarshaller.Listener() {
//            @Override
//            public void beforeUnmarshal(Object target, Object parent) {
//                if (target instanceof AppStreamPackage asp) {
//                }
//
//                if (target instanceof AppStreamPackage.DescriptionNode desc) {
//                    var id = ((AppStreamPackage) parent).getId();
//                    if (Strings.CI.equalsAny(id, "me.ahola.aphototoollibre", "ai.jan.Jan")) {
//                        System.out.println("BREAKPOINT: " + id);
//                    }
//                }
//            }
//
//            @Override
//            public void afterUnmarshal(Object target, Object parent) {
//                if (target instanceof AppStreamPackage asp) {
//                    var id = asp.getId();
//
//                    if (Strings.CI.containsAny(id, "aphototool", "4ktube")) {
//                        System.out.println("BREAKPOINT: " + id);
//                    }
//                }
//            }
//        });
    }

    public void populateAppStream(Map<String, Pkg> packageMap) {
        var activeRepositories = getActiveRepositories();
        var basePaths = List.of(
                Path.of("/var/lib/flatpak/appstream"),
                Path.of(FileUtils.getUserDirectoryPath(), ".local/share/flatpak/appstream")
        );
        for (var basePath : basePaths) {
            if (!Files.isDirectory(basePath)) {
                continue;
            }

            try {
                try (var repoStream = Files.walk(basePath, 2)) {
                    repoStream.filter(p -> p.getNameCount() == basePath.getNameCount() + 2)
                            .filter(Files::isDirectory)
                            .forEach(repoArchDir -> {
                                parseLatestXmlFromRepo(activeRepositories, repoArchDir, packageMap);
                            });
                }
            } catch (Exception e) {
                Exceptions.printStackTrace(e);
            }
        }
    }

    public void setEnricher(BiConsumer<AppStreamPackage, Pkg> enricher) {
        mEnricher = enricher;
    }

    private void applyXml(String repo, InputStream fileStream, Map<String, Pkg> packageMap) throws XMLStreamException, IOException, JAXBException {
        var factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);

        try (var streamReader = new InputStreamReader(fileStream, StandardCharsets.UTF_8); var bufferedReader = new BufferedReader(streamReader)) {
            var reader = factory.createXMLStreamReader(bufferedReader);
            while (reader.hasNext()) {
                int event = reader.getEventType();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    if ("component".equals(reader.getLocalName())) {
                        try {
                            var jaxbElement = mUnmarshaller.unmarshal(reader, AppStreamPackage.class);
                            var asp = jaxbElement.getValue();
                            var id = repo + asp.getId();

                            var pkg = packageMap.getOrDefault(id, packageMap.get(Strings.CI.removeEnd(id, ".desktop")));
                            if (pkg != null) {
                                if (mEnricher != null) {
                                    mEnricher.accept(asp, pkg);
                                }
                            } else {
                                //System.out.println("In xml but not in list: " + id);
                            }
                            continue;
                        } catch (JAXBException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                }

                if (reader.hasNext()) {
                    reader.next();
                }
            }
        }
    }

    private void parseLatestXmlFromRepo(Set<String> activeRepositories, Path repoArchDir, Map<String, Pkg> packageMap) {
        try {
            var activeGz = repoArchDir.resolve("active/appstream.xml.gz");
            var activeXml = repoArchDir.resolve("active/appstream.xml");

            if (Files.isRegularFile(activeGz)) {
                runParser(activeGz, true, packageMap, activeRepositories);
                return;
            } else if (Files.isRegularFile(activeXml)) {
                runParser(activeXml, false, packageMap, activeRepositories);
                return;
            }

            Path bestFile = null;
            long latestTime = 0;
            boolean isGz = false;

            try (var fileStream = Files.walk(repoArchDir, 2)) {
                var files = fileStream
                        .filter(Files::isRegularFile)
                        .filter(p -> !p.toString().contains("active"))
                        .toList();

                for (var p : files) {
                    var name = p.getFileName().toString().toLowerCase();
                    if (name.equals("appstream.xml") || name.equals("appstream.xml.gz")) {
                        var fileTime = Files.getLastModifiedTime(p).toMillis();

                        if (fileTime > latestTime) {
                            latestTime = fileTime;
                            bestFile = p;
                            isGz = name.endsWith(".gz");
                        }
                    }
                }
            }

            if (bestFile != null) {
                runParser(bestFile, isGz, packageMap, activeRepositories);
            }
        } catch (IOException e) {
            Exceptions.printStackTrace(e);
        }
    }

    private void runParser(Path path, boolean isGz, Map<String, Pkg> packageMap, Set<String> activeRepositories) {
        System.out.println("Parse: " + path);
        try (var fileStream = new FileInputStream(path.toFile())) {
            var finalStream = isGz ? new GZIPInputStream(fileStream) : fileStream;
            String repo = "";
            for (int i = 0; i < path.getNameCount(); i++) {
                if ("appstream".equals(path.getName(i).toString())) {
                    repo = path.getName(i + 1).toString().toLowerCase(Locale.ROOT);
                    break;
                }
            }
            if (activeRepositories.contains(repo)) {
                applyXml(repo, finalStream, packageMap);
            }
        } catch (Exception e) {
            Exceptions.printStackTrace(e);
        }
    }

    private Set<String> getActiveRepositories() {
        var command = List.of(
                FLATPAK,
                "remotes",
                "--columns=name"
        );

        var pb = Bridge.createProcessBuilder(command);
        pb.environment().put("LANGUAGE", "en_US");
        try {
            var process = pb.start();
            String commandOutput;
            try (var stream = process.getInputStream()) {
                commandOutput = IOUtils.toString(stream, StandardCharsets.UTF_8).trim();
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return commandOutput.lines()
                        //                        .skip(1)
                        .map(String::trim)
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.toSet());
            }
        } catch (IOException | InterruptedException ex) {
            Exceptions.printStackTrace(ex);
        }

        return Set.of();
    }
}
