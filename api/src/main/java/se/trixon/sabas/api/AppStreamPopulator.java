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
package se.trixon.sabas.api;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.openide.util.Exceptions;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class AppStreamPopulator extends BridgePopulator {

    private BiConsumer<AppStreamPackage, Pkg> mEnricher;
    private Unmarshaller mUnmarshaller;

    public AppStreamPopulator() {
        try {
            var jaxbContext = JAXBContext.newInstance(AppStreamPackage.class);
            mUnmarshaller = jaxbContext.createUnmarshaller();
        } catch (JAXBException e) {
            Exceptions.printStackTrace(e);
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

    public void populate(Path path, boolean isGz, Map<String, Pkg> packageMap, String repo) {
        System.out.println("Parse: " + path);
        try (var fileStream = new FileInputStream(path.toFile())) {
            var finalStream = isGz ? new GZIPInputStream(fileStream) : fileStream;
            populate(finalStream, packageMap, repo);
        } catch (Exception e) {
            Exceptions.printStackTrace(e);
        }
    }

    public void populate(InputStream fileStream, Map<String, Pkg> packageMap, String repo) throws XMLStreamException, IOException, JAXBException {
        var factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        var set = new TreeSet<String>();
        String currentOrigin = "unknown";
        try (var streamReader = new InputStreamReader(fileStream, StandardCharsets.UTF_8); var bufferedReader = new BufferedReader(streamReader)) {
            var reader = factory.createXMLStreamReader(bufferedReader);

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "components".equals(reader.getLocalName())) {
                    String originAttr = reader.getAttributeValue(null, "origin");
                    if (StringUtils.isNotBlank(originAttr)) {
                        currentOrigin = originAttr.trim().toLowerCase(Locale.ROOT);
                    }
                    break;
                }
            }

            while (reader.hasNext()) {
                int event = reader.getEventType();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    if ("component".equals(reader.getLocalName())) {
                        try {
                            var jaxbElement = mUnmarshaller.unmarshal(reader, AppStreamPackage.class);
                            var asp = jaxbElement.getValue();
                            asp.setRepo(repo);
                            var id = repo + asp.getId();
                            set.add(Objects.toString(asp.getType(), "-"));
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

        System.out.println("ORIGIN " + currentOrigin);
        System.out.println("FOUND TYPES");
        System.out.println(String.join("\n", set));
    }

    public void setEnricher(BiConsumer<AppStreamPackage, Pkg> enricher) {
        mEnricher = enricher;
    }

}
