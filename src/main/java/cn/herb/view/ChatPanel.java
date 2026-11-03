package cn.herb.view;

import cn.herb.controller.Tasks;
import cn.herb.model.*;
import cn.herb.service.Platform;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class ChatPanel extends JPanel {
    private final JTextArea transcript=new JTextArea();
    private final JTextField question=Theme.field(28);
    private final JButton send=Theme.button("发送问题",true);
    public ChatPanel(Platform platform,User user){
        super(new BorderLayout());JPanel page=Theme.page("本草知识问答","基于本地药材知识库的关键词匹配，支持查看当前账户的历史对话。");add(Theme.contentScroll(page));JPanel center=new JPanel(new BorderLayout(0,15));center.setOpaque(false);
        JPanel tips=Theme.row();for(String q:new String[]{"黄芪的功效","当归怎么保存","清热解毒"}){JButton b=Theme.button(q,false);b.addActionListener(e -> question.setText(q));tips.add(b);}JButton history=Theme.button("查看历史",false),clear=Theme.button("清空显示",false);tips.add(history);tips.add(clear);center.add(tips,BorderLayout.NORTH);
        transcript.setName("chat.transcript");transcript.setEditable(false);transcript.setLineWrap(true);transcript.setWrapStyleWord(true);transcript.setFont(Theme.BODY.deriveFont(15f));transcript.setForeground(Theme.INK);transcript.setMargin(new Insets(24,25,24,25));transcript.setText("本草助手\n\n你好，"+user.displayName()+"。可以询问药材的功效、用法和储存条件。\n例如：黄芪的功效是什么？\n\n──────────────────────────────\n");center.add(Theme.scroll(transcript));page.add(center);
        JPanel bottom=new JPanel(new BorderLayout(12,8));bottom.setOpaque(false);bottom.setBorder(new EmptyBorder(10,0,0,0));question.setName("chat.question");question.setToolTipText("输入药材名称或功效关键词，最多 500 字");send.setName("chat.send");bottom.add(question);bottom.add(send,BorderLayout.EAST);bottom.add(Theme.label("教学知识库示例，仅供课程演示。",12,Theme.MUTED),BorderLayout.SOUTH);page.add(bottom,BorderLayout.SOUTH);
        send.addActionListener(e -> {String q=question.getText();if(q.isBlank())return;question.setEnabled(false);Tasks.run(this,send,() -> platform.ask(user,q),answer -> {transcript.append("\n你\n"+q+"\n\n本草助手\n"+answer+"\n──────────────────────────────\n");transcript.setCaretPosition(transcript.getDocument().getLength());question.setText("");question.requestFocusInWindow();});});
        send.addPropertyChangeListener("enabled",e -> question.setEnabled(send.isEnabled()));question.addActionListener(e -> {if(send.isEnabled())send.doClick();});clear.addActionListener(e -> transcript.setText("显示已清空，已保存的历史记录仍可查看。\n"));
        history.addActionListener(e -> Tasks.run(this,history,() -> platform.history(user),records -> {if(records.isEmpty()){Theme.message(this,"当前账户暂无问答历史");return;}StringBuilder text=new StringBuilder("最近 100 条问答记录（从新到旧）\n\n");for(ChatRecord r:records)text.append(r.createdAt()).append("\n你：").append(r.question()).append("\n\n本草助手：\n").append(r.answer()).append("\n──────────────────────────────\n\n");JTextArea area=new JTextArea(text.toString(),23,55);area.setFont(Theme.BODY);area.setLineWrap(true);area.setWrapStyleWord(true);area.setEditable(false);area.setMargin(new Insets(15,15,15,15));area.setCaretPosition(0);JOptionPane.showMessageDialog(this,Theme.scroll(area),"我的问答历史",JOptionPane.PLAIN_MESSAGE);}));
    }
}
