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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.apache.commons.lang3.Strings;
import org.netbeans.api.extexecution.ExecutionDescriptor;
import org.netbeans.api.extexecution.ExecutionService;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import se.trixon.almond.nbp.dialogs.NbMessage;
import se.trixon.almond.util.Dict;
import se.trixon.almond.util.SystemHelper;
import se.trixon.sabas.Sabas;
import se.trixon.sabas.api.Bridge;
import se.trixon.sabas.api.Pkg;
import se.trixon.sabas.api.PkgDictionary;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class PkgManager {

    public static final String KEY_ALL_ITEMS = "allItems";
    public static final String KEY_BRIDGE = "bridge";
    public static final String KEY_FILTERED_ITEMS = "filteredItems";
    public static final String KEY_SELECTED_PKG = "selectedPkg";
    public static final String KEY_SELECTED_PKG_DETAILS = "selectedPkgDetails";
    public static final String KEY_TASK_RUNNING = "longTaskRunning";

    private final List<Pkg> mAllItems = Collections.synchronizedList(new ArrayList<>());
    private final List<Pkg> mFilteredItems = Collections.synchronizedList(new ArrayList<>());
    private InputOutput mInputOutput;
    private final Runnable mNoOp = () -> {
    };
    private final Options mOptions = Options.getInstance();

    public static PkgManager getInstance() {
        return Holder.INSTANCE;
    }

    private PkgManager() {
        init();
        initListeners();
    }

    public void cacheClear() {
        if (isBridgeInvalid()) {
            return;
        }

        externalExecutor("Clearing cache...", getBridge().onProvideCacheClearCommand(), mNoOp);
    }

    public void cacheUpdate() {
        if (isBridgeInvalid()) {
            return;
        }

        externalExecutor("Updating cache...", getBridge().onProvideCacheUpdateCommand(), () -> populatePackages());
    }

    public void displayVersion() {
        if (isBridgeInvalid()) {
            return;
        }

        externalExecutor("Getting version...", getBridge().onProvideVersionCommand(), mNoOp);

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

    public List<Pkg> getAllItems() {
        return mAllItems;
    }

    public Bridge getBridge() {
        return Sabas.getGlobalState().get(KEY_BRIDGE);
    }

    public Pkg getDetailedPkg() {
        return Sabas.getGlobalState().get(KEY_SELECTED_PKG_DETAILS);
    }

    public List<Pkg> getFilteredItems() {
        return mFilteredItems;
    }

    public Pkg getSelectedPkg() {
        return Sabas.getGlobalState().get(KEY_SELECTED_PKG);
    }

    public boolean isLongTaskRunning() {
        return Sabas.getGlobalState().get(KEY_TASK_RUNNING);
    }

    public void populatePackage(Pkg pkg) {
        if (pkg.getDetails() != null) {
            setSelectedPkgDetails(pkg);
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
                setSelectedPkgDetails(pkg);
                System.out.println("Package details loaded in " + SystemHelper.age(start));
            });
        });
    }

    public void populatePackages() {
        if (isBridgeInvalid()) {
            return;
        }

        setLongTaskRunning(true);
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
                    setLongTaskRunning(false);
                    return;
                }
                setAllItems(packages);
                setFilteredItems(packages);
                PkgDictionary.getInstance().debugPrint();
                System.out.println("Loaded in " + SystemHelper.age(start));
                setLongTaskRunning(false);
            });
        });
    }

    public void setAllItems(List<Pkg> newFilteredList) {
        mAllItems.clear();
        mAllItems.addAll(newFilteredList);

        Sabas.getGlobalState().send(KEY_ALL_ITEMS, List.copyOf(mAllItems));
    }

    public void setBridge(Bridge bridge) {
        if (bridge != null) {
            mOptions.put(Options.KEY_PM_BRIDGE, bridge.getClass().getSimpleName());
        }

        Sabas.getGlobalState().put(KEY_BRIDGE, bridge);
    }

    public void setFilteredItems(List<Pkg> newFilteredList) {
        mFilteredItems.clear();
        mFilteredItems.addAll(newFilteredList);

        Sabas.getGlobalState().send(KEY_FILTERED_ITEMS, List.copyOf(mFilteredItems));
    }

    public void setSelectedPkg(Pkg pkg) {
        Sabas.getGlobalState().put(KEY_SELECTED_PKG, pkg);
    }

    public void setSelectedPkgDetails(Pkg pkg) {
        Sabas.getGlobalState().put(KEY_SELECTED_PKG_DETAILS, pkg);
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

    private void externalExecutor(String displayName, List<String> externalCommand, Runnable postExecution) {
        if (mInputOutput == null) {
            mInputOutput = IOProvider.getDefault().getIO("Details", false);

        }
        var start = System.currentTimeMillis();
        var descriptor = new ExecutionDescriptor()
                .frontWindow(true)
                .inputOutput(mInputOutput)
                .showProgress(true)
                .noReset(true)
                .postExecution(() -> {
                    postExecution.run();
                    setLongTaskRunning(false);
                    System.out.println("Updated in " + SystemHelper.age(start));
                });

        var pb = new ProcessBuilder(externalCommand);
        pb.environment().put("LC_ALL", "C");

        var service = ExecutionService.newService(
                () -> pb.start(),
                descriptor,
                displayName
        );
        System.out.println(String.join(" ", externalCommand));
        setLongTaskRunning(true);
        service.run();
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

    private void initListeners() {
        Sabas.getGlobalState().addListener(gsce -> {
//            cacheUpdate();
        }, PkgManager.KEY_BRIDGE);
    }

    private boolean isBridgeInvalid() {
        if (getBridge() == null) {
            NbMessage.error("No bride selected", "Select a bridge in order to communicate with the backend.");
            return true;
        }

        return false;
    }

    private void setLongTaskRunning(boolean running) {
        Sabas.getGlobalState().put(KEY_TASK_RUNNING, running);

    }

    private static class Holder {

        private static final PkgManager INSTANCE = new PkgManager();
    }
}
