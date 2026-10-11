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

import java.util.Locale;
import java.util.ResourceBundle;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public enum PkgCategory {
    ALL("category_all"),
    ADDON("category_addon"),
    CODEC("category_codec"),
    CONSOLE("category_console"),
    DESKTOP("category_desktop"),
    DEV("category_dev"),
    DOC("category_doc"),
    DRIVER("category_driver"),
    FIRMWARE("category_firmware"),
    FONT("category_font"),
    GENERIC("category_generic"),
    INPUTMETHOD("category_inputmethod"),
    LIB("category_lib"),
    LOCALIZATION("category_localization"),
    OPERATING_SYSTEM("category_operating-system"),
    RUNTIME("category_runtime"),
    SERVICE("category_service"),
    THEME("category_theme"),
    WEB("category_web"),
    OTHER("category_other");
    private final String mKey;
    private final ResourceBundle mResourceBundle = ResourceBundle.getBundle(PkgCategory.class.getPackageName() + ".Bundle", Locale.getDefault());

    private PkgCategory(String key) {
        mKey = key;
    }

    @Override
    public String toString() {
        return mResourceBundle.getString(mKey);
    }

}
