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
import java.util.List;
import javax.swing.AbstractListModel;
import javax.swing.SwingUtilities;
import org.apache.commons.lang3.StringUtils;
import se.trixon.almond.util.swing.DelayedResetRunner;
import se.trixon.almond.util.swing.SwingHelper;
import se.trixon.sabas.Sabas;
import se.trixon.sabas.api.Pkg;
import se.trixon.sabas.core.Options;
import se.trixon.sabas.core.PkgManager;
import se.trixon.sabas.ui.parts.PkgRenderer;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class BrowserPanel extends javax.swing.JPanel {

    private final Options mOptions = Options.getInstance();
    private final PkgManager mPkgManager = PkgManager.getInstance();
    private final PkgListModel mPkgListModel;
    private final DateTimeFormatter mFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final DelayedResetRunner mDetailsDelayedResetRunner;
    private final DelayedResetRunner mSelectDelayedResetRunner;

    /**
     * Creates new form BrowserPanel
     */
    public BrowserPanel() {
        mDetailsDelayedResetRunner = new DelayedResetRunner(100, () -> mPkgManager.populatePackage(mPkgManager.getSelectedPkg()));
        mSelectDelayedResetRunner = new DelayedResetRunner(120, () -> {
            mPkgManager.setSelectedPkg(packagesList.getSelectedValue());
            updateFooter();
        });
        initComponents();
        initListeners();
        mPkgListModel = new PkgListModel(mPkgManager.getFilteredItems());
        packagesList.setModel(mPkgListModel);
        packagesList.setCellRenderer(new PkgRenderer());
    }

    public void postCreate() {
        restoreDividerPositions();
    }

    private void displayPackageInfo(Pkg pkg) {
        var cardLayout = (CardLayout) (cardPanel.getLayout());
        cardLayout.show(cardPanel, pkg == null ? "empty" : "info");

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
    }

    private void initListeners() {
        Helper.setupDividerMouseListener(splitPane, Options.KEY_UI_SPLIT_POS_CENTER);

        Sabas.getGlobalState().addListener(gsce -> {
            SwingUtilities.invokeLater(() -> {
                if (mPkgListModel != null) {
                    packagesList.clearSelection();
                    mPkgListModel.updateData();
                    if (mPkgListModel.getSize() > 0) {
                        packagesList.setSelectedIndex(0);
                    }
                }
                updateFooter();
            });
        }, PkgManager.KEY_FILTERED_ITEMS);

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

    private void updateFooter() {
        var selected = "";
        var selectedIndex = packagesList.getSelectedIndex();
        if (selectedIndex != -1) {
            selected = "@%,d/".formatted(selectedIndex + 1);
        }
        footerLabel.setText("%s%,d/%,d".formatted(
                selected,
                mPkgManager.getFilteredItems().size(),
                mPkgManager.getAllItems().size()
        ));
    }

    private void restoreDividerPositions() {
        int mainPos = mOptions.getInt(Options.KEY_UI_SPLIT_POS_CENTER);
        if (mainPos == 0) {
            splitPane.setDividerLocation(0.25);
        } else {
            splitPane.setDividerLocation(mainPos);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        splitPane = new javax.swing.JSplitPane();
        listPanel = new javax.swing.JPanel();
        packagesScrollPane = new javax.swing.JScrollPane();
        packagesList = new javax.swing.JList<>();
        footerLabel = new javax.swing.JLabel();
        cardPanel = new javax.swing.JPanel();
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
        infoTabbedPane = new javax.swing.JTabbedPane();
        descriptionLogPanel = new se.trixon.almond.util.swing.LogPanel();
        filesLogPanel = new se.trixon.almond.util.swing.LogPanel();
        requiresLogPanel = new se.trixon.almond.util.swing.LogPanel();
        requiredLogPanel = new se.trixon.almond.util.swing.LogPanel();
        providesLogPanel = new se.trixon.almond.util.swing.LogPanel();

        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.LINE_AXIS));

        listPanel.setLayout(new java.awt.BorderLayout());

        packagesScrollPane.setMinimumSize(new java.awt.Dimension(250, 23));
        packagesScrollPane.setPreferredSize(new java.awt.Dimension(300, 260));

        packagesList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        packagesList.setMinimumSize(new java.awt.Dimension(300, 160));
        packagesList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                packagesListValueChanged(evt);
            }
        });
        packagesScrollPane.setViewportView(packagesList);

        listPanel.add(packagesScrollPane, java.awt.BorderLayout.CENTER);

        footerLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        org.openide.awt.Mnemonics.setLocalizedText(footerLabel, "0/0"); // NOI18N
        listPanel.add(footerLabel, java.awt.BorderLayout.SOUTH);

        splitPane.setLeftComponent(listPanel);

        cardPanel.setLayout(new java.awt.CardLayout());

        javax.swing.GroupLayout emptyCardPanelLayout = new javax.swing.GroupLayout(emptyCardPanel);
        emptyCardPanel.setLayout(emptyCardPanelLayout);
        emptyCardPanelLayout.setHorizontalGroup(
            emptyCardPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 698, Short.MAX_VALUE)
        );
        emptyCardPanelLayout.setVerticalGroup(
            emptyCardPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 476, Short.MAX_VALUE)
        );

        cardPanel.add(emptyCardPanel, "empty");

        infoCardPanel.setLayout(new java.awt.BorderLayout());

        nameLabel.setFont(new java.awt.Font("Noto Sans", 1, 20)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(nameLabel, "NAME"); // NOI18N
        nameLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.nameLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(summaryLabel, "SUMMARY"); // NOI18N
        summaryLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.summaryLabel.toolTipText")); // NOI18N

        versionLabel.setFont(new java.awt.Font("Noto Sans", 1, 20)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(versionLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.versionLabel.text")); // NOI18N
        versionLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.versionLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(licenseLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.licenseLabel.text")); // NOI18N
        licenseLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.licenseLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(installSeparatorLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.installSeparatorLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(sizeInstallLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.sizeInstallLabel.text")); // NOI18N
        sizeInstallLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.sizeInstallLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(sizeDownloadLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.sizeDownloadLabel.text")); // NOI18N
        sizeDownloadLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.sizeDownloadLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(vendorLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.vendorLabel.text")); // NOI18N
        vendorLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.vendorLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(urlLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.urlLabel.text")); // NOI18N
        urlLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.urlLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(repositoryLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.repositoryLabel.text")); // NOI18N
        repositoryLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.repositoryLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(packagerLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.packagerLabel.text")); // NOI18N
        packagerLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.packagerLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(releaseLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.releaseLabel.text")); // NOI18N
        releaseLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.releaseLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(archLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.archLabel.text")); // NOI18N
        archLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.archLabel.toolTipText")); // NOI18N

        installedTimeLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        org.openide.awt.Mnemonics.setLocalizedText(installedTimeLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.installedTimeLabel.text")); // NOI18N
        installedTimeLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.installedTimeLabel.toolTipText")); // NOI18N

        buildTimeLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
        org.openide.awt.Mnemonics.setLocalizedText(buildTimeLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.buildTimeLabel.text")); // NOI18N
        buildTimeLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.buildTimeLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(idLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.idLabel.text")); // NOI18N
        idLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.idLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(releaseSeparatorLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.releaseSeparatorLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(epochLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.epochLabel.text")); // NOI18N
        epochLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.epochLabel.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(buildSeparatorLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.buildSeparatorLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(vendorSeparatorLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.vendorSeparatorLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(statusLabel, org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.statusLabel.text")); // NOI18N
        statusLabel.setToolTipText(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.statusLabel.toolTipText")); // NOI18N

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
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 335, Short.MAX_VALUE)
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
                .addContainerGap()
                .addComponent(urlLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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
                .addComponent(urlLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        infoCardPanel.add(infoHeaderPanel, java.awt.BorderLayout.NORTH);

        infoTabbedPane.setMinimumSize(new java.awt.Dimension(80, 166));
        infoTabbedPane.setPreferredSize(new java.awt.Dimension(698, 150));
        infoTabbedPane.addTab(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.descriptionLogPanel.TabConstraints.tabTitle"), descriptionLogPanel); // NOI18N
        infoTabbedPane.addTab(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.filesLogPanel.TabConstraints.tabTitle"), filesLogPanel); // NOI18N
        infoTabbedPane.addTab(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.requiresLogPanel.TabConstraints.tabTitle"), requiresLogPanel); // NOI18N
        infoTabbedPane.addTab(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.requiredLogPanel.TabConstraints.tabTitle"), requiredLogPanel); // NOI18N
        infoTabbedPane.addTab(org.openide.util.NbBundle.getMessage(BrowserPanel.class, "BrowserPanel.providesLogPanel.TabConstraints.tabTitle"), providesLogPanel); // NOI18N

        infoCardPanel.add(infoTabbedPane, java.awt.BorderLayout.CENTER);

        cardPanel.add(infoCardPanel, "info");

        splitPane.setRightComponent(cardPanel);

        add(splitPane);
    }// </editor-fold>//GEN-END:initComponents

    private void packagesListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_packagesListValueChanged
        if (!evt.getValueIsAdjusting()) {
            mSelectDelayedResetRunner.reset();
        }
    }//GEN-LAST:event_packagesListValueChanged

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
    private javax.swing.JPanel cardPanel;
    private se.trixon.almond.util.swing.LogPanel descriptionLogPanel;
    private javax.swing.JPanel emptyCardPanel;
    private javax.swing.JLabel epochLabel;
    private se.trixon.almond.util.swing.LogPanel filesLogPanel;
    private javax.swing.JLabel footerLabel;
    private javax.swing.JLabel idLabel;
    private javax.swing.JPanel infoCardPanel;
    private javax.swing.JPanel infoHeaderPanel;
    private javax.swing.JTabbedPane infoTabbedPane;
    private javax.swing.JLabel installSeparatorLabel;
    private javax.swing.JLabel installedTimeLabel;
    private javax.swing.JLabel licenseLabel;
    private javax.swing.JPanel listPanel;
    private javax.swing.JLabel nameLabel;
    private javax.swing.JLabel packagerLabel;
    private javax.swing.JList<Pkg> packagesList;
    private javax.swing.JScrollPane packagesScrollPane;
    private se.trixon.almond.util.swing.LogPanel providesLogPanel;
    private javax.swing.JLabel releaseLabel;
    private javax.swing.JLabel releaseSeparatorLabel;
    private javax.swing.JLabel repositoryLabel;
    private se.trixon.almond.util.swing.LogPanel requiredLogPanel;
    private se.trixon.almond.util.swing.LogPanel requiresLogPanel;
    private javax.swing.JLabel sizeDownloadLabel;
    private javax.swing.JLabel sizeInstallLabel;
    private javax.swing.JSplitPane splitPane;
    private javax.swing.JLabel statusLabel;
    private javax.swing.JLabel summaryLabel;
    private se.trixon.almond.util.swing.UriLabel urlLabel;
    private javax.swing.JLabel vendorLabel;
    private javax.swing.JLabel vendorSeparatorLabel;
    private javax.swing.JLabel versionLabel;
    // End of variables declaration//GEN-END:variables

    private static class PkgListModel extends AbstractListModel<Pkg> {

        private final List<? extends Pkg> sourceList;

        public PkgListModel(List<Pkg> filteredItems) {
            this.sourceList = filteredItems;
        }

        public void updateData() {
            fireContentsChanged(this, 0, Math.max(0, getSize() - 1));
        }

        @Override
        public int getSize() {
            return sourceList.size();
        }

        @Override
        public Pkg getElementAt(int index) {
            return sourceList.get(index);
        }
    }
}
