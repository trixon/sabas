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
        dtd = "-//se.trixon.sabas.ui//FilterGroup//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "FilterGroupTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "left2", openAtStartup = true, position = 2)
@ActionID(category = "Window", id = "se.trixon.sabas.ui.FilterGroupTopComponent")
@ActionReferences({
    @ActionReference(path = "Menu/Window", position = 60, separatorAfter = 61),
    @ActionReference(path = "Shortcuts", name = "D-6")
})
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_FilterGroupAction",
        preferredID = "FilterGroupTopComponent"
)
@Messages({
    "CTL_FilterGroupAction=Group",
    "CTL_FilterGroupTopComponent=Group",
    "HINT_FilterGroupTopComponent=This is a Group window"
})
public final class FilterGroupTopComponent extends BaseFilterTopComponent {

    public FilterGroupTopComponent() {
        super(DictionarySection.GROUP, Pkg::getGroupId);
        setName(Bundle.CTL_FilterGroupTopComponent());
        setToolTipText(Bundle.HINT_FilterGroupTopComponent());
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
