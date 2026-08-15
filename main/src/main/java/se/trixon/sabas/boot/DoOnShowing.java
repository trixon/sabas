/*
 * Copyright 2023 Patrik Karlström <patrik@trixon.se>.
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

import java.awt.Component;
import java.awt.Container;
import java.net.MalformedURLException;
import java.net.URI;
import javax.swing.JPanel;
import org.openide.awt.HtmlBrowser;
import org.openide.util.Exceptions;
import org.openide.windows.OnShowing;
import org.openide.windows.WindowManager;
import se.trixon.almond.nbp.Almond;
import se.trixon.almond.util.SystemHelper;
import se.trixon.sabas.Sabas;
import se.trixon.sabas.ui.BrowserPanel;

/**
 *
 * @author Patrik Karlström
 */
@OnShowing
public class DoOnShowing implements Runnable {

    @Override
    public void run() {
        SystemHelper.setDesktopBrowser(url -> {
            try {
                HtmlBrowser.URLDisplayer.getDefault().showURL(URI.create(url).toURL());
            } catch (MalformedURLException ex) {
                Exceptions.printStackTrace(ex);
            }
        });

        initCustomEditorMode();
//        var output = WindowManager.getDefault().findTopComponent("output");
//        output.setHtmlDisplayName("<html><b>%s</b></html>".formatted(output.getName()));
        Almond.hideTabs("output");
//        Almond.hideTabs("FilterTopComponent");
//        Almond.hideTabs("ActionsTopComponent");
        Sabas.displaySystemInformation();
    }

    private void initCustomEditorMode() {
        try {
            var editorPanel = (JPanel) findEditorAreaComponent(WindowManager.getDefault().getMainWindow());

            if (editorPanel != null) {
                var customPanel = new BrowserPanel();
                editorPanel.removeAll();
//                editorPanel.setLayout(new BorderLayout());
//                editorPanel.add(customPanel, BorderLayout.CENTER);
                editorPanel.add(customPanel);

                editorPanel.validate();
                editorPanel.repaint();
                customPanel.postCreate();
            }
        } catch (Exception e) {
            Exceptions.printStackTrace(e);
        }
    }

    private Component findEditorAreaComponent(Container container) {
        for (var component : container.getComponents()) {
            if (component.getClass().getName().endsWith("EditorView$EditorAreaComponent")) {
                return component;
            }

            if (component instanceof Container) {
                var found = findEditorAreaComponent((Container) component);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

}
