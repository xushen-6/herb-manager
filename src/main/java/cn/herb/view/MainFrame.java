package cn.herb.view;

import cn.herb.controller.Tasks;
import cn.herb.model.*;
import cn.herb.service.Platform;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.util.*;

public final class MainFrame extends JFrame {
    private final Platform platform;
    private User user;
    private final String mode;
    private final JPanel content=new JPanel(new BorderLayout());
    private final JLabel identity=new JLabel();
    private final Map<String,JButton> navigation=new LinkedHashMap<>();
    public MainFrame(Platform platform,User user,String mode) {
        super("本草 · 中药材智能管理平台"); this.platform=platform;this.user=user;this.mode=mode;
        setDefaultCloseOperation(EXIT_ON_CLOSE);setSize(1220,800);setMinimumSize(new Dimension(1060,700));setLocationRelativeTo(null);
        JPanel root=new JPanel(new BorderLayout());setContentPane(root);
        JPanel sidebar=new JPanel(new BorderLayout()); sidebar.setBackground(Theme.GREEN);sidebar.setPreferredSize(new Dimension(198,0));sidebar.setBorder(new EmptyBorder(28,16,24,16));
        JPanel brand=Theme.stack(0);brand.add(Theme.label("本 草",29,Color.WHITE));brand.add(Box.createVerticalStrut(7));brand.add(Theme.label("中药材智能管理平台",12,new Color(198,217,194)));brand.add(Box.createVerticalStrut(38));sidebar.add(brand,BorderLayout.NORTH);
        JPanel nav=Theme.stack(0);sidebar.add(nav,BorderLayout.CENTER);
        addNav(nav,"工作台",this::dashboard);addNav(nav,"药材管理",() -> show("药材管理",new MedicinalPanel(platform,user)));
        if(user.role().canEdit()) addNav(nav,"库存流水",() -> logs(false));
        addNav(nav,"智能问答",() -> show("智能问答",new ChatPanel(platform,user)));
        if(user.role()==Role.ADMIN) { addNav(nav,"用户管理",this::users);addNav(nav,"登录日志",() -> logs(true)); }
        addNav(nav,"个人资料",this::profile);
        JPanel foot=Theme.stack(0);JButton logout=Theme.button("退出登录",false);logout.addActionListener(e -> logout());foot.add(logout);foot.add(Box.createVerticalStrut(17));foot.add(Theme.label("JAVA SWING  /  v1.0",11,new Color(183,204,180)));sidebar.add(foot,BorderLayout.SOUTH);root.add(sidebar,BorderLayout.WEST);
        JPanel center=new JPanel(new BorderLayout());JPanel top=new JPanel(new BorderLayout());top.setBackground(Color.WHITE);top.setBorder(new EmptyBorder(16,28,16,28));top.add(Theme.label("本草档案  /  日常管理",13,Theme.MUTED),BorderLayout.WEST);identity.setFont(Theme.BODY);identity.setForeground(Theme.GREEN);updateIdentity();top.add(identity,BorderLayout.EAST);center.add(top,BorderLayout.NORTH);center.add(content);root.add(center);
        JPanel status=new JPanel(new BorderLayout());status.setBorder(new EmptyBorder(8,28,8,28));status.add(Theme.label("数据库："+mode,11,Theme.MUTED),BorderLayout.WEST);status.add(Theme.label("单位：库存（克） · 单价（元 / 克）",11,Theme.MUTED),BorderLayout.EAST);center.add(status,BorderLayout.SOUTH);
        JMenuBar menu=new JMenuBar();JMenu system=new JMenu("系统"),manage=new JMenu("业务");menu.add(system);menu.add(manage);
        for(String label:navigation.keySet()){JMenuItem item=new JMenuItem(label);item.addActionListener(e -> navigation.get(label).doClick());manage.add(item);}
        JMenuItem out=new JMenuItem("退出登录"), exit=new JMenuItem("退出系统");out.addActionListener(e -> logout());exit.addActionListener(e -> {if(Theme.confirm(this,"确定退出系统？"))dispose();});system.add(out);system.add(exit);setJMenuBar(menu);
        dashboard();
    }
    private void updateIdentity(){identity.setText(user.displayName()+"   /   "+user.role());}
    private void addNav(JPanel parent,String label,Runnable action){JButton b=Theme.button(label,false);b.setName("nav."+label);b.setAlignmentX(Component.LEFT_ALIGNMENT);b.setMaximumSize(new Dimension(165,45));b.setHorizontalAlignment(SwingConstants.LEFT);b.setBorder(new EmptyBorder(12,17,12,10));b.addActionListener(e -> action.run());parent.add(b);parent.add(Box.createVerticalStrut(10));navigation.put(label,b);}
    private void show(String key,JPanel panel){for(var entry:navigation.entrySet()){boolean active=entry.getKey().equals(key);entry.getValue().setBackground(active?new Color(230,239,222):Theme.GREEN);entry.getValue().setForeground(active?Theme.GREEN:new Color(220,234,216));}content.removeAll();content.add(panel);content.revalidate();content.repaint();}
    private void logout(){if(!Theme.confirm(this,"退出当前账户并返回登录界面？"))return;dispose();new LoginFrame(platform,mode).setVisible(true);}
    private void dashboard(){
        JPanel page=Theme.page("药材管理，一目了然",LocalDate.now()+"   /   你好，"+user.displayName()+"。查看库存状况，开始今天的工作。");
        JPanel body=new JPanel(new BorderLayout(0,24));body.setOpaque(false);JPanel stats=new JPanel(new GridLayout(1,4,16,0));stats.setOpaque(false);body.add(stats,BorderLayout.NORTH);
        JTable table=Theme.table("药材编号","药材名称","当前库存 / 克","预警阈值 / 克","状态");JPanel warnings=new JPanel(new BorderLayout(0,12));warnings.setOpaque(false);warnings.add(Theme.row(Theme.label("库存关注",18,Theme.GREEN),Theme.label("低于预警阈值的药材",12,Theme.MUTED)),BorderLayout.NORTH);warnings.add(Theme.scroll(table));body.add(warnings);
        JPanel actions=Theme.row();JButton herbs=Theme.button("进入药材管理 →",true),chat=Theme.button("打开知识问答",false),refresh=Theme.button("刷新概览",false);herbs.addActionListener(e -> navigation.get("药材管理").doClick());chat.addActionListener(e -> navigation.get("智能问答").doClick());refresh.addActionListener(e -> dashboard());actions.add(herbs);actions.add(chat);actions.add(refresh);body.add(actions,BorderLayout.SOUTH);page.add(body);show("工作台",page);
        Tasks.run(this,null,() -> platform.medicinals("","全部"),list -> {
            if(!page.isShowing())return;
            long low=list.stream().filter(Medicinal::lowStock).count();long qty=list.stream().mapToLong(Medicinal::stock).sum();long cats=list.stream().map(Medicinal::category).distinct().count();
            stats.add(metric("药材档案",String.valueOf(list.size()),"种在册药材"));stats.add(metric("库存总量",String.format("%,d",qty),"克 · 实时库存"));stats.add(metric("库存预警",String.valueOf(low),"种需要关注"));stats.add(metric("药材分类",String.valueOf(cats),"类已收录"));
            Theme.rows(table,list.stream().filter(Medicinal::lowStock).map(m -> new Object[]{m.code(),m.name(),m.stock(),m.threshold(),"待补货"}).toList());
            stats.revalidate();stats.repaint();
        });
    }
    private JPanel metric(String label,String value,String hint){JPanel p=Theme.stack(0);p.setOpaque(true);p.setBackground(Color.WHITE);p.setBorder(new EmptyBorder(22,22,22,18));p.add(Theme.label(label,13,Theme.MUTED));p.add(Box.createVerticalStrut(12));JLabel n=Theme.label(value,32,Theme.GREEN);n.setFont(n.getFont().deriveFont(Font.BOLD));p.add(n);p.add(Box.createVerticalStrut(6));p.add(Theme.label(hint,12,Theme.MUTED));return p;}
    private void logs(boolean login){JPanel p=Theme.page(login?"登录日志":"库存流水",login?"记录成功登录的账号与时间，最近 300 条":"每次入出库与库存变更在同一事务中保存，最近 300 条");JTable table=login?Theme.table("序号","登录账号","登录时间"):Theme.table("药材编号","药材名称","变动 / 克","结余 / 克","操作账号","备注","操作时间");p.add(Theme.scroll(table));JButton refresh=Theme.button("刷新记录",false);refresh.addActionListener(e -> Tasks.run(this,refresh,() -> login?platform.loginLogs(user):platform.stockLogs(user),rows -> Theme.rows(table,rows)));p.add(Theme.row(refresh),BorderLayout.SOUTH);show(login?"登录日志":"库存流水",p);refresh.doClick();}
    private void users(){
        JPanel page=Theme.page("用户管理","管理员可以新增或删除账户。公开注册的账户始终为访客。");JTable table=Theme.table("用户 ID","账号","姓名 / 昵称","角色");page.add(Theme.scroll(table));java.util.List<User> records=new ArrayList<>();JButton refresh=Theme.button("刷新",false),add=Theme.button("新增用户",true),remove=Theme.button("删除用户",false);
        refresh.addActionListener(e -> Tasks.run(this,refresh,() -> platform.users(user),list -> {records.clear();records.addAll(list);Theme.rows(table,list.stream().map(u -> new Object[]{u.id(),u.username(),u.displayName(),u.role()}).toList());}));
        add.addActionListener(e -> {JTextField account=Theme.field(18),name=Theme.field(18);JPasswordField pw=new JPasswordField(18);Theme.input(pw);JComboBox<Role> role=new JComboBox<>(Role.values());role.setSelectedItem(Role.GUEST);JPanel form=new JPanel(new GridLayout(0,2,12,12));form.add(new JLabel("账号"));form.add(account);form.add(new JLabel("姓名 / 昵称"));form.add(name);form.add(new JLabel("初始密码（至少 8 位）"));form.add(pw);form.add(new JLabel("角色"));form.add(role);
            if(JOptionPane.showConfirmDialog(this,form,"新增用户",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
            String raw=new String(pw.getPassword());Tasks.run(this,add,() -> {platform.createUser(user,account.getText(),raw,name.getText(),(Role)role.getSelectedItem());return true;},ok -> {refresh.doClick();Theme.message(this,"用户已创建");});});
        remove.addActionListener(e -> {int index=table.getSelectedRow();if(index<0){Theme.message(this,"请先选择用户");return;}User selected=records.get(table.convertRowIndexToModel(index));if(!Theme.confirm(this,"删除账户 "+selected.username()+"？该账户的问答历史也将删除。"))return;Tasks.run(this,remove,() -> {platform.deleteUser(user,selected.id());return true;},ok -> refresh.doClick());});
        page.add(Theme.row(add,remove,refresh),BorderLayout.SOUTH);show("用户管理",page);refresh.doClick();
    }
    private void profile(){
        JPanel page=Theme.page("个人资料","维护显示姓名。如需修改密码，请同时输入原密码和新密码。");JPanel wrap=new JPanel(new BorderLayout());wrap.setOpaque(false);JPanel form=new JPanel(new GridBagLayout());form.setOpaque(false);GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(10,10,10,10);g.anchor=GridBagConstraints.WEST;
        JTextField name=Theme.field(22);name.setText(user.displayName());JPasswordField old=new JPasswordField(22),pw=new JPasswordField(22),again=new JPasswordField(22);Theme.input(old);Theme.input(pw);Theme.input(again);
        Component[][] rows={{new JLabel("账号"),new JLabel(user.username())},{new JLabel("当前角色"),new JLabel(user.role().toString())},{new JLabel("姓名 / 昵称"),name},{new JLabel("原密码"),old},{new JLabel("新密码"),pw},{new JLabel("确认新密码"),again}};
        for(int i=0;i<rows.length;i++){g.gridy=i;g.gridx=0;form.add(rows[i][0],g);g.gridx=1;form.add(rows[i][1],g);}JButton save=Theme.button("保存资料",true);g.gridy=rows.length;g.gridx=1;form.add(save,g);wrap.add(form,BorderLayout.NORTH);page.add(wrap);show("个人资料",page);
        save.addActionListener(e -> {String next=new String(pw.getPassword()), previous=new String(old.getPassword());if(!next.equals(new String(again.getPassword()))){Theme.message(this,"两次新密码不一致");return;}Tasks.run(this,save,() -> platform.updateProfile(user,name.getText(),previous,next),updated -> {user=updated;updateIdentity();old.setText("");pw.setText("");again.setText("");Theme.message(this,"资料已保存");});});
    }
}
