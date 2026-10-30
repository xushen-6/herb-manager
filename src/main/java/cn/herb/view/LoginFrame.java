package cn.herb.view;

import cn.herb.controller.Tasks;
import cn.herb.service.Platform;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class LoginFrame extends JFrame {
    private final Platform platform;
    private final String mode;
    private final JTextField username=Theme.field(21);
    private final JPasswordField password=new JPasswordField(21);
    private final JButton login=Theme.button("登 录 平 台",true);
    public LoginFrame(Platform platform,String mode) {
        super("本草 · 中药材智能管理平台"); this.platform=platform; this.mode=mode;
        setDefaultCloseOperation(EXIT_ON_CLOSE); setSize(1000,650); setMinimumSize(new Dimension(900,600)); setLocationRelativeTo(null);
        JPanel root=new JPanel(new BorderLayout()); setContentPane(root);
        JPanel brand=new BrandPanel(); brand.setPreferredSize(new Dimension(430,0)); root.add(brand,BorderLayout.WEST);
        JPanel right=new JPanel(new GridBagLayout()); right.setBackground(Color.WHITE); root.add(right);
        JPanel form=new JPanel(new GridBagLayout()); form.setOpaque(false); GridBagConstraints g=new GridBagConstraints(); g.gridx=0; g.gridy=0; g.weightx=1; g.fill=GridBagConstraints.HORIZONTAL; g.insets=new Insets(7,0,7,0);
        form.add(Theme.title("欢迎回来"),g); g.gridy++; form.add(Theme.label("登录账户，开始今天的药材管理",14,Theme.MUTED),g);
        g.gridy++; g.insets=new Insets(24,0,4,0); form.add(Theme.label("账号",13,Theme.INK),g);
        g.gridy++; g.insets=new Insets(4,0,5,0); username.setName("login.username"); form.add(username,g);
        g.gridy++; form.add(Theme.label("密码",13,Theme.INK),g);
        g.gridy++; Theme.input(password); password.setName("login.password"); form.add(password,g);
        g.gridy++; g.insets=new Insets(20,0,8,0); login.setName("login.submit"); form.add(login,g);
        g.gridy++; g.insets=new Insets(3,0,8,0); JButton register=Theme.button("没有账号？注册访客账户",false); form.add(register,g);
        g.gridy++; form.add(Theme.label("数据库："+mode,12,Theme.MUTED),g);
        right.add(form); getRootPane().setDefaultButton(login);
        login.addActionListener(e -> submit()); register.addActionListener(e -> register());
    }
    private void submit() {
        String u=username.getText(), p=new String(password.getPassword());
        Tasks.run(this,login,() -> platform.login(u,p),user -> { dispose(); new MainFrame(platform,user,mode).setVisible(true); });
    }
    private void register() {
        JTextField u=Theme.field(18), email=Theme.field(18), emailCode=Theme.field(10), name=Theme.field(18); JPasswordField p=new JPasswordField(18), again=new JPasswordField(18); Theme.input(p); Theme.input(again);
        JButton sendCode=Theme.button("发送验证码",false), submit=Theme.button("完成注册",true);
        JPanel form=new JPanel(new GridLayout(0,2,12,12)); form.add(new JLabel("账号"));form.add(u); form.add(new JLabel("QQ 邮箱"));form.add(email); form.add(new JLabel("邮箱验证码")); JPanel codeRow=new JPanel(new BorderLayout(8,0));codeRow.add(emailCode);codeRow.add(sendCode,BorderLayout.EAST);form.add(codeRow); form.add(new JLabel("姓名 / 昵称"));form.add(name); form.add(new JLabel("密码（至少 8 位）"));form.add(p); form.add(new JLabel("确认密码"));form.add(again);
        JDialog dialog=new JDialog(this,"注册访客账户",true);dialog.setContentPane(new JPanel(new BorderLayout(0,12)));((JPanel)dialog.getContentPane()).setBorder(new EmptyBorder(15,15,15,15));((JPanel)dialog.getContentPane()).add(form);((JPanel)dialog.getContentPane()).add(Theme.row(submit),BorderLayout.SOUTH);dialog.pack();dialog.setLocationRelativeTo(this);
        sendCode.addActionListener(e -> Tasks.run(dialog,sendCode,() -> {platform.sendRegistrationCode(email.getText());return true;},ok -> Theme.message(dialog,"验证码已发送，请检查邮箱（5 分钟内有效）。")));
        submit.addActionListener(e -> {String raw=new String(p.getPassword());if(!raw.equals(new String(again.getPassword()))){Theme.message(dialog,"两次密码输入不一致");return;}Tasks.run(dialog,submit,() -> {platform.registerVerified(u.getText(),email.getText(),raw,name.getText(),emailCode.getText());return u.getText();},value -> {dialog.dispose();username.setText(value);Theme.message(this,"注册成功，请使用账号和密码登录。");});});
        dialog.setVisible(true);
    }
    private static final class BrandPanel extends JPanel {
        BrandPanel() { setBackground(Theme.GREEN); setLayout(new BorderLayout()); setBorder(new EmptyBorder(52,42,45,30));
            JPanel top=Theme.stack(0); top.add(Theme.label("BENCAO  /  本草",16,new Color(221,230,205))); top.add(Box.createVerticalStrut(80)); JLabel heading=Theme.label("本草有序",38,Color.WHITE); heading.setFont(heading.getFont().deriveFont(Font.BOLD)); top.add(heading); top.add(Box.createVerticalStrut(14)); top.add(Theme.label("让药材管理，简单而清晰。",18,new Color(219,232,214))); add(top,BorderLayout.NORTH);
            JPanel bottom=Theme.stack(0); bottom.add(Theme.label("药材档案   /   库存流转   /   知识问答",13,new Color(219,232,214)));bottom.add(Box.createVerticalStrut(15));bottom.add(Theme.label("Java 程序设计项目实践 · 基础版 1.0",12,new Color(183,204,180)));add(bottom,BorderLayout.SOUTH);
        }
        @Override protected void paintComponent(Graphics graphics) { super.paintComponent(graphics); Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(new Color(64,113,81));g.setStroke(new BasicStroke(2));int x=235,y=385; g.drawArc(x-100,y-100,210,200,10,280);for(int i=0;i<6;i++){int yy=y-85+i*27;g.drawOval(x-60,yy,65,25);g.drawOval(x+5,yy+12,65,25);}g.drawLine(x+3,y-100,x+3,y+100);g.dispose(); }
    }
}
