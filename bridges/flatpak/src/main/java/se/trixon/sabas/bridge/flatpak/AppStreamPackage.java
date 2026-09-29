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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Strings;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppStreamPackage {

    private final Map<String, String> mDescriptionMap = new HashMap<>();
    @JacksonXmlProperty(localName = "developer_name")
    private String mDeveloperName;
    @JacksonXmlProperty(localName = "id")
    private String mId;
    @JacksonXmlProperty(localName = "project_license")
    private String mLicense;
    @JacksonXmlProperty(localName = "metadata")
    private MetadataNode mMetadata;
    private Map<String, String> mNameMap;
    @JacksonXmlProperty(localName = "name")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<LangNode> mNames;
    @JacksonXmlProperty(localName = "release")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<ReleaseNode> mReleases = new ArrayList<>();
    @JacksonXmlProperty(localName = "summary")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<LangNode> mSummaries;
    private Map<String, String> mSummaryMap;
    @JacksonXmlProperty(localName = "url")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<UrlNode> mUrls;

    public AppStreamPackage() {
    }

    public String getDescription(String... langArgs) {
        return getLocalizedValue(mDescriptionMap, langArgs);
    }

    public String getDeveloperName() {
        return mDeveloperName;
    }

    public String getId() {
        return mId;
    }

    public String getLicense() {
        return mLicense;
    }

    public MetadataNode getMetadata() {
        return mMetadata;
    }

    public String getName(String... langArgs) {
        return getLocalizedValue(mNameMap, langArgs);
    }

    public List<LangNode> getNames() {
        return mNames;
    }

    public List<ReleaseNode> getReleases() {
        return mReleases;
    }

    public List<LangNode> getSummaries() {
        return mSummaries;
    }

    public String getSummary(String... langArgs) {
        return getLocalizedValue(mSummaryMap, langArgs);
    }

    public String getUrl(String type) {
        return Optional.ofNullable(mUrls).orElse(List.of()).stream()
                .filter(u -> Strings.CI.equals(u.type, type))
                .map(u -> u.value)
                .findFirst()
                .orElse(null);
    }

    public List<UrlNode> getUrls() {
        return mUrls;
    }

    @JsonSetter("description")
    public void setDescriptionNode(JsonNode node) {
        if (node == null) {
            return;
        }

        var htmlBuilder = new java.lang.StringBuilder("<html>");

        if (node.has("p")) {
            var pNode = node.get("p");
            if (pNode.isArray()) {
                for (var textNode : pNode) {
                    var text = textNode.asText().trim();
                    if (!text.isEmpty()) {
                        htmlBuilder.append("<p>").append(text).append("</p>\n");
                    }
                }
            } else {
                var text = pNode.asText().trim();
                if (!text.isEmpty()) {
                    htmlBuilder.append("<p>").append(text).append("</p>\n");
                }
            }
        }

        if (node.has("heading")) {
            var hNode = node.get("heading");
            if (hNode.isArray()) {
                for (var textNode : hNode) {
                    htmlBuilder.append("<h3>").append(textNode.asText().trim()).append("</h3>\n");
                }
            } else {
                htmlBuilder.append("<h3>").append(hNode.asText().trim()).append("</h3>\n");
            }
        }

        if (node.has("ul") && node.get("ul").has("li")) {
            var liNode = node.get("ul").get("li");
            htmlBuilder.append("<ul>\n");
            if (liNode.isArray()) {
                for (var itemNode : liNode) {
                    htmlBuilder.append("  <li>").append(itemNode.asText().trim()).append("</li>\n");
                }
            } else {
                htmlBuilder.append("  <li>").append(liNode.asText().trim()).append("</li>\n");
            }
            htmlBuilder.append("</ul>\n");
        }

        if (node.has("ol") && node.get("ol").has("li")) {
            var liNode = node.get("ol").get("li");
            htmlBuilder.append("<ol>\n");
            if (liNode.isArray()) {
                for (var itemNode : liNode) {
                    htmlBuilder.append("  <li>").append(itemNode.asText().trim()).append("</li>\n");
                }
            } else {
                htmlBuilder.append("  <li>").append(liNode.asText().trim()).append("</li>\n");
            }
            htmlBuilder.append("</ol>\n");
        }
        htmlBuilder.append("</html>");

        var finalHtml = htmlBuilder.toString().trim();
        if (!finalHtml.isEmpty()) {
//            var lang = node.has("@lang") ? node.get("@lang").asText() : "";
            String lang = "";
            var fields = node.fieldNames();
            while (fields.hasNext()) {
                String fieldName = fields.next();
                if (fieldName.startsWith("@") && Strings.CI.endsWith(fieldName, "lang")) {
                    lang = node.get(fieldName).asText();
                    break;
                }
            }

            mDescriptionMap.put(lang.toLowerCase(), finalHtml);
        }
    }

    @JsonSetter("name")
    public void setNames(List<LangNode> names) {
        mNames = names;
        mNameMap = createLangTextMap(names);
    }

    @JsonSetter("summary")
    public void setSummaries(List<LangNode> summaries) {
        mSummaries = summaries;
        mSummaryMap = createLangTextMap(summaries);
    }

    private Map<String, String> createLangTextMap(List<? extends LangText> langNodes) {
        return langNodes.stream()
                .collect(Collectors.toMap(
                        p -> Objects.toString(p.getLang(), "").toLowerCase(),
                        p -> Objects.toString(p.getText(), ""),
                        (existingValue, newValue) -> newValue
                ));
    }

    private String getLocalizedValue(Map<String, String> map, String... langArgs) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        var lang = langArgs != null && langArgs.length > 0
                ? langArgs[0].toLowerCase()
                : Locale.getDefault().getLanguage().toLowerCase();

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

    public static interface LangText {

        String getLang();

        String getText();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LangNode implements LangText {

        @JacksonXmlProperty(isAttribute = true, localName = "lang")
        private String lang;
        @JacksonXmlText
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetadataNode {

        @JacksonXmlProperty(localName = "value")
        @JacksonXmlElementWrapper(useWrapping = false)
        private List<ValueNode> values;

        public List<ValueNode> getValues() {
            return values;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReleaseNode {

        @JacksonXmlProperty(isAttribute = true, localName = "timestamp")
        private String timestamp;

        public String getTimestamp() {
            return timestamp;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UrlNode {

        @JacksonXmlProperty(isAttribute = true, localName = "type")
        private String type;

        @JacksonXmlText
        private String value;

        public String getType() {
            return type;
        }

        public String getValue() {
            return value;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ValueNode {

        @JacksonXmlProperty(isAttribute = true, localName = "key")
        private String key;
        @JacksonXmlText
        private String value;

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }
    }

}
