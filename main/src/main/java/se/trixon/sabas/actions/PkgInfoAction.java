/*
 * Copyright 2026 Patrik Karlström <patrik@trixon.se>.
 *
 * Licensed under the Apache License, Info 2.0 (the "License");
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
package se.trixon.sabas.actions;

import java.awt.event.ActionEvent;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;
import se.trixon.almond.nbp.dialogs.NbMessage;
import se.trixon.sabas.api.BridgeExecutor;

@ActionID(
        category = "Pkg",
        id = "se.trixon.sabas.actions.PkgInfoAction"
)
@ActionRegistration(
        displayName = "#CTL_PkgInfoAction",
        lazy = false
)
@ActionReferences({
    @ActionReference(path = "Menu/Commands", position = 2000), //    @ActionReference(path = "Shortcuts", name = "D-I")
})
@Messages("CTL_PkgInfoAction=Info")
public final class PkgInfoAction extends PkgBaseAction {

    public PkgInfoAction() {
        super(Bundle.CTL_PkgInfoAction(), true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        var bridge = mPkgManager.getBridge();
        var sb = new StringBuilder("<html>");
        addHeader(sb, "Install").append(convertCommand(bridge.onProvideTransactionInstall("PACKAGES...")));
        addHeader(sb, "Remove").append(convertCommand(bridge.onProvideTransactionRemove("PACKAGES...")));
        addHeader(sb, "Upgrade").append(convertCommand(bridge.onProvideTransactionUpgrade()));
        addHeader(sb, "Update").append(convertCommand(bridge.onProvideCacheUpdateCommand()));
        addHeader(sb, "Clear").append(convertCommand(bridge.onProvideCacheClearCommand()));

        NbMessage.information("Comands", sb.toString());
    }

    private StringBuilder addHeader(StringBuilder sb, String text) {
        return sb.append("<b>").append(text).append("</b><br/>");
    }

    private String convertCommand(BridgeExecutor executor) {
        return String.join(" ", executor.command()) + "<br/>";
    }
}
