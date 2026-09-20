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
package se.trixon.sabas.bridge.flatpak;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openide.util.lookup.ServiceProvider;
import se.trixon.sabas.api.Bridge;
import se.trixon.sabas.api.BridgeOperation;
import se.trixon.sabas.api.Pkg;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
@ServiceProvider(service = Bridge.class)
public class FlatpakBridge extends Bridge {

    public static final String FLATPAK = "flatpak";
    private final Map<String, String> mDefaultEnvironment = new HashMap<>();
    private final Populator mPopulator = new Populator();

    public FlatpakBridge() {
        super("FLATPAK", "1.16", "Linux");
    }

    @Override
    public String getCommand() {
        return FLATPAK;
    }

    @Override
    public List<Pkg> onGetPackageAll(Set<Process> processes) {
        var command = List.of(
                FLATPAK,
                "remote-ls",
                "--app",
                //                "--arch=*",
                "--columns=all"
        );

        return mPopulator.populate(command, processes);
    }

    @Override
    public Pkg.Details onGetPackageDetails(Set<Process> processes, Pkg pkg) {
        var details = new Pkg.Details();
        details.setRequires(pkg.getGroup());
        pkg.setDetails(details);

        return details;
    }

    @Override
    public String onGetVersion(Set<Process> processes) {
        return execute(List.of(FLATPAK, "--version"), processes);
    }

    @Override
    public BridgeOperation onProvideCacheClearCommand() {
        return new BridgeOperation(
                List.of("true"),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideCacheUpdateCommand() {
        return new BridgeOperation(
                List.of(
                        FLATPAK,
                        "update",
                        "--appstream"
                ),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionInstall(String... packages) {
        return new BridgeOperation(
                List.of(
                        FLATPAK,
                        "install",
                        "--noninteractive",
                        String.join(" ", packages)
                ),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionRemove(String... packages) {
        return new BridgeOperation(
                List.of(
                        FLATPAK,
                        "uninstall",
                        "--noninteractive",
                        String.join(" ", packages)
                ),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionUpgrade() {
        return new BridgeOperation(
                List.of(
                        FLATPAK,
                        "update",
                        "--noninteractive"
                ),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideVersionCommand() {
        return new BridgeOperation(
                List.of(FLATPAK, "--version"),
                mDefaultEnvironment
        );
    }

}
