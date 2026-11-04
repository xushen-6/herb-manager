package cn.herb.server;

import cn.herb.model.*;
import cn.herb.service.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** 仅监听回环地址，由 Nginx 提供 HTTPS；身份取自服务器会话。 */
public final class BackendServer implements AutoCloseable {
    private record Session(User user,Instant expires) {}
    private final Platform platform;
    private final HttpServer server;
    private final ExecutorService executor=Executors.newFixedThreadPool(8);
    private final ConcurrentMap<String,Session> sessions=new ConcurrentHashMap<>();
    private final ConcurrentMap<String,java.util.ArrayDeque<Long>> attempts=new ConcurrentHashMap<>();
    private final SecureRandom random=new SecureRandom();
    public BackendServer(Platform platform,int port) throws IOException {
        this.platform=platform;server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),32);
        server.createContext("/",this::handle);server.setExecutor(executor);
    }
    public void start() {server.start();}
    public int port() {return server.getAddress().getPort();}
    public void close() {server.stop(0);executor.shutdownNow();}
    private void handle(HttpExchange exchange) throws IOException {
        int status=200;Map<String,String> result;
        try {
            if(!exchange.getRequestMethod().equals("POST")) throw new IllegalArgumentException("请使用 POST 请求");
            byte[] body=exchange.getRequestBody().readNBytes(65537);
            if(body.length>65536) throw new IllegalArgumentException("请求内容过长");
            String route=exchange.getRequestURI().getPath();
            Map<String,String> data=Wire.decode(new String(body,StandardCharsets.UTF_8));
            if(Set.of("/login","/send-code","/register").contains(route)) limit(exchange,route);
            result=dispatch(route,data,exchange.getRequestHeaders().getFirst("Authorization"));
        } catch(SecurityException e) {status=401;result=Wire.fields("error",e.getMessage());}
        catch(IllegalArgumentException e) {status=400;result=Wire.fields("error",e.getMessage()==null?"请求参数不正确":e.getMessage());}
        catch(Exception e) {status=500;result=Wire.fields("error","服务器处理失败，请稍后重试");System.err.println("REQUEST_FAILED "+e.getClass().getSimpleName());}
        byte[] response=Wire.encode(result).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control","no-store");
        exchange.sendResponseHeaders(status,response.length);
        try(OutputStream out=exchange.getResponseBody()){out.write(response);}finally{exchange.close();}
    }
    private void limit(HttpExchange exchange,String route) {
        String address=exchange.getRequestHeaders().getFirst("X-Real-IP");
        if(address==null) address=exchange.getRemoteAddress().getAddress().getHostAddress();
        long now=System.currentTimeMillis();
        if(attempts.size()>10000) attempts.entrySet().removeIf(e->{synchronized(e.getValue()){return e.getValue().isEmpty() || e.getValue().peekLast()<now-60000;}});
        var times=attempts.computeIfAbsent(address+route,k->new java.util.ArrayDeque<>());
        synchronized(times){while(!times.isEmpty() && times.peekFirst()<now-60000)times.removeFirst();if(times.size()>=(route.equals("/send-code")?5:30))throw new IllegalArgumentException("请求过于频繁，请一分钟后重试");times.addLast(now);}
    }
    private Map<String,String> dispatch(String route,Map<String,String> d,String authorization) {
        if(route.equals("/health")) return Wire.fields("status","ok","ai",platform.aiEnabled());
        if(route.equals("/send-code")){platform.sendRegistrationCode(d.get("email"));return Map.of();}
        if(route.equals("/register")){platform.registerVerified(d.get("username"),d.get("email"),d.get("password"),d.get("name"),d.get("code"));return Map.of();}
        if(route.equals("/login")) {
            User user=platform.login(d.get("username"),d.get("password"));
            sessions.entrySet().removeIf(e->!e.getValue().expires().isAfter(Instant.now()));
            if(sessions.size()>=10000)throw new IllegalArgumentException("当前会话过多，请稍后重试");
            byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            sessions.put(token,new Session(user,Instant.now().plusSeconds(43200)));
            var result=Wire.user(user);result.put("token",token);return result;
        }
        String token=authorization!=null && authorization.startsWith("Bearer ")?authorization.substring(7):"";
        Session session=sessions.get(token);
        if(session==null || !session.expires().isAfter(Instant.now())){sessions.remove(token);throw new SecurityException("登录已过期，请重新登录");}
        User user=session.user();
        return switch(route) {
            case "/logout" -> {sessions.remove(token);yield Map.of();}
            case "/medicinals" -> Wire.list(platform.medicinals(d.get("keyword"),d.get("category")).stream().map(Wire::medicinal).toList());
            case "/save-medicinal" -> {platform.saveMedicinal(user,Wire.medicinal(d));yield Map.of();}
            case "/delete-medicinal" -> {platform.deleteMedicinal(user,Long.parseLong(d.get("id")));yield Map.of();}
            case "/adjust-stock" -> {platform.adjustStock(user,Long.parseLong(d.get("id")),Integer.parseInt(d.get("delta")),d.get("note"));yield Map.of();}
            case "/users" -> Wire.list(platform.users(user).stream().map(Wire::user).toList());
            case "/create-user" -> {platform.createUser(user,d.get("username"),d.get("password"),d.get("name"),Role.valueOf(d.get("role")));yield Map.of();}
            case "/delete-user" -> {platform.deleteUser(user,Long.parseLong(d.get("id")));yield Map.of();}
            case "/profile" -> Wire.user(platform.updateProfile(user,d.get("name"),d.get("oldPassword"),d.get("newPassword")));
            case "/ask" -> Wire.fields("answer",platform.ask(user,d.get("question")));
            case "/history" -> Wire.list(platform.history(user).stream().map(r->Wire.fields("id",r.id(),"question",r.question(),"answer",r.answer(),"createdAt",r.createdAt())).toList());
            case "/login-logs" -> logs(platform.loginLogs(user));
            case "/stock-logs" -> logs(platform.stockLogs(user));
            default -> throw new IllegalArgumentException("接口不存在");
        };
    }
    private Map<String,String> logs(List<Object[]> rows) {
        return Wire.list(rows.stream().map(row->{Map<String,String> m=new LinkedHashMap<>();for(int i=0;i<row.length;i++)m.put("c"+i,Objects.toString(row[i],""));return m;}).toList());
    }
}
