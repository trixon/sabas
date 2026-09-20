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
package se.trixon.sabas.bridge.apt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openide.util.lookup.ServiceProvider;
import se.trixon.sabas.api.Bridge;
import se.trixon.sabas.api.BridgeOperation;
import se.trixon.sabas.api.Pkg;
import se.trixon.sabas.api.PkgDictionary;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
@ServiceProvider(service = Bridge.class)
public class AptBridge extends Bridge {

    public static final String APT_GET = "apt-get";

    private final Map<String, String> mDefaultEnvironment = new HashMap<>();
    private final List<String> mDefaultEnvironmentList;
    private final PkgDictionary mDictionary = PkgDictionary.getInstance();
    private final AptPopulator mPopulator = new AptPopulator();

    public AptBridge() {
        super("APT", "3.0", "Debian 13");
        mDefaultEnvironmentList = List.of("env",
                "DEBIAN_FRONTEND=noninteractive",
                "DEBCONF_NONINTERACTIVE_SEEN=true",
                "DEBCONF_NOWARNINGS=yes",
                "TERM=dumb"
        );
        initExecutors();
    }

    @Override
    public String getCommand() {
        return APT_GET;
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
        return execute(List.of("apt", "--version"), processes);
    }

    @Override
    public BridgeOperation onProvideCacheClearCommand() {
        return new BridgeOperation(
                createBaseCommand(createShellScript("clear", null)),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideCacheUpdateCommand() {
        return new BridgeOperation(
                createBaseCommand(createShellScript("update", null)),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionInstall(String... packages) {
        return new BridgeOperation(
                createBaseCommand(createShellScript("install", packages)),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionRemove(String... packages) {
        return new BridgeOperation(
                createBaseCommand(createShellScript("remove", packages)),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideTransactionUpgrade() {
        return new BridgeOperation(
                createBaseCommand(createShellScript("upgrade", null)),
                mDefaultEnvironment
        );
    }

    @Override
    public BridgeOperation onProvideVersionCommand() {
        return new BridgeOperation(
                new ArrayList<>(List.of("apt", "--version")),
                mDefaultEnvironment
        );
    }

    private ArrayList<String> createBaseCommand(String shellCommand) {
        var command = new ArrayList<String>();
        command.add(PKEXEC);
        command.addAll(mDefaultEnvironmentList);
        command.add("sh");
        command.add("-c");
        command.add(shellCommand);

        return command;
    }

    private String createShellScript(String command, String[] packages) {
        // -q=2
        if (packages == null) {
            packages = new String[]{""};
        }

        return "%s %s %s %s %s && echo 'SABAS SUCCESS' || { echo 'SABAS ERROR'; exit 1; }"
                .formatted(
                        APT_GET,
                        command,
                        String.join(" ", packages),
                        "-o Dpkg::Use-Pty=0",
                        "-o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold"
                );
    }

    private void initExecutors() {
        mDefaultEnvironment.put("DEBIAN_FRONTEND", "noninteractive");
        mDefaultEnvironment.put("DEBCONF_NONINTERACTIVE_SEEN", "true");
        mDefaultEnvironment.put("DEBCONF_NOWARNINGS", "yes");
        mDefaultEnvironment.put("TERM", "dumb");
    }

}
