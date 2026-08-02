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

import java.awt.Color;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import se.trixon.almond.util.swing.SwingHelper;
import se.trixon.sabas.core.Options;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class MainPanel extends JPanel {

    private BrowserPanel mBrowserPanel;
    private FilterPanel mFilterPanel;
    private final JSplitPane mMainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
    private final Options mOptions = Options.getInstance();
    private QueuePanel mQueuePanel;
    private final JSplitPane mSubSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);

    public MainPanel() {
        init();
        initListeners();
    }

    public void postCreate() {
        restoreDividerPositions();
        mBrowserPanel.postCreate();
    }

    private void init() {
        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
        add(mMainSplitPane);
        mFilterPanel = new FilterPanel();
        mBrowserPanel = new BrowserPanel();
        mQueuePanel = new QueuePanel();

        mFilterPanel.setBackground(Color.RED);
        mBrowserPanel.setBackground(Color.YELLOW);
        mQueuePanel.setBackground(Color.GREEN);
        var minDimension = SwingHelper.getUIScaledDim(200, 1);
        mFilterPanel.setMinimumSize(minDimension);
        mQueuePanel.setMinimumSize(minDimension);
        mMainSplitPane.setLeftComponent(mFilterPanel);
        mMainSplitPane.setRightComponent(mSubSplitPane);
        mSubSplitPane.setLeftComponent(mBrowserPanel);
        mSubSplitPane.setRightComponent(mQueuePanel);

        mMainSplitPane.setResizeWeight(0.0);
        mSubSplitPane.setResizeWeight(1.0);
    }

    private void initListeners() {
        Helper.setupDividerMouseListener(mMainSplitPane, Options.KEY_UI_SPLIT_POS_LEFT);
        Helper.setupDividerMouseListener(mSubSplitPane, Options.KEY_UI_SPLIT_POS_RIGHT);
    }

    private void restoreDividerPositions() {
        int mainPos = mOptions.getInt(Options.KEY_UI_SPLIT_POS_LEFT);
        int subPos = mOptions.getInt(Options.KEY_UI_SPLIT_POS_RIGHT);
        if (mainPos == 0 && subPos == 0) {
            mMainSplitPane.setDividerLocation(0.25);
            mSubSplitPane.setDividerLocation(0.6666);
        } else {
            mMainSplitPane.setDividerLocation(mainPos);
            mSubSplitPane.setDividerLocation(subPos);
        }
    }

}
