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
package se.trixon.sabas.bridge.appimage;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.openide.modules.Places;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;
import se.trixon.sabas.api.Bridge;
import se.trixon.sabas.api.BridgeOperation;
import se.trixon.sabas.api.Pkg;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
@ServiceProvider(service = Bridge.class)
public class AppImageBridge extends Bridge {

    public static final String COMMAND = "true";
    public static final File FEED_JSON = new File(Places.getCacheSubdirectory("bridge/appimage"), "feed.json");
    private final Map<String, String> mDefaultEnvironment = new HashMap<>();
    private final AppImagePopulator mPopulator = new AppImagePopulator();

    public AppImageBridge() {
        super("AppImage", "1.0", "Linux");

    }

    @Override
    public String getCommand() {
        return null;
    }

    @Override
    public List<Pkg> onGetPackageAll(Set<Process> processes) {
        return mPopulator.populate(processes);
    }

    @Override
    public Pkg.Details onGetPackageDetails(Set<Process> processes, Pkg pkg) {
        return mPopulator.populateDetails(processes, pkg);
    }

    @Override
    public String onGetVersion(Set<Process> processes) {
        return "Not supported";
    }

    @Override
    public BridgeOperation onProvideCacheClearCommand() {
        return BridgeOperation.ofProcess(
                List.of(COMMAND),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideCacheUpdateCommand() {
        return BridgeOperation.ofRunnable(() -> {
            try {
                IOUtils.copy(URI.create("https://appimage.github.io/feed.json").toURL(), FEED_JSON);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        });
    }

    @Override
    public BridgeOperation onProvideTransactionInstall(String... packages) {
        return BridgeOperation.ofProcess(
                List.of(
                        COMMAND
                ),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionRemove(String... packages) {
        return BridgeOperation.ofProcess(
                List.of(
                        COMMAND
                ),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionUpgrade() {
        return BridgeOperation.ofProcess(
                List.of(COMMAND),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideVersionCommand() {
        return BridgeOperation.ofProcess(
                List.of("true"),
                mDefaultEnvironment
        );
    }

}
