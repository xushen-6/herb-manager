package cn.herb.service;

import cn.herb.model.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** 桌面端只持有 HTTPS 地址和短期会话，不保存数据库、SMTP 或 AI 密钥。 */
public final class RemotePlatform {
    private final String base;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile String token="";
    private boolean ai;
    public RemotePlatform(String url) {
        base=url.replaceAll("/+$",""); URI uri=URI.create(base);
        if(!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) && Set.of("127.0.0.1","localhost").contains(uri.getHost())))
            throw new IllegalArgumentException("服务器地址必须使用 HTTPS");
    }
    public Map<String,String> call(String operation,Map<String,String> fields) {
        var request=HttpRequest.newBuilder(URI.create(base+"/"+operation)).timeout(Duration.ofSeconds(60))
                .header("Content-Type","application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(Wire.encode(fields)));
        if(!token.isEmpty()) request.header("Authorization","Bearer "+token);
        try {
            var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            Map<String,String> body=Wire.decode(response.body());
            if(response.statusCode()!=200) throw new IllegalArgumentException(body.getOrDefault("error","服务器暂时不可用（HTTP "+response.statusCode()+"）"));
            return body;
        } catch(java.io.IOException e) { throw new IllegalStateException("无法连接服务器，请检查网络后重试"); }
        catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException("请求已中断"); }
    }
    public void initialize() { ai=Boolean.parseBoolean(call("health",Map.of()).get("ai")); }
    public boolean aiEnabled() { return ai; }
    public User login(String account,String password) {
        var data=call("login",Wire.fields("username",account,"password",password)); token=data.get("token"); return Wire.user(data);
    }
    public void logout() {
        try {if(!token.isEmpty()) call("logout",Map.of());}
        catch(RuntimeException ignored) { /* 服务重启或断网时仍允许用户退出本地界面。 */ }
        finally {token="";}
    }
    public List<Medicinal> medicinals(String keyword,String category) { return Wire.list(call("medicinals",Wire.fields("keyword",keyword,"category",category))).stream().map(Wire::medicinal).toList(); }
    public List<User> users() { return Wire.list(call("users",Map.of())).stream().map(Wire::user).toList(); }
    public List<ChatRecord> history() { return Wire.list(call("history",Map.of())).stream().map(m->new ChatRecord(Long.parseLong(m.get("id")),m.get("question"),m.get("answer"),m.get("createdAt"))).toList(); }
    public List<Object[]> logs(boolean login) { return Wire.list(call(login?"login-logs":"stock-logs",Map.of())).stream().map(m->{Object[] row=new Object[login?3:7];for(int i=0;i<row.length;i++)row[i]=m.get("c"+i);return row;}).toList(); }
}
