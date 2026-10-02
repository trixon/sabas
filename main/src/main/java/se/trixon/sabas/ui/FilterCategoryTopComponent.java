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
import se.trixon.sabas.api.PkgCategory;
import se.trixon.sabas.ui.parts.ZebraListCellRenderer;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(
        dtd = "-//se.trixon.sabas.ui//FilterCategory//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "FilterCategoryTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "left2", openAtStartup = true, position = 1)
@ActionID(category = "Window", id = "se.trixon.sabas.ui.FilterCategoryTopComponent")
@ActionReferences({
    @ActionReference(path = "Menu/Window", position = 10),
    @ActionReference(path = "Shortcuts", name = "D-1")
})
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_FilterCategoryAction",
        preferredID = "FilterCategoryTopComponent"
)
@Messages({
    "CTL_FilterCategoryAction=Category",
    "CTL_FilterCategoryTopComponent=Category"
})
public final class FilterCategoryTopComponent extends BaseFilterTopComponent {

    private JList<PkgCategory> list;

    public FilterCategoryTopComponent() {
        super(DictionarySection.CATEGORY, pkg -> -1);
        setName(Bundle.CTL_FilterCategoryTopComponent());
        putClientProperty(TopComponent.PROP_MAXIMIZATION_DISABLED, Boolean.TRUE);
        init();
    }

    @Override
    public boolean filter(Pkg pkg) {
        var selectedValues = list.getSelectedValuesList();
        if (selectedValues.isEmpty() || selectedValues.contains(PkgCategory.ALL)) {
            return true;
        }

        return selectedValues.contains(pkg.getCategory());
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
        var statusModel = new DefaultListModel<PkgCategory>();
        statusModel.addAll(List.of(PkgCategory.values()));
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
