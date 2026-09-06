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

import java.awt.Component;
import java.awt.Dimension;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.border.EmptyBorder;
import org.openide.awt.StatusLineElementProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import se.trixon.almond.util.swing.SwingHelper;
import se.trixon.sabas.api.Bridge;
import se.trixon.sabas.core.PkgManager;

/**
 *
 * @author Patrik Karlström
 */
@ServiceProvider(service = StatusLineElementProvider.class, position = 599)
public class BridgeStatusLineElement implements StatusLineElementProvider {

    private JComboBox<Bridge> mComboBox;
    private final PkgManager mPkgManager = PkgManager.getInstance();

    public BridgeStatusLineElement() {
    }

    @Override
    public Component getStatusLineElement() {
        if (mComboBox == null) {
            init();
            initListeners();
        }

        return mComboBox;
    }

    private void init() {
        mComboBox = new JComboBox<>();
        mComboBox.setBorder(new EmptyBorder(0, 0, 0, 0));
        mComboBox.setRenderer(new BridgeSelectorRenderer());
        mComboBox.setPreferredSize(new Dimension(200, mComboBox.getPreferredSize().height));
//        mComboBox.setMinimumSize(SwingHelper.getUIScaledDim(200, 1));
        Lookup.getDefault().lookupResult(Bridge.class).addLookupListener(lookupEvent -> {
            updateBridges();
        });
        updateBridges();

        mComboBox.setSelectedItem(mPkgManager.getBridge());
    }

    private void initListeners() {
        mComboBox.addActionListener(actionEvent -> {
            mPkgManager.setBridge((Bridge) mComboBox.getSelectedItem());
        });
    }

    private void updateBridges() {
        SwingHelper.runLater(() -> {
            var bridges = Lookup.getDefault().lookupAll(Bridge.class).stream().toArray(Bridge[]::new);
            var comboBoxCheckModel = new DefaultComboBoxModel<Bridge>(bridges);
            mComboBox.setModel(comboBoxCheckModel);
        });
    }
}
