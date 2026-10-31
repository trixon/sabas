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

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Insets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import static javax.swing.Box.createHorizontalGlue;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JTextPane;
import javax.swing.text.html.HTMLDocument;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import se.trixon.almond.util.Dict;
import se.trixon.almond.util.swing.DelayedResetRunner;
import se.trixon.almond.util.swing.SwingHelper;
import se.trixon.sabas.api.Pkg;
import se.trixon.sabas.api.Sabas;
import se.trixon.sabas.core.Options;
import se.trixon.sabas.core.PkgManager;
import se.trixon.sabas.ui.parts.DelimiterLabel;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
/**
 * Top component which displays something.
 */
@ConvertAsProperties(
        dtd = "-//se.trixon.sabas.ui//Package//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "PackageTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "center", openAtStartup = true)
@ActionID(category = "Window", id = "se.trixon.sabas.ui.PackageTopComponent")
//@ActionReference(path = "Menu/Window" /*, position = 333 */)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_PackageAction",
        preferredID = "PackageTopComponent"
)
@NbBundle.Messages({
    "CTL_PackageAction=Package",
    "CTL_PackageTopComponent=Package"
})
public class PackageTopComponent extends TopComponent {

    private final Box mMergedCategoryGroupBox = Box.createHorizontalBox();
    private final Box mMergedRepoArchBox = Box.createHorizontalBox();
    private final Box mMergedSizeBox = Box.createHorizontalBox();
    private final Box mMergedTimeBox = Box.createHorizontalBox();
    private final Options mOptions = Options.getInstance();
    private final PkgManager mPkgManager = PkgManager.getInstance();
    private final DateTimeFormatter mFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final DelayedResetRunner mDetailsDelayedResetRunner;

    /**
     * Creates new form BrowserPanel
     */
    public PackageTopComponent() {
        mDetailsDelayedResetRunner = new DelayedResetRunner(100, () -> mPkgManager.populatePackage(mPkgManager.getSelectedPkg()));
        initComponents();
        initInfoPanel();
        initListeners();
        setName(Bundle.CTL_PackageTopComponent());
        putClientProperty(TopComponent.PROP_CLOSING_DISABLED, Boolean.TRUE);
        putClientProperty(TopComponent.PROP_MAXIMIZATION_DISABLED, Boolean.TRUE);

        var doc = (HTMLDocument) descHtmlTextPane.getDocument();
        doc.setAsynchronousLoadPriority(-1);

        descHtmlTextPane.setMargin(new Insets(4, 16, 16, 16));
        descPlainTextPane.setMargin(new Insets(16, 16, 16, 16));
    }

    private void displayDesc(String key, String desc, JTextPane textPane) {
        var cardLayout = (CardLayout) descriptionPanel.getLayout();
        cardLayout.show(descriptionPanel, key);

        textPane.setText(desc);
        textPane.setCaretPosition(0);

//        System.out.println(">%s<".formatted(desc));
    }

