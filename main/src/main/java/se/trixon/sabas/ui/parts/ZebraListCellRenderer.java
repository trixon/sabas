package se.trixon.sabas.ui.parts;

import java.awt.Component;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import se.trixon.almond.util.GraphicsHelper;

public class ZebraListCellRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        var bg = isSelected ? list.getSelectionBackground() : (index & 1) == 0 ? list.getBackground() : GraphicsHelper.getZebraStripe(list.getBackground());
        setBackground(bg);

        return this;
    }
}
