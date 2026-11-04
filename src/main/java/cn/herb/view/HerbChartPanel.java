package cn.herb.view;

import cn.herb.model.Medicinal;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.List;

/**
 * 药材库存分析图表。图形全部由 Graphics2D 绘制，不依赖前端运行时。
 * 趋势图始终只展示当前选中的一味药材，避免把不同药材混在同一条折线中比较。
 */
public final class HerbChartPanel extends JPanel {
    private static final String DAILY = "日线";
    private static final String WEEKLY = "周线";
    private static final String MONTHLY = "月线";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd");
    private static final int TREND_DAYS = 35;

    private List<Medicinal> herbs = List.of();
    private List<Event> events = List.of();
    private Map<String, List<Event>> eventIndex = Map.of();
    private List<Summary> summaryCache = List.of();
    private String activePeriod = DAILY;
    private boolean loading;

    private final JComboBox<String> periodBox = new JComboBox<>(new String[]{DAILY, WEEKLY, MONTHLY});
    private final JComboBox<Medicinal> herbBox = new JComboBox<>();

    private final TrendCanvas trend = new TrendCanvas();
    private final PredictionCanvas prediction = new PredictionCanvas();
    private final DistributionCanvas recommendation = new DistributionCanvas(false);
    private final DistributionCanvas confidence = new DistributionCanvas(true);

    public HerbChartPanel() {
        super(new BorderLayout(0, 16));
        setOpaque(false);
        setPreferredSize(new Dimension(980, 1320));
        add(header(), BorderLayout.NORTH);
        add(body(), BorderLayout.CENTER);
        configure();
        refresh();
    }

