package cn.herb.controller;

import javax.swing.*;
import java.awt.Component;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** 数据库任务离开 EDT，结果在 EDT 更新视图。 */
public final class Tasks {
    private Tasks() {}
    public static <T> void run(Component owner,JButton trigger,Callable<T> task,Consumer<T> success) {
        if(trigger!=null) trigger.setEnabled(false);
        new SwingWorker<T,Void>() {
            @Override protected T doInBackground() throws Exception { return task.call(); }
            @Override protected void done() {
                if(trigger!=null) trigger.setEnabled(true);
                try { success.accept(get()); }
                catch(Exception e) {
                    Throwable cause=e; while(cause.getCause()!=null && !(cause instanceof IllegalArgumentException) && !(cause instanceof SecurityException)) cause=cause.getCause();
                    JOptionPane.showMessageDialog(owner,cause.getMessage()==null?"操作失败，请稍后重试":cause.getMessage(),"操作提示",JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }
}
