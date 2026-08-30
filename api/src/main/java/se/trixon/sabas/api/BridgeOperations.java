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
package se.trixon.sabas.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public interface BridgeOperations {

    default public List<Pkg> onGetPackageAll(Set<Process> processes) {
        return new ArrayList<>();
    }

    default public Pkg.Details onGetPackageDetails(Set<Process> processes, Pkg pkg) {
        return null;
    }

    default public String onGetVersion(Set<Process> processes) {
        return "NO-OP";
    }

    default public List<String> onProvideCacheClearCommand() {
        return List.of();
    }

    default public List<String> onProvideCacheUpdateCommand() {
        return List.of();
    }

    default public List<String> onProvideVersionCommand() {
        return List.of();
    }

}
