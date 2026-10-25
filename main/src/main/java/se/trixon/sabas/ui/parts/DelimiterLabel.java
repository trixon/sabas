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
package se.trixon.sabas.ui.parts;

import java.awt.Color;
import java.util.Objects;
import javax.swing.JLabel;
import javax.swing.UIManager;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class DelimiterLabel extends JLabel {

    private static final Color MUTED_COLOR = Objects.requireNonNullElse(UIManager.getColor("textInactiveText"), Color.RED);

    public DelimiterLabel() {
        this(Delimiter.BULLET);
    }

    public DelimiterLabel(Delimiter delimiter) {
        this(delimiter.string);
    }

    public DelimiterLabel(String delimiter) {
        super(delimiter);
        setForeground(MUTED_COLOR);
//        setForeground(Color.RED);
    }

    public static enum Delimiter {
        BULLET(" • "),
        PARENTHESIS_LEFT(" ("),
        PARENTHESIS_RIGHT(")");
        private final String string;

        private Delimiter(String string) {
            this.string = string;
        }
    }
}
