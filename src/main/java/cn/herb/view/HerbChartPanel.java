package cn.herb.view;

import cn.herb.model.Medicinal;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * StockChartView.vue 的纯 Swing 翻译版。
 * 所有图形均由 Graphics2D 绘制，不依赖 HTML、JavaScript、ECharts 或其他前端运行时。
 */
public final class HerbChartPanel extends JPanel {
    private static final String DAILY = "日线";
    private static final String WEEKLY = "周线";
    private static final String MONTHLY = "月线";
    private static final int MAX_FAVORITES = 5;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private List<Medicinal> herbs = List.of();
    private List<Event> events = List.of();
    private final LinkedHashSet<String> favorites = new LinkedHashSet<>();
    private String activePeriod = DAILY;
    private double progress = 1;
    private boolean loading;

    private final JComboBox<String> periodBox = new JComboBox<>(new String[]{DAILY, WEEKLY, MONTHLY});
    private final JSlider zoom = new JSlider(20, 100, 100);
    private final JLabel zoomLabel = Theme.label("显示 100%", 12, Theme.MUTED);
    private final JTextField search = Theme.field(16);
    private final DefaultListModel<Medicinal> pickerModel = new DefaultListModel<>();
    private final JList<Medicinal> picker = new JList<>(pickerModel);
    private final JPanel favoriteTags = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JTable compareTable = Theme.table("药材", "编号", "变化", "历史上涨概率", "库存达标率", "推荐状态", "信心");
    private final Map<String, JButton> periodButtons = new LinkedHashMap<>();

    private final TrendCanvas trend = new TrendCanvas();
    private final PredictionCanvas prediction = new PredictionCanvas();
    private final DistributionCanvas recommendation = new DistributionCanvas(false);
    private final DistributionCanvas confidence = new DistributionCanvas(true);
    private final CompareCanvas compare = new CompareCanvas();
    private final javax.swing.Timer animation = new javax.swing.Timer(24, e -> {
        progress = Math.min(1, progress + .065);
        repaintAllCharts();
        if (progress >= 1) ((javax.swing.Timer) e.getSource()).stop();
    });

    public HerbChartPanel() {
        super(new BorderLayout(0, 16));
        setOpaque(false);
        setPreferredSize(new Dimension(980, 1500));
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
        title.add(Theme.label("库存变化、基准完成率与多种药材对比", 13, Theme.MUTED));
        root.add(title, BorderLayout.WEST);
        JPanel controls = Theme.row();
        controls.add(Theme.label("周期", 12, Theme.MUTED));
        periodBox.setPreferredSize(new Dimension(82, 34));
        controls.add(periodBox);
        controls.add(Theme.label("范围", 12, Theme.MUTED));
        zoom.setOpaque(false);
        zoom.setPreferredSize(new Dimension(130, 30));
        controls.add(zoom);
        controls.add(zoomLabel);
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
        body.add(chartCard("各药材不同周期库存变化对比", "将库存流水按日线、周线、月线聚合，正数表示入库增加，负数表示出库减少", trend));
        body.add(Box.createVerticalStrut(16));
        body.add(chartCard("各药材库存基准完成率", "当前库存 / 药材预警基准 × 100%，颜色表示是否达到基准", prediction));
        body.add(Box.createVerticalStrut(16));
        Theme.ResponsiveGrid distributions = Theme.responsiveGrid(2, 2, 1, 14, 14);
        distributions.setAlignmentX(LEFT_ALIGNMENT);
        distributions.add(chartCard("库存状态分布", "当前库存达到预警基准的药材比例", recommendation));
        distributions.add(chartCard("库存信心程度分布", "根据流水数量和历史正向变动估算数据稳定度", confidence));
        body.add(distributions);
        body.add(Box.createVerticalStrut(16));
        body.add(compareCard());
        return body;
    }

