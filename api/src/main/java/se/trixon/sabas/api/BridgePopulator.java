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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public abstract class BridgePopulator {

    public static final String FIELD_SEPARATOR = "\u001F";
    public static String RECORD_SEPARATOR = "\u001E";

    protected final PkgDictionary mDictionary = PkgDictionary.getInstance();

    public List<Pkg> populate(Set<Process> processes) {
        return null;
    }

    public Pkg.Details populateDetails(Set<Process> processes, Pkg pkg) {
        return null;
    }

    protected long getSize(String sizeString) {
        if (sizeString == null || sizeString.trim().isEmpty() || "Available".equalsIgnoreCase(sizeString)) {
            return 0;
        }
        String[] item = sizeString.trim().split("[^0-9.A-Za-z]+");
        if (item.length < 2) {
            return 0;
        }

        long factor = switch (item[1].trim()) {
            case "kB", "KB" ->
                1024L;
            case "MB" ->
                1024L * 1024L;
            case "GB" ->
                1024L * 1024L * 1024L;
            default ->
                1L;
        };

        try {
            double sizeDouble = Double.parseDouble(item[0].trim());
            return (long) (sizeDouble * factor);

        } catch (NumberFormatException e) {
            return 0;
        }
    }

    protected String stripDuplicateRowss(String s) {
        return Arrays.stream(StringUtils.split(s, "\n"))
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .collect(Collectors.joining("\n"));
    }

}
