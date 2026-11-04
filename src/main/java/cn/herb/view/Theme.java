package cn.herb.view;

import cn.herb.util.Assets;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;

/** 集中管理 Swing 端的颜色、间距、控件状态和响应式布局。 */
public final class Theme {
    public static final Color GREEN = new Color(30, 85, 65);
    public static final Color GREEN_HOVER = new Color(39, 111, 84);
    public static final Color GREEN_DARK = new Color(22, 69, 52);
    public static final Color GREEN_TINT = new Color(231, 242, 233);
    public static final Color ACCENT = new Color(188, 148, 83);
    public static final Color BG = new Color(246, 248, 245);
    public static final Color SURFACE = Color.WHITE;
    public static final Color INK = new Color(34, 48, 42);
    public static final Color MUTED = new Color(113, 126, 117);
    public static final Color LINE = new Color(220, 228, 220);
    public static final Color DISABLED_BG = new Color(238, 241, 238);
    public static final Color DISABLED_FG = new Color(157, 169, 160);
    public static final Color TOPBAR = new Color(252, 254, 251);
    public static final Color NAV_HOVER = new Color(242, 248, 242);
    public static final Color NAV_ACTIVE = new Color(232, 244, 235);
    public static final Color NAV_ACTIVE_BORDER = new Color(172, 207, 181);
    public static final int CONTENT_MAX_WIDTH = 1540;
    public static final Font BODY = new Font("Microsoft YaHei UI", Font.PLAIN, 14);

