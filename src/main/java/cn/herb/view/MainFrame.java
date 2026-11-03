package cn.herb.view;

import cn.herb.controller.Tasks;
import cn.herb.model.Medicinal;
import cn.herb.model.Role;
import cn.herb.model.User;
import cn.herb.service.Platform;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 主窗口：顶部导航、可滚动内容和角色菜单均由 Swing 布局管理器组织。 */
public final class MainFrame extends JFrame {
    private static final Set<String> MEDIUM_NAV = Set.of("工作台", "药材管理", "库存流水", "智能问答");
    private static final Set<String> NARROW_NAV = Set.of("工作台", "药材管理", "智能问答");

    private final Platform platform;
    private User user;
    private final String mode;
    private final JPanel root = new JPanel(new BorderLayout());
    private final JPanel topbar = new JPanel(new BorderLayout());
    private final JPanel directNav = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
    private final JPanel navigationHost = new JPanel(new BorderLayout());
    private final JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    private final JPanel account = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
    private final JButton moreButton = Theme.navButton("更多");
    private final JPopupMenu morePopup = new JPopupMenu();
    private final JPanel contentViewport;
    private final AnimatedContentPanel content = new AnimatedContentPanel();
    private final Map<String, JButton> navigation = new LinkedHashMap<>();
    private final JLabel identity = Theme.label("", 13, Theme.INK);
    private final JLabel roleBadge = Theme.chip("");
    private String currentKey = "工作台";
    private boolean compactNavigation;

    public MainFrame(Platform platform, User user, String mode) {
        super("本草 · 中药材智能管理平台");
        this.platform = platform;
        this.user = user;
        this.mode = mode;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1440, 900);
        setMinimumSize(new Dimension(820, 620));
        setResizable(true);
        setLocationRelativeTo(null);
        setContentPane(root);
        root.setBackground(Theme.BG);

