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
package se.trixon.sabas.boot;

import java.net.MalformedURLException;
import java.net.URI;
import javax.swing.SwingUtilities;
import org.openide.awt.HtmlBrowser;
import org.openide.util.Exceptions;
import org.openide.windows.OnShowing;
import se.trixon.almond.nbp.Almond;
import se.trixon.almond.nbp.dialogs.NbMessage;
import se.trixon.almond.util.SystemHelper;
import se.trixon.sabas.Sabas;
import se.trixon.sabas.core.PkgManager;

/**
 *
 * @author Patrik Karlström
 */
@OnShowing
public class DoOnShowing implements Runnable {

    private final PkgManager mPkgManager = PkgManager.getInstance();

    @Override
    public void run() {
        SystemHelper.setDesktopBrowser(url -> {
            try {
                HtmlBrowser.URLDisplayer.getDefault().showURL(URI.create(url).toURL());
            } catch (MalformedURLException ex) {
                Exceptions.printStackTrace(ex);
            }
        });

        Almond.openTopComponent("ActionsTopComponent");
        Almond.openAndActivateTopComponent("FilterTopComponent");
        Almond.hideTabs("output");
        Sabas.displaySystemInformation();
        if (mPkgManager.getBridge() == null) {
            var message = """
                        You need to select a bridge in order to get started.
                        If no one is available, activate at least one plugin via menu Tools/Plugins.
                          Make your choise in the 'Installed' tab.""";

            NbMessage.warning("No bridge configured", message);
        } else {
            SwingUtilities.invokeLater(() -> mPkgManager.cacheUpdate());
        }
    }

}
