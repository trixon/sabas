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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.openide.util.Exceptions;
import se.trixon.sabas.api.Pkg;

public class AppLauncher {

    public static void launch(Pkg pkg) {
        if (pkg == null) {
            return;
        }

        Thread.startVirtualThread(() -> {
            var desktopId = pkg.getDesktopId();
            var desktopIdNoExt = Strings.CI.removeEnd(desktopId, ".desktop");
            List command = null;

            if (StringUtils.isNotBlank(desktopId)) {
                if (pkg.getValues().contains("isFlatpak")) {
                    command = List.of("flatpak", "run", desktopIdNoExt);
                } else {
                    var systemPath = "/usr/share/applications/" + desktopId;
                    var userPath = FileUtils.getUserDirectoryPath() + "/.local/share/applications/" + desktopId;
                    var finalPath = Files.exists(Path.of(userPath)) ? userPath : systemPath;

                    if (isCommandAvailable("kstart")) {
                        command = List.of("kstart", "--application", desktopIdNoExt);
                    } else if (isCommandAvailable("gio")) {
                        command = List.of("gio", "launch", finalPath);
                    } else if (isCommandAvailable("kioclient")) {
                        command = List.of("kioclient", "exec", finalPath);
                    } else if (isCommandAvailable("gtk-launch")) {
                        command = List.of("gtk-launch", desktopIdNoExt);
                    }
                }
            } else {
                if (isCommandAvailable(pkg.getName())) {
                    command = List.of(pkg.getName());
                }
            }

            if (command != null) {
                System.out.println(String.join(" ", command));
                try {
                    var pb = new ProcessBuilder(command);
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);

                    pb.start();
                } catch (IOException e) {
                    Exceptions.printStackTrace(e);
                }
            }
        });
    }

    private static boolean isCommandAvailable(String cmd) {
        try {
            return new ProcessBuilder("which", cmd).start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