    private JPanel chartCard(String title, String subtitle, Chart canvas) {
        JPanel card = Theme.card();
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        JPanel heading = Theme.stack(3);
        heading.add(Theme.label(title, 17, Theme.GREEN));
        heading.add(Theme.label(subtitle, 12, Theme.MUTED));
        card.add(heading, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(canvas, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel compareCard() {
        JPanel card = Theme.card();
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(18, 18, 18, 18));
        JPanel head = new JPanel(new BorderLayout(10, 6));
        head.setOpaque(false);
        JPanel text = Theme.stack(3);
        text.add(Theme.label("药材对比", 19, Theme.GREEN));
        text.add(Theme.label("对应参考项目的自选股区域，最多选择 5 种药材", 12, Theme.MUTED));
        head.add(text, BorderLayout.WEST);
        JPanel buttons = Theme.row();
        for (String p : new String[]{DAILY, WEEKLY, MONTHLY}) {
            JButton b = Theme.button(p, false);
            b.setName("chart.period." + p);
            b.addActionListener(e -> setPeriod(p));
            periodButtons.put(p, b);
            buttons.add(b);
        }
        head.add(buttons, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        card.add(new ResponsiveCompare(buildPicker(), buildCompareContent()), BorderLayout.CENTER);
        return card;
    }

    private JComponent buildPicker() {
        JPanel side = Theme.card();
        side.setLayout(new BorderLayout(0, 10));
        side.setBorder(new EmptyBorder(14, 14, 14, 14));
        side.add(search, BorderLayout.NORTH);
        JPanel actions = Theme.row();
        JButton suggested = Theme.button("一键加入推荐", true);
        JButton clear = Theme.button("清空", false);
        suggested.addActionListener(e -> { favorites.clear(); summaries().stream().sorted(Comparator.comparingDouble(Summary::prediction).reversed()).limit(3).forEach(s -> favorites.add(s.key())); refresh(); });
        clear.addActionListener(e -> { favorites.clear(); refresh(); });
        actions.add(suggested); actions.add(clear);
        side.add(actions, BorderLayout.SOUTH);
        picker.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        picker.setFixedCellHeight(58);
        picker.setVisibleRowCount(8);
        picker.setFont(Theme.BODY);
        picker.setCellRenderer((list, value, index, selected, focus) -> {
            JPanel row = new JPanel(new BorderLayout(8, 3));
            row.setOpaque(true); row.setBackground(selected ? Theme.GREEN_TINT : Theme.SURFACE);
            row.setBorder(new EmptyBorder(7, 8, 7, 8));
            JPanel left = Theme.stack(2);
            JLabel name = Theme.label(value.name(), 13, Theme.INK); name.setFont(name.getFont().deriveFont(Font.BOLD));
            left.add(name); left.add(Theme.label(value.code(), 11, Theme.MUTED));
            row.add(left, BorderLayout.WEST);
            Summary s = summary(value);
            JPanel right = Theme.stack(2);
            right.add(Theme.label(favorites.contains(key(value)) ? "已加入" : s.status(), 11, favorites.contains(key(value)) ? Theme.GREEN : Theme.ACCENT));
            right.add(Theme.label(String.format(Locale.ROOT, "预测 %.1f%%", s.prediction()), 10, Theme.MUTED));
            row.add(right, BorderLayout.EAST);
            return row;
        });
        picker.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int i = picker.locationToIndex(e.getPoint());
                if (i < 0) return;
                Medicinal m = pickerModel.get(i);
                if (favorites.contains(key(m))) favorites.remove(key(m));
                else if (favorites.size() < MAX_FAVORITES) favorites.add(key(m));
                refresh();
            }
        });
        JScrollPane scroll = new JScrollPane(picker);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        side.add(scroll, BorderLayout.CENTER);
        return side;
    }

    private JComponent buildCompareContent() {
        JPanel content = Theme.card();
        content.setLayout(new BorderLayout(0, 10));
        content.setBorder(new EmptyBorder(14, 14, 14, 14));
        favoriteTags.setOpaque(false);
        content.add(favoriteTags, BorderLayout.NORTH);
        JScrollPane chartScroll = new JScrollPane(compare, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        chartScroll.setBorder(null); chartScroll.setOpaque(false); chartScroll.getViewport().setOpaque(false);
        content.add(chartScroll, BorderLayout.CENTER);
        JScrollPane tableScroll = Theme.scroll(compareTable);
        tableScroll.setPreferredSize(new Dimension(620, 180));
        content.add(tableScroll, BorderLayout.SOUTH);
        return content;
    }

    private void configure() {
        periodBox.addActionListener(e -> { if (!loading) setPeriod(String.valueOf(periodBox.getSelectedItem())); });
        zoom.addChangeListener(e -> { zoomLabel.setText("显示 " + zoom.getValue() + "%"); repaintAllCharts(); });
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshPicker(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshPicker(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshPicker(); }
        });
    }

