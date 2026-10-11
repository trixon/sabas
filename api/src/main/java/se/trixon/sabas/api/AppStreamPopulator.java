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
import java.util.function.Function;
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
    private JAXBContext mJaxbContext;

    public AppStreamPopulator() {
        try {
            mJaxbContext = JAXBContext.newInstance(AppStreamPackage.class);
        } catch (JAXBException ex) {
            Exceptions.printStackTrace(ex);
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

    public void populate(Path path, boolean isGz, Map<String, Pkg> packageMap, String repo, Function<AppStreamPackage, String> keyStrategy) {
        System.out.println("Pre-Parse: %s\t%s".formatted(repo, path));
        try (var fileStream = new FileInputStream(path.toFile()); var finalStream = isGz ? new GZIPInputStream(fileStream) : fileStream) {
            populate(finalStream, packageMap, repo, keyStrategy);
        } catch (Exception e) {
            Exceptions.printStackTrace(e);
        }
    }

    public void populate(InputStream inputStream, Map<String, Pkg> packageMap, String repo, Function<AppStreamPackage, String> keyStrategy) throws XMLStreamException, IOException, JAXBException {
        var startTime = System.currentTimeMillis();
        var appCount = 0;
        System.out.println("Parse: %s\t%s".formatted(repo, "inputStream"));
        var unmarshaller = mJaxbContext.createUnmarshaller();
        var factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        var unsupportedTypes = new TreeSet<String>();

        String currentOrigin = "unknown";
        try (var streamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8); var bufferedReader = new BufferedReader(streamReader)) {
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
                            var jaxbElement = unmarshaller.unmarshal(reader, AppStreamPackage.class);
                            var asp = jaxbElement.getValue();
                            appCount++;
                            asp.setRepo(repo);
                            var id = keyStrategy.apply(asp);
                            var pkg = packageMap.getOrDefault(id, packageMap.get(Strings.CI.removeEnd(id, ".desktop")));
                            if (pkg != null) {
                                pkg.setAppStream(true);
                                var type = asp.getType();
                                var category = switch (Objects.toString(type, "")) {
                                    case "desktop", "desktop-application" ->
                                        PkgCategory.DESKTOP;
                                    case "console-application" ->
                                        PkgCategory.CONSOLE;
                                    case "web-application" ->
                                        PkgCategory.WEB;
                                    case "addon" ->
                                        PkgCategory.ADDON;
                                    case "codec" ->
                                        PkgCategory.CODEC;
                                    case "firmware" ->
                                        PkgCategory.FIRMWARE;
                                    case "inputmethod" ->
                                        PkgCategory.INPUTMETHOD;
                                    case "operating-system" ->
                                        PkgCategory.OPERATING_SYSTEM;
                                    case "generic" ->
                                        PkgCategory.GENERIC;
                                    case "localization" ->
                                        PkgCategory.LOCALIZATION;
                                    case "runtime" ->
                                        PkgCategory.RUNTIME;
                                    case "driver" ->
                                        PkgCategory.DRIVER;
                                    case "font" ->
                                        PkgCategory.FONT;
                                    case "icon-theme" ->
                                        PkgCategory.THEME;
                                    case "service" ->
                                        PkgCategory.SERVICE;
                                    default ->
                                        PkgCategory.OTHER;
                                };

                                pkg.setCategory(category);
                                pkg.setCategoryId(mDictionary.getOrCreateId(DictionarySection.CATEGORY, category.toString()));
                                if (category == PkgCategory.OTHER && type != null) {
                                    unsupportedTypes.add(type);

                                }
                                synchronized (pkg) {
                                    pkg.setValue(AppStreamPackage.class, asp);
                                    mEnricher.accept(asp, pkg);
                                }
                            } else {
//                                System.out.println("In xml but not in list: " + id);
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

        if (!unsupportedTypes.isEmpty()) {
            System.out.println("UNSUPPORTED TYPES");
            System.out.println(String.join("\n", unsupportedTypes));
        }
        System.out.println("initAppStream for %d packages took %d ms".formatted(appCount, System.currentTimeMillis() - startTime));
    }

    public void setEnricher(BiConsumer<AppStreamPackage, Pkg> enricher) {
        mEnricher = enricher;
    }

}
