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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class Bridge implements BridgeOperations {

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

    public ProcessBuilder createProcessBuilder(List<String> command) {
        var processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put("LC_ALL", "C");
        processBuilder.redirectErrorStream(true);

        return processBuilder;
    }

    public String execute(List command, Set<Process> processes) {
        return execute(command, processes, Map.of());
    }

    public String execute(List command, Set<Process> processes, Map<String, String> map) {
        System.out.println(String.join(" ", command));
        String output = null;
        Process process = null;
        try {
            var processBuilder = new ProcessBuilder(command);
            processBuilder.environment().putAll(map);
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();
//            process.
            processes.add(process);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            output = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            output = null;
            System.err.println(e.toString());
        } finally {
            if (process != null) {
                processes.remove(process);
            }
            Thread.interrupted();
        }

        return output;
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

    public File getUserHome() {
        return SystemUtils.getUserHome();
    }

    public Path getUserHomePath() {
        return SystemUtils.getUserHomePath();
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