    public void setData(List<Medicinal> value, List<Object[]> rows) {
        herbs = value == null ? List.of() : List.copyOf(value);
        events = parse(rows);
        if (favorites.isEmpty()) herbs.stream().limit(3).forEach(m -> favorites.add(key(m)));
        favorites.removeIf(k -> herbs.stream().noneMatch(m -> key(m).equals(k)));
        refresh();
    }

    private void setPeriod(String p) {
        if (!List.of(DAILY, WEEKLY, MONTHLY).contains(p)) return;
        activePeriod = p;
        loading = true; periodBox.setSelectedItem(p); loading = false;
        periodButtons.forEach((name, b) -> { b.setBackground(name.equals(p) ? Theme.GREEN : Theme.SURFACE); b.setForeground(name.equals(p) ? Color.WHITE : Theme.GREEN); });
        restart();
    }

    private void refresh() {
        refreshPicker(); refreshTags(); refreshTable(); updateCards();
        periodButtons.forEach((name, b) -> { b.setBackground(name.equals(activePeriod) ? Theme.GREEN : Theme.SURFACE); b.setForeground(name.equals(activePeriod) ? Color.WHITE : Theme.GREEN); });
        restart();
    }

    private void updateCards() {
        Container parent = getComponentCount() > 1 && getComponent(1) instanceof Container c ? c : null;
        if (!(parent instanceof JPanel body) || body.getComponentCount() == 0 || !(body.getComponent(0) instanceof Theme.ResponsiveGrid cards)) return;
        cards.removeAll();
        List<Summary> all = summaries();
        long recommended = all.stream().filter(s -> s.prediction() >= 100).count();
        long high = all.stream().filter(s -> s.confidence().equals("高")).count();
        cards.add(metric("药材池", String.valueOf(all.size()), "当前在册药材"));
        cards.add(metric(activePeriod + "达标", String.valueOf(recommended), "库存达到基准"));
        cards.add(metric("高信心", String.valueOf(high), "有足够流水记录"));
        cards.add(metric("自选药材", favorites.size() + "/" + MAX_FAVORITES, "底部对比区域"));
        cards.revalidate(); cards.repaint();
    }