        contentViewport = new CenterContentPanel(content, Theme.CONTENT_MAX_WIDTH);
        buildTopbar();
        buildContentArea();
        buildStatusBar();
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                updateResponsiveLayout();
                contentViewport.revalidate();
            }
        });
        updateIdentity();
        SwingUtilities.invokeLater(this::updateResponsiveLayout);
        dashboard();
    }

    private void buildTopbar() {
        topbar.setBackground(Theme.TOPBAR);
        topbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE));
        topbar.setPreferredSize(new Dimension(0, 82));

        brand.setOpaque(false);
        brand.setBorder(new EmptyBorder(0, 32, 0, 24));
        brand.add(Theme.brandMark());
        JPanel brandText = Theme.stack(2);
        brandText.add(Theme.label("本草鉴别", 17, Theme.INK));
        brandText.add(Theme.label("中药材智能管理平台", 11, Theme.MUTED));
        brand.add(brandText);
        topbar.add(brand, BorderLayout.WEST);

        directNav.setOpaque(false);
        directNav.setBorder(new EmptyBorder(0, 6, 0, 6));
        navigationHost.setOpaque(false);
        navigationHost.add(directNav, BorderLayout.CENTER);
        moreButton.setName("nav.more");
        moreButton.addActionListener(event -> showMoreMenu());
        navigationHost.add(moreButton, BorderLayout.EAST);
        topbar.add(navigationHost, BorderLayout.CENTER);

        account.setOpaque(false);
        account.setBorder(new EmptyBorder(0, 16, 0, 32));
        JPanel identityPanel = Theme.stack(2);
        identityPanel.add(identity);
        identityPanel.add(roleBadge);
        account.add(identityPanel);
        JButton logout = Theme.button("退出", false);
        logout.setName("nav.logout");
        logout.setBorder(new EmptyBorder(8, 10, 8, 10));
        logout.addActionListener(event -> logout());
        account.add(logout);
        topbar.add(account, BorderLayout.EAST);
        root.add(topbar, BorderLayout.NORTH);
    }

    private void buildContentArea() {
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(true);
        center.setBackground(Theme.BG);
        center.add(Theme.contentScroll(contentViewport), BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);
    }

    private void buildStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setOpaque(true);
        status.setBackground(Theme.BG);
        status.setBorder(new EmptyBorder(7, 28, 8, 28));
        status.add(Theme.label("数据库：" + mode, 11, Theme.MUTED), BorderLayout.WEST);
        status.add(Theme.label("单位：库存（克） · 单价（元 / 克）", 11, Theme.MUTED), BorderLayout.EAST);
        root.add(status, BorderLayout.SOUTH);
    }

    private void updateIdentity() {
        identity.setText(user.displayName());
        roleBadge.setText(user.role().toString());
        roleBadge.setToolTipText("当前角色：" + user.role());
    }

    private void addNav(String label, Runnable action) {
        JButton button = Theme.navButton(label);
        button.setName("nav." + label);
        button.addActionListener(event -> {
            currentKey = label;
            action.run();
        });
        directNav.add(button);
        navigation.put(label, button);
    }

    private void buildNavigation() {
        addNav("工作台", this::dashboard);
        addNav("药材管理", () -> show("药材管理", new MedicinalPanel(platform, user)));
        if (user.role().canEdit()) addNav("库存流水", () -> logs(false));
        addNav("智能问答", () -> show("智能问答", new ChatPanel(platform, user)));
        if (user.role() == Role.ADMIN) {
            addNav("用户管理", this::users);
            addNav("登录日志", () -> logs(true));
        }
        addNav("个人资料", this::profile);
    }

    private void updateResponsiveLayout() {
        if (navigation.isEmpty()) buildNavigation();
        int width = Math.max(getWidth(), topbar.getWidth());
        boolean narrow = width < 900;
        boolean medium = width >= 900 && width < 1200;
        compactNavigation = medium || narrow;
        for (Map.Entry<String, JButton> entry : navigation.entrySet()) {
            String label = entry.getKey();
            JButton button = entry.getValue();
            boolean visible = !narrow ? (!medium || MEDIUM_NAV.contains(label) || label.equals(currentKey)) : NARROW_NAV.contains(label) || label.equals(currentKey);
            button.setVisible(visible);
            Theme.navDensity(button, compactNavigation);
            Theme.navState(button, label.equals(currentKey));
        }
        morePopup.removeAll();
        boolean hasHidden = false;
        for (Map.Entry<String, JButton> entry : navigation.entrySet()) {
            if (entry.getValue().isVisible()) continue;
            hasHidden = true;
            JMenuItem item = new JMenuItem(entry.getKey());
            item.setFont(Theme.BODY);
            item.setForeground(entry.getKey().equals(currentKey) ? Theme.GREEN : Theme.INK);
            item.addActionListener(event -> entry.getValue().doClick());
            morePopup.add(item);
        }
        moreButton.setVisible(hasHidden || narrow || medium);
        moreButton.setText(narrow ? "更多" : "更多菜单");
        Theme.navDensity(moreButton, compactNavigation);
        Theme.navState(moreButton, hasHidden && !navigation.containsKey(currentKey));
        directNav.revalidate();
        navigationHost.revalidate();
        topbar.revalidate();
        topbar.repaint();
    }

    private void showMoreMenu() {
        if (morePopup.getComponentCount() == 0) return;
        morePopup.show(moreButton, 0, moreButton.getHeight());
    }

    private void show(String key, JPanel panel) {
        currentKey = key;
        for (Map.Entry<String, JButton> entry : navigation.entrySet()) Theme.navState(entry.getValue(), entry.getKey().equals(key));
        updateResponsiveLayout();
        content.setPage(panel);
    }

    private void logout() {
        if (!Theme.confirm(this, "退出当前账户并返回登录界面？")) return;
        dispose();
        new LoginFrame(platform, mode).setVisible(true);
    }

    private void dashboard() {
        currentKey = "工作台";
        JPanel page = Theme.page("药材管理，一目了然", LocalDate.now() + "   /   你好，" + user.displayName() + "。查看库存状况，开始今天的工作。");
        page.setBorder(new EmptyBorder(30, 40, 36, 40));
        JPanel banner = Theme.heroBanner("药材管理，一目了然", LocalDate.now() + "   /   你好，" + user.displayName() + "。查看库存状况，开始今天的工作。", "dashboard-accent-v1.png");
        page.remove(0);
        page.add(banner, BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 20));
        body.setOpaque(false);
        Theme.ResponsiveGrid stats = Theme.responsiveGrid(4, 2, 1, 16, 16);
        stats.setName("dashboard.stats");
        body.add(stats, BorderLayout.NORTH);

        HerbChartPanel charts = new HerbChartPanel();
        charts.setName("dashboard.charts");
        JTable table = Theme.table("药材编号", "药材名称", "当前库存 / 克", "预警阈值 / 克", "状态");
        JPanel warnings = Theme.card();
        warnings.setLayout(new BorderLayout(0, 12));
        warnings.setBorder(new EmptyBorder(18, 18, 18, 18));
        JPanel warningHeading = new JPanel(new BorderLayout());
        warningHeading.setOpaque(false);
        warningHeading.add(Theme.label("库存关注", 18, Theme.GREEN), BorderLayout.WEST);
        warningHeading.add(Theme.label("低于预警阈值的药材", 12, Theme.MUTED), BorderLayout.EAST);
        warnings.add(warningHeading, BorderLayout.NORTH);
        warnings.add(Theme.scroll(table), BorderLayout.CENTER);

        Theme.ResponsiveGrid visual = Theme.responsiveGrid(2, 2, 1, 16, 16);
        visual.add(charts);
        visual.add(warnings);
        body.add(visual, BorderLayout.CENTER);

        JPanel actions = Theme.row();
        JButton herbs = Theme.button("进入药材管理 →", true);
        JButton chat = Theme.button("打开知识问答", false);
        JButton refresh = Theme.button("刷新概览", false);
        herbs.addActionListener(event -> navigation.get("药材管理").doClick());
        chat.addActionListener(event -> navigation.get("智能问答").doClick());
        refresh.addActionListener(event -> dashboard());
        actions.add(herbs);
        actions.add(chat);
        actions.add(refresh);
        body.add(actions, BorderLayout.SOUTH);
        page.add(body, BorderLayout.CENTER);
        show("工作台", page);

        Tasks.run(this, null, () -> platform.medicinals("", "全部"), list -> {
            if (!page.isShowing()) return;
            long low = list.stream().filter(Medicinal::lowStock).count();
            long qty = list.stream().mapToLong(Medicinal::stock).sum();
            long cats = list.stream().map(Medicinal::category).distinct().count();
            stats.removeAll();
            stats.add(metric("药材档案", String.valueOf(list.size()), "种在册药材"));
            stats.add(metric("库存总量", String.format("%,d", qty), "克 · 实时库存"));
            stats.add(metric("库存预警", String.valueOf(low), "种需要关注"));
            stats.add(metric("药材分类", String.valueOf(cats), "类已收录"));
            Theme.rows(table, list.stream().filter(Medicinal::lowStock).map(m -> new Object[]{m.code(), m.name(), m.stock(), m.threshold(), "待补货"}).toList());
            stats.revalidate();
            stats.repaint();
            if (user.role().canEdit()) Tasks.run(this, null, () -> platform.stockLogs(user), rows -> charts.setData(list, rows));
            else charts.setData(list, java.util.List.of());
        });
    }

    private JPanel metric(String label, String value, String hint) {
        JPanel panel = Theme.card();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(19, 20, 18, 18));
        panel.add(Theme.label(label, 13, Theme.MUTED));
        panel.add(Box.createVerticalStrut(10));
        JLabel number = Theme.label(value, 30, Theme.GREEN);
        number.setFont(number.getFont().deriveFont(Font.BOLD));
        panel.add(number);
        panel.add(Box.createVerticalStrut(5));
        panel.add(Theme.label(hint, 12, Theme.MUTED));
        return panel;
    }

    private void logs(boolean login) {
        JPanel page = Theme.page(login ? "登录日志" : "库存流水", login ? "记录成功登录的账号与时间，最近 300 条" : "每次入出库与库存变更在同一事务中保存，最近 300 条");
        JTable table = login ? Theme.table("序号", "登录账号", "登录时间") : Theme.table("药材编号", "药材名称", "变动 / 克", "结余 / 克", "操作账号", "备注", "操作时间");
        page.add(Theme.scroll(table), BorderLayout.CENTER);
        JButton refresh = Theme.button("刷新记录", false);
        refresh.addActionListener(event -> Tasks.run(this, refresh, () -> login ? platform.loginLogs(user) : platform.stockLogs(user), rows -> Theme.rows(table, rows)));
        page.add(Theme.row(refresh), BorderLayout.SOUTH);
        show(login ? "登录日志" : "库存流水", page);
        refresh.doClick();
    }

    private void users() {
        JPanel page = Theme.page("用户管理", "管理员可以新增或删除账户。公开注册的账户始终为访客。");
        JTable table = Theme.table("用户 ID", "账号", "姓名 / 昵称", "角色");
        page.add(Theme.scroll(table), BorderLayout.CENTER);
        java.util.List<User> records = new ArrayList<>();
        JButton refresh = Theme.button("刷新", false);
        JButton add = Theme.button("新增用户", true);
        JButton remove = Theme.button("删除用户", false);
        refresh.addActionListener(event -> Tasks.run(this, refresh, () -> platform.users(user), list -> {
            records.clear();
            records.addAll(list);
            Theme.rows(table, list.stream().map(u -> new Object[]{u.id(), u.username(), u.displayName(), u.role()}).toList());
        }));
        add.addActionListener(event -> {
            JTextField account = Theme.field(18), name = Theme.field(18);
            JPasswordField password = new JPasswordField(18);
            Theme.input(password);
            JComboBox<Role> role = new JComboBox<>(Role.values());
            role.setSelectedItem(Role.GUEST);
            JPanel form = new JPanel(new GridLayout(0, 2, 12, 12));
            form.add(new JLabel("账号"));
            form.add(account);
            form.add(new JLabel("姓名 / 昵称"));
            form.add(name);
            form.add(new JLabel("初始密码（至少 8 位）"));
            form.add(password);
            form.add(new JLabel("角色"));
            form.add(role);
            if (JOptionPane.showConfirmDialog(this, form, "新增用户", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            String raw = new String(password.getPassword());
            Tasks.run(this, add, () -> {
                platform.createUser(user, account.getText(), raw, name.getText(), (Role) role.getSelectedItem());
                return true;
            }, ok -> {
                refresh.doClick();
                Theme.message(this, "用户已创建");
            });
        });
        remove.addActionListener(event -> {
            int index = table.getSelectedRow();
            if (index < 0) {
                Theme.message(this, "请先选择用户");
                return;
            }
            User selected = records.get(table.convertRowIndexToModel(index));
            if (!Theme.confirm(this, "删除账户 " + selected.username() + "？该账户的问答历史也将删除。")) return;
            Tasks.run(this, remove, () -> {
                platform.deleteUser(user, selected.id());
                return true;
            }, ok -> refresh.doClick());
        });
        page.add(Theme.row(add, remove, refresh), BorderLayout.SOUTH);
        show("用户管理", page);
        refresh.doClick();
    }

    private void profile() {
        JPanel page = Theme.page("个人资料", "维护显示姓名。如需修改密码，请同时输入原密码和新密码。");
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints grid = new GridBagConstraints();
        grid.insets = new Insets(10, 10, 10, 10);
        grid.anchor = GridBagConstraints.WEST;
        JTextField name = Theme.field(22);
        name.setText(user.displayName());
        JPasswordField old = new JPasswordField(22), password = new JPasswordField(22), again = new JPasswordField(22);
        Theme.input(old);
        Theme.input(password);
        Theme.input(again);
        Component[][] rows = {{new JLabel("账号"), new JLabel(user.username())}, {new JLabel("当前角色"), new JLabel(user.role().toString())}, {new JLabel("姓名 / 昵称"), name}, {new JLabel("原密码"), old}, {new JLabel("新密码"), password}, {new JLabel("确认新密码"), again}};
        for (int index = 0; index < rows.length; index++) {
            grid.gridy = index;
            grid.gridx = 0;
            form.add(rows[index][0], grid);
            grid.gridx = 1;
            form.add(rows[index][1], grid);
        }
        JButton save = Theme.button("保存资料", true);
        grid.gridy = rows.length;
        grid.gridx = 1;
        form.add(save, grid);
        wrap.add(form, BorderLayout.NORTH);
        page.add(wrap, BorderLayout.CENTER);
        show("个人资料", page);
        save.addActionListener(event -> {
            String next = new String(password.getPassword());
            String previous = new String(old.getPassword());
            if (!next.equals(new String(again.getPassword()))) {
                Theme.message(this, "两次新密码不一致");
                return;
            }
            Tasks.run(this, save, () -> platform.updateProfile(user, name.getText(), previous, next), updated -> {
                user = updated;
                updateIdentity();
                old.setText("");
                password.setText("");
                again.setText("");
                Theme.message(this, "资料已保存");
            });
        });
    }

    private static final class CenterContentPanel extends JPanel implements Scrollable {
        private final JComponent child;
        private final int maxWidth;

        private CenterContentPanel(JComponent child, int maxWidth) {
            super(null);
            this.child = child;
            this.maxWidth = maxWidth;
            setOpaque(false);
            setBorder(new EmptyBorder(0, 0, 12, 0));
            add(child);
        }

        @Override public void doLayout() {
            int width = Math.min(maxWidth, Math.max(1, getWidth()));
            int x = Math.max(0, (getWidth() - width) / 2);
            Dimension preferred = child.getPreferredSize();
            child.setBounds(x, 0, width, Math.max(getHeight(), preferred.height));
            child.doLayout();
        }

        @Override public Dimension getPreferredSize() {
            Dimension preferred = child.getPreferredSize();
            return new Dimension(maxWidth, Math.max(1, preferred.height));
        }

        @Override public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(maxWidth, 760);
        }

        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? 24 : 18;
        }

        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? Math.max(40, visibleRect.height - 24) : Math.max(40, visibleRect.width - 18);
        }

        @Override public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class AnimatedContentPanel extends JPanel {
        private JComponent page;
        private float progress = 1f;
        private int offset;
        private final Timer timer = new Timer(16, event -> {
            progress = Math.min(1f, progress + 0.12f);
            offset = Math.round((1f - progress) * 12f);
            revalidate();
            repaint();
            if (progress >= 1f) ((Timer) event.getSource()).stop();
        });

        private AnimatedContentPanel() {
            super(null);
            setOpaque(false);
        }

        private void setPage(JComponent next) {
            if (page != null) remove(page);
            page = next;
            page.setOpaque(false);
            add(page);
            progress = 0f;
            offset = 12;
            timer.restart();
            revalidate();
            repaint();
        }

        @Override public void doLayout() {
            if (page == null) return;
            Dimension preferred = page.getPreferredSize();
            page.setBounds(0, offset, getWidth(), Math.max(preferred.height, getHeight() - offset));
        }

        @Override public Dimension getPreferredSize() {
            if (page == null) return new Dimension(1, 1);
            Dimension preferred = page.getPreferredSize();
            return new Dimension(preferred.width, preferred.height + 12);
        }

        @Override protected void paintChildren(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setComposite(AlphaComposite.SrcOver.derive(0.55f + progress * 0.45f));
            super.paintChildren(g);
            g.dispose();
        }
    }
}
