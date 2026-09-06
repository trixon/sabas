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
package se.trixon.sabas.ui;

import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import se.trixon.sabas.api.DictionarySection;
import se.trixon.sabas.api.Pkg;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(
        dtd = "-//se.trixon.sabas.ui//FilterPackager//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "FilterPackagerTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "left5", openAtStartup = true, position = 2)
@ActionID(category = "Window", id = "se.trixon.sabas.ui.FilterPackagerTopComponent")
@ActionReferences({
    @ActionReference(path = "Menu/Window", position = 50),
    @ActionReference(path = "Shortcuts", name = "D-5")
})
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_FilterPackagerAction",
        preferredID = "FilterPackagerTopComponent"
)
@Messages({
    "CTL_FilterPackagerAction=Packager",
    "CTL_FilterPackagerTopComponent=Packager",
    "HINT_FilterPackagerTopComponent=This is a FilterPackager window"
})
public final class FilterPackagerTopComponent extends BaseFilterTopComponent {

    public FilterPackagerTopComponent() {
        super(DictionarySection.PACKAGER, Pkg::getPackagerId);
        setName(Bundle.CTL_FilterPackagerTopComponent());
        setToolTipText(Bundle.HINT_FilterPackagerTopComponent());
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }
}