    private JPanel metric(String label, String value, String hint) {
        JPanel p = Theme.card(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setBorder(new EmptyBorder(16, 18, 15, 16));
        p.add(Theme.label(label, 12, Theme.MUTED)); p.add(Box.createVerticalStrut(7));
        JLabel n = Theme.label(value, 28, Theme.GREEN); n.setFont(n.getFont().deriveFont(Font.BOLD)); p.add(n);
        p.add(Box.createVerticalStrut(4)); p.add(Theme.label(hint, 11, Theme.MUTED)); return p;
    }

    private void refreshPicker() {
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        pickerModel.clear();
        herbs.stream().filter(m -> q.isEmpty() || m.name().toLowerCase(Locale.ROOT).contains(q) || m.code().toLowerCase(Locale.ROOT).contains(q)).limit(20).forEach(pickerModel::addElement);
        picker.revalidate(); picker.repaint();
    }

    private void refreshTags() {
        favoriteTags.removeAll();
        for (Medicinal m : herbs) if (favorites.contains(key(m))) {
            JButton tag = Theme.button(m.name() + "  ×", false);
            tag.addActionListener(e -> { favorites.remove(key(m)); refresh(); }); favoriteTags.add(tag);
        }
        if (favorites.isEmpty()) favoriteTags.add(Theme.label("当前还没有加入对比的药材。", 12, Theme.MUTED));
        favoriteTags.revalidate(); favoriteTags.repaint();
    }

    private void refreshTable() {
        Theme.rows(compareTable, favoriteSummaries().stream().map(s -> new Object[]{s.name(), s.code(), signed(s.change()), percent(s.historyProbability()), percent(s.prediction()), s.status(), s.confidence()}).toList());
    }

    private void restart() { progress = 0; animation.restart(); repaintAllCharts(); }
    private void repaintAllCharts() { trend.repaint(); prediction.repaint(); recommendation.repaint(); confidence.repaint(); compare.repaint(); }

    private List<Event> parse(List<Object[]> rows) {
        if (rows == null) return List.of();
        List<Event> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i); if (r == null || r.length < 7) continue;
            result.add(new Event(text(r[0]), text(r[1]), number(r[2]), number(r[3]), time(r[6], i)));
        }
        result.sort(Comparator.comparing(Event::time)); return result;
    }

    private List<Summary> summaries() { return herbs.stream().map(this::summary).sorted(Comparator.comparingDouble(Summary::prediction).reversed()).toList(); }
    private List<Summary> favoriteSummaries() { return summaries().stream().filter(s -> favorites.contains(s.key())).toList(); }
    private Summary summary(Medicinal m) {
        List<Event> e = periodEvents(m.name());
        int positive = (int) e.stream().filter(x -> x.delta() > 0).count();
        double probability = e.isEmpty() ? 50 : positive * 100d / e.size();
        double change = e.isEmpty() ? 0 : e.stream().mapToInt(Event::delta).sum() * 100d / Math.max(1, e.get(0).remaining());
        double prediction = m.stock() * 100d / Math.max(1, m.threshold());
        String status = prediction >= 100 ? "推荐" : prediction >= 60 ? "观察" : "谨慎";
        String confidence = e.size() >= 5 ? "高" : e.size() >= 2 ? "中" : "低";
        return new Summary(key(m), m.name(), m.code(), change, probability, prediction, status, confidence, e.size());
    }

    private List<Event> periodEvents(String name) {
        List<Event> all = events.stream().filter(e -> e.name().equals(name)).toList();
        if (all.isEmpty()) return all;
        long days = DAILY.equals(activePeriod) ? 1 : WEEKLY.equals(activePeriod) ? 7 : 30;
        LocalDateTime end = all.get(all.size() - 1).time();
        List<Event> filtered = all.stream().filter(e -> !e.time().isBefore(end.minusDays(days))).toList();
        return filtered.isEmpty() ? List.of(all.get(all.size() - 1)) : filtered;
    }

    private String key(Medicinal m) { return m.code() + "|" + m.name(); }
    private static String text(Object o) { return o == null ? "" : String.valueOf(o).trim(); }
    private static int number(Object o) { try { return o instanceof Number n ? n.intValue() : Integer.parseInt(text(o)); } catch (Exception e) { return 0; } }
    private static LocalDateTime time(Object o, int fallback) {
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        String s = text(o); if (s.isEmpty()) return LocalDate.of(2000, 1, 1).atStartOfDay().plusSeconds(fallback);
        for (DateTimeFormatter f : List.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))) try { return LocalDateTime.parse(s, f); } catch (DateTimeParseException ignored) { }
        try { return LocalDate.parse(s).atStartOfDay(); } catch (DateTimeParseException ignored) { return LocalDate.of(2000, 1, 1).atStartOfDay().plusSeconds(fallback); }
    }
    private static String percent(double n) { return String.format(Locale.ROOT, "%.1f%%", n); }
    private static String signed(double n) { return String.format(Locale.ROOT, "%s%.1f%%", n >= 0 ? "+" : "", n); }
    private static String shortName(String s) { return s.length() > 7 ? s.substring(0, 6) + "…" : s; }

    private abstract class Chart extends JPanel {
        private int hover = -1;
        Chart(int h) { setOpaque(false); setPreferredSize(new Dimension(980, h)); setToolTipText("");
            addMouseMotionListener(new MouseMotionAdapter() { public void mouseMoved(MouseEvent e) { int n = hit(e.getX()); if (n != hover) { hover = n; repaint(); } } });
            addMouseListener(new MouseAdapter() { public void mouseExited(MouseEvent e) { hover = -1; repaint(); } }); }
        int hover() { return hover; }
        abstract int hit(int x);
        abstract String tip(int i);
        public String getToolTipText(MouseEvent e) { int i = hit(e.getX()); return i < 0 ? null : tip(i); }
        Graphics2D g(Graphics x) { Graphics2D g = (Graphics2D) x.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON); return g; }
        void axes(Graphics2D g, int l, int t, int r, int b, double max, String unit) { g.setFont(Theme.BODY.deriveFont(10f)); for (int i=0;i<=4;i++) { int y=b-(b-t)*i/4; g.setColor(i==0?Theme.MUTED:new Color(220,230,222)); g.drawLine(l,y,r,y); g.setColor(Theme.MUTED); g.drawString(String.format(Locale.ROOT,"%.0f%s", max*i/4,unit), 5,y+4); } g.setColor(Theme.LINE); g.drawLine(l,t,l,b); g.drawLine(l,b,r,b); }
        void legend(Graphics2D g, int x, int y, String[] names, Color[] colors) { g.setFont(Theme.BODY.deriveFont(11f)); for(int i=0;i<names.length;i++){g.setColor(colors[i]);g.fillOval(x,y-8,10,10);g.setColor(Theme.MUTED);g.drawString(names[i],x+15,y+1);x+=g.getFontMetrics().stringWidth(names[i])+34;} }
        void tooltip(Graphics2D g,int x,int t,int b,String text){if(text==null)return;String[] ls=text.split("\\n");g.setFont(Theme.BODY.deriveFont(11f));int w=20;for(String s:ls)w=Math.max(w,g.getFontMetrics().stringWidth(s)+20);int h=14+ls.length*16;int bx=Math.min(Math.max(4,x+10),Math.max(4,getWidth()-w-4));int by=Math.max(t+4,Math.min(b-h,t+16));g.setColor(new Color(255,255,255,245));g.fillRoundRect(bx,by,w,h,10,10);g.setColor(Theme.GREEN);g.drawRoundRect(bx,by,w,h,10,10);g.setColor(Theme.INK);for(int i=0;i<ls.length;i++)g.drawString(ls[i],bx+10,by+17+i*16);}
    }

    private final class TrendCanvas extends Chart {
        TrendCanvas(){super(360);}
        protected void paintComponent(Graphics x){super.paintComponent(x);Graphics2D g=g(x);List<Summary> data=visible(summaries());int l=64,t=56,r=getWidth()-30,b=getHeight()-74;double max=nice(data.stream().mapToDouble(s->Math.abs(s.change())).max().orElse(1));axes(g,l,t,r,b,max,"%");legend(g,l,28,new String[]{"日变化","周变化","月变化"},new Color[]{Theme.GREEN,Theme.ACCENT,new Color(104,135,176)});String[] labels={DAILY,WEEKLY,MONTHLY};Color[] colors={Theme.GREEN,Theme.ACCENT,new Color(104,135,176)};for(int q=0;q<3;q++){g.setColor(colors[q]);g.setStroke(new BasicStroke(3f));Point prev=null;for(int i=0;i<data.size();i++){Summary base=data.get(i);double v=changeFor(base.name(),labels[q]);int xx=l+(r-l)*i/Math.max(1,data.size()-1);int yy=(t+b)/2-(int)(v/max*(b-t)/2*phase(.05,.78));if(prev!=null)g.drawLine(prev.x,prev.y,xx,yy);g.fillOval(xx-5,yy-5,10,10);prev=new Point(xx,yy);} }g.setColor(Theme.MUTED);g.setFont(Theme.BODY.deriveFont(10f));for(int i=0;i<data.size();i++){int xx=l+(r-l)*i/Math.max(1,data.size()-1);String n=shortName(data.get(i).name());g.drawString(n,xx-g.getFontMetrics().stringWidth(n)/2,b+22);}if(hover()>=0&&hover()<data.size())tooltip(g,l+(r-l)*hover()/Math.max(1,data.size()-1),t,b,trendTip(data.get(hover())));g.dispose();}
        private String trendTip(Summary s){return "药材："+s.name()+"\n日变化："+percent(changeFor(s.name(),DAILY))+"\n周变化："+percent(changeFor(s.name(),WEEKLY))+"\n月变化："+percent(changeFor(s.name(),MONTHLY));}
        int hit(int x){List<Summary>d=visible(summaries());return d.isEmpty()?-1:Math.max(0,Math.min(d.size()-1,Math.round((x-64)*(d.size()-1f)/Math.max(1,getWidth()-94))));}
        String tip(int i){List<Summary>d=visible(summaries());return i<0||i>=d.size()?null:trendTip(d.get(i));}
    }

    private final class PredictionCanvas extends Chart {
        PredictionCanvas(){super(340);}
        protected void paintComponent(Graphics x){super.paintComponent(x);Graphics2D g=g(x);List<Summary>d=visible(summaries());int l=64,t=54,r=getWidth()-30,b=getHeight()-76;double max=Math.max(120,nice(d.stream().mapToDouble(Summary::prediction).max().orElse(100)));axes(g,l,t,r,b,max,"%");legend(g,l,27,new String[]{"达标","低于基准"},new Color[]{Theme.GREEN,Theme.ACCENT});int slot=Math.max(45,(r-l)/Math.max(1,d.size()));for(int i=0;i<d.size();i++){Summary s=d.get(i);int xx=l+slot*i+slot/2;int h=(int)((b-t)*s.prediction()/max*phase(.08,.82));int y=b-h;g.setPaint(new GradientPaint(0,y,s.prediction()>=100?new Color(105,177,125):new Color(220,169,96),0,b,s.prediction()>=100?Theme.GREEN:new Color(166,105,45)));g.fill(new RoundRectangle2D.Double(xx-20,y,40,h,10,10));g.setColor(Theme.MUTED);String n=shortName(s.name());g.setFont(Theme.BODY.deriveFont(10f));g.drawString(n,xx-g.getFontMetrics().stringWidth(n)/2,b+22);if(hover()==i)tooltip(g,xx,t,b,"药材："+s.name()+"\n完成率："+percent(s.prediction())+"\n状态："+s.status());}g.dispose();}
        int hit(int x){List<Summary>d=visible(summaries());int slot=Math.max(45,(getWidth()-94)/Math.max(1,d.size()));return d.isEmpty()?-1:Math.max(0,Math.min(d.size()-1,(x-64-slot/2)/slot));}
        String tip(int i){List<Summary>d=visible(summaries());return i<0||i>=d.size()?null:"药材："+d.get(i).name()+"\n完成率："+percent(d.get(i).prediction());}
    }

    private final class DistributionCanvas extends Chart {
        private final boolean confidenceMode;
        DistributionCanvas(boolean c){super(190);confidenceMode=c;setPreferredSize(new Dimension(460,190));}
        protected void paintComponent(Graphics x){super.paintComponent(x);Graphics2D g=g(x);List<Segment>s=segments();int l=28,r=getWidth()-24,y=62,h=38;int total=Math.max(1,herbs.size()), cursor=l;legend(g,l,30,s.stream().map(Segment::name).toArray(String[]::new),s.stream().map(Segment::color).toArray(Color[]::new));for(Segment a:s){int w=(r-l)*a.value()/total;int draw=(int)(w*phase(.2,.76));g.setPaint(new GradientPaint(0,y,a.color().brighter(),0,y+h,a.color().darker()));g.fillRoundRect(cursor,y,draw,h,12,12);if(draw>45){g.setColor(Color.WHITE);g.setFont(Theme.BODY.deriveFont(Font.BOLD,11f));g.drawString(a.name()+" "+a.value(),cursor+8,y+24);}cursor+=w;}g.setColor(Theme.MUTED);g.setFont(Theme.BODY.deriveFont(11f));g.drawString("共 "+herbs.size()+" 种药材",l,y+h+28);if(hover()>=0)tooltip(g,l+80,y,y+h,s.stream().map(a->a.name()+"："+a.value()+" 种").collect(Collectors.joining("\\n")));g.dispose();}
        List<Segment>segments(){if(!confidenceMode)return List.of(new Segment("推荐",(int)summaries().stream().filter(s->s.prediction()>=100).count(),Theme.GREEN),new Segment("谨慎",(int)summaries().stream().filter(s->s.prediction()<100).count(),Theme.ACCENT));return List.of(new Segment("高",(int)summaries().stream().filter(s->s.confidence().equals("高")).count(),Theme.GREEN),new Segment("中",(int)summaries().stream().filter(s->s.confidence().equals("中")).count(),new Color(123,159,107)),new Segment("低",(int)summaries().stream().filter(s->s.confidence().equals("低")).count(),Theme.ACCENT));}
        int hit(int x){return x>=28&&x<=getWidth()-24?0:-1;}
        String tip(int i){return segments().stream().map(a->a.name()+"："+a.value()+" 种").collect(Collectors.joining("\n"));}
    }

    private final class CompareCanvas extends Chart {
        CompareCanvas(){super(390);}
        protected void paintComponent(Graphics x){super.paintComponent(x);Graphics2D g=g(x);List<Summary>d=favoriteSummaries();int l=64,t=62,r=getWidth()-54,b=getHeight()-72;double max=Math.max(100,nice(d.stream().mapToDouble(s->Math.max(s.prediction(),s.historyProbability())).max().orElse(100)));axes(g,l,t,r,b,max,"%");legend(g,l,29,new String[]{"库存达标率","历史上涨概率","平均变化"},new Color[]{Theme.GREEN,Theme.ACCENT,new Color(104,135,176)});int slot=Math.max(100,(r-l)/Math.max(1,d.size()));List<Point>line=new ArrayList<>();for(int i=0;i<d.size();i++){Summary s=d.get(i);int xx=l+slot*i+slot/2;bar(g,xx-28,s.prediction(),max,b,t,22,Theme.GREEN);bar(g,xx+4,s.historyProbability(),max,b,t,22,Theme.ACCENT);int yy=(t+b)/2-(int)(s.change()/Math.max(10,max)*((b-t)/2)*phase(.18,.84));line.add(new Point(xx,yy));g.setColor(Theme.MUTED);g.setFont(Theme.BODY.deriveFont(10f));String n=shortName(s.name());g.drawString(n,xx-g.getFontMetrics().stringWidth(n)/2,b+22);g.drawString(s.code(),xx-g.getFontMetrics().stringWidth(s.code())/2,b+38);}g.setColor(new Color(104,135,176));g.setStroke(new BasicStroke(3f));for(int i=1;i<line.size();i++)g.drawLine(line.get(i-1).x,line.get(i-1).y,line.get(i).x,line.get(i).y);for(Point p:line)g.fillOval(p.x-5,p.y-5,10,10);if(hover()>=0&&hover()<d.size())tooltip(g,line.get(hover()).x,t,b,"药材："+d.get(hover()).name()+"\n库存达标率："+percent(d.get(hover()).prediction())+"\n历史上涨概率："+percent(d.get(hover()).historyProbability())+"\n平均变化："+percent(d.get(hover()).change()));g.dispose();}
        void bar(Graphics2D g,int x,double v,double max,int b,int t,int w,Color c){int h=(int)((b-t)*v/max*phase(.15,.8));g.setPaint(new GradientPaint(0,b-h,c.brighter(),0,b,c.darker()));g.fill(new RoundRectangle2D.Double(x,b-h,w,h,8,8));}
        int hit(int x){List<Summary>d=favoriteSummaries();int slot=Math.max(100,(getWidth()-118)/Math.max(1,d.size()));return d.isEmpty()?-1:Math.max(0,Math.min(d.size()-1,(x-64-slot/2)/slot));}
        String tip(int i){List<Summary>d=favoriteSummaries();return i<0||i>=d.size()?null:d.get(i).name();}
    }

    private double changeFor(String name,String period) { String old=activePeriod; activePeriod=period; List<Event> e=periodEvents(name); activePeriod=old; if(e.isEmpty()) return 0; return e.stream().mapToInt(Event::delta).sum()*100d/Math.max(1,e.get(0).remaining()); }
    private List<Summary> visible(List<Summary> all){int n=Math.max(1,(int)Math.ceil(all.size()*zoom.getValue()/100d));return all.subList(0,Math.min(n,all.size()));}
    private double phase(double start,double end){return progress<=start?0:Math.min(1,(progress-start)/(end-start));}
    private static double nice(double n){if(n<=10)return 10;double p=Math.pow(10,Math.floor(Math.log10(n)));double q=n/p;return (q<=1?1:q<=2?2:q<=5?5:10)*p;}

    private record Event(String code,String name,int delta,int remaining,LocalDateTime time){}
    private record Summary(String key,String name,String code,double change,double historyProbability,double prediction,String status,String confidence,int count){}
    private record Segment(String name,int value,Color color){}

    private static final class ResponsiveCompare extends JPanel {
        private final JComponent left,right;
        ResponsiveCompare(JComponent l,JComponent r){super(null);left=l;right=r;setOpaque(false);add(l);add(r);}
        public void doLayout(){int w=getWidth();if(w<880){int h=Math.max(300,left.getPreferredSize().height);left.setBounds(0,0,w,h);right.setBounds(0,h+14,w,Math.max(460,getHeight()-h-14));}else{int lw=Math.min(310,Math.max(250,w/3));left.setBounds(0,0,lw,getHeight());right.setBounds(lw+14,0,Math.max(1,w-lw-14),getHeight());}}
        public Dimension getPreferredSize(){int w=getParent()==null?980:getParent().getWidth();return w>0&&w<880?new Dimension(w,left.getPreferredSize().height+14+560):new Dimension(980,580);}
    }
}
