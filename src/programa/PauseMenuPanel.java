package programa;

import javax.swing.*;
import java.awt.*;

/**
 * Panel superpuesto para el menú de pausa.
 * Muestra botones de Reanudar, Guardar y Salir, y un interruptor para VSync.
 */
public class PauseMenuPanel extends JPanel {
    public interface Listener {
        void onResume();
        void onSave();
        void onExit();
        void onToggleVSync(boolean enabled);
    }

    public PauseMenuPanel(Listener listener) {
        setOpaque(false); // transparente, dibujaremos fondo semi-transparente en paintComponent
        setLayout(new GridBagLayout());

        JPanel box = new JPanel(new GridLayout(0,1,0,12));
        box.setOpaque(false);

        JLabel title = new JLabel("Pausa", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 42f));
        title.setForeground(Color.WHITE);

        JButton resume = new JButton("Reanudar");
        resume.setFont(resume.getFont().deriveFont(Font.BOLD, 20f));
        resume.addActionListener(e -> { if (listener != null) listener.onResume(); });

        JButton save = new JButton("Guardar y salir");
        save.setFont(save.getFont().deriveFont(Font.PLAIN, 18f));
        save.addActionListener(e -> {
            if (listener != null) {
                listener.onSave();
                listener.onExit();
            }
        });

        JCheckBox vsync = new JCheckBox("Limitar a VSync");
        vsync.setOpaque(false);
        vsync.setForeground(Color.WHITE);
        vsync.setSelected(true);
        vsync.addActionListener(e -> { if (listener != null) listener.onToggleVSync(vsync.isSelected()); });

        box.add(title);
        box.add(resume);
        box.add(save);
        box.add(vsync);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.insets = new Insets(10,10,10,10);
        add(box, gbc);
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Fondo semi-transparente para oscurecer el juego
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(new Color(0,0,0,160));
        g2.fillRect(0,0,getWidth(),getHeight());
        g2.dispose();
        super.paintComponent(g);
    }
}