    private void displayPackageInfo(Pkg pkg) {
        var cardLayout = (CardLayout) getLayout();
        cardLayout.show(this, pkg == null ? "empty" : "info");

        if (pkg != null) {
            if (pkg.isInstalled()) {
                alterStateButton.setText("- %s".formatted(Dict.REMOVE.toString()));
            } else {
                alterStateButton.setText("+ %s".formatted(Dict.ADD.toString()));
            }
            startButton.setVisible(pkg.isInstalled() && StringUtils.isNotBlank(pkg.getDesktopId()));
            statusLabel.setVisible(pkg.isOrphaned() || pkg.isUpgradable());
            statusLabel.setText(pkg.getStatus());
            urlLabel.setToolTipText(pkg.getUrl());
            urlLabel.setUri(pkg.getUrl() == null ? "" : pkg.getUrl());

            var namePrefix = pkg.hasAppStream() ? "* " : "";
            nameLabel.setText(namePrefix + pkg.getName());
            summaryLabel.setText(pkg.getSummary());
//
            categoryLabel.setText(pkg.getCategory().toString());
            groupLabel.setText(pkg.getGroup());
            merge(mMergedCategoryGroupBox, categoryLabel, groupLabel);
//
            repositoryLabel.setText(pkg.getRepository());
            archLabel.setText(pkg.getArch());
            merge(mMergedRepoArchBox, repositoryLabel, archLabel);
//
            licenseLabel.setText(pkg.getLicense());
            versionLabel.setText(pkg.getVersion());
//
            sizeDownloadLabel.setText(formatSize(pkg.getSizeDownload()));
            sizeInstallLabel.setText(formatSize(pkg.getSizeInstall()));
            mergeParenthesis(mMergedSizeBox, sizeDownloadLabel, sizeInstallLabel);
//
            populateTime(pkg);
//
            var desc = pkg.getDescription();
            if (Strings.CI.startsWith(desc, "<html>")) {
                displayDesc("DESC_HTML", desc, descHtmlTextPane);
            } else {
                displayDesc("DESC_PLAIN", desc, descPlainTextPane);
            }

            populateMisc(pkg);

            if (pkg.getDetails() == null) {
                filesLogPanel.getTextArea().setText("");
                providesLogPanel.getTextArea().setText("");
                requiresLogPanel.getTextArea().setText("");
                mDetailsDelayedResetRunner.reset();
            } else {
                displayPackageInfoDetails(pkg);
            }
        }
    }

    private void displayPackageInfoDetails(Pkg pkg) {
        populateTime(pkg);
        var details = pkg.getDetails();
        if (details != null) {
            filesLogPanel.getTextArea().setText(details.getFiles());
            providesLogPanel.getTextArea().setText(details.getProvides());
            requiresLogPanel.getTextArea().setText(details.getRequires());
            requiredLogPanel.getTextArea().setText(details.getRequired());
            filesLogPanel.scrollToTop();
            providesLogPanel.scrollToTop();
            requiresLogPanel.scrollToTop();
            requiredLogPanel.scrollToTop();
        }
    }

    private void initInfoPanel() {
        //infoHeaderPanel.add(new JSeparator());
        infoHeaderPanel.add(createFlexibleRow(nameLabel, createHorizontalGlue(), versionLabel));
        infoHeaderPanel.add(createFlexibleRow(summaryLabel, createHorizontalGlue()));
        infoHeaderPanel.add(createFlexibleRow(mMergedCategoryGroupBox, createHorizontalGlue(), mMergedSizeBox));
        infoHeaderPanel.add(createFlexibleRow(mMergedRepoArchBox, createHorizontalGlue(), mMergedTimeBox));
        infoHeaderPanel.add(createFlexibleRow(licenseLabel, createHorizontalGlue()));
        infoHeaderPanel.add(Box.createVerticalStrut(16));
        infoHeaderPanel.add(new JSeparator());
        infoHeaderPanel.add(Box.createVerticalStrut(16));
    }

    private Box createFlexibleRow(Component... components) {
        var row = Box.createHorizontalBox();
        row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        for (var component : components) {
            row.add(component);
        }

        return row;
    }

    private void initListeners() {
        Sabas.getGlobalState().addListener(gsce -> {
            SwingHelper.runLater(() -> {
                displayPackageInfo(gsce.getValue());
            });
        }, PkgManager.KEY_SELECTED_PKG);

        Sabas.getGlobalState().addListener(gsce -> {
            if (mPkgManager.getSelectedPkg() == mPkgManager.getDetailedPkg()) {
                SwingHelper.runLater(() -> displayPackageInfoDetails(gsce.getValue()));
            }
        }, PkgManager.KEY_SELECTED_PKG_DETAILS);

        Sabas.getGlobalState().addListener(gsce -> {
            SwingHelper.enableComponents(this, !gsce.<Boolean>getValue());
        }, PkgManager.KEY_TASK_RUNNING);
    }

    private void populateMisc(Pkg pkg) {
        var map = new LinkedHashMap<String, String>() {
            {
                put("id", pkg.getId());
                put("vendor", pkg.getVendor());
                put("packager", pkg.getPackager());
            }
        };

        var sb = new StringBuilder();

        for (var entry : map.entrySet()) {
            var title = entry.getKey();
            var value = entry.getValue();
            if (StringUtils.isNotBlank(value)) {
                sb.append(title).append("\n").append(value).append("\n\n");
            }
        }

        miscLogPanel.getTextArea().setText(sb.toString().trim());
    }

