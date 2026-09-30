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
package se.trixon.sabas.actions;

import java.awt.event.ActionEvent;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

@ActionID(
        category = "Pkg",
        id = "se.trixon.sabas.actions.PkgCheckUpdatesAction"
)
@ActionRegistration(
        displayName = "#CTL_PkgCheckUpdatesAction",
        lazy = false
)
@ActionReferences({
    @ActionReference(path = "Menu/Commands", position = 9),
    @ActionReference(path = "Shortcuts", name = "D-K")
})
@Messages("CTL_PkgCheckUpdatesAction=Check updates")
public final class PkgCheckUpdatesAction extends PkgBaseAction {

    public PkgCheckUpdatesAction() {
        super(Bundle.CTL_PkgCheckUpdatesAction(), true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        mPkgManager.cacheUpdate(() -> mPkgManager.checkForUpdates(true));
    }
}
