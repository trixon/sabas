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
package se.trixon.sabas.bridge.pkgman;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.Exceptions;
import se.trixon.sabas.api.BridgePopulator;
import se.trixon.sabas.api.DictionarySection;
import se.trixon.sabas.api.Pkg;
import se.trixon.sabas.api.PkgDictionary;
import static se.trixon.sabas.bridge.pkgman.PkgmanBridge.PKGMAN;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class PkgmanPopulator extends BridgePopulator {

    private final ExecutorService mDnfExecutor = Executors.newFixedThreadPool(3);

    @Override
    public List<Pkg> populate(Set<Process> processes) {
        return List.of();
    }

    @Override
    public Pkg.Details populateDetails(Set<Process> processes, Pkg pkg) {
        var details = new Pkg.Details();
//        details.setFiles(stripDuplicateRowss(files));
//        details.setProvides(stripDuplicateRowss(provides));
//        details.setRequires(stripDuplicateRowss(requires));

        return details;
    }
}
