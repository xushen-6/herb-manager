package cn.herb.view;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;

public final class Theme {
    public static final Color GREEN=new Color(30,85,65), ACCENT=new Color(188,148,83), BG=new Color(245,247,243), INK=new Color(34,48,42), MUTED=new Color(113,126,117), LINE=new Color(222,229,220);
    public static final Font BODY=new Font("Microsoft YaHei UI",Font.PLAIN,14);
    private Theme() {}
    public static void install() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch(Exception ignored) {}
        for(Object key:UIManager.getDefaults().keySet().toArray()) if(UIManager.get(key) instanceof Font) UIManager.put(key,BODY);
        UIManager.put("Panel.background",BG); UIManager.put("Label.foreground",INK);
        UIManager.put("OptionPane.background",BG); UIManager.put("Table.selectionBackground",new Color(224,237,227)); UIManager.put("Table.selectionForeground",INK);
        UIManager.put("TextField.selectionBackground",new Color(198,222,203));
    }
    public static JLabel label(String text,int size,Color color) { JLabel l=new JLabel(text); l.setFont(BODY.deriveFont((float)size)); l.setForeground(color); return l; }
    public static JLabel title(String text) { JLabel l=label(text,25,INK); l.setFont(l.getFont().deriveFont(Font.BOLD)); return l; }
    public static JButton button(String text,boolean primary) {
        JButton b=new JButton(text); b.setFont(BODY); b.setFocusPainted(false); b.setOpaque(true); b.setBackground(primary?GREEN:Color.WHITE); b.setForeground(primary?Color.WHITE:GREEN); b.setBorder(new CompoundBorder(new LineBorder(primary?GREEN:LINE),new EmptyBorder(9,17,9,17))); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); return b;
    }
    public static JTextField field(int columns) { JTextField f=new JTextField(columns); input(f); return f; }
    public static void input(JComponent c) { c.setFont(BODY); c.setBorder(new CompoundBorder(new LineBorder(LINE),new EmptyBorder(9,10,9,10))); }
    public static JPanel row(Component... cs) { JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,10,5)); p.setOpaque(false); for(Component c:cs)p.add(c); return p; }
    public static JPanel stack(int gap) { JPanel p=new JPanel(); p.setOpaque(false); p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS)); return p; }
    public static JPanel page(String title,String subtitle) {
        JPanel p=new JPanel(new BorderLayout(0,20)); p.setBorder(new EmptyBorder(26,28,24,28));
        JPanel head=stack(0); head.add(title(title)); head.add(Box.createVerticalStrut(7)); head.add(label(subtitle,13,MUTED)); p.add(head,BorderLayout.NORTH); return p;
    }
    public static JScrollPane scroll(Component c) { JScrollPane s=new JScrollPane(c); s.setBorder(new LineBorder(LINE)); s.getVerticalScrollBar().setUnitIncrement(20); return s; }
    public static JTable table(String... columns) {
        JTable t=new JTable(new DefaultTableModel(columns,0) { @Override public boolean isCellEditable(int r,int c){return false;} });
        t.setRowHeight(43); t.setFont(BODY); t.setShowVerticalLines(false); t.setGridColor(LINE); t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); t.setAutoCreateRowSorter(true); t.setFillsViewportHeight(true); t.setBackground(Color.WHITE);
        t.getTableHeader().setFont(BODY.deriveFont(Font.BOLD,13)); t.getTableHeader().setPreferredSize(new Dimension(0,42)); t.getTableHeader().setBackground(new Color(233,239,231)); t.getTableHeader().setForeground(GREEN);
        DefaultTableCellRenderer r=new DefaultTableCellRenderer() { @Override public Component getTableCellRendererComponent(JTable table,Object v,boolean selected,boolean focused,int row,int col) { super.getTableCellRendererComponent(table,v,selected,focused,row,col); setBorder(new EmptyBorder(0,10,0,8)); if(!selected) setBackground(row%2==0?Color.WHITE:new Color(249,251,248)); return this; } };
        t.setDefaultRenderer(Object.class,r); return t;
    }
    public static void rows(JTable table,java.util.List<Object[]> rows) { DefaultTableModel m=(DefaultTableModel)table.getModel(); m.setRowCount(0); for(Object[] row:rows)m.addRow(row); }
    public static void message(Component owner,String text) { JOptionPane.showMessageDialog(owner,text,"操作完成",JOptionPane.INFORMATION_MESSAGE); }
    public static boolean confirm(Component owner,String text) { return JOptionPane.showConfirmDialog(owner,text,"确认操作",JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE)==JOptionPane.YES_OPTION; }
}