    private JComponent header() {
        JPanel root = new JPanel(new BorderLayout(12, 8));
        root.setOpaque(false);
        JPanel title = Theme.stack(4);
        title.add(Theme.label("药材数据分析仪表盘", 24, Theme.GREEN));
        title.add(Theme.label("单药材库存趋势、基准完成率与库存结构分析", 13, Theme.MUTED));
        root.add(title, BorderLayout.WEST);

        JPanel controls = Theme.row();
        controls.add(Theme.label("查看粒度", 12, Theme.MUTED));
        periodBox.setPreferredSize(new Dimension(82, 34));
        controls.add(periodBox);
        controls.add(Theme.label("查看药材", 12, Theme.MUTED));
        herbBox.setPreferredSize(new Dimension(172, 34));
        herbBox.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                     boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Medicinal m) setText(m.name() + "  ·  " + m.code());
                else setText(value == null ? "暂无药材" : String.valueOf(value));
                return this;
            }
        });
        controls.add(herbBox);
        root.add(controls, BorderLayout.SOUTH);
        return root;
    }

    private JPanel body() {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        Theme.ResponsiveGrid cards = Theme.responsiveGrid(4, 2, 1, 14, 14);
        cards.setAlignmentX(LEFT_ALIGNMENT);
        body.add(cards);
        body.add(Box.createVerticalStrut(18));
        body.add(chartCard("库存结余趋势",
                "单独查看一味药材的变化率；历史日期保持稳定，最后一天跟随实时库存更新", trend));
        body.add(Box.createVerticalStrut(16));
        body.add(chartCard("库存基准完成率",
                "当前库存 / 药材基准 × 100%，绿色表示达到基准，金色表示需要补货", prediction));
        body.add(Box.createVerticalStrut(16));

        Theme.ResponsiveGrid distributions = Theme.responsiveGrid(2, 2, 1, 14, 14);
        distributions.setAlignmentX(LEFT_ALIGNMENT);
        distributions.add(chartCard("库存状态分布", "当前库存达到预警基准的药材比例", recommendation));
        distributions.add(chartCard("库存信心程度分布", "根据流水数量和历史正向变动估算数据稳定度", confidence));
        body.add(distributions);
        return body;
    }

    private JPanel chartCard(String title, String subtitle, Chart canvas) {
        JPanel card = Theme.card();
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        card.setMinimumSize(new Dimension(0, canvas.chartHeight + 82));
        JPanel heading = Theme.stack(3);
        heading.add(Theme.label(title, 17, Theme.GREEN));
        heading.add(Theme.label(subtitle, 12, Theme.MUTED));
        card.add(heading, BorderLayout.NORTH);
        // 图表直接放入卡片，不再嵌套一个只允许横向滚动的 JScrollPane。
        // 这样鼠标停在图上滚轮时会自然交给工作台外层纵向滚动。
        card.add(canvas, BorderLayout.CENTER);
        return card;
    }

    private void configure() {
        periodBox.addActionListener(e -> {
            if (!loading) setPeriod(String.valueOf(periodBox.getSelectedItem()));
        });
        herbBox.addActionListener(e -> {
            if (!loading) {
                trend.invalidateData();
                repaintAllCharts();
            }
        });
    }

    public void setData(List<Medicinal> value, List<Object[]> rows) {
        String previousCode = selectedHerb() == null ? "" : selectedHerb().code();
        herbs = value == null ? List.of() : List.copyOf(value);
        events = parse(rows);
        if (events.isEmpty() && !herbs.isEmpty()) events = demoEvents(herbs);
        eventIndex = indexEvents(events);

        loading = true;
        herbBox.removeAllItems();
        herbs.forEach(herbBox::addItem);
        int selected = -1;
        for (int i = 0; i < herbs.size(); i++) {
            if (Objects.equals(herbs.get(i).code(), previousCode)) {
                selected = i;
                break;
            }
        }
        if (selected < 0 && !herbs.isEmpty()) selected = 0;
        if (selected >= 0) herbBox.setSelectedIndex(selected);
        loading = false;
        refresh();
    }

    private void setPeriod(String period) {
        if (!Set.of(DAILY, WEEKLY, MONTHLY).contains(period)) return;
        activePeriod = period;
        loading = true;
        periodBox.setSelectedItem(period);
        loading = false;
        refresh();
    }

    private void refresh() {
        summaryCache = herbs.stream().map(this::summary)
                .sorted(Comparator.comparingDouble(Summary::prediction).reversed()).toList();
        updateCards();
        trend.invalidateData();
        repaintAllCharts();
    }

    private void updateCards() {
        Container parent = getComponentCount() > 1 && getComponent(1) instanceof Container c ? c : null;
        if (!(parent instanceof JPanel body) || body.getComponentCount() == 0
                || !(body.getComponent(0) instanceof Theme.ResponsiveGrid cards)) return;
        cards.removeAll();
        Medicinal selected = selectedHerb();
        Summary selectedSummary = selected == null ? null : summary(selected);
        long recommended = summaryCache.stream().filter(s -> s.prediction() >= 100).count();
        long high = summaryCache.stream().filter(s -> s.confidence().equals("高")).count();
        cards.add(metric("药材池", String.valueOf(summaryCache.size()), "当前在册药材"));
        cards.add(metric("当前药材", selected == null ? "—" : selected.name(), "趋势图正在查看"));
        cards.add(metric("基准完成率", selectedSummary == null ? "—" : percent(selectedSummary.prediction()),
                recommended + " 种达到库存基准"));
        cards.add(metric("数据稳定度", selectedSummary == null ? "—" : selectedSummary.confidence(),
                high + " 种药材有较充分流水"));
        cards.revalidate();
        cards.repaint();
    }

    private JPanel metric(String label, String value, String hint) {
        JPanel panel = Theme.card();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(16, 18, 15, 16));
        panel.add(Theme.label(label, 12, Theme.MUTED));
        panel.add(Box.createVerticalStrut(7));
        JLabel number = Theme.label(value, value.length() > 6 ? 20 : 28, Theme.GREEN);
        number.setFont(number.getFont().deriveFont(Font.BOLD));
        panel.add(number);
        panel.add(Box.createVerticalStrut(4));
        panel.add(Theme.label(hint, 11, Theme.MUTED));
        return panel;
    }

    private Medicinal selectedHerb() {
        Object value = herbBox.getSelectedItem();
        return value instanceof Medicinal m ? m : null;
    }

    private List<Event> parse(List<Object[]> rows) {
        if (rows == null) return List.of();
        List<Event> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            if (row == null || row.length < 7) continue;
            result.add(new Event(text(row[0]), text(row[1]), number(row[2]), number(row[3]), time(row[6], i)));
        }
        result.sort(Comparator.comparing(Event::time));
        return List.copyOf(result);
    }

    private Map<String, List<Event>> indexEvents(List<Event> source) {
        Map<String, List<Event>> result = new LinkedHashMap<>();
        for (Event event : source) result.computeIfAbsent(event.key(), ignored -> new ArrayList<>()).add(event);
        result.replaceAll((key, value) -> List.copyOf(value));
        return Map.copyOf(result);
    }

    /** 空流水时只在内存中生成演示数据，不写入 stock_log，也不改变真实库存。 */
    private List<Event> demoEvents(List<Medicinal> source) {
        List<Event> result = new ArrayList<>();
        for (Medicinal herb : source) result.addAll(demoEventsFor(herb));
        return List.copyOf(result);
    }

    /** 历史日期由药材编号决定，最后一天使用当前库存，因此刷新库存后只有最后一个点会变化。 */
    private List<Event> demoEventsFor(Medicinal herb) {
        List<Event> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(TREND_DAYS - 1L);
        int balance = demoAnchor(herb);
        for (int day = 0; day < TREND_DAYS - 1; day++) {
            int delta = demoDelta(herb.code(), day);
            balance = Math.max(1, balance + delta);
            result.add(new Event(herb.code(), herb.name(), delta, balance,
                    start.plusDays(day).atTime(12, 0)));
        }
        int liveDelta = herb.stock() - balance;
        result.add(new Event(herb.code(), herb.name(), liveDelta, herb.stock(),
                today.atTime(18, 0)));
        return result;
    }

    private static int demoAnchor(Medicinal herb) {
        int threshold = Math.max(30, herb.threshold());
        int offset = Math.floorMod(Objects.hash(herb.code(), herb.name()), 36);
        return Math.max(1, threshold * 2 + offset);
    }

    private static int demoDelta(String code, int day) {
        int wave = Math.floorMod(Objects.hash(code, day), 9) - 4;
        if (day % 8 == 0) wave += 8;
        if (day % 11 == 0) wave -= 5;
        return wave;
    }

    private Summary summary(Medicinal herb) {
        List<Event> period = periodEvents(herb);
        int positive = (int) period.stream().filter(event -> event.delta() > 0).count();
        double probability = period.isEmpty() ? 50 : positive * 100d / period.size();
        double change = period.isEmpty() ? 0
                : period.stream().mapToInt(Event::delta).sum() * 100d
                / Math.max(1, period.get(0).remaining());
        double prediction = herb.stock() * 100d / Math.max(1, herb.threshold());
        String status = prediction >= 100 ? "达标" : prediction >= 60 ? "观察" : "待补货";
        String confidence = period.size() >= 10 ? "高" : period.size() >= 3 ? "中" : "低";
        return new Summary(herb.code(), herb.name(), herb.code(), change, probability,
                prediction, status, confidence, period.size());
    }

    private List<Event> periodEvents(Medicinal herb) {
        if (herb == null) return List.of();
        List<Event> all = eventIndex.getOrDefault(eventKey(herb), List.of());
        if (all.isEmpty()) return all;
        long days = DAILY.equals(activePeriod) ? 1 : WEEKLY.equals(activePeriod) ? 7 : 30;
        LocalDateTime end = all.get(all.size() - 1).time();
        List<Event> filtered = all.stream()
                .filter(event -> !event.time().isBefore(end.minusDays(days)))
                .toList();
        return filtered.isEmpty() ? List.of(all.get(all.size() - 1)) : filtered;
    }

    private List<TrendPoint> trendPoints() {
        Medicinal herb = selectedHerb();
        if (herb == null) return List.of();
        List<Event> source = eventIndex.getOrDefault(eventKey(herb), List.of());
        if (source.isEmpty()) source = demoEventsFor(herb);
        return aggregate(dailyPoints(herb, source), activePeriod);
    }

    private List<TrendPoint> dailyPoints(Medicinal herb, List<Event> source) {
        Map<LocalDate, List<Event>> byDay = new TreeMap<>();
        for (Event event : source) byDay.computeIfAbsent(event.time().toLocalDate(), ignored -> new ArrayList<>()).add(event);
        if (byDay.isEmpty()) return List.of();

        LocalDate latest = byDay.keySet().stream().max(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate end = latest.isAfter(LocalDate.now()) ? latest : LocalDate.now();
        LocalDate first = end.minusDays(TREND_DAYS - 1L);
        LocalDate earliest = byDay.keySet().stream().min(LocalDate::compareTo).orElse(first);
        if (earliest.isAfter(first)) first = earliest;

        int running = initialBalanceBefore(byDay, first);
        List<TrendPoint> points = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(end); date = date.plusDays(1)) {
            List<Event> eventsOnDay = byDay.getOrDefault(date, List.of());
            int delta = eventsOnDay.stream().mapToInt(Event::delta).sum();
            if (!eventsOnDay.isEmpty()) running = eventsOnDay.get(eventsOnDay.size() - 1).remaining();
            points.add(new TrendPoint(date, running, delta));
        }
        // 最后一个点永远代表当前库存，入库/出库后会立即向上/向下移动。
        if (!points.isEmpty()) {
            TrendPoint last = points.get(points.size() - 1);
            int liveDelta = herb.stock() - (points.size() == 1 ? last.remaining() : points.get(points.size() - 2).remaining());
            points.set(points.size() - 1, new TrendPoint(last.date(), herb.stock(), liveDelta));
        }
        return List.copyOf(points);
    }

    private int initialBalanceBefore(Map<LocalDate, List<Event>> byDay, LocalDate first) {
        int balance = 0;
        boolean found = false;
        for (Map.Entry<LocalDate, List<Event>> entry : byDay.entrySet()) {
            if (!entry.getKey().isBefore(first)) break;
            List<Event> day = entry.getValue();
            if (!day.isEmpty()) {
                balance = day.get(day.size() - 1).remaining();
                found = true;
            }
        }
        if (found) return balance;
        List<Event> firstDay = byDay.get(first);
        if (firstDay != null && !firstDay.isEmpty()) {
            Event event = firstDay.get(0);
            return Math.max(0, event.remaining() - event.delta());
        }
        for (List<Event> day : byDay.values()) if (!day.isEmpty()) return Math.max(0, day.get(0).remaining() - day.get(0).delta());
        return 0;
    }

    private List<TrendPoint> aggregate(List<TrendPoint> daily, String period) {
        if (DAILY.equals(period) || daily.size() <= 1) return daily;
        Map<LocalDate, List<TrendPoint>> buckets = new LinkedHashMap<>();
        for (TrendPoint point : daily) {
            LocalDate bucket = WEEKLY.equals(period)
                    ? point.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    : point.date().withDayOfMonth(1);
            buckets.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(point);
        }
        List<TrendPoint> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<TrendPoint>> entry : buckets.entrySet()) {
            List<TrendPoint> values = entry.getValue();
            TrendPoint last = values.get(values.size() - 1);
            result.add(new TrendPoint(entry.getKey(), last.remaining(), values.stream().mapToInt(TrendPoint::delta).sum()));
        }
        return List.copyOf(result);
    }

    private String eventKey(Event event) { return event.code() + "|" + event.name(); }
    private String eventKey(Medicinal herb) { return herb.code() + "|" + herb.name(); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static int number(Object value) {
        try { return value instanceof Number n ? n.intValue() : Integer.parseInt(text(value)); }
        catch (Exception ignored) { return 0; }
    }
    private static LocalDateTime time(Object value, int fallback) {
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        String text = text(value);
        if (text.isEmpty()) return LocalDate.of(2000, 1, 1).atStartOfDay().plusSeconds(fallback);
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))) {
            try { return LocalDateTime.parse(text, formatter); }
            catch (DateTimeParseException ignored) { }
        }
        try { return LocalDate.parse(text).atStartOfDay(); }
        catch (DateTimeParseException ignored) { return LocalDate.of(2000, 1, 1).atStartOfDay().plusSeconds(fallback); }
    }
    private static String percent(double value) { return String.format(Locale.ROOT, "%.1f%%", value); }
    private static String shortName(String value) { return value.length() > 7 ? value.substring(0, 6) + "…" : value; }

    private abstract class Chart extends JPanel {
        private int hover = -1;
        private final int chartHeight;

        Chart(int height) {
            chartHeight = height;
            setOpaque(false);
            setPreferredSize(new Dimension(900, height));
            setMinimumSize(new Dimension(0, height));
            setToolTipText("");
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent event) {
                    int next = hit(event);
                    if (next != hover) { hover = next; repaint(); }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent event) { hover = -1; repaint(); }
            });
            // 不做图表缩放；滚轮统一推动工作台外层纵向滚动条。
            addMouseWheelListener(event -> {
                JScrollPane outer = outerScrollPane();
                if (outer == null) return;
                JScrollBar bar = outer.getVerticalScrollBar();
                int unit = Math.max(12, bar.getUnitIncrement(Math.max(1, event.getWheelRotation())));
                int amount = event.getWheelRotation() * unit * Math.max(1, event.getScrollAmount());
                bar.setValue(Math.max(0, Math.min(bar.getMaximum() - bar.getVisibleAmount(), bar.getValue() + amount)));
                event.consume();
            });
        }

        private JScrollPane outerScrollPane() {
            Container parent = getParent();
            while (parent != null) {
                if (parent instanceof JScrollPane scroll) return scroll;
                parent = parent.getParent();
            }
            return null;
        }

        void invalidateData() { hover = -1; }
        int hover() { return hover; }
        int hit(MouseEvent event) { return hit(event.getX()); }
        abstract int hit(int x);
        abstract String tip(int index);
        @Override public String getToolTipText(MouseEvent event) {
            int index = hit(event);
            return index < 0 ? null : tip(index);
        }

        Graphics2D graphics(Graphics source) {
            Graphics2D graphics = (Graphics2D) source.create();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            return graphics;
        }

        void axes(Graphics2D g, int left, int top, int right, int bottom, double min, double max, String unit) {
            if (max <= min) max = min + 1;
            double step = (max - min) / 4d;
            int decimals = step < 1 ? 1 : 0;
            g.setFont(Theme.BODY.deriveFont(10f));
            for (int i = 0; i <= 4; i++) {
                double value = min + (max - min) * i / 4d;
                int y = bottom - (bottom - top) * i / 4;
                g.setColor(new Color(220, 230, 222));
                g.drawLine(left, y, right, y);
                g.setColor(Theme.MUTED);
                g.drawString(formatAxis(value, unit, decimals), 5, y + 4);
            }
            if (min < 0 && max > 0) {
                int zeroY = yFor(0, min, max, top, bottom);
                Stroke previous = g.getStroke();
                g.setColor(new Color(104, 132, 113));
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                        1f, new float[]{6f, 4f}, 0f));
                g.drawLine(left, zeroY, right, zeroY);
                g.setStroke(previous);
                g.setColor(Theme.GREEN_DARK);
                g.drawString("0" + unit, 5, zeroY + 4);
            }
            g.setColor(Theme.LINE);
            g.drawLine(left, top, left, bottom);
            g.drawLine(left, bottom, right, bottom);
        }

        private String formatAxis(double value, String unit, int decimals) {
            return String.format(Locale.ROOT, decimals == 0 ? "%.0f%s" : "%.1f%s", value, unit);
        }

        int yFor(double value, double min, double max, int top, int bottom) {
            double ratio = (Math.max(min, Math.min(max, value)) - min) / Math.max(0.0001, max - min);
            return bottom - (int) Math.round(ratio * (bottom - top));
        }

        void point(Graphics2D g, int x, int y, Color color, boolean active) {
            if (active) {
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 42));
                g.fillOval(x - 13, y - 13, 26, 26);
                g.setColor(Color.WHITE);
                g.fillOval(x - 8, y - 8, 16, 16);
                g.setColor(color);
                g.setStroke(new BasicStroke(2f));
                g.drawOval(x - 9, y - 9, 18, 18);
                g.fillOval(x - 4, y - 4, 8, 8);
            } else {
                g.setColor(color);
                g.fillOval(x - 5, y - 5, 10, 10);
            }
        }

        void legend(Graphics2D g, int x, int y, String[] names, Color[] colors) {
            g.setFont(Theme.BODY.deriveFont(11f));
            for (int i = 0; i < names.length; i++) {
                g.setColor(colors[i]);
                g.fillOval(x, y - 8, 10, 10);
                g.setColor(Theme.MUTED);
                g.drawString(names[i], x + 15, y + 1);
                x += g.getFontMetrics().stringWidth(names[i]) + 34;
            }
        }

        void tooltip(Graphics2D g, int x, int top, int bottom, String text) {
            if (text == null) return;
            String[] lines = text.split("\\n");
            g.setFont(Theme.BODY.deriveFont(11f));
            int width = 20;
            for (String line : lines) width = Math.max(width, g.getFontMetrics().stringWidth(line) + 20);
            int height = 14 + lines.length * 16;
            int boxX = Math.min(Math.max(4, x + 10), Math.max(4, getWidth() - width - 4));
            int boxY = Math.max(top + 4, Math.min(bottom - height, top + 16));
            g.setColor(new Color(255, 255, 255, 246));
            g.fillRoundRect(boxX, boxY, width, height, 10, 10);
            g.setColor(Theme.GREEN);
            g.drawRoundRect(boxX, boxY, width, height, 10, 10);
            g.setColor(Theme.INK);
            for (int i = 0; i < lines.length; i++) g.drawString(lines[i], boxX + 10, boxY + 17 + i * 16);
        }
    }

    private final class TrendCanvas extends Chart {
        TrendCanvas() { super(360); }

        @Override protected void paintComponent(Graphics source) {
            super.paintComponent(source);
            Graphics2D g = graphics(source);
            List<TrendPoint> points = trendPoints();
            int left = 76, top = 56, right = Math.max(left + 1, getWidth() - 30), bottom = getHeight() - 78;
            if (points.isEmpty()) {
                g.setColor(Theme.MUTED);
                g.drawString("暂无可展示的库存变化", left, (top + bottom) / 2);
                g.dispose();
                return;
            }
            double base = Math.max(1, points.get(0).remaining());
            List<Double> values = points.stream().map(point -> relative(point.remaining(), base)).toList();
            double[] bounds = valueBounds(values);
            axes(g, left, top, right, bottom, bounds[0], bounds[1], "%");
            legend(g, left, 28, new String[]{"相对起点变化", "0% 起点基准"},
                    new Color[]{Theme.GREEN, new Color(104, 132, 113)});

            g.setColor(Theme.GREEN);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Point previous = null;
            for (int i = 0; i < points.size(); i++) {
                int x = pointX(i, points.size(), left, right);
                int y = yFor(values.get(i), bounds[0], bounds[1], top, bottom);
                if (previous != null) g.drawLine(previous.x, previous.y, x, y);
                point(g, x, y, Theme.GREEN, hover() == i);
                previous = new Point(x, y);
            }

            g.setColor(Theme.MUTED);
            g.setFont(Theme.BODY.deriveFont(10f));
            int labelStep = Math.max(1, (int) Math.ceil(points.size() / 9d));
            for (int i = 0; i < points.size(); i++) {
                if (i != 0 && i != points.size() - 1 && i % labelStep != 0) continue;
                int x = pointX(i, points.size(), left, right);
                String label = DATE.format(points.get(i).date());
                g.drawString(label, x - g.getFontMetrics().stringWidth(label) / 2, bottom + 22);
            }
            if (hover() >= 0 && hover() < points.size()) {
                TrendPoint point = points.get(hover());
                int x = pointX(hover(), points.size(), left, right);
                String live = point.date().equals(LocalDate.now()) ? "（实时）" : "";
                tooltip(g, x, top, bottom, "日期：" + DATE.format(point.date()) + live
                        + "\n库存：" + point.remaining() + " 克"
                        + "\n相对起点：" + percent(relative(point.remaining(), base))
                        + "\n当日变动：" + signedQuantity(point.delta()));
            }
            g.dispose();
        }

        private double relative(int remaining, double base) { return (remaining - base) * 100d / Math.max(1, base); }
        private int pointX(int index, int count, int left, int right) {
            return count <= 1 ? (left + right) / 2 : left + (right - left) * index / (count - 1);
        }
        private double[] valueBounds(List<Double> values) { return HerbChartPanel.this.bounds(values); }
        @Override int hit(int x) {
            List<TrendPoint> points = trendPoints();
            if (points.isEmpty()) return -1;
            int left = 76, right = Math.max(left + 1, getWidth() - 30);
            int index = Math.round((x - left) * (points.size() - 1f) / Math.max(1, right - left));
            return Math.max(0, Math.min(points.size() - 1, index));
        }
        @Override String tip(int index) {
            List<TrendPoint> points = trendPoints();
            return index < 0 || index >= points.size() ? null : "日期：" + DATE.format(points.get(index).date());
        }
    }

    private final class PredictionCanvas extends Chart {
        PredictionCanvas() { super(330); }

        @Override protected void paintComponent(Graphics source) {
            super.paintComponent(source);
            Graphics2D g = graphics(source);
            List<Summary> data = summaryCache;
            int left = 76, top = 54, right = Math.max(left + 1, getWidth() - 30), bottom = getHeight() - 76;
            double max = upperBound(data.stream().mapToDouble(Summary::prediction).max().orElse(100), 100);
            axes(g, left, top, right, bottom, 0, max, "%");
            legend(g, left, 27, new String[]{"达到基准", "低于基准"}, new Color[]{Theme.GREEN, Theme.ACCENT});
            if (data.isEmpty()) {
                g.setColor(Theme.MUTED);
                g.drawString("暂无药材数据", left, (top + bottom) / 2);
                g.dispose();
                return;
            }
            double band = (right - left) / (double) data.size();
            int barWidth = (int) Math.max(18, Math.min(58, band * .64));
            for (int i = 0; i < data.size(); i++) {
                Summary summary = data.get(i);
                int x = left + (int) Math.round(band * (i + .5));
                int height = (int) Math.round((bottom - top) * Math.max(0, summary.prediction()) / max);
                int y = bottom - height;
                Color topColor = summary.prediction() >= 100 ? new Color(105, 177, 125) : new Color(220, 169, 96);
                Color bottomColor = summary.prediction() >= 100 ? Theme.GREEN : new Color(166, 105, 45);
                g.setPaint(new GradientPaint(0, y, topColor, 0, bottom, bottomColor));
                g.fill(new RoundRectangle2D.Double(x - barWidth / 2d, y, barWidth, height, 10, 10));
                if (hover() == i) {
                    g.setColor(new Color(bottomColor.getRed(), bottomColor.getGreen(), bottomColor.getBlue(), 48));
                    g.fillRoundRect(x - barWidth / 2 - 5, Math.max(top, y - 5), barWidth + 10, Math.max(10, height + 5), 12, 12);
                    tooltip(g, x, top, bottom, "药材：" + summary.name() + "\n完成率：" + percent(summary.prediction())
                            + "\n状态：" + summary.status());
                }
                g.setColor(Theme.MUTED);
                g.setFont(Theme.BODY.deriveFont(10f));
                String label = shortName(summary.name());
                g.drawString(label, x - g.getFontMetrics().stringWidth(label) / 2, bottom + 22);
            }
            g.dispose();
        }

        @Override int hit(int x) {
            if (summaryCache.isEmpty()) return -1;
            int left = 76, right = Math.max(left + 1, getWidth() - 30);
            double band = (right - left) / (double) summaryCache.size();
            int index = (int) ((x - left) / Math.max(1, band));
            return Math.max(0, Math.min(summaryCache.size() - 1, index));
        }
        @Override String tip(int index) {
            return index < 0 || index >= summaryCache.size() ? null
                    : "药材：" + summaryCache.get(index).name() + "\n完成率：" + percent(summaryCache.get(index).prediction());
        }
    }

    private final class DistributionCanvas extends Chart {
        private final boolean confidenceMode;
        DistributionCanvas(boolean confidenceMode) {
            super(190);
            this.confidenceMode = confidenceMode;
            setPreferredSize(new Dimension(0, 190));
            setMinimumSize(new Dimension(0, 190));
        }

        @Override protected void paintComponent(Graphics source) {
            super.paintComponent(source);
            Graphics2D g = graphics(source);
            List<Segment> segments = segments();
            int left = 28, right = Math.max(left + 1, getWidth() - 24), y = 62, height = 38;
            int total = segments.stream().mapToInt(Segment::value).sum();
            legend(g, left, 30, segments.stream().map(Segment::name).toArray(String[]::new),
                    segments.stream().map(Segment::color).toArray(Color[]::new));
            g.setColor(new Color(239, 245, 240));
            g.fillRoundRect(left, y, right - left, height, 12, 12);
            int cursor = left;
            for (Segment segment : segments) {
                int width = total == 0 ? 0 : (right - left) * segment.value() / total;
                g.setColor(segment.color());
                g.fillRoundRect(cursor, y, width, height, 12, 12);
                if (width > 42) {
                    g.setColor(Color.WHITE);
                    g.setFont(Theme.BODY.deriveFont(Font.BOLD, 11f));
                    g.drawString(segment.name() + " " + segment.value(), cursor + 8, y + 24);
                }
                cursor += width;
            }
            g.setColor(Theme.MUTED);
            g.setFont(Theme.BODY.deriveFont(11f));
            g.drawString("共 " + herbs.size() + " 种药材", left, y + height + 28);
            if (hover() >= 0) tooltip(g, left + 70, y, y + height,
                    segments.stream().map(segment -> segment.name() + "：" + segment.value() + " 种")
                            .reduce((a, b) -> a + "\n" + b).orElse("暂无数据"));
            g.dispose();
        }

        private List<Segment> segments() {
            if (!confidenceMode) {
                return List.of(new Segment("达到基准", (int) summaryCache.stream().filter(s -> s.prediction() >= 100).count(), Theme.GREEN),
                        new Segment("低于基准", (int) summaryCache.stream().filter(s -> s.prediction() < 100).count(), Theme.ACCENT));
            }
            return List.of(new Segment("高", (int) summaryCache.stream().filter(s -> s.confidence().equals("高")).count(), Theme.GREEN),
                    new Segment("中", (int) summaryCache.stream().filter(s -> s.confidence().equals("中")).count(), new Color(123, 159, 107)),
                    new Segment("低", (int) summaryCache.stream().filter(s -> s.confidence().equals("低")).count(), Theme.ACCENT));
        }
        @Override int hit(int x) { return x >= 28 && x <= getWidth() - 24 ? 0 : -1; }
        @Override String tip(int index) { return segments().toString(); }
    }

    private void repaintAllCharts() {
        trend.repaint();
        prediction.repaint();
        recommendation.repaint();
        confidence.repaint();
    }

    private double relativeBoundsPadding(double span) { return Math.max(1, span * .12); }
    private double[] bounds(List<Double> values) {
        if (values == null || values.isEmpty()) return new double[]{-10, 10};
        double min = values.stream().filter(Double::isFinite).min(Double::compareTo).orElse(-1d);
        double max = values.stream().filter(Double::isFinite).max(Double::compareTo).orElse(1d);
        min = Math.min(min, 0);
        max = Math.max(max, 0);
        if (min == max) { min -= 1; max += 1; }
        double step = niceStep((max - min) / 4d);
        double lower = Math.floor(min / step) * step;
        double upper = Math.ceil(max / step) * step;
        if (lower == upper) { lower -= step; upper += step; }
        if (lower == 0) lower = -step;
        if (upper == 0) upper = step;
        return new double[]{lower, upper};
    }

    private static double upperBound(double value, double minimum) {
        double target = Math.max(minimum, value);
        double step = niceStep(target / 4d);
        return Math.max(minimum, Math.ceil(target / step) * step);
    }

    private static double niceStep(double raw) {
        if (!Double.isFinite(raw) || raw <= 0) return 1;
        double power = Math.pow(10, Math.floor(Math.log10(raw)));
        double normalized = raw / power;
        double nice = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
        return nice * power;
    }

    private static String signedQuantity(int value) { return (value >= 0 ? "+" : "") + value + " 克"; }

    private record Event(String code, String name, int delta, int remaining, LocalDateTime time) {
        private String key() { return code + "|" + name; }
    }
    private record TrendPoint(LocalDate date, int remaining, int delta) {}
    private record Summary(String key, String name, String code, double change, double historyProbability,
                           double prediction, String status, String confidence, int count) {}
    private record Segment(String name, int value, Color color) {}
}
