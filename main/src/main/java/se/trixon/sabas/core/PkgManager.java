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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
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
import se.trixon.almond.nbp.dialogs.NbMessage;
import se.trixon.almond.util.Dict;
import se.trixon.almond.util.SystemHelper;
import se.trixon.sabas.api.Bridge;
import se.trixon.sabas.api.Pkg;
import se.trixon.sabas.api.PkgDictionary;

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

    public void cacheClear() {
        if (isBridgeInvalid()) {
            return;
        }

        mLongTaskRunningProperty.set(true);

        var threadRef = new AtomicReference<Thread>();
        var processes = ConcurrentHashMap.<Process>newKeySet();
        var progressHandle = ProgressHandle.createHandle("Clearing cache", createCanceller(processes, threadRef));
        progressHandle.start();

        var start = System.currentTimeMillis();

        CompletableFuture.supplyAsync(() -> {
            threadRef.set(Thread.currentThread());
            return getBridge().onCacheClear(processes);
        }).whenComplete((string, ex) -> {
            progressHandle.finish();
            SwingUtilities.invokeLater(() -> {
                if (ex != null) {
                    Exceptions.printStackTrace(ex);
                    mLongTaskRunningProperty.set(false);
                    return;
                }
                System.out.println(string);
                System.out.println("Cleared in " + SystemHelper.age(start));
                mLongTaskRunningProperty.set(false);
            });
        });
    }

    public void cacheUpdate() {
        if (isBridgeInvalid()) {
            return;
        }

        mLongTaskRunningProperty.set(true);
        var threadRef = new AtomicReference<Thread>();
        var processes = ConcurrentHashMap.<Process>newKeySet();
        var progressHandle = ProgressHandle.createHandle("Updating cache", createCanceller(processes, threadRef));
        progressHandle.start();

        var start = System.currentTimeMillis();

        CompletableFuture.supplyAsync(() -> {
            threadRef.set(Thread.currentThread());
            return getBridge().onCacheUpdate(processes);
        }).whenComplete((string, ex) -> {
            progressHandle.finish();
            SwingUtilities.invokeLater(() -> {
                if (ex != null) {
                    Exceptions.printStackTrace(ex);
                    mLongTaskRunningProperty.set(false);
                    return;
                }
                System.out.println(string);
                System.out.println("Updated in " + SystemHelper.age(start));
            });
        }).thenRunAsync(() -> populatePackages());
    }

    public ObjectProperty<Pkg> detailedPkgProperty() {
        return mDetailedPkgProperty;
    }

    public void displayVersion() {
        if (isBridgeInvalid()) {
            return;
        }

        var processes = ConcurrentHashMap.<Process>newKeySet();

        CompletableFuture.supplyAsync(() -> getBridge().onGetVersion(processes))
                .whenComplete((String version, Throwable ex) -> {
                    SwingUtilities.invokeLater(() -> {
                        if (ex != null) {
                            Exceptions.printStackTrace(ex);
                        }
                        NbMessage.information(Dict.VERSION.toString(), version);
                    });
                });
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

        var threadRef = new AtomicReference<Thread>();
        var processes = ConcurrentHashMap.<Process>newKeySet();
        var progressHandle = ProgressHandle.createHandle("Get package details", createCanceller(processes, threadRef));
        progressHandle.start();

        var start = System.currentTimeMillis();

        CompletableFuture.supplyAsync(() -> {
            threadRef.set(Thread.currentThread());
            return getBridge().onGetPackageDetails(processes, pkg);
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
        if (isBridgeInvalid()) {
            return;
        }

        mLongTaskRunningProperty.set(true);
        PkgDictionary.getInstance().clear();

        var threadRef = new AtomicReference<Thread>();
        var processes = ConcurrentHashMap.<Process>newKeySet();
        var progressHandle = ProgressHandle.createHandle("Loading packages", createCanceller(processes, threadRef));
        progressHandle.start();

        var start = System.currentTimeMillis();

        CompletableFuture.supplyAsync(() -> {
            threadRef.set(Thread.currentThread());
            return getBridge().onGetPackagesAll(processes);
        }).whenComplete((List<Pkg> packages, Throwable ex) -> {
            progressHandle.finish();
            SwingUtilities.invokeLater(() -> {
                if (ex != null) {
                    Exceptions.printStackTrace(ex);
                    mLongTaskRunningProperty.set(false);
                    return;
                }
                mAllItems.setAll(packages);
                mFilteredItems.setAll(packages);
                PkgDictionary.getInstance().debugPrint();
                System.out.println("Loaded in " + SystemHelper.age(start));
                mLongTaskRunningProperty.set(false);
            });
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

    private Cancellable createCanceller(Set<Process> processes, AtomicReference<Thread> threadRef) {
        return () -> {
            for (var p : processes) {
                if (p != null && p.isAlive()) {
                    p.destroyForcibly();
                }
            }
            processes.clear();

            Thread t = threadRef.get();
            if (t != null) {
                t.interrupt();
            }
            return true;
        };
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

    private boolean isBridgeInvalid() {
        if (getBridge() == null) {
            NbMessage.error("No bride selected", "Select a bridge in order to communicate with the backend.");
            return true;
        }

        return false;
    }

    private static class Holder {

        private static final PkgManager INSTANCE = new PkgManager();
    }
}