    private void merge(Box box, JLabel... labels) {
        box.removeAll();
        var isFirst = true;

        for (var label : labels) {
            if (label != null && StringUtils.isNotBlank(label.getText())) {
                if (!isFirst) {
                    box.add(new DelimiterLabel());
                }

                box.add(label);
                isFirst = false;
            }
        }
    }

    private void mergeParenthesis(Box box, JLabel primaryLabel, JLabel secondaryLabel) {
        box.removeAll();

        boolean hasPrimary = StringUtils.isNotBlank(primaryLabel.getText());
        boolean hasSecondary = StringUtils.isNotBlank(secondaryLabel.getText());

        if (hasPrimary) {
            box.add(primaryLabel);
        }

        if (hasSecondary) {
            box.add(new DelimiterLabel(DelimiterLabel.Delimiter.PARENTHESIS_LEFT));
            box.add(secondaryLabel);
            box.add(new DelimiterLabel(DelimiterLabel.Delimiter.PARENTHESIS_RIGHT));
        }
    }

    private void populateTime(Pkg pkg) {
        var buildTime = pkg.getTimeBuild() > 0 ? mFormatter.format(pkg.getTimeBuildInstant()) : "-";
        var installedTime = "Not installed";
        if (pkg.getTimeInstalled() > 0) {
            installedTime = mFormatter.format(pkg.getTimeInstalledInstant());
        }

        timeBuildLabel.setText(buildTime);
        timeInstalledLabel.setText(installedTime);

        mergeParenthesis(mMergedTimeBox, timeBuildLabel, timeInstalledLabel);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        nameLabel = new javax.swing.JLabel();
        versionLabel = new javax.swing.JLabel();
        categoryLabel = new javax.swing.JLabel();
        groupLabel = new javax.swing.JLabel();
        sizeInstallLabel = new javax.swing.JLabel();
        sizeDownloadLabel = new javax.swing.JLabel();
        repositoryLabel = new javax.swing.JLabel();
        archLabel = new javax.swing.JLabel();
        timeInstalledLabel = new javax.swing.JLabel();
        timeBuildLabel = new javax.swing.JLabel();
        summaryLabel = new se.trixon.sabas.ui.parts.MultiLineLabel();
        licenseLabel = new se.trixon.sabas.ui.parts.MultiLineLabel();
        emptyCardPanel = new javax.swing.JPanel();
        infoCardPanel = new javax.swing.JPanel();
        infoCardRootPanel = new javax.swing.JPanel();
        commandPanel = new javax.swing.JPanel();
        startButton = new javax.swing.JButton();
        alterStateButton = new javax.swing.JButton();
        statusLabel = new javax.swing.JLabel();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
        urlLabel = new se.trixon.almond.util.swing.UriLabel();
        filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(16, 0), new java.awt.Dimension(16, 0), new java.awt.Dimension(16, 0));
        infoHeaderPanel = new javax.swing.JPanel();
        infoTabbedPane = new javax.swing.JTabbedPane();
        descriptionPanel = new javax.swing.JPanel();
        descHtmlScrollPane = new javax.swing.JScrollPane();
        descHtmlTextPane = new javax.swing.JTextPane();
        descPlainScrollPane = new javax.swing.JScrollPane();
        descPlainTextPane = new javax.swing.JTextPane();
        filesLogPanel = new se.trixon.almond.util.swing.LogPanel();
        requiresLogPanel = new se.trixon.almond.util.swing.LogPanel();
        requiredLogPanel = new se.trixon.almond.util.swing.LogPanel();
        providesLogPanel = new se.trixon.almond.util.swing.LogPanel();
        miscLogPanel = new se.trixon.almond.util.swing.LogPanel();

        nameLabel.setFont(new java.awt.Font("Noto Sans", 1, 20)); // NOI18N
        nameLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.nameLabel.toolTipText")); // NOI18N

