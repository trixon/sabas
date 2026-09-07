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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.apache.commons.lang3.StringUtils;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import se.trixon.almond.util.swing.DelayedResetRunner;
import se.trixon.almond.util.swing.SwingHelper;
import se.trixon.sabas.Sabas;
import se.trixon.sabas.api.Pkg;
import se.trixon.sabas.core.Options;
import se.trixon.sabas.core.PkgManager;

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
@ActionReference(path = "Menu/Window" /*, position = 333 */)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_PackageAction",
        preferredID = "PackageTopComponent"
)
@NbBundle.Messages({
    "CTL_PackageAction=Package",
    "CTL_PackageTopComponent=Package",
    "HINT_PackageTopComponent=This is a Package window"
})
public class PackageTopComponent extends TopComponent {

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
        initListeners();
        setName(Bundle.CTL_PackageTopComponent());
        setToolTipText(Bundle.HINT_PackageTopComponent());
        putClientProperty(TopComponent.PROP_CLOSING_DISABLED, Boolean.TRUE);
        putClientProperty(TopComponent.PROP_MAXIMIZATION_DISABLED, Boolean.TRUE);
    }

    private void displayPackageInfo(Pkg pkg) {
        var cardLayout = (CardLayout) getLayout();
        cardLayout.show(this, pkg == null ? "empty" : "info");

        if (pkg != null) {
            int maxLicenseLength = 60;
            idLabel.setText(pkg.getId());
            nameLabel.setText(pkg.getName());
            summaryLabel.setText(pkg.getSummary());
            descriptionLogPanel.clear();
            descriptionLogPanel.println(pkg.getDescription());
            if (pkg.getLicense().length() > maxLicenseLength) {
                descriptionLogPanel.println("\n FULL LICENSE");
                descriptionLogPanel.println("\n" + pkg.getLicense());
            }
            var epoch = "";
            try {
                epoch = String.valueOf(pkg.getEpoch());
            } catch (NumberFormatException e) {
                //
            }
            epochLabel.setText(epoch);
            descriptionLogPanel.scrollToTop();
            versionLabel.setText(pkg.getVersion());
            licenseLabel.setText(StringUtils.abbreviate(pkg.getLicense(), maxLicenseLength));
            sizeDownloadLabel.setText(formatSize(pkg.getSizeDownload()));
            sizeInstallLabel.setText(formatSize(pkg.getSizeInstall()));
            vendorLabel.setText(pkg.getVendor());
            urlLabel.setText(pkg.getUrl());
            urlLabel.setUri(pkg.getUrl() == null ? "" : pkg.getUrl());
            repositoryLabel.setText(pkg.getRepository());
            packagerLabel.setText(StringUtils.abbreviate(pkg.getPackager(), 50));
            releaseLabel.setText(pkg.getRelease());
            archLabel.setText(pkg.getArch());
            groupLabel.setText(pkg.getGroup());
            updateBuildTime(pkg);
            if (pkg.getTimeInstalled() == 0) {
                installedTimeLabel.setText("Not installed");
            } else {
                installedTimeLabel.setText(mFormatter.format(pkg.getTimeInstalledInstant()));
            }
            statusLabel.setText(pkg.getStatus());

            if (pkg.getDetails() == null) {
                filesLogPanel.getTextArea().setText("");
                providesLogPanel.getTextArea().setText("");
                requiresLogPanel.getTextArea().setText("");
                mDetailsDelayedResetRunner.reset();
                makeBusy(true);
            } else {
                displayPackageInfoDetails(pkg);
            }
        }
    }

    private void displayPackageInfoDetails(Pkg pkg) {
        updateBuildTime(pkg);
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
        makeBusy(false);
    }

    private void initListeners() {
        Sabas.getGlobalState().addListener(gsce -> {
            displayPackageInfo(gsce.getValue());
        }, PkgManager.KEY_SELECTED_PKG);

        Sabas.getGlobalState().addListener(gsce -> {
            if (mPkgManager.getSelectedPkg() == mPkgManager.getDetailedPkg()) {
                displayPackageInfoDetails(gsce.getValue());
            }
        }, PkgManager.KEY_SELECTED_PKG_DETAILS);

        Sabas.getGlobalState().addListener(gsce -> {
            SwingHelper.enableComponents(this, !gsce.<Boolean>getValue());
        }, PkgManager.KEY_TASK_RUNNING);
    }

    private void updateBuildTime(Pkg pkg) {
        var text = pkg.getTimeBuild() > 0 ? mFormatter.format(pkg.getTimeBuildInstant()) : "-";
        buildTimeLabel.setText(text);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        emptyCardPanel = new javax.swing.JPanel();
        infoCardPanel = new javax.swing.JPanel();
        infoHeaderPanel = new javax.swing.JPanel();
        nameLabel = new javax.swing.JLabel();
        summaryLabel = new javax.swing.JLabel();
        versionLabel = new javax.swing.JLabel();
        licenseLabel = new javax.swing.JLabel();
        installSeparatorLabel = new javax.swing.JLabel();
        sizeInstallLabel = new javax.swing.JLabel();
        sizeDownloadLabel = new javax.swing.JLabel();
        vendorLabel = new javax.swing.JLabel();
        urlLabel = new se.trixon.almond.util.swing.UriLabel();
        repositoryLabel = new javax.swing.JLabel();
        packagerLabel = new javax.swing.JLabel();
        releaseLabel = new javax.swing.JLabel();
        archLabel = new javax.swing.JLabel();
        installedTimeLabel = new javax.swing.JLabel();
        buildTimeLabel = new javax.swing.JLabel();
        idLabel = new javax.swing.JLabel();
        releaseSeparatorLabel = new javax.swing.JLabel();
        epochLabel = new javax.swing.JLabel();
        buildSeparatorLabel = new javax.swing.JLabel();
        vendorSeparatorLabel = new javax.swing.JLabel();
        statusLabel = new javax.swing.JLabel();
        groupLabel = new javax.swing.JLabel();
        infoTabbedPane = new javax.swing.JTabbedPane();
        descriptionLogPanel = new se.trixon.almond.util.swing.LogPanel();
        filesLogPanel = new se.trixon.almond.util.swing.LogPanel();
        requiresLogPanel = new se.trixon.almond.util.swing.LogPanel();
        requiredLogPanel = new se.trixon.almond.util.swing.LogPanel();
        providesLogPanel = new se.trixon.almond.util.swing.LogPanel();

        setLayout(new java.awt.CardLayout());

        javax.swing.GroupLayout emptyCardPanelLayout = new javax.swing.GroupLayout(emptyCardPanel);
        emptyCardPanel.setLayout(emptyCardPanelLayout);
        emptyCardPanelLayout.setHorizontalGroup(
            emptyCardPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 1006, Short.MAX_VALUE)
        );
        emptyCardPanelLayout.setVerticalGroup(
            emptyCardPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 496, Short.MAX_VALUE)
        );

        add(emptyCardPanel, "empty");

        infoCardPanel.setLayout(new java.awt.BorderLayout());

        nameLabel.setFont(new java.awt.Font("Noto Sans", 1, 20)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(nameLabel, "NAME"); // NOI18N
        nameLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.nameLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(summaryLabel, "SUMMARY"); // NOI18N
        summaryLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.summaryLabel.toolTipText")); // NOI18N

        versionLabel.setFont(new java.awt.Font("Noto Sans", 1, 20)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(versionLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.versionLabel.text")); // NOI18N
        versionLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.versionLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(licenseLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.licenseLabel.text")); // NOI18N
        licenseLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.licenseLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(installSeparatorLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.installSeparatorLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(sizeInstallLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.sizeInstallLabel.text")); // NOI18N
        sizeInstallLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.sizeInstallLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(sizeDownloadLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.sizeDownloadLabel.text")); // NOI18N
        sizeDownloadLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.sizeDownloadLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(vendorLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.vendorLabel.text")); // NOI18N
        vendorLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.vendorLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(urlLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.urlLabel.text")); // NOI18N
        urlLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.urlLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(repositoryLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.repositoryLabel.text")); // NOI18N
        repositoryLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.repositoryLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(packagerLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.packagerLabel.text")); // NOI18N
        packagerLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.packagerLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(releaseLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.releaseLabel.text")); // NOI18N
        releaseLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.releaseLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(archLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.archLabel.text")); // NOI18N
        archLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.archLabel.toolTipText")); // NOI18N

        installedTimeLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        org.openide.awt.Mnemonics.setLocalizedText(installedTimeLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.installedTimeLabel.text")); // NOI18N
        installedTimeLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.installedTimeLabel.toolTipText")); // NOI18N

        buildTimeLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        org.openide.awt.Mnemonics.setLocalizedText(buildTimeLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.buildTimeLabel.text")); // NOI18N
        buildTimeLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.buildTimeLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(idLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.idLabel.text")); // NOI18N
        idLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.idLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(releaseSeparatorLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.releaseSeparatorLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(epochLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.epochLabel.text")); // NOI18N
        epochLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.epochLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(buildSeparatorLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.buildSeparatorLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(vendorSeparatorLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.vendorSeparatorLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(statusLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.statusLabel.text")); // NOI18N
        statusLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.statusLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(groupLabel, org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.groupLabel.text")); // NOI18N
        groupLabel.setToolTipText(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.groupLabel.toolTipText")); // NOI18N

        javax.swing.GroupLayout infoHeaderPanelLayout = new javax.swing.GroupLayout(infoHeaderPanel);
        infoHeaderPanel.setLayout(infoHeaderPanelLayout);
        infoHeaderPanelLayout.setHorizontalGroup(
            infoHeaderPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(infoHeaderPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(infoHeaderPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, infoHeaderPanelLayout.createSequentialGroup()
                        .addComponent(repositoryLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(sizeDownloadLabel))
                    .addGroup(infoHeaderPanelLayout.createSequentialGroup()
                        .addComponent(vendorLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(vendorSeparatorLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(packagerLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(sizeInstallLabel)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(infoHeaderPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(buildSeparatorLabel, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(installSeparatorLabel, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(infoHeaderPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(buildTimeLabel, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(installedTimeLabel, javax.swing.GroupLayout.Alignment.TRAILING))
                .addContainerGap())
            .addGroup(infoHeaderPanelLayout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(licenseLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(archLabel)
                .addContainerGap())
            .addGroup(infoHeaderPanelLayout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(summaryLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(epochLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(releaseSeparatorLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(releaseLabel)
                .addContainerGap())
            .addGroup(infoHeaderPanelLayout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(nameLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(versionLabel)
                .addGap(6, 6, 6))
            .addGroup(infoHeaderPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(idLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(statusLabel)
                .addContainerGap())
            .addGroup(infoHeaderPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(infoHeaderPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(groupLabel)
                    .addComponent(urlLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        infoHeaderPanelLayout.setVerticalGroup(
            infoHeaderPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(infoHeaderPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(infoHeaderPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(nameLabel)
                    .addComponent(versionLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(infoHeaderPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(summaryLabel)
                    .addComponent(releaseLabel)
                    .addComponent(releaseSeparatorLabel)
                    .addComponent(epochLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(infoHeaderPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(licenseLabel)
                    .addComponent(archLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(infoHeaderPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(buildTimeLabel)
                    .addComponent(buildSeparatorLabel)
                    .addComponent(sizeDownloadLabel)
                    .addComponent(repositoryLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(infoHeaderPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(installedTimeLabel)
                    .addComponent(installSeparatorLabel)
                    .addComponent(sizeInstallLabel)
                    .addComponent(vendorLabel)
                    .addComponent(vendorSeparatorLabel)
                    .addComponent(packagerLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(infoHeaderPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(idLabel)
                    .addComponent(statusLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(groupLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(urlLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        infoCardPanel.add(infoHeaderPanel, java.awt.BorderLayout.NORTH);

        infoTabbedPane.setMinimumSize(new java.awt.Dimension(80, 166));
        infoTabbedPane.setPreferredSize(new java.awt.Dimension(698, 150));
        infoTabbedPane.addTab(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.descriptionLogPanel.TabConstraints.tabTitle"), descriptionLogPanel); // NOI18N
        infoTabbedPane.addTab(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.filesLogPanel.TabConstraints.tabTitle"), filesLogPanel); // NOI18N
        infoTabbedPane.addTab(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.requiresLogPanel.TabConstraints.tabTitle"), requiresLogPanel); // NOI18N
        infoTabbedPane.addTab(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.requiredLogPanel.TabConstraints.tabTitle"), requiredLogPanel); // NOI18N
        infoTabbedPane.addTab(org.openide.util.NbBundle.getMessage(PackageTopComponent.class, "PackageTopComponent.providesLogPanel.TabConstraints.tabTitle"), providesLogPanel); // NOI18N

        infoCardPanel.add(infoTabbedPane, java.awt.BorderLayout.CENTER);

        add(infoCardPanel, "info");
    }// </editor-fold>//GEN-END:initComponents
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
    private javax.swing.JLabel archLabel;
    private javax.swing.JLabel buildSeparatorLabel;
    private javax.swing.JLabel buildTimeLabel;
    private se.trixon.almond.util.swing.LogPanel descriptionLogPanel;
    private javax.swing.JPanel emptyCardPanel;
    private javax.swing.JLabel epochLabel;
    private se.trixon.almond.util.swing.LogPanel filesLogPanel;
    private javax.swing.JLabel groupLabel;
    private javax.swing.JLabel idLabel;
    private javax.swing.JPanel infoCardPanel;
    private javax.swing.JPanel infoHeaderPanel;
    private javax.swing.JTabbedPane infoTabbedPane;
    private javax.swing.JLabel installSeparatorLabel;
    private javax.swing.JLabel installedTimeLabel;
    private javax.swing.JLabel licenseLabel;
    private javax.swing.JLabel nameLabel;
    private javax.swing.JLabel packagerLabel;
    private se.trixon.almond.util.swing.LogPanel providesLogPanel;
    private javax.swing.JLabel releaseLabel;
    private javax.swing.JLabel releaseSeparatorLabel;
    private javax.swing.JLabel repositoryLabel;
    private se.trixon.almond.util.swing.LogPanel requiredLogPanel;
    private se.trixon.almond.util.swing.LogPanel requiresLogPanel;
    private javax.swing.JLabel sizeDownloadLabel;
    private javax.swing.JLabel sizeInstallLabel;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JLabel summaryLabel;
    private se.trixon.almond.util.swing.UriLabel urlLabel;
    private javax.swing.JLabel vendorLabel;
    private javax.swing.JLabel vendorSeparatorLabel;
    private javax.swing.JLabel versionLabel;
    // End of variables declaration//GEN-END:variables

}
