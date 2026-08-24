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
package se.trixon.sabas.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javax.swing.SwingUtilities;
import org.apache.commons.lang3.Strings;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import se.trixon.almond.util.SystemHelper;
import se.trixon.sabas.core.api.Bridge;
import se.trixon.sabas.core.api.Command;
import se.trixon.sabas.core.api.Pkg;
import se.trixon.sabas.core.api.PkgDictionary;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class PkgManager {

    private final ObservableList<Pkg> mAllItemsRaw = FXCollections.observableArrayList();
    private final ObservableList<Pkg> mAllItems = FXCollections.synchronizedObservableList(mAllItemsRaw);
    private final ObjectProperty<Bridge> mBridgeProperty = new SimpleObjectProperty<>();
    private final ObjectProperty<Pkg> mDetailedPkgProperty = new SimpleObjectProperty<>();
    private final ObservableList<Pkg> mFilteredItemsRaw = FXCollections.observableArrayList();
    private final ObservableList<Pkg> mFilteredItems = FXCollections.synchronizedObservableList(mFilteredItemsRaw);
    private final BooleanProperty mLongTaskRunningProperty = new SimpleBooleanProperty();
    private final Options mOptions = Options.getInstance();
    private final ObjectProperty<Pkg> mSelectedPkgProperty = new SimpleObjectProperty<>();

    public static PkgManager getInstance() {
        return Holder.INSTANCE;
    }

    private PkgManager() {
        init();
    }

    public ObjectProperty<Bridge> bridgeProperty() {
        return mBridgeProperty;
    }

    public ObjectProperty<Pkg> detailedPkgProperty() {
        return mDetailedPkgProperty;
    }

    public ObservableList<Pkg> getAllItems() {
        return mAllItems;
    }

    public Bridge getBridge() {
        return mBridgeProperty.get();
    }

    public Pkg getDetailedPkg() {
        return mDetailedPkgProperty.get();
    }

    public ObservableList<Pkg> getFilteredItems() {
        return mFilteredItems;
    }

    public Pkg getSelectedPkg() {
        return mSelectedPkgProperty.get();
    }

    public boolean isLongTaskRunning() {
        return mLongTaskRunningProperty.get();
    }

    public BooleanProperty longTaskRunningProperty() {
        return mLongTaskRunningProperty;
    }

    public void populatePackage(Pkg pkg) {
        if (pkg.getDetails() != null) {
            mDetailedPkgProperty.set(pkg);
            return;
        }

        Cancellable canceller = () -> {
            getBridge().abortCurrentOperation();
            return true;
        };

        var progressHandle = ProgressHandle.createHandle("Get package details", canceller);
        progressHandle.start();

        var start = System.currentTimeMillis();

        CompletableFuture.supplyAsync(() -> {
            return getBridge().doGetPackageDetails(pkg);
        }).whenComplete((details, exception) -> {
            progressHandle.finish();

            if (exception != null) {
                Exceptions.printStackTrace(exception);
                return;
            }

            if (details != null) {
                pkg.setDetails(details);
            }

            SwingUtilities.invokeLater(() -> {
                mDetailedPkgProperty.set(pkg);
                System.out.println("Package details loaded in " + SystemHelper.age(start));
            });
        });
    }

    public void populatePackages() {
        mLongTaskRunningProperty.set(true);
        PkgDictionary.getInstance().clear();

        Cancellable canceller = () -> {
            getBridge().abortCurrentOperation();
            return true;
        };
        var progressHandle = ProgressHandle.createHandle("Loading packages", canceller);
        progressHandle.start();

        var start = System.currentTimeMillis();

        getBridge().executeAsync(Command.GET_PACKAGES_ALL, (List<Pkg> packages) -> {
            mAllItems.setAll(packages);
            mFilteredItems.setAll(packages);
        }).whenComplete((Void result, Throwable exception) -> {
            progressHandle.finish();
            PkgDictionary.getInstance().debugPrint();
            if (exception != null) {
                Exceptions.printStackTrace(exception);
            }
            System.out.println("Loaded in " + SystemHelper.age(start));
            mLongTaskRunningProperty.set(false);
        });
    }

    public ObjectProperty<Pkg> selectedPkgProperty() {
        return mSelectedPkgProperty;
    }

    public void setBridge(Bridge bridge) {
        if (bridge != null) {
            mOptions.put(Options.KEY_PM_BRIDGE, bridge.getClass().getSimpleName());
        }
        mBridgeProperty.set(bridge);
    }

    public void setSelectedPkg(Pkg pkg) {
        mSelectedPkgProperty.set(pkg);
    }

    private void init() {
        var bridgeName = mOptions.get(Options.KEY_PM_BRIDGE, "");
        for (var bridge : Lookup.getDefault().lookupAll(Bridge.class)) {
            if (Strings.CI.equals(bridgeName, bridge.getClass().getSimpleName())) {
                setBridge(bridge);
                break;
            }
        }
    }

    private static class Holder {

        private static final PkgManager INSTANCE = new PkgManager();
    }
}