        versionLabel.setFont(versionLabel.getFont().deriveFont(versionLabel.getFont().getSize()+2f));
        versionLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.versionLabel.toolTipText")); // NOI18N

        categoryLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.categoryLabel.toolTipText")); // NOI18N

        groupLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.groupLabel.toolTipText")); // NOI18N

        sizeInstallLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.sizeInstallLabel.toolTipText")); // NOI18N

        sizeDownloadLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.sizeDownloadLabel.toolTipText")); // NOI18N

        repositoryLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.repositoryLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(archLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.archLabel.text")); // NOI18N
        archLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.archLabel.toolTipText")); // NOI18N

        timeInstalledLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        timeInstalledLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.timeInstalledLabel.toolTipText")); // NOI18N

        timeBuildLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        timeBuildLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.timeBuildLabel.toolTipText")); // NOI18N

        summaryLabel.setRows(2);
        summaryLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.summaryLabel.toolTipText")); // NOI18N
        summaryLabel.setFont(summaryLabel.getFont().deriveFont((summaryLabel.getFont().getStyle() | java.awt.Font.ITALIC), summaryLabel.getFont().getSize()+2));

        licenseLabel.setRows(2);
        licenseLabel.setText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.licenseLabel.text")); // NOI18N
        licenseLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.licenseLabel.toolTipText")); // NOI18N
        licenseLabel.setFont(licenseLabel.getFont().deriveFont(licenseLabel.getFont().getSize()-2f));

        setLayout(new java.awt.CardLayout());

        javax.swing.GroupLayout emptyCardPanelLayout = new javax.swing.GroupLayout(emptyCardPanel);
        emptyCardPanel.setLayout(emptyCardPanelLayout);
        emptyCardPanelLayout.setHorizontalGroup(
            emptyCardPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 775, Short.MAX_VALUE)
        );
        emptyCardPanelLayout.setVerticalGroup(
            emptyCardPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 429, Short.MAX_VALUE)
        );

        add(emptyCardPanel, "empty");

        infoCardPanel.setLayout(new java.awt.BorderLayout());

        infoCardRootPanel.setLayout(new java.awt.BorderLayout());

        commandPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 4, 4, 4));
        commandPanel.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(startButton, "▶ %s".formatted(Dict.START.toString()));
        startButton.setToolTipText(Dict.START.toString());
        startButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        startButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 8);
        commandPanel.add(startButton, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(alterStateButton, "+"); // NOI18N
        alterStateButton.setToolTipText(Dict.ADD.toString());
        alterStateButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        alterStateButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                alterStateButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 8);
        commandPanel.add(alterStateButton, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(statusLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.statusLabel.text")); // NOI18N
        statusLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.statusLabel.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        commandPanel.add(statusLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.weightx = 1.0;
        commandPanel.add(filler1, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(urlLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.urlLabel.text")); // NOI18N
        urlLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.urlLabel.toolTipText")); // NOI18N
        urlLabel.setMaximumSize(new java.awt.Dimension(236, 28));
        commandPanel.add(urlLabel, new java.awt.GridBagConstraints());
        commandPanel.add(filler2, new java.awt.GridBagConstraints());

        infoCardRootPanel.add(commandPanel, java.awt.BorderLayout.NORTH);

        infoHeaderPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 16, 16, 16));
        infoHeaderPanel.setLayout(new javax.swing.BoxLayout(infoHeaderPanel, javax.swing.BoxLayout.PAGE_AXIS));
        infoCardRootPanel.add(infoHeaderPanel, java.awt.BorderLayout.CENTER);

        infoCardPanel.add(infoCardRootPanel, java.awt.BorderLayout.PAGE_START);

        infoTabbedPane.setMinimumSize(new java.awt.Dimension(80, 166));

        descriptionPanel.setLayout(new java.awt.CardLayout());

        descHtmlTextPane.setEditable(false);
        descHtmlTextPane.setContentType("text/html"); // NOI18N
        descHtmlTextPane.setText(""); // NOI18N
        descHtmlScrollPane.setViewportView(descHtmlTextPane);

        descriptionPanel.add(descHtmlScrollPane, "DESC_HTML");

        descPlainTextPane.setEditable(false);
        descPlainScrollPane.setViewportView(descPlainTextPane);

        descriptionPanel.add(descPlainScrollPane, "DESC_PLAIN");

        infoTabbedPane.addTab(Dict.DESCRIPTION.toString(), descriptionPanel);
        infoTabbedPane.addTab(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.filesLogPanel.TabConstraints.tabTitle"), filesLogPanel); // NOI18N
        infoTabbedPane.addTab(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.requiresLogPanel.TabConstraints.tabTitle"), requiresLogPanel); // NOI18N
        infoTabbedPane.addTab(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.requiredLogPanel.TabConstraints.tabTitle"), requiredLogPanel); // NOI18N
        infoTabbedPane.addTab(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.providesLogPanel.TabConstraints.tabTitle"), providesLogPanel); // NOI18N
        infoTabbedPane.addTab(Dict.MISCELLANEOUS.toString(), miscLogPanel);

        infoCardPanel.add(infoTabbedPane, java.awt.BorderLayout.CENTER);

        add(infoCardPanel, "info");
    }// </editor-fold>//GEN-END:initComponents

    private void alterStateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_alterStateButtonActionPerformed
        if (mPkgManager.getSelectedPkg().isInstalled()) {
            mPkgManager.remove();
        } else {
            mPkgManager.install();
        }
    }//GEN-LAST:event_alterStateButtonActionPerformed

    private void startButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startButtonActionPerformed
        mPkgManager.launch();
    }//GEN-LAST:event_startButtonActionPerformed

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }

        final long KiB = 1024L;
        final long MiB = KiB * 1024L;
        final long GiB = MiB * 1024L;

        if (bytes >= GiB) {
            return String.format("%.2f GiB", (double) bytes / GiB);
        } else if (bytes >= MiB) {
            return String.format("%.2f MiB", (double) bytes / MiB);
        } else if (bytes >= KiB) {
            return String.format("%.2f KiB", (double) bytes / KiB);
        } else {
            return bytes + " B";
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton alterStateButton;
    private javax.swing.JLabel archLabel;
    private javax.swing.JLabel categoryLabel;
    private javax.swing.JPanel commandPanel;
    private javax.swing.JScrollPane descHtmlScrollPane;
    private javax.swing.JTextPane descHtmlTextPane;
    private javax.swing.JScrollPane descPlainScrollPane;
    private javax.swing.JTextPane descPlainTextPane;
    private javax.swing.JPanel descriptionPanel;
    private javax.swing.JPanel emptyCardPanel;
    private se.trixon.almond.util.swing.LogPanel filesLogPanel;
    private javax.swing.Box.Filler filler1;
    private javax.swing.Box.Filler filler2;
    private javax.swing.JLabel groupLabel;
    private javax.swing.JPanel infoCardPanel;
    private javax.swing.JPanel infoCardRootPanel;
    private javax.swing.JPanel infoHeaderPanel;
    private javax.swing.JTabbedPane infoTabbedPane;
    private se.trixon.sabas.ui.parts.MultiLineLabel licenseLabel;
    private se.trixon.almond.util.swing.LogPanel miscLogPanel;
    private javax.swing.JLabel nameLabel;
    private se.trixon.almond.util.swing.LogPanel providesLogPanel;
    private javax.swing.JLabel repositoryLabel;
    private se.trixon.almond.util.swing.LogPanel requiredLogPanel;
    private se.trixon.almond.util.swing.LogPanel requiresLogPanel;
    private javax.swing.JLabel sizeDownloadLabel;
    private javax.swing.JLabel sizeInstallLabel;
    private javax.swing.JButton startButton;
    private javax.swing.JLabel statusLabel;
    private se.trixon.sabas.ui.parts.MultiLineLabel summaryLabel;
    private javax.swing.JLabel timeBuildLabel;
    private javax.swing.JLabel timeInstalledLabel;
    private se.trixon.almond.util.swing.UriLabel urlLabel;
    private javax.swing.JLabel versionLabel;
    // End of variables declaration//GEN-END:variables

}
