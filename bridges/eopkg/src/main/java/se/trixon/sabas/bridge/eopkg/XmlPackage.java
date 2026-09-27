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
package se.trixon.sabas.bridge.eopkg;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class XmlPackage {

    @JacksonXmlProperty(localName = "InstalledSize")
    private Long installedSize;
    @JacksonXmlProperty(localName = "Name")
    private String name;
    @JacksonXmlProperty(localName = "PackageSize")
    private Long packageSize;
    @JacksonXmlProperty(localName = "PackageURI")
    private String packageUri;
    @JacksonXmlProperty(localName = "Source")
    private SourceNode source;

    public Long getInstalledSize() {
        return installedSize;
    }

    public String getName() {
        return name;
    }

    public Long getPackageSize() {
        return packageSize;
    }

    public String getPackageUri() {
        return packageUri;
    }

    public SourceNode getSource() {
        return source;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PackagerNode {

        @JacksonXmlProperty(localName = "Name")
        private String name;

        public String getName() {
            return name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SourceNode {

        @JacksonXmlProperty(localName = "Homepage")
        private String homepage;

        @JacksonXmlProperty(localName = "Packager")
        private PackagerNode packager;

        public String getHomepage() {
            return homepage;
        }

        public PackagerNode getPackager() {
            return packager;
        }
    }
}
