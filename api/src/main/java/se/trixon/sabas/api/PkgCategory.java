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
    GUI("category_gui"),
    TUI("category_tui"),
    CLI("category_cli"),
    THEME("category_theme"),
    FONT("category_font"),
    SERVICE("category_service"),
    LIB("category_lib"),
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
/*
public enum PackageType {
    CLI,    // Interaktiva kommandoradsverktyg (t.ex. git, htop, curl)
    TUI,    // Textbaserade grafiska gränssnitt (t.ex. mc, ncmpcpp, tig)
    GUI,    // Fullständiga grafiska skrivbordsappar (t.ex. GIMP, VLC, 0 A.D.)
    LIB,    // Programmeringsbibliotek och utvecklingsfiler (t.ex. glibc, libxml2)
    FONT,   // Typsnitt och teckensnittfamiljer
    THEME,  // Ikonpaket, skrivbords- och markörteman
    SERVICE,// Bakgrundstjänster, systemserverprogram och daemons
    OTHER   // Dokumentation, rådata, metapaket och övrigt som inte passar ovan
}
 */
