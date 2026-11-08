package ca.yorku.careeragent.ui;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;

/** Shared desktop presentation; no application rules or provider configuration live here. */
final class CareerTheme {
    static final Color CANVAS = new Color(0xF3F6FA);
    static final Color SURFACE = Color.WHITE;
    static final Color INK = new Color(0x172B45);
    static final Color MUTED = new Color(0x64748B);
    static final Color LINE = new Color(0xDFE7F0);
    static final Color ACCENT = new Color(0x2563EB);
    static final Color NAVY = new Color(0x14263E);
    static final Color NAV_SELECTED = new Color(0x294568);
    static final Color MINT = new Color(0x80E3CA);

    private CareerTheme() {}

    static Font font(int style, int size) { return new Font("Segoe UI", style, size); }

    static void install() {
        try { UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel"); }
        catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) {
            // The layout and explicit component styles also work with the platform fallback.
        }
        UIManager.put("control", SURFACE);
        UIManager.put("nimbusBase", new Color(0xDBE5F2));
        UIManager.put("nimbusBlueGrey", new Color(0xE8EEF6));
        UIManager.put("nimbusLightBackground", SURFACE);
        UIManager.put("nimbusSelectionBackground", ACCENT);
        UIManager.put("nimbusFocus", ACCENT);
        UIManager.put("text", INK);
        UIManager.put("defaultFont", new FontUIResource(font(Font.PLAIN, 13)));
        var keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (UIManager.get(key) instanceof Font) UIManager.put(key, new FontUIResource(font(Font.PLAIN, 13)));
        }
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("SplitPane.size", 12);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("Panel.background", SURFACE);
        UIManager.put("Label.foreground", INK);
        UIManager.put("TextField.foreground", INK);
        UIManager.put("TextArea.foreground", INK);
        UIManager.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    static JLabel label(String text, int style, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font(style, size));
        label.setForeground(color);
        return label;
    }

    static JTextArea hint(String text) {
        JTextArea hint = new JTextArea(text);
        hint.setEditable(false);
        hint.setFocusable(false);
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setOpaque(false);
        hint.setFont(font(Font.PLAIN, 12));
        hint.setForeground(MUTED);
        hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        // Two lines leave room for explanations at the narrower supported window sizes.
        hint.setRows(2);
        hint.setMinimumSize(new Dimension(0, 38));
        return hint;
    }

    static JPanel card() {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = smooth(graphics);
                g.setColor(SURFACE);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g.setColor(LINE);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        return panel;
    }

    static void styleScrollBars(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JComboBox<?> choices) {
                Dimension size = choices.getPreferredSize();
                choices.setPreferredSize(new Dimension(Math.max(124, size.width), 38));
                choices.setMinimumSize(new Dimension(0, 38));
                choices.setFont(font(Font.PLAIN, 13));
            }
            if (child instanceof JScrollPane scroll) {
                for (JScrollBar bar : new JScrollBar[]{scroll.getVerticalScrollBar(), scroll.getHorizontalScrollBar()}) {
                    bar.setUI(new BasicScrollBarUI() {
                        private JButton emptyButton() {
                            JButton button = new JButton();
                            button.setPreferredSize(new Dimension(0, 0));
                            button.setMinimumSize(new Dimension(0, 0));
                            button.setMaximumSize(new Dimension(0, 0));
                            return button;
                        }
                        @Override protected JButton createDecreaseButton(int orientation) { return emptyButton(); }
                        @Override protected JButton createIncreaseButton(int orientation) { return emptyButton(); }
                        @Override protected void paintTrack(Graphics g, JComponent component, Rectangle bounds) {
                            g.setColor(scroll.getViewport().getBackground());
                            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                        }
                        @Override protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
                            if (!component.isEnabled() || bounds.isEmpty()) return;
                            Graphics2D g = smooth(graphics);
                            g.setColor(isThumbRollover() ? new Color(0x94A3B8) : new Color(0xCFD8E4));
                            g.fillRoundRect(bounds.x + 2, bounds.y + 2, Math.max(3, bounds.width - 4),
                                    Math.max(3, bounds.height - 4), 6, 6);
                            g.dispose();
                        }
                        @Override protected Dimension getMinimumThumbSize() { return new Dimension(8, 28); }
                        @Override public Dimension getPreferredSize(JComponent component) { return new Dimension(10, 48); }
                    });
                    bar.setUnitIncrement(24);
                }
            }
            if (child instanceof Container container) styleScrollBars(container);
        }
    }

    static void styleDivider(JSplitPane split) {
        split.setUI(new BasicSplitPaneUI() {
            @Override public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override public void paint(Graphics graphics) {
                        graphics.setColor(CANVAS);
                        graphics.fillRect(0, 0, getWidth(), getHeight());
                        graphics.setColor(new Color(0xC8D2E0));
                        graphics.fillRoundRect(getWidth() / 2 - 1, getHeight() / 2 - 10, 2, 20, 2, 2);
                    }
                };
            }
        });
        split.setBackground(CANVAS);
    }

    static JButton button(String text, boolean primary) {
        JButton button = new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = smooth(graphics);
                Color fill = primary ? ACCENT : SURFACE;
                if (!isEnabled()) fill = new Color(0xEEF2F7);
                else if (getModel().isPressed()) fill = primary ? new Color(0x1D4ED8) : new Color(0xE8EFF9);
                else if (getModel().isRollover()) fill = primary ? new Color(0x3474F4) : new Color(0xF1F5FC);
                g.setColor(fill);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g.setColor(isFocusOwner() ? ACCENT : primary && isEnabled() ? fill : LINE);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                if (isFocusOwner()) {
                    g.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 8, 8);
                }
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setUI(new BasicButtonUI());
        button.setFont(font(Font.BOLD, 12));
        button.setForeground(primary ? Color.WHITE : INK);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(11, 14, 11, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setToolTipText(text);
        return button;
    }

    static final class InputBorder extends AbstractBorder {
        @Override public Insets getBorderInsets(Component component) { return new Insets(9, 11, 9, 11); }
        @Override public Insets getBorderInsets(Component component, Insets insets) {
            insets.set(9, 11, 9, 11); return insets;
        }
        @Override public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = smooth(graphics);
            g.setColor(component.isFocusOwner() ? ACCENT : LINE);
            g.drawRoundRect(x, y, width - 1, height - 1, 9, 9);
            g.dispose();
        }
    }

    /** Reflows complete buttons instead of forcing long labels into equal, narrow cells. */
    static final class Actions extends JPanel {
        private static final int GAP = 8;

        Actions() { setLayout(null); setOpaque(false); }

        private Dimension arrange(boolean place) {
            int width = getWidth();
            if (getParent() != null && getParent().getWidth() > 0) width = getParent().getWidth();
            if (width <= 0) width = 430;
            int x = 0, y = 0, rowHeight = 0, usedWidth = 0;
            for (Component child : getComponents()) {
                if (!child.isVisible()) continue;
                Dimension preferred = child.getPreferredSize();
                int childWidth = Math.min(width, preferred.width);
                int childHeight = Math.max(38, preferred.height);
                if (x > 0 && x + childWidth > width) { x = 0; y += rowHeight + GAP; rowHeight = 0; }
                if (place) child.setBounds(x, y, childWidth, childHeight);
                usedWidth = Math.max(usedWidth, x + childWidth);
                x += childWidth + GAP;
                rowHeight = Math.max(rowHeight, childHeight);
            }
            return new Dimension(usedWidth, y + rowHeight);
        }
        @Override public Dimension getPreferredSize() { return arrange(false); }
        @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
        @Override public void doLayout() { arrange(true); }
    }

    static final class NavigationRenderer extends JLabel implements ListCellRenderer<String> {
        private boolean selected;
        private boolean focused;
        NavigationRenderer() {
            setFont(font(Font.PLAIN, 13));
            setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 12));
        }
        @Override public Component getListCellRendererComponent(JList<? extends String> list, String value,
                int index, boolean selected, boolean focused) {
            this.selected = selected; this.focused = focused;
            setText(value);
            setForeground(selected ? Color.WHITE : new Color(0xB7C6D9));
            setFont(font(selected ? Font.BOLD : Font.PLAIN, 13));
            return this;
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            if (selected) {
                g.setColor(NAV_SELECTED);
                g.fillRoundRect(0, 3, getWidth() - 1, getHeight() - 6, 10, 10);
                g.setColor(MINT);
                g.fillRoundRect(0, 14, 3, getHeight() - 28, 3, 3);
            }
            if (focused) {
                g.setColor(MINT);
                g.drawRoundRect(1, 4, getWidth() - 3, getHeight() - 9, 9, 9);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static Graphics2D smooth(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g;
    }
}
