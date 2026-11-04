package cn.herb.util;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/** QQ SMTP 邮箱验证码。授权码只从运行时配置读取，不落盘到源码。 */
public final class EmailCodeService {
    private static final Duration CODE_TTL=Duration.ofMinutes(5);
    private static final Duration SEND_INTERVAL=Duration.ofSeconds(60);
    private final String host, port, username, password;
    private final Map<String, Code> codes=new ConcurrentHashMap<>();
    private final SecureRandom random=new SecureRandom();
    public EmailCodeService(String host,String port,String username,String password) {
        this.host=host;this.port=port;this.username=username;this.password=password;
    }
    public boolean enabled() { return validEmail(username) && password!=null && !password.isBlank(); }
    public void send(String recipient,String key) {
        if(!enabled()) throw new IllegalStateException("尚未配置 QQ SMTP 发件邮箱和授权码，请填写 config.properties");
        if(!validEmail(recipient)) throw new IllegalArgumentException("请输入有效的邮箱地址");
        Instant now=Instant.now(); Code previous=codes.get(key);
        if(previous!=null && Duration.between(previous.sentAt(),now).compareTo(SEND_INTERVAL)<0) throw new IllegalArgumentException("验证码发送过于频繁，请稍后再试");
        String value=String.format("%06d",random.nextInt(1_000_000));
        Properties props=new Properties();props.put("mail.smtp.host",host);props.put("mail.smtp.port",port);props.put("mail.smtp.auth","true");props.put("mail.smtp.ssl.enable","465".equals(port));props.put("mail.smtp.starttls.enable","587".equals(port));props.put("mail.smtp.connectiontimeout","10000");props.put("mail.smtp.timeout","20000");props.put("mail.smtp.writetimeout","20000");
        Session session=Session.getInstance(props,new Authenticator(){@Override protected PasswordAuthentication getPasswordAuthentication(){return new PasswordAuthentication(username,password);}});
        try {
            Message message=new MimeMessage(session);message.setFrom(new InternetAddress(username));message.setRecipients(Message.RecipientType.TO,InternetAddress.parse(recipient));message.setSubject("本草管理平台注册验证码");message.setText("你的注册验证码是："+value+"\n\n验证码 5 分钟内有效，请勿转发给他人。");Transport.send(message);codes.put(key,new Code(value,now));
        } catch(MessagingException e) { throw new IllegalStateException("验证码发送失败，请检查 QQ SMTP 配置或网络连接",e); }
    }
    public void verify(String key,String value) {
        Code code=codes.get(key);if(code==null || Duration.between(code.sentAt(),Instant.now()).compareTo(CODE_TTL)>0){codes.remove(key);throw new IllegalArgumentException("验证码不存在或已过期，请重新获取");}
        if(value==null || !code.value().equals(value.trim())) throw new IllegalArgumentException("邮箱验证码不正确");codes.remove(key);
    }
    public static boolean validEmail(String value) { return value!=null && value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"); }
    private record Code(String value,Instant sentAt) {}
}
