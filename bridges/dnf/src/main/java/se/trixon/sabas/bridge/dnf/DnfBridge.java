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
package se.trixon.sabas.bridge.dnf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.lookup.ServiceProvider;
import se.trixon.sabas.api.Bridge;
import se.trixon.sabas.api.BridgeOperation;
import se.trixon.sabas.api.Pkg;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
@ServiceProvider(service = Bridge.class)
public class DnfBridge extends Bridge {

    public static final String DNF_COMMAND = "dnf5";
    private final Map<String, String> mDefaultEnvironment = new HashMap<>();
    private final DnfPopulator mPopulator = new DnfPopulator();

    public DnfBridge() {
        super("DNF", "5.4", "Fedora 44");
    }

    @Override
    public String getCommand() {
        return DNF_COMMAND;
    }

    @Override
    public boolean isPkconSupported() {
        return true;
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
        return StringUtils.substringBefore(execute(List.of(DNF_COMMAND, "--version"), processes), "\n\n");
    }

    @Override
    public BridgeOperation onProvideCacheClearCommand() {
        return BridgeOperation.ofProcess(
                List.of(DNF_COMMAND, "clean", "expire-cache"),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideCacheUpdateCommand() {
        return BridgeOperation.ofProcess(
                List.of(DNF_COMMAND, "makecache"),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionInstall(String... packages) {
        return BridgeOperation.ofProcess(
                List.of(
                        PKEXEC_COMMAND,
                        DNF_COMMAND,
                        "install",
                        String.join(" ", packages)
                ),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionRemove(String... packages) {
        return BridgeOperation.ofProcess(
                List.of(
                        PKEXEC_COMMAND,
                        DNF_COMMAND,
                        "remove",
                        String.join(" ", packages)
                ),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionUpgrade() {
        return BridgeOperation.ofProcess(
                List.of(PKEXEC_COMMAND, DNF_COMMAND, "upgrade"),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideVersionCommand() {
        return BridgeOperation.ofProcess(
                List.of(DNF_COMMAND, "--version"),
                mDefaultEnvironment
        );
    }

}
