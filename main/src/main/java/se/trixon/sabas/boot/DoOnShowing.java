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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import javax.swing.SwingUtilities;
import org.openide.awt.HtmlBrowser;
import org.openide.util.Exceptions;
import org.openide.windows.IOProvider;
import org.openide.windows.OnShowing;
import se.trixon.almond.nbp.Almond;
import se.trixon.almond.nbp.dialogs.NbMessage;
import se.trixon.almond.nbp.output.OutputHelper;
import se.trixon.almond.nbp.output.OutputLineMode;
import se.trixon.almond.util.Dict;
import se.trixon.almond.util.SystemHelper;
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

        Almond.openAndActivateTopComponent("FilterTopComponent");
        Almond.hideTabs("output");
        displaySystemInformation();

        if (mPkgManager.getBridge() == null) {
            var message = """
                        In order to get started, you need to select a bridge in the absolute lower right corner of the window.

                        If no one is available, activate at least one plugin via menu Tools/Plugins.
                          Make your choise in the 'Installed' tab.""";

            NbMessage.warning("No bridge configured", message);
            NbMessage.information("Lower right corner", "... then you're good to go.");
        } else {
            SwingUtilities.invokeLater(() -> {
                var makeFullInit = false;
                if (makeFullInit) {
                    mPkgManager.cacheUpdate(false);
                } else {
                    mPkgManager.populatePackages(false);
                }
            });
        }
    }

    private void displaySystemInformation() {
        var io = IOProvider.getDefault().getIO(Dict.INFORMATION.toString(), false);
        var outputHelper = new OutputHelper(Dict.INFORMATION.toString(), io, false);

        io.select();
        try (var out = io.getOut()) {
            out.reset();
            outputHelper.println(OutputLineMode.INFO, SystemHelper.getSystemInfo());
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

}
