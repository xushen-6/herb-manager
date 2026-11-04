package cn.herb.service;

import cn.herb.model.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** UTF-8 表单协议，正确保留中文、换行和长回答，客户端与服务端共用。 */
public final class Wire {
    private Wire() {}
    public static Map<String,String> fields(Object... values) {
        Map<String,String> map=new LinkedHashMap<>();
        for(int i=0;i<values.length;i+=2) map.put(values[i].toString(), Objects.toString(values[i+1],""));
        return map;
    }
    public static String encode(Map<String,String> map) {
        StringJoiner out=new StringJoiner("&");
        map.forEach((k,v)->out.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));
        return out.toString();
    }
    public static Map<String,String> decode(String text) {
        Map<String,String> result=new LinkedHashMap<>();
        if(!text.isEmpty()) for(String field:text.split("&")) {
            String[] pair=field.split("=",2);
            String key=URLDecoder.decode(pair[0],StandardCharsets.UTF_8);
            if(result.put(key,URLDecoder.decode(pair.length==2?pair[1]:"",StandardCharsets.UTF_8))!=null)
                throw new IllegalArgumentException("重复的请求字段");
        }
        return result;
    }
    public static Map<String,String> user(User u) { return fields("id",u.id(),"username",u.username(),"email",u.email(),"name",u.displayName(),"role",u.role().name()); }
    public static User user(Map<String,String> m) { return new User(Long.parseLong(m.get("id")),m.get("username"),m.get("email"),m.get("name"),Role.valueOf(m.get("role"))); }
    public static Map<String,String> medicinal(Medicinal m) { return fields("id",m.id(),"code",m.code(),"name",m.name(),"category",m.category(),"origin",m.origin(),"stock",m.stock(),"price",m.price(),"efficacy",m.efficacy(),"usage",m.usage(),"storage",m.storage(),"threshold",m.threshold()); }
    public static Medicinal medicinal(Map<String,String> m) { return new Medicinal(Long.parseLong(m.get("id")),m.get("code"),m.get("name"),m.get("category"),m.get("origin"),Integer.parseInt(m.get("stock")),new BigDecimal(m.get("price")),m.get("efficacy"),m.get("usage"),m.get("storage"),Integer.parseInt(m.get("threshold"))); }
    public static Map<String,String> list(List<Map<String,String>> rows) {
        Map<String,String> out=fields("count",rows.size());
        for(int i=0;i<rows.size();i++) { final String prefix=i+"."; rows.get(i).forEach((k,v)->out.put(prefix+k,v)); }
        return out;
    }
    public static List<Map<String,String>> list(Map<String,String> encoded) {
        int count=Integer.parseInt(encoded.getOrDefault("count","0"));
        if(count<0 || count>100000) throw new IllegalArgumentException("返回记录数异常");
        List<Map<String,String>> rows=new ArrayList<>();
        for(int i=0;i<count;i++) {
            String prefix=i+"."; Map<String,String> row=new LinkedHashMap<>();
            encoded.forEach((k,v)->{if(k.startsWith(prefix)) row.put(k.substring(prefix.length()),v);}); rows.add(row);
        }
        return rows;
    }
}
