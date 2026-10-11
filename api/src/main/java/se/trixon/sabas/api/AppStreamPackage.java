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

import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "component")
public class AppStreamPackage {

    public static final Set<String> ACCEPTABLE_LANGUAGES = new HashSet<>(List.of(
            "",
            "en",
            Locale.getDefault().getLanguage().toLowerCase(Locale.ROOT)
    ));

    private Map<String, String> mDescriptionMap;
    @XmlElement(name = "description")
    private List<DescriptionNode> mDescriptions;
    @XmlElement(name = "developer_name")
    private String mDeveloperName;
    @XmlElement(name = "id")
    private String mId;
    @XmlElement(name = "project_license")
    private String mLicense;
    @XmlElement(name = "metadata")
    private MetadataNode mMetadata;
    private Map<String, String> mNameMap;
    @XmlElement(name = "name")
    private List<LangTextNode> mNames;
    @XmlElement(name = "pkgname")
    /**
     * Debian
     */
    private String mPkgName;
    @XmlElement(name = "release")
    private List<ReleaseNode> mReleases;
    private String mRepo;
    @XmlElement(name = "summary")
    private List<LangTextNode> mSummaries;
    private Map<String, String> mSummaryMap;
    @XmlAttribute(name = "type")
    private String mType;
    @XmlElement(name = "url")
    private List<UrlNode> mUrls;

    public AppStreamPackage() {
    }

    public String getDescription(String... langArgs) {
        return getLocalizedValue(mDescriptionMap, langArgs);
    }

    public String getDeveloperName() {
        return mDeveloperName;
    }

    public String getFlathubVerificationWebsite() {
        if (mMetadata == null || mMetadata.getValues() == null) {
            return null;
        }
        return mMetadata.getValues().stream()
                .filter(v -> "flathub::verification::website".equals(v.getKey()))
                .map(ValueNode::getValue)
                .findFirst()
                .orElse(null);
    }

    public String getId() {
        return mId != null ? mId.trim() : null;
    }

    public String getLatestReleaseTimestamp() {
        if (mReleases == null || mReleases.isEmpty()) {
            return null;
        }
        return mReleases.get(0).getTimestamp();
    }

    public String getLicense() {
        return mLicense;
    }

    public String getName(String... langArgs) {
        return getLocalizedValue(mNameMap, langArgs);
    }

    /**
     * Debian
     *
     * @return
     */
    public String getPkgName() {
        return StringUtils.trim(mPkgName);
    }

    public String getRepo() {
        return mRepo;
    }

    public String getSummary(String... langArgs) {
        return getLocalizedValue(mSummaryMap, langArgs);
    }

    public String getType() {
        return StringUtils.trim(mType);
    }

    public String getUrl(String type) {
        if (mUrls == null) {
            return null;
        }

        return mUrls.stream()
                .filter(u -> Strings.CI.equals(u.getType(), type))
                .map(UrlNode::getValue)
                .findFirst()
                .orElse(null);
    }

    public void setRepo(String repo) {
        mRepo = repo;
    }

    /*
    This one is a keeper...
     */
    private void afterUnmarshal(Unmarshaller unmarshaller, Object parent) {
        mNameMap = createLocalizedTextMap(mNames);
        mSummaryMap = createLocalizedTextMap(mSummaries);
        mDescriptionMap = createLocalizedTextMap(mDescriptions);
//        try {
//            System.out.println("%d\t%d\t%d\t\t%d\t%d\t%d".formatted(
//                    mNames.size(), mSummaries.size(), mDescriptions.size(),
//                    mNameMap.size(), mSummaryMap.size(), mDescriptionMap.size()
//            ));
//        } catch (Exception e) {
//        }
    }

    private Map<String, String> createLocalizedTextMap(List<? extends LocalizedText> langNodes) {
        if (langNodes == null) {
            return new HashMap<>();
        }

        var map = langNodes.stream()
                .filter(p -> ACCEPTABLE_LANGUAGES.contains(Objects.toString(p.getLang(), "")))
                .collect(Collectors.toMap(
                        p -> Objects.toString(p.getLang(), "").toLowerCase(Locale.ROOT),
                        p -> Objects.toString(p.getText(), ""),
                        (existingValue, newValue) -> newValue
                ));

        langNodes.clear();

        return map;
    }

    private String getLocalizedValue(Map<String, String> map, String... langArgs) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        var lang = (langArgs != null && langArgs.length > 0)
                ? langArgs[0].toLowerCase()
                : Locale.getDefault().getLanguage().toLowerCase(Locale.ROOT);

        var text = map.get(lang);
        if (text != null) {
            return text;
        }

        text = map.get("en");
        if (text != null) {
            return text;
        }

        text = map.get("");
        if (text != null) {
            return text;
        }

        return map.values().stream().findFirst().orElse(null);
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class DescriptionNode implements LocalizedText {

        @XmlAttribute(name = "lang", namespace = "http://www.w3.org/XML/1998/namespace")
        private String lang;
        @XmlAnyElement
        private List<Object> descriptionElements = new ArrayList<>();

        @Override
        public String getLang() {
            return lang;
        }

        public String getHtmlContent() {
            if (descriptionElements == null || descriptionElements.isEmpty()) {
                return "";
            }

            var htmlBuilder = new java.lang.StringBuilder();

            try {
                var tf = TransformerFactory.newInstance();
                var transformer = tf.newTransformer();
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                transformer.setOutputProperty(OutputKeys.METHOD, "html");

                for (var obj : descriptionElements) {
                    if (obj instanceof org.w3c.dom.Element element) {
                        var writer = new StringWriter();
                        transformer.transform(new DOMSource(element), new StreamResult(writer));
                        var rawXml = writer.toString().trim();
                        if (!rawXml.isEmpty()) {
                            htmlBuilder.append(rawXml).append("\n");
                        }
                    } else if (obj != null) {
                        var text = obj.toString().trim();
                        if (!text.isEmpty()) {
                            if (!text.startsWith("<")) {
                                htmlBuilder.append("<p>").append(text).append("</p>\n");
                            } else {
                                htmlBuilder.append(text).append("\n");
                            }
                        }
                    }
                }

                return htmlBuilder.toString().trim();
            } catch (IllegalArgumentException | TransformerException e) {
                for (var obj : descriptionElements) {
                    if (obj != null) {
                        htmlBuilder.append(obj.toString().trim()).append("\n");
                    }
                }
                return htmlBuilder.toString().trim();
            }
        }

        @Override
        public String getText() {
            return getHtmlContent();
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class LangTextNode implements LocalizedText {

        @XmlAttribute(name = "lang", namespace = "http://www.w3.org/XML/1998/namespace")
        private String lang;

        @XmlValue
        private String text;

        @Override
        public String getLang() {
            return lang;
        }

        @Override
        public String getText() {
            return text;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class MetadataNode {

        @XmlElement(name = "value")
        private List<ValueNode> values;

        public List<ValueNode> getValues() {
            return values;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ReleaseNode {

        @XmlAttribute(name = "timestamp")
        private String timestamp;

        public String getTimestamp() {
            return timestamp;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class UrlNode {

        @XmlAttribute(name = "type")
        private String type;

        @XmlValue
        private String value;

        public String getType() {
            return type;
        }

        public String getValue() {
            return value;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ValueNode {

        @XmlAttribute(name = "key")
        private String key;

        @XmlValue
        private String value;

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }
    }

    public interface LocalizedText {

        String getLang();

        String getText();
    }

}
