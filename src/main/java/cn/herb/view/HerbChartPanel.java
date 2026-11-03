package cn.herb.view;

import cn.herb.model.Medicinal;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 药材数据分析仪表盘的纯 Swing 翻译版。
 *
 * <p>本类对应参考页面的标题、指标卡、两张主图、两张分布图和底部对比区。
 * 不使用浏览器或图表库，所有图表均由 Graphics2D 绘制，数据库数据由 MainFrame
 * 通过 Platform.stockLogs 异步加载后注入。</p>
 */
public final class HerbChartPanel extends JPanel {
    private static final String PERIOD_7 = "近 7 天";
    private static final String PERIOD_30 = "近 30 天";
    private static final String PERIOD_ALL = "全部";
    private static final String MODE_QUANTITY = "数量";
    private static final String MODE_PERCENT = "百分比";
    private static final int MAX_COMPARE = 5;
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.ROOT);

    private List<Medicinal> herbs = List.of();
    private List<StockEvent> events = List.of();
    private final LinkedHashSet<String> comparisonKeys = new LinkedHashSet<>();
    private String selected = "";
    private String period = PERIOD_30;
    private int baseline = 1000;
    private double animationProgress = 1d;
    private boolean updatingData;

    private final JComboBox<String> mode = new JComboBox<>(new String[]{MODE_QUANTITY, MODE_PERCENT});
    private final JComboBox<String> herb = new JComboBox<>();
    private final JComboBox<String> periodBox = new JComboBox<>(new String[]{PERIOD_7, PERIOD_30, PERIOD_ALL});
    private final JSpinner baselineField = new JSpinner(new SpinnerNumberModel(1000, 1, 1_000_000_000, 100));
    private final JSlider range = new JSlider(20, 100, 100);
    private final JLabel rangeLabel = Theme.label("范围 100%", 12, Theme.MUTED);

    private final Theme.ResponsiveGrid overviewCards = Theme.responsiveGrid(4, 2, 1, 14, 14);
    private final DefaultListModel<Medicinal> selectorModel = new DefaultListModel<>();
    private final JList<Medicinal> selector = new JList<>(selectorModel);
    private final JTextField selectorSearch = Theme.field(14);
    private final JPanel tags = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
    private final JTable compareTable = Theme.table("药材", "编号", "当前库存", "预警阈值", "期间变化", "达标率", "流水条数");
    private final Map<String, JButton> periodButtons = new HashMap<>();

    private final LineCanvas trendCanvas = new LineCanvas();
    private final BarCanvas inventoryCanvas = new BarCanvas();
    private final DistributionCanvas statusCanvas = new DistributionCanvas(false);
    private final DistributionCanvas coverageCanvas = new DistributionCanvas(true);
    private final CompareCanvas compareCanvas = new CompareCanvas();
    private final List<PlotCanvas> canvases = List.of(trendCanvas, inventoryCanvas, statusCanvas, coverageCanvas, compareCanvas);

    private final Timer animation = new Timer(24, event -> {
        animationProgress = Math.min(1d, animationProgress + 0.075d);
        repaintCanvases();
        if (animationProgress >= 1d) ((Timer) event.getSource()).stop();
    });

    public HerbChartPanel() {
        super(new BorderLayout(0, 14));
        setOpaque(false);
        setPreferredSize(new Dimension(900, 1220));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        configureControls();
        updatePeriodButtons();
        updateOverview();
        updateSelectorModel();
        updateCompareArea();
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);
        JPanel heading = Theme.stack(5);
        heading.add(Theme.label("药材数据分析仪表盘", 23, Theme.GREEN));
        heading.add(Theme.label("库存结余、基准达标与多种药材对比", 13, Theme.MUTED));
        header.add(heading, BorderLayout.WEST);

        JPanel controls = Theme.row();
        controls.add(Theme.label("显示", 12, Theme.MUTED));
        styleControl(mode, 82);
        controls.add(mode);
        controls.add(Theme.label("基准库存", 12, Theme.MUTED));
        baselineField.setToolTipText("用于计算库存基准完成率");
        styleControl(baselineField, 92);
        controls.add(baselineField);
        controls.add(Theme.label("折线药材", 12, Theme.MUTED));
        styleControl(herb, 112);
        controls.add(herb);
        controls.add(Theme.label("时间段", 12, Theme.MUTED));
        styleControl(periodBox, 92);
        controls.add(periodBox);
        controls.add(Theme.label("显示范围", 12, Theme.MUTED));
        range.setOpaque(false);
        range.setPreferredSize(new Dimension(118, 28));
        range.setToolTipText("缩放主图和柱形图显示的数据范围");
        controls.add(range);
        controls.add(rangeLabel);
        header.add(controls, BorderLayout.SOUTH);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(0, 2, 18, 2));
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        overviewCards.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(overviewCards);
        body.add(Box.createVerticalStrut(18));

        body.add(chartCard("库存结余趋势", "使用库存流水中的结余字段，查看选中药材在所选时间段的变化", trendCanvas));
        body.add(Box.createVerticalStrut(16));
        body.add(chartCard("药材库存基准对比", "柱形高度表示当前库存；百分比模式为当前库存 / 基准库存 × 100%", inventoryCanvas));
        body.add(Box.createVerticalStrut(16));

        Theme.ResponsiveGrid distributions = Theme.responsiveGrid(2, 2, 1, 14, 14);
        distributions.setAlignmentX(Component.LEFT_ALIGNMENT);
        distributions.add(chartCard("库存状态分布", "当前库存与预警阈值的关系", statusCanvas));
        distributions.add(chartCard("库存基准完成率分布", "按基准库存完成率分组", coverageCanvas));
        body.add(distributions);
        body.add(Box.createVerticalStrut(16));

        body.add(buildCompareSection());
        return body;
    }

    private JPanel chartCard(String title, String subtitle, PlotCanvas canvas) {
        JPanel card = Theme.card();
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(16, 16, 14, 16));
        JPanel heading = new JPanel(new BorderLayout(10, 3));
        heading.setOpaque(false);
        heading.add(Theme.label(title, 17, Theme.GREEN), BorderLayout.WEST);
        heading.add(Theme.label(subtitle, 12, Theme.MUTED), BorderLayout.SOUTH);
        card.add(heading, BorderLayout.NORTH);
        card.add(horizontalPlotScroll(canvas), BorderLayout.CENTER);
        return card;
    }

    private JScrollPane horizontalPlotScroll(PlotCanvas canvas) {
        JScrollPane scroll = new JScrollPane(canvas, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getHorizontalScrollBar().setUnitIncrement(32);
        return scroll;
    }

    private JPanel buildCompareSection() {
        JPanel section = Theme.card();
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setLayout(new BorderLayout(0, 12));
        section.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);
        JPanel title = Theme.stack(4);
        title.add(Theme.label("药材对比", 19, Theme.GREEN));
        title.add(Theme.label("选择最多 5 种药材，按时间段比较当前库存、阈值和变化幅度", 12, Theme.MUTED));
        header.add(title, BorderLayout.WEST);
        JPanel switches = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        switches.setOpaque(false);
        for (String value : new String[]{PERIOD_7, PERIOD_30, PERIOD_ALL}) {
            JButton button = Theme.button(value, false);
            button.setName("chart.period." + value);
            button.addActionListener(event -> setPeriod(value));
            periodButtons.put(value, button);
            switches.add(button);
        }
        header.add(switches, BorderLayout.EAST);
        section.add(header, BorderLayout.NORTH);

        JPanel body = new ResponsiveComparePanel(buildSelectorSidebar(), buildCompareContent());
        section.add(body, BorderLayout.CENTER);
        return section;
    }

    private JPanel buildSelectorSidebar() {
        JPanel sidebar = Theme.card();
        sidebar.setLayout(new BorderLayout(0, 10));
        sidebar.setBorder(new EmptyBorder(14, 14, 14, 14));
        JPanel toolbar = new JPanel(new BorderLayout(8, 8));
        toolbar.setOpaque(false);
        selectorSearch.setToolTipText("搜索药材名称或编号");
        toolbar.add(selectorSearch, BorderLayout.CENTER);
        JPanel actions = Theme.row();
        JButton suggested = Theme.button("一键加入推荐", true);
        JButton clear = Theme.button("清空", false);
        suggested.addActionListener(event -> useSuggested());
        clear.addActionListener(event -> {
            comparisonKeys.clear();
            updateCompareArea();
            restartAnimation();
        });
        actions.add(suggested);
        actions.add(clear);
        toolbar.add(actions, BorderLayout.SOUTH);
        sidebar.add(toolbar, BorderLayout.NORTH);

        selector.setName("chart.herb.selector");
        selector.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        selector.setVisibleRowCount(8);
        selector.setFixedCellHeight(62);
        selector.setFont(Theme.BODY);
        selector.setBackground(Theme.SURFACE);
        selector.setCellRenderer(new HerbCellRenderer());
        selector.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 1) return;
                int index = selector.locationToIndex(event.getPoint());
                if (index < 0 || index >= selectorModel.size()) return;
                Medicinal medicinal = selectorModel.get(index);
                toggleComparison(medicinal);
            }
        });
        JScrollPane listScroll = new JScrollPane(selector, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.setBorder(new ThemeListBorder());
        listScroll.setPreferredSize(new Dimension(300, 310));
        listScroll.getVerticalScrollBar().setUnitIncrement(18);
        sidebar.add(listScroll, BorderLayout.CENTER);
        return sidebar;
    }

    private JPanel buildCompareContent() {
        JPanel content = Theme.card();
        content.setLayout(new BorderLayout(0, 10));
        content.setBorder(new EmptyBorder(14, 14, 14, 14));
        tags.setOpaque(false);
        content.add(tags, BorderLayout.NORTH);
        content.add(horizontalPlotScroll(compareCanvas), BorderLayout.CENTER);
        JScrollPane tableScroll = Theme.scroll(compareTable);
        tableScroll.setPreferredSize(new Dimension(620, 190));
        content.add(tableScroll, BorderLayout.SOUTH);
        return content;
    }

    private void configureControls() {
        mode.addActionListener(event -> {
            if (!updatingData) restartAnimation();
        });
        herb.addActionListener(event -> {
            if (updatingData) return;
            selected = String.valueOf(herb.getSelectedItem());
            restartAnimation();
        });
        periodBox.addActionListener(event -> {
            if (!updatingData) setPeriod(String.valueOf(periodBox.getSelectedItem()));
        });
        baselineField.addChangeListener(event -> {
            baseline = Math.max(1, ((Number) baselineField.getValue()).intValue());
            updateOverview();
            updateCompareArea();
            restartAnimation();
        });
        range.addChangeListener(event -> {
            rangeLabel.setText("范围 " + range.getValue() + "%");
            if (!range.getValueIsAdjusting()) restartAnimation();
            else repaintCanvases();
        });
        selectorSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateSelectorModel(); }
            @Override public void removeUpdate(DocumentEvent event) { updateSelectorModel(); }
            @Override public void changedUpdate(DocumentEvent event) { updateSelectorModel(); }
        });
    }

    /** 注入 MainFrame 异步获取的药材档案和库存流水。 */
    public void setData(List<Medicinal> value, List<Object[]> records) {
        updatingData = true;
        herbs = value == null ? List.of() : List.copyOf(value);
        events = parseEvents(records);
        Set<String> validKeys = herbs.stream().map(this::keyOf).collect(Collectors.toSet());
        comparisonKeys.removeIf(key -> !validKeys.contains(key));
        if (comparisonKeys.isEmpty()) herbs.stream().limit(3).map(this::keyOf).forEach(comparisonKeys::add);
        herb.removeAllItems();
        for (Medicinal medicinal : herbs) herb.addItem(medicinal.name());
        if (herbs.stream().noneMatch(item -> item.name().equals(selected))) selected = herbs.isEmpty() ? "" : herbs.get(0).name();
        if (!selected.isEmpty()) herb.setSelectedItem(selected);
        updatingData = false;
        updateSelectorModel();
        updateOverview();
        updateCompareArea();
        restartAnimation();
    }

    private List<StockEvent> parseEvents(List<Object[]> records) {
        if (records == null || records.isEmpty()) return List.of();
        List<StockEvent> parsed = new ArrayList<>();
        for (int index = 0; index < records.size(); index++) {
            Object[] row = records.get(index);
            if (row == null || row.length < 7) continue;
            String name = text(row[1]);
            if (name.isEmpty()) continue;
            parsed.add(new StockEvent(
                    text(row[0]),
                    name,
                    number(row[2]),
                    number(row[3]),
                    text(row[4]),
                    text(row[5]),
                    parseTime(row[6], index)
            ));
        }
        parsed.sort(Comparator.comparing(StockEvent::time));
        return List.copyOf(parsed);
    }

    private static LocalDateTime parseTime(Object value, int fallbackIndex) {
        String raw = text(value);
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof java.sql.Date date) return date.toLocalDate().atStartOfDay();
        if (raw.isEmpty()) return LocalDateTime.of(2000, 1, 1, 0, 0).plusSeconds(fallbackIndex);
        try { return LocalDateTime.parse(raw); } catch (DateTimeParseException ignored) { }
        try { return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)); } catch (DateTimeParseException ignored) { }
        try { return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)); } catch (DateTimeParseException ignored) { }
        try { return LocalDate.parse(raw).atStartOfDay(); } catch (DateTimeParseException ignored) { }
        return LocalDateTime.of(2000, 1, 1, 0, 0).plusSeconds(fallbackIndex);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return (int) Math.round(Double.parseDouble(text(value))); } catch (NumberFormatException ignored) { return 0; }
    }

    private void setPeriod(String value) {
        if (!PERIOD_7.equals(value) && !PERIOD_30.equals(value) && !PERIOD_ALL.equals(value)) return;
        period = value;
        updatingData = true;
        periodBox.setSelectedItem(value);
        updatingData = false;
        updatePeriodButtons();
        updateOverview();
        updateCompareArea();
        restartAnimation();
    }

    private void updatePeriodButtons() {
        for (Map.Entry<String, JButton> entry : periodButtons.entrySet()) {
            boolean active = entry.getKey().equals(period);
            JButton button = entry.getValue();
            button.setBackground(active ? Theme.GREEN : Theme.SURFACE);
            button.setForeground(active ? Color.WHITE : Theme.GREEN);
            button.repaint();
        }
    }

    private void updateOverview() {
        overviewCards.removeAll();
        long low = herbs.stream().filter(Medicinal::lowStock).count();
        long good = herbs.stream().filter(item -> item.stock() >= item.threshold()).count();
        overviewCards.add(overviewCard("药材总数", String.valueOf(herbs.size()), "种在册药材"));
        overviewCards.add(overviewCard("库存达标", String.valueOf(good), period + "内当前达标"));
        overviewCards.add(overviewCard("库存预警", String.valueOf(low), "种需要及时补货"));
        overviewCards.add(overviewCard("对比药材", comparisonKeys.size() + "/" + MAX_COMPARE, "底部选择列表"));
        overviewCards.revalidate();
        overviewCards.repaint();
    }

    private JPanel overviewCard(String label, String value, String hint) {
        JPanel card = Theme.card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 18, 15, 16));
        card.add(Theme.label(label, 12, Theme.MUTED));
        card.add(Box.createVerticalStrut(8));
        JLabel number = Theme.label(value, 28, Theme.GREEN);
        number.setFont(number.getFont().deriveFont(Font.BOLD));
        card.add(number);
        card.add(Box.createVerticalStrut(4));
        card.add(Theme.label(hint, 11, Theme.MUTED));
        return card;
    }

    private void updateSelectorModel() {
        String keyword = selectorSearch.getText().trim().toLowerCase(Locale.ROOT);
        selectorModel.clear();
        herbs.stream()
                .filter(item -> keyword.isEmpty() || item.name().toLowerCase(Locale.ROOT).contains(keyword) || item.code().toLowerCase(Locale.ROOT).contains(keyword))
                .sorted(Comparator.comparingDouble(this::coverage).reversed())
                .limit(10)
                .forEach(selectorModel::addElement);
        selector.revalidate();
        selector.repaint();
    }

    private void useSuggested() {
        comparisonKeys.clear();
        herbs.stream().sorted(Comparator.comparingDouble(this::coverage).reversed()).limit(3).map(this::keyOf).forEach(comparisonKeys::add);
        updateOverview();
        updateCompareArea();
        restartAnimation();
    }

    private void toggleComparison(Medicinal medicinal) {
        String key = keyOf(medicinal);
        if (comparisonKeys.contains(key)) comparisonKeys.remove(key);
        else if (comparisonKeys.size() < MAX_COMPARE) comparisonKeys.add(key);
        updateOverview();
        updateCompareArea();
        restartAnimation();
    }

    private void updateCompareArea() {
        tags.removeAll();
        for (Medicinal medicinal : compareMedicinals()) {
            JButton tag = Theme.button(medicinal.name() + "  ×", false);
            tag.setBorder(new EmptyBorder(6, 10, 6, 10));
            tag.addActionListener(event -> {
                comparisonKeys.remove(keyOf(medicinal));
                updateOverview();
                updateCompareArea();
                restartAnimation();
            });
            tags.add(tag);
        }
        if (comparisonKeys.isEmpty()) tags.add(Theme.label("当前还没有加入对比的药材，请从左侧选择。", 12, Theme.MUTED));
        Theme.rows(compareTable, compareRows().stream().map(row -> new Object[]{row.name, row.code, row.current, row.threshold, formatPercent(row.changePercent), formatPercent(row.coverage), row.eventCount}).toList());
        tags.revalidate();
        tags.repaint();
        compareTable.revalidate();
        compareTable.repaint();
        compareCanvas.revalidate();
        compareCanvas.repaint();
    }

    private List<Medicinal> compareMedicinals() {
        return herbs.stream().filter(item -> comparisonKeys.contains(keyOf(item))).toList();
    }

    private List<CompareDatum> compareRows() {
        List<CompareDatum> rows = new ArrayList<>();
        for (Medicinal medicinal : compareMedicinals()) {
            List<StockEvent> history = periodEvents(medicinal.name());
            int first = history.isEmpty() ? medicinal.stock() : history.get(0).remaining;
            int last = history.isEmpty() ? medicinal.stock() : history.get(history.size() - 1).remaining;
            double change = first == 0 ? (last == 0 ? 0d : 100d) : (last - first) * 100d / first;
            rows.add(new CompareDatum(medicinal, last, medicinal.threshold(), change, coverage(medicinal), history.size()));
        }
        return rows;
    }

    private double coverage(Medicinal medicinal) {
        return medicinal.stock() * 100d / Math.max(1, baseline);
    }

    private static String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private String keyOf(Medicinal medicinal) {
        return medicinal.code() + "|" + medicinal.name();
    }

    private Medicinal findSelected() {
        return herbs.stream().filter(item -> item.name().equals(selected)).findFirst().orElse(null);
    }

    private List<StockEvent> periodEvents(String herbName) {
        List<StockEvent> all = events.stream().filter(item -> item.herbName.equals(herbName)).sorted(Comparator.comparing(StockEvent::time)).toList();
        if (all.isEmpty() || PERIOD_ALL.equals(period)) return all;
        LocalDateTime end = all.get(all.size() - 1).time;
        long days = PERIOD_7.equals(period) ? 7 : 30;
        LocalDateTime cutoff = end.minusDays(days);
        List<StockEvent> filtered = all.stream().filter(item -> !item.time.isBefore(cutoff)).toList();
        return filtered.isEmpty() ? List.of(all.get(all.size() - 1)) : filtered;
    }

    private List<TrendPoint> trendPoints() {
        Medicinal medicinal = findSelected();
        if (medicinal == null) return List.of();
        List<StockEvent> filtered = periodEvents(medicinal.name());
        if (filtered.isEmpty()) return List.of(new TrendPoint(LocalDateTime.now(), medicinal.stock(), 0, "当前库存"));
        List<TrendPoint> points = filtered.stream().map(item -> new TrendPoint(item.time, item.remaining, item.delta, item.note)).toList();
        return points;
    }

    private List<BarDatum> barData() {
        List<Medicinal> sorted = herbs.stream().sorted(Comparator.comparingInt(Medicinal::stock).reversed()).toList();
        int count = Math.max(1, (int) Math.ceil(sorted.size() * range.getValue() / 100d));
        return sorted.subList(0, Math.min(count, sorted.size())).stream().map(item -> new BarDatum(item, modeValue(item.stock()), item.stock() >= item.threshold())).toList();
    }

    private double modeValue(int value) {
        return MODE_PERCENT.equals(mode.getSelectedItem()) ? value * 100d / Math.max(1, baseline) : value;
    }

    private double modeTarget() {
        return MODE_PERCENT.equals(mode.getSelectedItem()) ? 100d : baseline;
    }

    private void restartAnimation() {
        animationProgress = 0d;
        animation.restart();
        repaintCanvases();
    }

    private void repaintCanvases() {
        for (PlotCanvas canvas : canvases) {
            canvas.revalidate();
            canvas.repaint();
        }
    }

    private static void styleControl(JComponent component, int width) {
        component.setPreferredSize(new Dimension(width, 34));
        component.setFont(Theme.BODY.deriveFont(12f));
        component.setBackground(Theme.SURFACE);
        component.setForeground(Theme.INK);
        component.setBorder(new ThemeControlBorder());
    }

    private abstract class PlotCanvas extends JPanel {
        private int hoverIndex = -1;

        private PlotCanvas(int height) {
            setOpaque(false);
            setPreferredSize(new Dimension(900, height));
            setToolTipText("");
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent event) {
                    int next = hoverIndexAt(event.getX(), event.getY());
                    if (next != hoverIndex) {
                        hoverIndex = next;
                        repaint();
                    }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent event) {
                    hoverIndex = -1;
                    repaint();
                }
            });
        }

        protected int hoverIndex() { return hoverIndex; }
        protected abstract int hoverIndexAt(int x, int y);
        protected abstract String tooltipAt(int index);

        @Override public String getToolTipText(MouseEvent event) {
            int index = hoverIndexAt(event.getX(), event.getY());
            return index < 0 ? null : tooltipAt(index);
        }

        protected Graphics2D setup(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            return g;
        }

        protected void chartFrame(Graphics2D g, int left, int top, int right, int bottom, double max, String unit) {
            g.setColor(Theme.LINE);
            g.setStroke(new BasicStroke(1f));
            for (int i = 0; i <= 4; i++) {
                int y = bottom - (bottom - top) * i / 4;
                g.setColor(i == 0 ? Theme.MUTED : new Color(222, 231, 223));
                g.drawLine(left, y, right, y);
                g.setColor(Theme.MUTED);
                String label = compactNumber(max * i / 4d) + unit;
                g.setFont(Theme.BODY.deriveFont(10f));
                g.drawString(label, 5, y + 4);
            }
            g.setColor(Theme.MUTED);
            g.drawLine(left, top, left, bottom);
            g.drawLine(left, bottom, right, bottom);
        }

        protected void drawLegend(Graphics2D g, int x, int y, Legend... legends) {
            int cursor = x;
            g.setFont(Theme.BODY.deriveFont(11f));
            for (Legend legend : legends) {
                g.setColor(legend.color);
                g.fillRoundRect(cursor, y - 8, 10, 10, 4, 4);
                g.setColor(Theme.MUTED);
                g.drawString(legend.label, cursor + 15, y + 1);
                cursor += 15 + g.getFontMetrics().stringWidth(legend.label) + 18;
            }
        }

        protected void drawBaseline(Graphics2D g, int left, int right, int bottom, int top, double max) {
            double target = modeTarget();
            if (target <= 0 || target > max * 1.25d) return;
            int y = bottom - (int) Math.round((target / max) * (bottom - top));
            g.setColor(Theme.ACCENT);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1f, new float[]{6f, 4f}, 0));
            g.drawLine(left, y, right, y);
            g.setStroke(new BasicStroke(1f));
            g.setFont(Theme.BODY.deriveFont(10f));
            g.drawString("基准 " + compactNumber(target) + (MODE_PERCENT.equals(mode.getSelectedItem()) ? "%" : "克"), left + 5, Math.max(top + 12, y - 5));
        }

        protected void drawHover(Graphics2D g, int x, int top, int bottom, String text) {
            if (text == null || text.isEmpty()) return;
            g.setColor(new Color(30, 85, 65, 38));
            g.drawLine(x, top, x, bottom);
            FontMetrics metrics = g.getFontMetrics(Theme.BODY.deriveFont(11f));
            String[] lines = text.split("\\n");
            int width = 20;
            for (String line : lines) width = Math.max(width, metrics.stringWidth(line) + 20);
            int height = 14 + lines.length * 16;
            int boxX = Math.min(Math.max(4, x + 10), Math.max(4, getWidth() - width - 4));
            int boxY = Math.max(top + 4, Math.min(bottom - height, top + 18));
            g.setColor(new Color(255, 255, 255, 245));
            g.fillRoundRect(boxX, boxY, width, height, 10, 10);
            g.setColor(Theme.GREEN);
            g.drawRoundRect(boxX, boxY, width, height, 10, 10);
            g.setColor(Theme.INK);
            g.setFont(Theme.BODY.deriveFont(11f));
            for (int i = 0; i < lines.length; i++) g.drawString(lines[i], boxX + 10, boxY + 17 + i * 16);
        }

        protected String compactNumber(double value) {
            if (Math.abs(value) >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000d);
            if (Math.abs(value) >= 10_000) return String.format(Locale.ROOT, "%.1f万", value / 10_000d);
            if (Math.abs(value) >= 1000) return String.format(Locale.ROOT, "%.1fk", value / 1000d);
            if (Math.abs(value - Math.rint(value)) < 0.01) return String.valueOf((long) Math.rint(value));
            return String.format(Locale.ROOT, "%.1f", value);
        }
    }

    private final class LineCanvas extends PlotCanvas {
        private LineCanvas() { super(300); }

        @Override public Dimension getPreferredSize() {
            return new Dimension(Math.max(820, trendPoints().size() * 68 + 120), 300);
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = setup(graphics);
            int left = 62, top = 42, right = getWidth() - 24, bottom = getHeight() - 52;
            List<TrendPoint> points = trendPoints();
            double max = Math.max(1d, points.stream().mapToDouble(point -> point.remaining).max().orElse(1d));
            max = Math.max(max, modeTarget());
            chartFrame(g, left, top, right, bottom, max, "克");
            drawLegend(g, left, 22, new Legend("库存结余", Theme.GREEN), new Legend("库存基准", Theme.ACCENT));
            drawBaseline(g, left, right, bottom, top, max);
            if (!points.isEmpty()) {
                int visibleStart = Math.max(0, points.size() - Math.max(1, (int) Math.ceil(points.size() * range.getValue() / 100d)));
                List<TrendPoint> visible = points.subList(visibleStart, points.size());
                drawTrendSeries(g, visible, left, right, top, bottom, max);
            } else {
                drawEmpty(g, "当前没有可绘制的库存流水");
            }
            g.dispose();
        }

        private void drawTrendSeries(Graphics2D g, List<TrendPoint> points, int left, int right, int top, int bottom, double max) {
            if (points.isEmpty()) return;
            double phase = phase(0.02, 0.8);
            int completed = Math.min(points.size() - 1, (int) Math.floor((points.size() - 1) * phase));
            double partial = (points.size() - 1) * phase - completed;
            Point previous = null;
            for (int index = 0; index <= completed && index < points.size(); index++) {
                Point current = trendPoint(points, index, left, right, top, bottom, max);
                if (previous != null) drawLineSegment(g, previous, current, Theme.GREEN);
                drawNode(g, current.x, current.y, Theme.ACCENT, index < completed || phase >= 1d);
                previous = current;
            }
            if (completed < points.size() - 1 && previous != null && partial > 0) {
                Point next = trendPoint(points, completed + 1, left, right, top, bottom, max);
                Point partialPoint = new Point((int) Math.round(previous.x + (next.x - previous.x) * partial), (int) Math.round(previous.y + (next.y - previous.y) * partial));
                drawLineSegment(g, previous, partialPoint, Theme.GREEN);
                drawNode(g, partialPoint.x, partialPoint.y, Theme.ACCENT, false);
            }
            int labelStep = Math.max(1, (int) Math.ceil(points.size() / Math.max(1d, (right - left) / 78d)));
            g.setFont(Theme.BODY.deriveFont(10f));
            for (int index = 0; index < points.size(); index += labelStep) {
                Point point = trendPoint(points, index, left, right, top, bottom, max);
                String label = points.get(index).time.format(DISPLAY_TIME);
                g.setColor(Theme.MUTED);
                g.drawString(label, point.x - g.getFontMetrics().stringWidth(label) / 2, bottom + 20);
            }
            int hover = hoverIndex();
            if (hover >= 0 && hover < points.size()) {
                Point point = trendPoint(points, hover, left, right, top, bottom, max);
                drawNode(g, point.x, point.y, Theme.GREEN, true);
                drawHover(g, point.x, top, bottom, trendTooltip(points.get(hover)));
            }
        }

        private Point trendPoint(List<TrendPoint> points, int index, int left, int right, int top, int bottom, double max) {
            int x = left + (int) Math.round(index * (right - left) / (double) Math.max(1, points.size() - 1));
            int y = bottom - (int) Math.round(points.get(index).remaining / max * (bottom - top));
            return new Point(x, y);
        }

        private String trendTooltip(TrendPoint point) {
            return "药材：" + selected + "\n时间：" + point.time.format(DISPLAY_TIME) + "\n库存结余：" + point.remaining + " 克\n变动：" + (point.delta >= 0 ? "+" : "") + point.delta + " 克";
        }

        @Override protected int hoverIndexAt(int x, int y) {
            List<TrendPoint> points = trendPoints();
            if (points.isEmpty()) return -1;
            int left = 62, right = getWidth() - 24;
            int visibleStart = Math.max(0, points.size() - Math.max(1, (int) Math.ceil(points.size() * range.getValue() / 100d)));
            int count = points.size() - visibleStart;
            if (x < left - 15 || x > right + 15) return -1;
            int index = (int) Math.round((x - left) * (count - 1d) / Math.max(1, right - left));
            return Math.max(0, Math.min(count - 1, index));
        }

        @Override protected String tooltipAt(int index) {
            List<TrendPoint> points = trendPoints();
            if (points.isEmpty()) return "暂无库存流水";
            int visibleStart = Math.max(0, points.size() - Math.max(1, (int) Math.ceil(points.size() * range.getValue() / 100d)));
            return trendTooltip(points.get(Math.min(points.size() - 1, visibleStart + index)));
        }
    }

    private final class BarCanvas extends PlotCanvas {
        private BarCanvas() { super(320); }

        @Override public Dimension getPreferredSize() {
            return new Dimension(Math.max(820, barData().size() * 86 + 110), 320);
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = setup(graphics);
            int left = 62, top = 52, right = getWidth() - 24, bottom = getHeight() - 62;
            List<BarDatum> data = barData();
            double max = data.stream().mapToDouble(item -> item.value).max().orElse(1d);
            max = Math.max(max, modeTarget());
            max = niceMax(max);
            chartFrame(g, left, top, right, bottom, max, MODE_PERCENT.equals(mode.getSelectedItem()) ? "%" : "克");
            drawLegend(g, left, 25, new Legend("达标", Theme.GREEN), new Legend("需补货", Theme.ACCENT));
            drawBaseline(g, left, right, bottom, top, max);
            if (data.isEmpty()) {
                drawEmpty(g, "暂无药材数据");
                g.dispose();
                return;
            }
            double phase = phase(0.08, 0.82);
            int slot = Math.max(36, (right - left) / Math.max(1, data.size()));
            int barWidth = Math.max(12, Math.min(48, slot * 2 / 3));
            g.setFont(Theme.BODY.deriveFont(10f));
            for (int index = 0; index < data.size(); index++) {
                BarDatum datum = data.get(index);
                double itemPhase = Math.max(0d, Math.min(1d, phase * 1.25d - index * 0.035d));
                int x = left + slot * index + slot / 2;
                int fullHeight = (int) Math.round(datum.value / max * (bottom - top));
                int height = Math.max(2, (int) Math.round(fullHeight * itemPhase));
                int y = bottom - height;
                Paint fill = new GradientPaint(0, y, datum.good ? new Color(107, 175, 125) : new Color(213, 169, 100), 0, bottom, datum.good ? Theme.GREEN : new Color(167, 112, 54));
                g.setPaint(fill);
                g.fill(new RoundRectangle2D.Double(x - barWidth / 2d, y, barWidth, height, 10, 10));
                if (itemPhase >= 1d && range.getValue() <= 45) {
                    g.setColor(Theme.INK);
                    String value = formatModeValue(datum.value);
                    g.drawString(value, x - g.getFontMetrics().stringWidth(value) / 2, Math.max(top + 12, y - 5));
                }
                g.setColor(Theme.MUTED);
                String label = shorten(datum.medicinal.name(), 6);
                g.drawString(label, x - g.getFontMetrics().stringWidth(label) / 2, bottom + 21);
                g.setColor(Theme.MUTED);
                String code = shorten(datum.medicinal.code(), 7);
                g.drawString(code, x - g.getFontMetrics().stringWidth(code) / 2, bottom + 36);
            }
            int hover = hoverIndex();
            if (hover >= 0 && hover < data.size()) {
                int x = left + slot * hover + slot / 2;
                int height = (int) Math.round(data.get(hover).value / max * (bottom - top) * phase);
                drawHover(g, x, top, bottom, barTooltip(data.get(hover)));
                g.setColor(Theme.GREEN);
                g.drawRoundRect(x - barWidth / 2 - 2, bottom - height - 2, barWidth + 4, height + 2, 12, 12);
            }
            g.dispose();
        }

        private String formatModeValue(double value) {
            return MODE_PERCENT.equals(mode.getSelectedItem()) ? formatPercent(value) : compactNumber(value) + "克";
        }

        private String barTooltip(BarDatum datum) {
            return "药材：" + datum.medicinal.name() + "\n编号：" + datum.medicinal.code() + "\n当前库存：" + datum.medicinal.stock() + " 克\n基准完成率：" + formatPercent(coverage(datum.medicinal)) + "\n状态：" + (datum.good ? "库存达标" : "低于预警阈值");
        }

        @Override protected int hoverIndexAt(int x, int y) {
            List<BarDatum> data = barData();
            if (data.isEmpty()) return -1;
            int left = 62, right = getWidth() - 24;
            int slot = Math.max(36, (right - left) / Math.max(1, data.size()));
            int index = Math.round((x - left - slot / 2f) / slot);
            return x < left - slot / 2 || x > right + slot / 2 ? -1 : Math.max(0, Math.min(data.size() - 1, index));
        }

        @Override protected String tooltipAt(int index) {
            List<BarDatum> data = barData();
            return index < 0 || index >= data.size() ? null : barTooltip(data.get(index));
        }
    }

    private final class DistributionCanvas extends PlotCanvas {
        private final boolean coverageDistribution;

        private DistributionCanvas(boolean coverageDistribution) {
            super(188);
            this.coverageDistribution = coverageDistribution;
        }

        @Override public Dimension getPreferredSize() { return new Dimension(760, 188); }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = setup(graphics);
            List<Segment> segments = segments();
            int left = 34, right = getWidth() - 28, top = 56, barHeight = 38;
            int total = Math.max(1, herbs.size());
            double phase = phase(0.2, 0.76);
            int cursor = left;
            for (Segment segment : segments) {
                int fullWidth = (int) Math.round((right - left) * segment.value / total);
                int width = (int) Math.round(fullWidth * phase);
                Paint paint = new GradientPaint(0, top, segment.color.brighter(), 0, top + barHeight, segment.color.darker());
                g.setPaint(paint);
                g.fillRoundRect(cursor, top, Math.max(0, width), barHeight, 12, 12);
                if (width > 42) {
                    g.setColor(Color.WHITE);
                    g.setFont(Theme.BODY.deriveFont(Font.BOLD, 11f));
                    String label = segment.label + " " + segment.value;
                    g.drawString(label, cursor + 8, top + 24);
                }
                cursor += fullWidth;
            }
            drawLegend(g, left, 30, segments.stream().map(segment -> new Legend(segment.label, segment.color)).toArray(Legend[]::new));
            g.setColor(Theme.MUTED);
            g.setFont(Theme.BODY.deriveFont(11f));
            g.drawString("共 " + herbs.size() + " 种药材", left, top + barHeight + 28);
            g.drawString("悬停查看分布详情", right - 130, top + barHeight + 28);
            if (hoverIndex() >= 0) drawHover(g, Math.min(right - 4, left + 80), top, top + barHeight, distributionTooltip(segments));
            g.dispose();
        }

        private List<Segment> segments() {
            if (!coverageDistribution) {
                int good = (int) herbs.stream().filter(item -> item.stock() >= item.threshold()).count();
                return List.of(new Segment("库存达标", good, Theme.GREEN), new Segment("低库存", herbs.size() - good, Theme.ACCENT));
            }
            int good = (int) herbs.stream().filter(item -> coverage(item) >= 100d).count();
            int middle = (int) herbs.stream().filter(item -> coverage(item) >= 50d && coverage(item) < 100d).count();
            return List.of(new Segment("达标", good, Theme.GREEN), new Segment("接近", middle, new Color(123, 159, 107)), new Segment("偏低", herbs.size() - good - middle, Theme.ACCENT));
        }

        private String distributionTooltip(List<Segment> segments) {
            return segments.stream().map(item -> item.label + "：" + item.value + " 种").collect(Collectors.joining("\n"));
        }

        @Override protected int hoverIndexAt(int x, int y) {
            return y >= 45 && y <= 115 && x >= 34 && x <= getWidth() - 28 ? 0 : -1;
        }

        @Override protected String tooltipAt(int index) { return distributionTooltip(segments()); }
    }

    private final class CompareCanvas extends PlotCanvas {
        private CompareCanvas() { super(360); }

        @Override public Dimension getPreferredSize() { return new Dimension(Math.max(760, compareRows().size() * 130 + 120), 360); }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = setup(graphics);
            int left = 62, top = 58, right = getWidth() - 52, bottom = getHeight() - 64;
            List<CompareDatum> rows = compareRows();
            drawLegend(g, left, 27, new Legend("当前库存", Theme.GREEN), new Legend("预警阈值", Theme.ACCENT), new Legend("期间变化", new Color(104, 135, 176)));
            if (rows.isEmpty()) {
                drawAxesOnly(g, left, top, right, bottom);
                drawEmpty(g, "请选择药材加入对比");
                g.dispose();
                return;
            }
            double max = rows.stream().mapToDouble(item -> MODE_PERCENT.equals(mode.getSelectedItem()) ? Math.max(modeValue(item.current), modeValue(item.threshold)) : Math.max(item.current, item.threshold)).max().orElse(1d);
            max = niceMax(Math.max(1d, max));
            chartFrame(g, left, top, right, bottom, max, MODE_PERCENT.equals(mode.getSelectedItem()) ? "%" : "克");
            drawRightAxis(g, right, top, bottom, rows);
            double phase = phase(0.18, 0.84);
            int slot = Math.max(80, (right - left) / Math.max(1, rows.size()));
            int barWidth = Math.max(12, Math.min(24, slot / 5));
            List<Point> linePoints = new ArrayList<>();
            for (int index = 0; index < rows.size(); index++) {
                CompareDatum datum = rows.get(index);
                int x = left + slot * index + slot / 2;
                double current = modeValue(datum.current);
                double threshold = modeValue(datum.threshold);
                double itemPhase = Math.max(0d, Math.min(1d, phase * 1.3d - index * 0.05d));
                drawCompareBar(g, x - barWidth - 4, current, max, bottom, top, barWidth, itemPhase, Theme.GREEN);
                drawCompareBar(g, x + 4, threshold, max, bottom, top, barWidth, itemPhase, Theme.ACCENT);
                int y = changeY(datum.changePercent, rows, top, bottom);
                linePoints.add(new Point(x, y));
                g.setColor(Theme.MUTED);
                g.setFont(Theme.BODY.deriveFont(10f));
                String name = shorten(datum.name, 6);
                g.drawString(name, x - g.getFontMetrics().stringWidth(name) / 2, bottom + 21);
                String code = shorten(datum.code, 8);
                g.drawString(code, x - g.getFontMetrics().stringWidth(code) / 2, bottom + 36);
            }
            drawCompareLine(g, linePoints, phase);
            int hover = hoverIndex();
            if (hover >= 0 && hover < rows.size()) {
                Point point = linePoints.get(hover);
                drawHover(g, point.x, top, bottom, compareTooltip(rows.get(hover)));
                g.setColor(Theme.GREEN);
                g.fillOval(point.x - 6, point.y - 6, 12, 12);
            }
            g.dispose();
        }

        private void drawCompareBar(Graphics2D g, int x, double value, double max, int bottom, int top, int width, double phase, Color color) {
            int height = Math.max(2, (int) Math.round(value / max * (bottom - top) * phase));
            int y = bottom - height;
            g.setPaint(new GradientPaint(0, y, color.brighter(), 0, bottom, color.darker()));
            g.fill(new RoundRectangle2D.Double(x, y, width, height, 9, 9));
        }

        private void drawCompareLine(Graphics2D g, List<Point> points, double phase) {
            if (points.isEmpty()) return;
            int complete = Math.min(points.size() - 1, (int) Math.floor((points.size() - 1) * phase));
            Point previous = null;
            g.setStroke(new BasicStroke(3f));
            for (int index = 0; index <= complete; index++) {
                Point point = points.get(index);
                if (previous != null) {
                    g.setColor(new Color(104, 135, 176));
                    g.drawLine(previous.x, previous.y, point.x, point.y);
                }
                g.setColor(new Color(104, 135, 176));
                g.fillOval(point.x - 5, point.y - 5, 10, 10);
                previous = point;
            }
            g.setStroke(new BasicStroke(1f));
        }

        private void drawRightAxis(Graphics2D g, int right, int top, int bottom, List<CompareDatum> rows) {
            double scale = changeScale(rows);
            g.setColor(new Color(104, 135, 176));
            g.drawLine(right, top, right, bottom);
            g.setFont(Theme.BODY.deriveFont(10f));
            g.drawString(formatPercent(scale), right - 42, top + 4);
            g.drawString("0.0%", right - 35, (top + bottom) / 2 + 4);
            g.drawString(formatPercent(-scale), right - 48, bottom);
        }

        private void drawAxesOnly(Graphics2D g, int left, int top, int right, int bottom) {
            g.setColor(Theme.LINE);
            g.drawLine(left, bottom, right, bottom);
            g.drawLine(left, top, left, bottom);
        }

        private int changeY(double change, List<CompareDatum> rows, int top, int bottom) {
            double scale = changeScale(rows);
            return (top + bottom) / 2 - (int) Math.round(change / scale * (bottom - top) / 2d);
        }

        private double changeScale(List<CompareDatum> rows) {
            double max = rows.stream().mapToDouble(item -> Math.abs(item.changePercent)).max().orElse(1d);
            return Math.max(10d, Math.ceil(max / 10d) * 10d);
        }

        private String compareTooltip(CompareDatum item) {
            return "药材：" + item.name + "\n编号：" + item.code + "\n当前库存：" + item.current + " 克\n预警阈值：" + item.threshold + " 克\n期间变化：" + formatPercent(item.changePercent) + "\n达标率：" + formatPercent(item.coverage);
        }

        @Override protected int hoverIndexAt(int x, int y) {
            List<CompareDatum> rows = compareRows();
            if (rows.isEmpty()) return -1;
            int left = 62, right = getWidth() - 52;
            int slot = Math.max(80, (right - left) / Math.max(1, rows.size()));
            int index = Math.round((x - left - slot / 2f) / slot);
            return x < left - slot / 2 || x > right + slot / 2 ? -1 : Math.max(0, Math.min(rows.size() - 1, index));
        }

        @Override protected String tooltipAt(int index) {
            List<CompareDatum> rows = compareRows();
            return index < 0 || index >= rows.size() ? null : compareTooltip(rows.get(index));
        }
    }

    private double phase(double start, double end) {
        if (animationProgress <= start) return 0d;
        return Math.min(1d, (animationProgress - start) / Math.max(0.01d, end - start));
    }

    private static double niceMax(double value) {
        if (value <= 10) return 10;
        double power = Math.pow(10, Math.floor(Math.log10(value)));
        double normalized = value / power;
        double nice = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
        return nice * power;
    }

    private static void drawLineSegment(Graphics2D g, Point from, Point to, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(from.x, from.y, to.x, to.y);
        g.setStroke(new BasicStroke(1f));
    }

    private static void drawNode(Graphics2D g, int x, int y, Color color, boolean emphasized) {
        int size = emphasized ? 13 : 9;
        g.setColor(new Color(255, 255, 255, 220));
        g.fillOval(x - size / 2 - 2, y - size / 2 - 2, size + 4, size + 4);
        g.setColor(color);
        g.fillOval(x - size / 2, y - size / 2, size, size);
    }

    private void drawEmpty(Graphics2D g, String text) {
        g.setColor(Theme.MUTED);
        g.setFont(Theme.BODY.deriveFont(13f));
        g.drawString(text, Math.max(80, getWidth() / 2 - 90), getHeight() / 2);
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static final class StockEvent {
        private final String code;
        private final String herbName;
        private final int delta;
        private final int remaining;
        private final String operator;
        private final String note;
        private final LocalDateTime time;

        private StockEvent(String code, String herbName, int delta, int remaining, String operator, String note, LocalDateTime time) {
            this.code = code;
            this.herbName = herbName;
            this.delta = delta;
            this.remaining = remaining;
            this.operator = operator;
            this.note = note;
            this.time = time;
        }

        private LocalDateTime time() { return time; }
    }

    private static final class TrendPoint {
        private final LocalDateTime time;
        private final int remaining;
        private final int delta;
        private final String note;

        private TrendPoint(LocalDateTime time, int remaining, int delta, String note) {
            this.time = time;
            this.remaining = remaining;
            this.delta = delta;
            this.note = note;
        }
    }

    private static final class BarDatum {
        private final Medicinal medicinal;
        private final double value;
        private final boolean good;

        private BarDatum(Medicinal medicinal, double value, boolean good) {
            this.medicinal = medicinal;
            this.value = value;
            this.good = good;
        }
    }

    private static final class Segment {
        private final String label;
        private final int value;
        private final Color color;

        private Segment(String label, int value, Color color) {
            this.label = label;
            this.value = Math.max(0, value);
            this.color = color;
        }
    }

    private static final class CompareDatum {
        private final String name;
        private final String code;
        private final int current;
        private final int threshold;
        private final double changePercent;
        private final double coverage;
        private final int eventCount;

        private CompareDatum(Medicinal medicinal, int current, int threshold, double changePercent, double coverage, int eventCount) {
            this.name = medicinal.name();
            this.code = medicinal.code();
            this.current = current;
            this.threshold = threshold;
            this.changePercent = changePercent;
            this.coverage = coverage;
            this.eventCount = eventCount;
        }
    }

    private static final class Legend {
        private final String label;
        private final Color color;

        private Legend(String label, Color color) {
            this.label = label;
            this.color = color;
        }
    }

    private final class HerbCellRenderer implements ListCellRenderer<Medicinal> {
        @Override public Component getListCellRendererComponent(JList<? extends Medicinal> list, Medicinal value, int index, boolean selectedCell, boolean focus) {
            JPanel panel = new JPanel(new BorderLayout(8, 4));
            panel.setOpaque(true);
            panel.setBorder(new EmptyBorder(8, 10, 8, 8));
            panel.setBackground(selectedCell ? Theme.GREEN_TINT : Theme.SURFACE);
            JPanel namePanel = Theme.stack(2);
            JLabel name = Theme.label(value.name(), 13, Theme.INK);
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            namePanel.add(name);
            namePanel.add(Theme.label(value.code(), 11, Theme.MUTED));
            panel.add(namePanel, BorderLayout.WEST);
            JPanel stats = Theme.stack(2);
            JLabel state = Theme.label(value.stock() >= value.threshold() ? "库存达标" : "低库存", 11, value.stock() >= value.threshold() ? Theme.GREEN : Theme.ACCENT);
            stats.add(state);
            stats.add(Theme.label("基准 " + formatPercent(coverage(value)), 10, Theme.MUTED));
            panel.add(stats, BorderLayout.EAST);
            panel.setToolTipText(comparisonKeys.contains(keyOf(value)) ? "已加入对比，点击移除" : "点击加入对比（最多 5 种）");
            return panel;
        }
    }

    private static final class ResponsiveComparePanel extends JPanel {
        private final JComponent sidebar;
        private final JComponent content;

        private ResponsiveComparePanel(JComponent sidebar, JComponent content) {
            super(null);
            this.sidebar = sidebar;
            this.content = content;
            setOpaque(false);
            add(sidebar);
            add(content);
        }

        @Override public void doLayout() {
            int width = getWidth();
            if (width < 880) {
                int sideHeight = Math.max(300, sidebar.getPreferredSize().height);
                sidebar.setBounds(0, 0, width, sideHeight);
                content.setBounds(0, sideHeight + 14, width, Math.max(420, getHeight() - sideHeight - 14));
            } else {
                int sidebarWidth = Math.min(300, Math.max(250, width / 3));
                sidebar.setBounds(0, 0, sidebarWidth, getHeight());
                content.setBounds(sidebarWidth + 14, 0, Math.max(1, width - sidebarWidth - 14), getHeight());
            }
        }

        @Override public Dimension getPreferredSize() {
            int width = getParent() == null ? 900 : getParent().getWidth();
            if (width > 0 && width < 880) return new Dimension(width, sidebar.getPreferredSize().height + 14 + 520);
            return new Dimension(900, 560);
        }
    }

    private static final class ThemeControlBorder extends javax.swing.border.AbstractBorder {
        @Override public Insets getBorderInsets(Component component) { return new Insets(1, 8, 1, 8); }
        @Override public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(Theme.LINE);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawRoundRect(x, y, width - 1, height - 1, 8, 8);
            g.dispose();
        }
    }

    private static final class ThemeListBorder extends javax.swing.border.AbstractBorder {
        @Override public Insets getBorderInsets(Component component) { return new Insets(1, 1, 1, 1); }
        @Override public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            graphics.setColor(Theme.LINE);
            graphics.drawRoundRect(x, y, width - 1, height - 1, 10, 10);
        }
    }
}
