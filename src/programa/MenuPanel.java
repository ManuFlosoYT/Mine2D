// filepath: /home/manu/Documents/GitHub/Mine2D/src/programa/MenuPanel.java
package programa;

import javax.swing.*;
import java.awt.*;

/**
 * Menú principal simple con dos botones: Jugar y Salir.
 */
public class MenuPanel extends JPanel {

    public interface Listener {
        void onPlayRequested();
        void onExitRequested();
    }

    private final Listener listener;

    public MenuPanel(Listener listener) {
        this.listener = listener;
        initUI();
    }

    private void initUI() {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = GridBagConstraints.RELATIVE;
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel box = new JPanel();
        box.setLayout(new GridLayout(0, 1, 0, 10));
        box.setOpaque(false);

        JLabel title = new JLabel("Mine2D", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 48f));
        title.setForeground(new Color(230, 230, 230));

        JButton play = new JButton("Jugar");
        play.setFont(play.getFont().deriveFont(Font.BOLD, 24f));
        play.addActionListener(e -> {
            if (listener != null) listener.onPlayRequested();
        });

        JButton exit = new JButton("Salir");
        exit.setFont(exit.getFont().deriveFont(Font.PLAIN, 20f));
        exit.addActionListener(e -> {
            if (listener != null) listener.onExitRequested();
        });

        box.add(title);
        box.add(play);
        box.add(exit);

        add(box, gbc);

        setBackground(new Color(30, 30, 30));
    }
}

