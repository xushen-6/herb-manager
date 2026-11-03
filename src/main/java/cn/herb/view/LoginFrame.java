package cn.herb.view;

import cn.herb.controller.Tasks;
import cn.herb.service.Platform;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import cn.herb.util.Assets;

/** 登录和注册共用同一个窗口，通过右侧表单切换完成流程。 */
public final class LoginFrame extends JFrame {
    private final Platform platform;
    private final String mode;
    private final JPanel formHost = new JPanel(new GridBagLayout());
    private final JTextField username = Theme.field(21);
    private final JPasswordField password = new JPasswordField(21);
    private final JButton login = Theme.button("登 录 平 台", true);
    private JPanel brand;
    private JPanel right;

    public LoginFrame(Platform platform, String mode) {
        super("本草 · 中药材智能管理平台");
        this.platform = platform;
        this.mode = mode;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 650);
        setMinimumSize(new Dimension(680, 560));
        setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        setContentPane(root);
        brand = new BrandPanel();
        brand.setPreferredSize(new Dimension(470, 0));
        root.add(brand, BorderLayout.WEST);
        formHost.setOpaque(false);
        right = new JPanel(new BorderLayout());
        right.setBackground(Theme.SURFACE);
        JScrollPane formScroll = new JScrollPane(formHost, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.setBorder(null);
        formScroll.setOpaque(false);
        formScroll.getViewport().setBackground(Theme.SURFACE);
        formScroll.getVerticalScrollBar().setUnitIncrement(18);
        right.add(formScroll, BorderLayout.CENTER);
        root.add(right, BorderLayout.CENTER);
        login.addActionListener(event -> submitLogin());
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                updateResponsiveLayout();
            }
        });
        showLogin();
        updateResponsiveLayout();
    }

    private void updateResponsiveLayout() {
        int width = Math.max(getWidth(), getPreferredSize().width);
        boolean compact = width < 1020;
        boolean narrow = width < 860;
        brand.setPreferredSize(new Dimension(narrow ? 0 : (compact ? 360 : 470), 0));
        brand.setVisible(!narrow);
        formHost.setBorder(new EmptyBorder(compact ? 24 : 36, compact ? 30 : 76, compact ? 24 : 36, compact ? 30 : 76));
        right.revalidate();
        right.repaint();
    }

    private void showLogin() {
        formHost.removeAll();
        formHost.setBorder(new EmptyBorder(28, 70, 28, 70));
        JPanel form = baseForm("欢迎回来", "登录账户，开始今天的药材管理");
        addField(form, "账号", username, "login.username");
        Theme.input(password);
        password.setName("login.password");
        addField(form, "密码", password, null);
        login.setName("login.submit");
        addRow(form, login, 20);
        JButton register = Theme.button("没有账号？注册访客账户", false);
        register.setName("login.register");
        register.addActionListener(event -> showRegister());
        addRow(form, register, 3);
        addRow(form, Theme.label("登录使用账号和密码，注册时需要邮箱验证码", 12, Theme.MUTED), 3);
        addRow(form, Theme.label("数据库：" + mode, 12, Theme.MUTED), 3);
        formHost.add(form);
        finishForm();
        getRootPane().setDefaultButton(login);
    }

    private void showRegister() {
        formHost.removeAll();
        formHost.setBorder(new EmptyBorder(28, 70, 28, 70));
        JPanel form = baseForm("注册访客账户", "填写邮箱并获取验证码，完成后返回登录");
        JTextField account = Theme.field(21), email = Theme.field(21), emailCode = Theme.field(10), name = Theme.field(21);
        JPasswordField raw = new JPasswordField(21), again = new JPasswordField(21);
        Theme.input(raw);
        Theme.input(again);
        addField(form, "账号", account, "register.username");
        addField(form, "QQ 邮箱", email, "register.email");
        JButton send = Theme.button("发送验证码", false);
        JPanel codeRow = new JPanel(new BorderLayout(10, 0));
        codeRow.setOpaque(false);
        codeRow.add(emailCode);
        codeRow.add(send, BorderLayout.EAST);
        addField(form, "邮箱验证码", codeRow, "register.code");
        addField(form, "姓名 / 昵称", name, "register.name");
        addField(form, "密码（至少 6 位）", raw, null);
        addField(form, "确认密码", again, null);
        JButton submit = Theme.button("完成注册", true);
        submit.setName("register.submit");
        addRow(form, submit, 18);
        JButton back = Theme.button("返回登录", false);
        back.setName("register.back");
        back.addActionListener(event -> showLogin());
        addRow(form, back, 3);
        addRow(form, Theme.label("验证码 5 分钟内有效，发送频率为每分钟一次", 12, Theme.MUTED), 3);
        send.addActionListener(event -> Tasks.run(this, send, () -> {
            platform.sendRegistrationCode(email.getText());
            return true;
        }, ok -> Theme.message(this, "验证码已发送，请检查邮箱。")));
        submit.addActionListener(event -> {
            String first = new String(raw.getPassword());
            if (!first.equals(new String(again.getPassword()))) {
                Theme.message(this, "两次密码输入不一致");
                return;
            }
            Tasks.run(this, submit, () -> {
                platform.registerVerified(account.getText(), email.getText(), first, name.getText(), emailCode.getText());
                return account.getText();
            }, value -> {
                showLogin();
                username.setText(value);
                Theme.message(this, "注册成功，请使用账号和密码登录。");
            });
        });
        formHost.add(form);
        finishForm();
        getRootPane().setDefaultButton(submit);
    }

    private JPanel baseForm(String title, String subtitle) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setPreferredSize(new Dimension(430, 500));
        GridBagConstraints grid = new GridBagConstraints();
        grid.gridx = 0;
        grid.gridy = 0;
        grid.weightx = 1;
        grid.fill = GridBagConstraints.HORIZONTAL;
        grid.insets = new Insets(7, 0, 7, 0);
        form.putClientProperty("grid", grid);
        addRow(form, Theme.title(title), 0);
        addRow(form, Theme.label(subtitle, 14, Theme.MUTED), 0);
        return form;
    }

    private void addField(JPanel form, String label, JComponent component, String name) {
        if (name != null) component.setName(name);
        addRow(form, Theme.label(label, 13, Theme.INK), 18);
        addRow(form, component, 4);
    }

    private void addRow(JPanel form, Component component, int top) {
        GridBagConstraints grid = (GridBagConstraints) form.getClientProperty("grid");
        grid.insets = new Insets(top, 0, 4, 0);
        form.add(component, grid);
        grid.gridy++;
    }

    private void finishForm() {
        formHost.revalidate();
        formHost.repaint();
    }

    private void submitLogin() {
        String account = username.getText();
        String rawPassword = new String(password.getPassword());
        Tasks.run(this, login, () -> platform.login(account, rawPassword), loggedIn -> {
            dispose();
            new MainFrame(platform, loggedIn, mode).setVisible(true);
        });
    }

    private static final class BrandPanel extends JPanel {
        private final BufferedImage illustration = Assets.image("login-background-v1.png");

        BrandPanel() {
            setBackground(Theme.GREEN);
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(52, 42, 45, 30));
            JPanel top = Theme.stack(0);
            top.add(Theme.label("BENCAO  /  本草", 16, new Color(221, 230, 205)));
            top.add(Box.createVerticalStrut(80));
            JLabel heading = Theme.label("本草有序", 38, Color.WHITE);
            heading.setFont(heading.getFont().deriveFont(Font.BOLD));
            top.add(heading);
            top.add(Box.createVerticalStrut(14));
            top.add(Theme.label("让药材管理，简单而清晰。", 18, new Color(219, 232, 214)));
            add(top, BorderLayout.NORTH);
            JPanel bottom = Theme.stack(0);
            bottom.add(Theme.label("药材档案   /   库存流转   /   知识问答", 13, new Color(219, 232, 214)));
            bottom.add(Box.createVerticalStrut(15));
            bottom.add(Theme.label("Java 程序设计项目实践 · 基础版 1.0", 12, new Color(183, 204, 180)));
            add(bottom, BorderLayout.SOUTH);
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            if (illustration != null && getWidth() > 0 && getHeight() > 0) {
                double scale = Math.max((double) getWidth() / illustration.getWidth(), (double) getHeight() / illustration.getHeight());
                int drawWidth = (int) Math.ceil(illustration.getWidth() * scale);
                int drawHeight = (int) Math.ceil(illustration.getHeight() * scale);
                int x = (getWidth() - drawWidth) / 2;
                int y = (getHeight() - drawHeight) / 2;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f));
                g.drawImage(illustration, x, y, drawWidth, drawHeight, null);
            }
            g.setComposite(AlphaComposite.SrcOver);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(64, 113, 81));
            g.setStroke(new BasicStroke(2));
            int x = 235, y = 385;
            g.drawArc(x - 100, y - 100, 210, 200, 10, 280);
            for (int index = 0; index < 6; index++) {
                int yy = y - 85 + index * 27;
                g.drawOval(x - 60, yy, 65, 25);
                g.drawOval(x + 5, yy + 12, 65, 25);
            }
            g.drawLine(x + 3, y - 100, x + 3, y + 100);
            g.dispose();
        }
    }
}
