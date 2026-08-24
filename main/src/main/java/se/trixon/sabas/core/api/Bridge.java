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
package se.trixon.sabas.core.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import se.trixon.sabas.core.api.Pkg.Details;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class Bridge {

    protected final Set<Process> mActiveProcesses = ConcurrentHashMap.newKeySet();
    protected final String mFieldSeparator = "\u001F";
    protected final String mRecordSeparator = "\u001E";

    private String mDescription;
    private String mName;
    private String mSupports;

    public Bridge(String name, String description, String supports) {
        mName = name;
        mDescription = description;
        mSupports = supports;
    }

    public void abortCurrentOperation() {
        mActiveProcesses.stream()
                .filter(p -> p != null && p.isAlive())
                .forEach(p -> p.destroyForcibly());
        mActiveProcesses.clear();
    }

    public Details doGetPackageDetails(Pkg pkg) {
        return null;
    }

    public List<Pkg> doGetPackagesAll() {
        return new ArrayList<>();
    }

    public String doGetVersion() {
        return "NO-OP";
    }

    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<Void> executeAsync(Command command, Consumer<T> action) {
        return CompletableFuture.supplyAsync(() -> {
            return (T) switch (command) {
                case GET_VERSION ->
                    doGetVersion();
                case GET_PACKAGES_ALL ->
                    doGetPackagesAll();
                default ->
                    throw new AssertionError("Unknown command: " + command);
            };
        }).thenAccept(action);
    }

    public String getDescription() {
        return mDescription;
    }

    public String getName() {
        return mName;
    }

    public String getSupports() {
        return mSupports;
    }

    public void setDescription(String description) {
        mDescription = description;
    }

    public void setName(String name) {
        mName = name;
    }

    public void setSupports(String supports) {
        mSupports = supports;
    }

    protected String stripDuplicateRowss(String s) {
        return Arrays.stream(StringUtils.split(s, "\n"))
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .collect(Collectors.joining("\n"));
    }
}
