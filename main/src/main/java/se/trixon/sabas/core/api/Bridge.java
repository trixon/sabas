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
package se.trixon.sabas.core.api;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class Bridge {

    private String mName;
    private String mDescription;
    private String mSupports;

    public Bridge(String name, String description, String supports) {
        mName = name;
        mDescription = description;
        mSupports = supports;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getName() {
        return mName;
    }

    public String getSupports() {
        return mSupports;
    }

    public void setDescription(String description) {
        this.mDescription = description;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public void setSupports(String supports) {
        this.mSupports = supports;
    }

}
