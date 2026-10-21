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
package se.trixon.sabas.ui.parts;

import java.util.Comparator;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import org.openide.util.Lookup;
import se.trixon.almond.util.swing.SwingHelper;
import se.trixon.sabas.api.Bridge;
import se.trixon.sabas.api.Sabas;
import se.trixon.sabas.core.PkgManager;

/**
 *
 * @author Patrik Karlström
 */
public class BridgeComboBox extends JComboBox<Bridge> {

    private final PkgManager mPkgManager = PkgManager.getInstance();
    private boolean mInstantChange = true;

    public BridgeComboBox() {
        init();
        initListeners();
        Object a = getSelectedItem();
    }

    public boolean isInstantChange() {
        return mInstantChange;
    }

    public void setInstantChange(boolean instantChange) {
        mInstantChange = instantChange;
    }

    private void init() {
        setRenderer(new BridgeSelectorRenderer());
        Lookup.getDefault().lookupResult(Bridge.class).addLookupListener(lookupEvent -> {
            updateBridges();
        });
        updateBridges();

        setSelectedItem(mPkgManager.getBridge());
    }

    private void initListeners() {
        addActionListener(actionEvent -> {
            if (mInstantChange) {
                var newBridge = (Bridge) getSelectedItem();
                if (newBridge != mPkgManager.getBridge()) {
                    mPkgManager.setBridge(newBridge);
                }
            }
        });

        Sabas.getGlobalState().addListener(gsce -> {
            Bridge bridge = gsce.getValue();
            if (bridge != getSelectedItem()) {
                setSelectedItem(bridge);
            }
        }, PkgManager.KEY_BRIDGE);
    }

    private void updateBridges() {
        SwingHelper.runLater(() -> {
            var bridges = Lookup.getDefault().lookupAll(Bridge.class).stream()
                    .filter(bridge -> bridge.isCommandAvailable())
                    .sorted(Comparator.comparing(Bridge::getName, String.CASE_INSENSITIVE_ORDER))
                    .toArray(Bridge[]::new);
            var comboBoxCheckModel = new DefaultComboBoxModel<Bridge>(bridges);
            setModel(comboBoxCheckModel);
        });
    }
}