    private Theme() {}

    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        for (Object key : UIManager.getDefaults().keySet().toArray()) {
            if (UIManager.get(key) instanceof Font) UIManager.put(key, BODY);
        }
        UIManager.put("Panel.background", BG);
        UIManager.put("Label.foreground", INK);
        UIManager.put("OptionPane.background", BG);
        UIManager.put("OptionPane.messageForeground", INK);
        UIManager.put("Table.selectionBackground", new Color(224, 237, 227));
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("TextField.selectionBackground", new Color(198, 222, 203));
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumb", new Color(184, 202, 188));
        UIManager.put("ScrollBar.thumbDarkShadow", new Color(184, 202, 188));
        UIManager.put("ScrollBar.thumbHighlight", new Color(184, 202, 188));
        UIManager.put("ScrollBar.track", new Color(242, 247, 242));
        UIManager.put("ScrollBar.trackHighlight", new Color(242, 247, 242));
        UIManager.put("PopupMenu.background", SURFACE);
        UIManager.put("MenuItem.background", SURFACE);
        UIManager.put("MenuItem.selectionBackground", GREEN_TINT);
        UIManager.put("MenuItem.selectionForeground", GREEN);
    }

    public static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(BODY.deriveFont((float) size));
        label.setForeground(color);
        return label;
    }

    public static JLabel title(String text) {
        JLabel label = label(text, 26, INK);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    /** 充满内容宽度的标题横幅，图片按比例 contain 绘制，避免响应式窗口拉伸变形。 */
    public static JPanel heroBanner(String titleText, String subtitleText, String assetName) {
        JPanel banner = new HeroBanner(assetName);
        banner.setLayout(new BorderLayout(18, 0));
        banner.setBorder(new EmptyBorder(24, 32, 24, 32));
        JPanel copy = stack(7);
        JLabel title = title(titleText);
        title.setForeground(GREEN_DARK);
        copy.add(title);
        copy.add(label(subtitleText, 13, MUTED));
        banner.add(copy, BorderLayout.WEST);
        return banner;
    }

    public static JButton button(String text, boolean primary) {
        StyledButton button = new StyledButton(text, primary, false);
        button.setFont(BODY);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setRolloverEnabled(true);
        button.setBackground(primary ? GREEN : SURFACE);
        button.setForeground(primary ? Color.WHITE : GREEN);
        button.putClientProperty("theme.disabled.fg", DISABLED_FG);
        button.setBorder(new EmptyBorder(9, 17, 9, 17));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    public static JButton navButton(String text) {
        StyledButton button = new StyledButton(text, false, true);
        button.setFont(BODY.deriveFont(13f));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setRolloverEnabled(true);
        button.setBackground(SURFACE);
        button.setForeground(INK);
        button.putClientProperty("theme.disabled.fg", DISABLED_FG);
        button.putClientProperty("theme.nav.active", Boolean.FALSE);
        navDensity(button, false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    public static void navState(JButton button, boolean active) {
        button.putClientProperty("theme.nav.active", active);
        button.setBackground(active ? NAV_ACTIVE : TOPBAR);
        button.setForeground(active ? GREEN_DARK : INK);
        navDensity(button, isCompactNav(button));
        button.repaint();
    }

    public static void navDensity(JButton button, boolean compact) {
        if (button == null) return;
        button.putClientProperty("theme.nav.compact", compact);
        boolean active = Boolean.TRUE.equals(button.getClientProperty("theme.nav.active"));
        button.setFont(BODY.deriveFont(compact ? 12f : 14f));
        button.setBorder(new EmptyBorder(compact ? 9 : 12, compact ? 12 : 17, compact ? 9 : 12, compact ? 12 : 17));
        button.putClientProperty("theme.nav.active", active);
    }

    private static boolean isCompactNav(JButton button) {
        return Boolean.TRUE.equals(button.getClientProperty("theme.nav.compact"));
    }

    public static JLabel chip(String text) {
        JLabel chip = label(text, 12, GREEN);
        chip.setOpaque(true);
        chip.setBackground(GREEN_TINT);
        chip.setBorder(new CompoundBorder(new RoundedBorder(new Color(205, 226, 208), 1, 14), new EmptyBorder(4, 9, 4, 9)));
        return chip;
    }

    public static JComponent brandMark() {
        return new BrandMark();
    }

    public static JTextField field(int columns) {
        JTextField field = new JTextField(columns);
        input(field);
        return field;
    }

    public static void input(JComponent component) {
        component.setFont(BODY);
        component.setBorder(new CompoundBorder(new RoundedBorder(LINE, 1, 9), new EmptyBorder(8, 10, 8, 10)));
        component.setBackground(SURFACE);
        component.setForeground(INK);
        component.setOpaque(true);
        component.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) {
                component.setBorder(new CompoundBorder(new RoundedBorder(new Color(128, 177, 146), 1, 9), new EmptyBorder(8, 10, 8, 10)));
            }
            @Override public void focusLost(java.awt.event.FocusEvent event) {
                component.setBorder(new CompoundBorder(new RoundedBorder(LINE, 1, 9), new EmptyBorder(8, 10, 8, 10)));
            }
        });
    }

    public static JPanel row(Component... components) {
        JPanel panel = new WrapLayoutPanel(FlowLayout.LEFT, 10, 5);
        panel.setOpaque(false);
        for (Component component : components) panel.add(component);
        return panel;
    }

    public static JPanel stack(int gap) {
        JPanel panel = new StackPanel(gap);
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    public static JPanel page(String title, String subtitle) {
        JPanel page = new JPanel(new BorderLayout(0, 22));
        page.setOpaque(false);
        page.setBorder(new EmptyBorder(28, 32, 30, 32));
        JPanel header = stack(8);
        header.add(title(title));
        header.add(label(subtitle, 13, MUTED));
        page.add(header, BorderLayout.NORTH);
        return page;
    }

    public static JScrollPane scroll(Component component) {
        JScrollPane scroll = new JScrollPane(component, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(new RoundedBorder(LINE, 1, 10));
        scroll.setOpaque(false);
        scroll.getViewport().setBackground(SURFACE);
        scroll.getViewport().setOpaque(true);
        styleScrollBars(scroll, false);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.getHorizontalScrollBar().setUnitIncrement(20);
        return scroll;
    }

    public static JScrollPane contentScroll(Component component) {
        JScrollPane scroll = new JScrollPane(component, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setBackground(BG);
        scroll.getViewport().setOpaque(true);
        styleScrollBars(scroll, true);
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        return scroll;
    }

    /** 统一使用轻量圆角滚动条，避免 Swing 默认金属风格破坏卡片的视觉层次。 */
    public static void styleScrollBars(JScrollPane scroll, boolean outer) {
        if (scroll == null) return;
        installScrollBar(scroll.getVerticalScrollBar(), outer);
        installScrollBar(scroll.getHorizontalScrollBar(), outer);
    }

    private static void installScrollBar(JScrollBar bar, boolean outer) {
        bar.setOpaque(false);
        bar.setBorder(null);
        bar.setPreferredSize(new Dimension(outer ? 9 : 8, outer ? 9 : 8));
        bar.setUnitIncrement(18);
        bar.setUI(new SoftScrollBarUI(outer));
    }

    public static JPanel card() {
        return new SurfacePanel();
    }

    public static ResponsiveGrid responsiveGrid(int wideColumns, int mediumColumns, int narrowColumns, int hgap, int vgap) {
        return new ResponsiveGrid(wideColumns, mediumColumns, narrowColumns, hgap, vgap);
    }

    public static int responsiveColumns(int width, int wideColumns, int mediumColumns, int narrowColumns) {
        if (width >= 1200) return wideColumns;
        if (width >= 900) return mediumColumns;
        return narrowColumns;
    }

    public static JTable table(String... columns) {
        JTable table = new JTable(new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        });
        table.setRowHeight(45);
        table.setFont(BODY);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(LINE);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setBackground(SURFACE);
        table.setForeground(INK);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setFont(BODY.deriveFont(Font.BOLD, 13));
        table.getTableHeader().setPreferredSize(new Dimension(0, 44));
        table.getTableHeader().setBackground(new Color(235, 242, 235));
        table.getTableHeader().setForeground(GREEN);
        table.getTableHeader().setBorder(new EmptyBorder(0, 0, 1, 0));
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                setBorder(new EmptyBorder(0, 10, 0, 8));
                setForeground(INK);
                if (!selected) setBackground(row % 2 == 0 ? SURFACE : new Color(249, 251, 248));
                return this;
            }
        };
        table.setDefaultRenderer(Object.class, renderer);
        return table;
    }

    public static void rows(JTable table, java.util.List<Object[]> rows) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);
        for (Object[] row : rows) model.addRow(row);
    }

    public static void message(Component owner, String text) {
        JOptionPane.showMessageDialog(owner, text, "操作完成", JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component owner, String text) {
        return JOptionPane.showConfirmDialog(owner, text, "确认操作", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    public static final class ResponsiveGrid extends JPanel {
        private final int wideColumns;
        private final int mediumColumns;
        private final int narrowColumns;
        private final int hgap;
        private final int vgap;
        private int columns = -1;

        private ResponsiveGrid(int wideColumns, int mediumColumns, int narrowColumns, int hgap, int vgap) {
            super(new GridLayout(0, Math.max(1, narrowColumns), hgap, vgap));
            this.wideColumns = Math.max(1, wideColumns);
            this.mediumColumns = Math.max(1, mediumColumns);
            this.narrowColumns = Math.max(1, narrowColumns);
            this.hgap = hgap;
            this.vgap = vgap;
            setOpaque(false);
        }

        public int columnsForWidth(int width) {
            return responsiveColumns(width, wideColumns, mediumColumns, narrowColumns);
        }

        public void applyWidth(int width) {
            int next = columnsForWidth(width);
            if (next == columns) return;
            columns = next;
            setLayout(new GridLayout(0, next, hgap, vgap));
            revalidate();
            repaint();
        }

        @Override public void doLayout() {
            applyWidth(getWidth());
            super.doLayout();
        }
    }

    private static final class SoftScrollBarUI extends BasicScrollBarUI {
        private final boolean outer;

        private SoftScrollBarUI(boolean outer) {
            this.outer = outer;
        }

        @Override protected void configureScrollBarColors() {
            thumbColor = outer ? new Color(171, 196, 177) : new Color(188, 207, 193);
            trackColor = outer ? new Color(238, 245, 239) : new Color(247, 250, 247);
        }

        @Override protected JButton createDecreaseButton(int orientation) { return invisibleButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return invisibleButton(); }

        @Override protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(trackColor);
            int inset = outer ? 2 : 1;
            g.fillRoundRect(bounds.x + inset, bounds.y + inset, Math.max(1, bounds.width - inset * 2), Math.max(1, bounds.height - inset * 2), 8, 8);
            g.dispose();
        }

        @Override protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
            if (!component.isEnabled() || bounds.width <= 0 || bounds.height <= 0) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(thumbColor);
            int inset = 2;
            g.fillRoundRect(bounds.x + inset, bounds.y + inset, Math.max(4, bounds.width - inset * 2), Math.max(4, bounds.height - inset * 2), 8, 8);
            g.dispose();
        }

        private JButton invisibleButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
            return button;
        }
    }

    private static final class StyledButton extends JButton {
        private final boolean primary;
        private final boolean navigation;

        private StyledButton(String text, boolean primary, boolean navigation) {
            super(text);
            this.primary = primary;
            this.navigation = navigation;
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = Math.max(0, getWidth() - 1);
            int height = Math.max(0, getHeight() - 1);
            ButtonModel model = getModel();
            boolean active = Boolean.TRUE.equals(getClientProperty("theme.nav.active"));
            Color fill = getBackground();
            if (!isEnabled()) fill = DISABLED_BG;
            else if (model.isPressed()) fill = primary ? GREEN_DARK : new Color(224, 234, 226);
            else if (model.isRollover()) fill = primary ? GREEN_HOVER : (navigation ? NAV_HOVER : new Color(245, 249, 245));
            if (!navigation || active || model.isRollover()) {
                g.setColor(fill);
                g.fillRoundRect(0, 0, width, height, navigation ? 16 : 10, navigation ? 16 : 10);
            }
            if (navigation && active) {
                g.setColor(isEnabled() ? NAV_ACTIVE_BORDER : DISABLED_FG);
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(0, 0, width, height, 16, 16);
                g.setColor(isEnabled() ? GREEN : DISABLED_FG);
                g.fillRoundRect(12, Math.max(0, height - 3), Math.max(8, width - 24), 3, 3, 3);
            }
            if (isFocusOwner() && isEnabled()) {
                g.setColor(ACCENT);
                g.setStroke(new BasicStroke(1.2f));
                g.drawRoundRect(1, 1, Math.max(0, width - 2), Math.max(0, height - 2), 10, 10);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class HeroBanner extends JPanel {
        private final BufferedImage illustration;

        private HeroBanner(String assetName) {
            setOpaque(false);
            illustration = Assets.image(assetName);
            setPreferredSize(new Dimension(0, 152));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = Math.max(0, getWidth() - 1);
            int height = Math.max(0, getHeight() - 1);
            g.setColor(new Color(238, 247, 239));
            g.fillRoundRect(0, 0, width, height, 22, 22);
            g.setColor(new Color(211, 229, 214));
            g.drawRoundRect(0, 0, width, height, 22, 22);
            if (illustration != null && getWidth() > 0 && getHeight() > 0) {
                int maxWidth = Math.min(Math.max(170, getWidth() / 3), 430);
                int maxHeight = Math.max(90, getHeight() - 18);
                double scale = Math.min((double) maxWidth / illustration.getWidth(), (double) maxHeight / illustration.getHeight());
                int drawWidth = (int) Math.round(illustration.getWidth() * scale);
                int drawHeight = (int) Math.round(illustration.getHeight() * scale);
                int x = getWidth() - drawWidth - 16;
                int y = (getHeight() - drawHeight) / 2;
                g.setComposite(AlphaComposite.SrcOver.derive(0.90f));
                g.drawImage(illustration, x, y, drawWidth, drawHeight, null);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class SurfacePanel extends JPanel {
        private SurfacePanel() {
            setOpaque(false);
            setBackground(SURFACE);
            setBorder(new EmptyBorder(1, 1, 2, 1));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = Math.max(0, getWidth() - 1);
            int height = Math.max(0, getHeight() - 2);
            g.setColor(new Color(30, 65, 48, 12));
            g.fillRoundRect(1, 3, width, height, 14, 14);
            g.setColor(getBackground());
            g.fillRoundRect(0, 0, width, height, 12, 12);
            g.setColor(LINE);
            g.drawRoundRect(0, 0, width, height, 12, 12);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class RoundedBorder extends AbstractBorder {
        private final Color color;
        private final int thickness;
        private final int radius;

        private RoundedBorder(Color color, int thickness, int radius) {
            this.color = color;
            this.thickness = thickness;
            this.radius = radius;
        }

        @Override public Insets getBorderInsets(Component component) {
            return new Insets(thickness + 1, thickness + 1, thickness + 1, thickness + 1);
        }

        @Override public Insets getBorderInsets(Component component, Insets insets) {
            insets.left = thickness + 1;
            insets.right = thickness + 1;
            insets.top = thickness + 1;
            insets.bottom = thickness + 1;
            return insets;
        }

        @Override public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);
            g.setStroke(new BasicStroke(thickness));
            g.drawRoundRect(x + thickness / 2, y + thickness / 2, Math.max(0, width - thickness), Math.max(0, height - thickness), radius, radius);
            g.dispose();
        }
    }

    private static final class BrandMark extends JComponent {
        private BrandMark() {
            setPreferredSize(new Dimension(36, 36));
            setMinimumSize(new Dimension(36, 36));
            setMaximumSize(new Dimension(36, 36));
            setToolTipText("本草");
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = Math.min(getWidth(), getHeight()) - 2;
            g.setColor(new Color(236, 247, 235));
            g.fillOval(1, 1, size, size);
            g.setColor(GREEN);
            g.setStroke(new BasicStroke(2f));
            g.drawOval(2, 2, size - 2, size - 2);
            g.setColor(new Color(80, 139, 93));
            g.fillOval(size / 2 - 2, size / 3, 5, size / 2);
            g.setColor(ACCENT);
            g.drawArc(size / 3, size / 3, size / 3, size / 3, 190, 165);
            g.dispose();
        }
    }

    private static final class StackPanel extends JPanel {
        private final int gap;

        private StackPanel(int gap) {
            this.gap = Math.max(0, gap);
        }

        @Override protected void addImpl(Component component, Object constraints, int index) {
            if (gap > 0 && getComponentCount() > 0) super.addImpl(Box.createVerticalStrut(gap), null, -1);
            super.addImpl(component, constraints, index);
        }
    }

    /** FlowLayout 的可换行版本，避免窄窗口下筛选器和操作按钮被裁掉。 */
    private static final class WrapLayoutPanel extends JPanel {
        private final int hgap;
        private final int vgap;

        private WrapLayoutPanel(int alignment, int hgap, int vgap) {
            super(null);
            this.hgap = hgap;
            this.vgap = vgap;
        }

        @Override public void doLayout() {
            int width = getWidth();
            Insets insets = getInsets();
            int available = Math.max(1, width - insets.left - insets.right);
            int x = insets.left;
            int y = insets.top + vgap;
            int rowHeight = 0;
            for (Component component : getComponents()) {
                if (!component.isVisible()) continue;
                Dimension size = component.getPreferredSize();
                if (x > insets.left && x + size.width > insets.left + available) {
                    x = insets.left;
                    y += rowHeight + vgap;
                    rowHeight = 0;
                }
                component.setBounds(x, y, size.width, size.height);
                x += size.width + hgap;
                rowHeight = Math.max(rowHeight, size.height);
            }
        }

        @Override public Dimension getPreferredSize() {
            int width = getWidth() > 0 ? getWidth() : 700;
            int available = Math.max(1, width - getInsets().left - getInsets().right);
            int x = 0;
            int rowHeight = 0;
            int height = vgap;
            for (Component component : getComponents()) {
                if (!component.isVisible()) continue;
                Dimension size = component.getPreferredSize();
                if (x > 0 && x + size.width > available) {
                    height += rowHeight + vgap;
                    x = 0;
                    rowHeight = 0;
                }
                x += size.width + hgap;
                rowHeight = Math.max(rowHeight, size.height);
            }
            return new Dimension(width, height + rowHeight + vgap + getInsets().top + getInsets().bottom);
        }

        @Override public Dimension getMinimumSize() {
            return new Dimension(0, getPreferredSize().height);
        }
    }
}
