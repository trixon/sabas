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

import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import se.trixon.sabas.api.DictionarySection;
import se.trixon.sabas.api.Pkg;
import se.trixon.sabas.api.PkgStatus;
import se.trixon.sabas.ui.parts.ZebraListCellRenderer;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(
        dtd = "-//se.trixon.sabas.ui//FilterStatus//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "FilterStatusTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "left2", openAtStartup = true, position = 1)
@ActionID(category = "Window", id = "se.trixon.sabas.ui.FilterStatusTopComponent")
@ActionReferences({
    @ActionReference(path = "Menu/Window", position = 10),
    @ActionReference(path = "Shortcuts", name = "D-1")
})
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_FilterStatusAction",
        preferredID = "FilterStatusTopComponent"
)
@Messages({
    "CTL_FilterStatusAction=Status",
    "CTL_FilterStatusTopComponent=Status"
})
public final class FilterStatusTopComponent extends BaseFilterTopComponent {

    private JList<PkgStatus> list;

    public FilterStatusTopComponent() {
        super(DictionarySection.STATUS, pkg -> -1);
        setName(Bundle.CTL_FilterStatusTopComponent());
        putClientProperty(TopComponent.PROP_MAXIMIZATION_DISABLED, Boolean.TRUE);
        init();
    }

    @Override
    public boolean filter(Pkg pkg) {
        var selectedValues = list.getSelectedValuesList();
        if (selectedValues.isEmpty() || selectedValues.contains(PkgStatus.ALL)) {
            return true;
        }
        if (pkg.isUpgradable() && selectedValues.contains(PkgStatus.UPGRADABLE)) {
            return true;
        }
        if (pkg.isOrphaned() && selectedValues.contains(PkgStatus.ORPHANED)) {
            return true;
        }
        if (pkg.isInstalled() && selectedValues.contains(PkgStatus.INSTALLED)) {
            return true;
        }

        if (!pkg.isInstalled() && selectedValues.contains(PkgStatus.AVAILABLE)) {
            return true;
        }

        return false;

    }

    @Override
    public void reset() {
        list.setSelectedIndex(0);
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

    private void init() {
        var statusModel = new DefaultListModel<PkgStatus>();
        statusModel.addAll(List.of(PkgStatus.values()));
        list = new JList<>(statusModel);
        list.setCellRenderer(new ZebraListCellRenderer());
        list.setSelectedIndex(0);
        removeAll();
        add(list);

        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                mFilterManager.requestFiltering();
            }
        });

    }
}
