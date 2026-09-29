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

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.io.FileUtils;
import org.openide.util.Exceptions;
import se.trixon.sabas.api.BridgePopulator;
import se.trixon.sabas.api.Pkg;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class AppStreamPopulator extends BridgePopulator {

    private BiConsumer<AppStreamPackage, Pkg> mEnricher;

    public AppStreamPopulator() {
    }

    public void populateAppStream(Map<String, Pkg> packageMap) {
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
                                parseLatestXmlFromRepo(repoArchDir, packageMap);
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

    private void applyXml(InputStream fileStream, Map<String, Pkg> packageMap) throws XMLStreamException, IOException {
        var factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
//        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        var reader = factory.createXMLStreamReader(fileStream, "UTF-8");
        var xmlMapper = new XmlMapper();
        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                var tagName = reader.getLocalName();

                if ("component".equals(tagName)) {
                    var asp = xmlMapper.readValue(reader, AppStreamPackage.class);
                    var id = asp.getId();
                    if (id != null && packageMap.containsKey(id)) {
                        var pkg = packageMap.get(id);
                        if (mEnricher != null) {
                            mEnricher.accept(asp, pkg);
                        }
                    }
                }
            }
        }
    }

    private void parseLatestXmlFromRepo(Path repoArchDir, Map<String, Pkg> packageMap) {
        try {
            var activeGz = repoArchDir.resolve("active/appstream.xml.gz");
            var activeXml = repoArchDir.resolve("active/appstream.xml");

            if (Files.isRegularFile(activeGz)) {
                runParser(activeGz, true, packageMap);
                return;
            } else if (Files.isRegularFile(activeXml)) {
                runParser(activeXml, false, packageMap);
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
                runParser(bestFile, isGz, packageMap);
            }
        } catch (IOException e) {
            Exceptions.printStackTrace(e);
        }
    }

    private void runParser(Path p, boolean isGz, Map<String, Pkg> packageMap) {
        try (var fileStream = new FileInputStream(p.toFile())) {
            var finalStream = isGz ? new GZIPInputStream(fileStream) : fileStream;
            applyXml(finalStream, packageMap);
        } catch (Exception e) {
            Exceptions.printStackTrace(e);
        }
    }

}
