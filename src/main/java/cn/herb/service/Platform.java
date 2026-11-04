package cn.herb.service;

import cn.herb.dao.*;
import cn.herb.model.*;
import cn.herb.util.Passwords;
import cn.herb.util.EmailCodeService;
import cn.herb.util.ImageCaptcha;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** 业务与权限统一入口，视图不能直接修改 DAO。 */
public final class Platform {
    public static final String[] CATEGORIES={"清热","理气","补血","利湿","补气","解表","安神","其他"};
    private final Database db;
    private final JdbcDao dao;
    private final EmailCodeService emailCodes;
    private final String defaultEmail;
    private final DeepSeekClient deepSeek;
    private final RemotePlatform remote;
    public Platform(RemotePlatform remote) { this.remote=Objects.requireNonNull(remote);db=null;dao=null;emailCodes=null;defaultEmail="";deepSeek=null; }
    public Platform(Database db) { this(db,new EmailCodeService("","","", ""),"test@example.com", null); }
    public Platform(Database db,EmailCodeService emailCodes,String defaultEmail) { this(db,emailCodes,defaultEmail,null); }
    public Platform(Database db,EmailCodeService emailCodes,String defaultEmail,DeepSeekClient deepSeek) { this.remote=null;this.db=db;dao=new JdbcDao(db);this.emailCodes=emailCodes;this.defaultEmail=defaultEmail;this.deepSeek=deepSeek; }
    public boolean aiEnabled() { return remote!=null?remote.aiEnabled():deepSeek!=null && deepSeek.enabled(); }
    public boolean remoteEnabled() { return remote!=null; }
    public void logout() { if(remote!=null) remote.logout(); }
    public void initialize() {
        if(remote!=null){remote.initialize();return;}
        db.initialize();
        if(dao.query("SELECT COUNT(*) FROM app_user",r -> r.getLong(1)).get(0)==0) {
            insertUser("admin",defaultEmail,"123456","系统管理员",Role.ADMIN);
            insertUser("yaoshi",defaultEmail,"123456","值班药师",Role.PHARMACIST);
            insertUser("123456",defaultEmail,"123456","体验访客",Role.GUEST);
        }
        migrateDemoAccounts();
        dao.update("UPDATE app_user SET email=? WHERE email IS NULL OR TRIM(email)=''",defaultEmail);
        // 演示种子仅在首次建库时写入；删除全部药材后不会再次自动恢复。
        dao.update("CREATE TABLE IF NOT EXISTS app_meta (meta_key VARCHAR(50) PRIMARY KEY, meta_value VARCHAR(100))");
        if(dao.query("SELECT meta_value FROM app_meta WHERE meta_key='seeded'",r -> r.getString(1)).isEmpty()) {
            if(medicinals("","全部").isEmpty()) seedHerbs();
            dao.update("INSERT INTO app_meta(meta_key,meta_value) VALUES('seeded','true')");
        }
    }
    private void migrateDemoAccounts() {
        migrateDemoAccount("pharmacist","yaoshi",Role.PHARMACIST);
        migrateDemoAccount("guest","123456",Role.GUEST);
        dao.update("UPDATE app_user SET password_hash=?, role=?, email=COALESCE(NULLIF(TRIM(email),''), ?) WHERE username='admin'",Passwords.md5("123456"),Role.ADMIN.name(),defaultEmail);
        dao.update("UPDATE app_user SET password_hash=?, role=?, email=COALESCE(NULLIF(TRIM(email),''), ?) WHERE username='yaoshi'",Passwords.md5("123456"),Role.PHARMACIST.name(),defaultEmail);
        dao.update("UPDATE app_user SET password_hash=?, role=?, email=COALESCE(NULLIF(TRIM(email),''), ?) WHERE username='123456'",Passwords.md5("123456"),Role.GUEST.name(),defaultEmail);
    }
    private void migrateDemoAccount(String legacy, String target, Role role) {
        List<Long> oldIds=dao.query("SELECT id FROM app_user WHERE username=?",r -> r.getLong(1),legacy);
        if(oldIds.isEmpty()) return;
        List<Long> targetIds=dao.query("SELECT id FROM app_user WHERE username=?",r -> r.getLong(1),target);
        long oldId=oldIds.get(0);
        if(targetIds.isEmpty()) {
            dao.update("UPDATE app_user SET username=?, role=? WHERE id=?",target,role.name(),oldId);
        } else if(targetIds.get(0)!=oldId) {
            long targetId=targetIds.get(0);
            dao.update("UPDATE chat_history SET user_id=? WHERE user_id=?",targetId,oldId);
            dao.update("DELETE FROM app_user WHERE id=?",oldId);
        }
    }
    private void seedHerbs() {
        String[][] rows={
            {"HQ001","黄芪","补气","甘肃","128","36.00","补气升阳，益卫固表","知识库示例：传统用法为煎服，具体使用咨询专业人员","置通风干燥处","30"},
            {"DG002","当归","补血","甘肃","86","58.00","补血活血，调经止痛","知识库示例：传统用法为煎服，具体使用咨询专业人员","阴凉干燥，防潮防蛀","20"},
            {"JYH003","金银花","清热","山东","15","68.00","清热解毒，疏散风热","知识库示例：传统用法为煎服，具体使用咨询专业人员","避光密封，防潮","25"},
            {"FL004","茯苓","利湿","云南","210","28.00","利水渗湿，健脾宁心","知识库示例：传统用法为煎服，具体使用咨询专业人员","干燥通风，防蛀","40"},
            {"CP005","陈皮","理气","广东","72","42.00","理气健脾，燥湿化痰","知识库示例：传统用法为煎服，具体使用咨询专业人员","干燥密封，防潮","20"},
            {"GC006","甘草","补气","内蒙古","160","22.50","补脾益气，清热解毒，调和诸药","知识库示例：传统用法为煎服，具体使用咨询专业人员","通风干燥处","30"},
            {"BH007","薄荷","解表","江苏","12","18.00","疏散风热，清利头目","知识库示例：芳香药传统煎服常后下，具体使用咨询专业人员","阴凉避光，密封保存","20"},
            {"SZR008","酸枣仁","安神","河北","45","96.00","养心补肝，宁心安神","知识库示例：传统用法为煎服，具体使用咨询专业人员","阴凉干燥，防油变","15"}
        };
        for(String[] r:rows) writeMedicinal(new Medicinal(0,r[0],r[1],r[2],r[3],Integer.parseInt(r[4]),new BigDecimal(r[5]),r[6],r[7],r[8],Integer.parseInt(r[9])));
    }
    /** 账号密码基础入口，桌面登录使用带本地图片验证码的重载。 */
    public User login(String username,String password) { if(remote!=null)return remote.login(username,password);User user=credentialUser(username,password);dao.update("INSERT INTO login_log(username) VALUES(?)",user.username());return user; }
    public User login(String username,String password,ImageCaptcha captcha,String input) {
        if(captcha==null) throw new IllegalArgumentException("请刷新图片验证码");
        captcha.verify(input);
        return login(username,password);
    }
    @Deprecated public User login(String username,String password,String captcha,String expected) {
        if(clean(expected).isEmpty() || clean(captcha).isEmpty() || !clean(captcha).equalsIgnoreCase(clean(expected))) throw new IllegalArgumentException("图片验证码不正确");
        return login(username,password);
    }
    private User credentialUser(String username,String password) { var users=dao.query("SELECT * FROM app_user WHERE username=? AND password_hash=?",Platform::user,clean(username),Passwords.md5(password==null?"":password));if(users.isEmpty()) throw new IllegalArgumentException("账号或密码不正确");return users.get(0); }
    public void sendRegistrationCode(String email) { if(!EmailCodeService.validEmail(email)) throw new IllegalArgumentException("请输入有效的邮箱地址");if(remote!=null){remote.call("send-code",Wire.fields("email",email));return;}emailCodes.send(clean(email),"register|"+clean(email).toLowerCase(Locale.ROOT)); }
    /** 测试和兼容入口，不发送验证码；注册界面使用 registerVerified。 */
    public void register(String username,String password,String name) { if(remote!=null)throw new IllegalArgumentException("请通过邮箱验证码注册");insertUser(username,defaultEmail,password,name,Role.GUEST); }
    public void registerVerified(String username,String email,String rawPassword,String name,String code) { if(!EmailCodeService.validEmail(email)) throw new IllegalArgumentException("请输入有效的邮箱地址");if(remote!=null){remote.call("register",Wire.fields("username",username,"email",email,"password",rawPassword,"name",name,"code",code));return;}emailCodes.verify("register|"+clean(email).toLowerCase(Locale.ROOT),code);insertUser(username,email,rawPassword,name,Role.GUEST); }
    public void createUser(User actor,String username,String password,String name,Role role) {
        if(remote!=null){remote.call("create-user",Wire.fields("username",username,"password",password,"name",name,"role",role==null?"":role.name()));return;}
        requireAdmin(actor); if(role==null) throw new IllegalArgumentException("请选择角色"); insertUser(username,actor.email(),password,name,role);
    }
    private void insertUser(String username,String email,String password,String name,Role role) {
        username=clean(username);
        if(!username.matches("[A-Za-z0-9_]{3,32}")) throw new IllegalArgumentException("账号应为 3～32 位字母、数字或下划线");
        validPassword(password); text(name,"姓名",50);if(!EmailCodeService.validEmail(email)) throw new IllegalArgumentException("请输入有效的邮箱地址");
        dao.update("INSERT INTO app_user(username,email,password_hash,display_name,role) VALUES(?,?,?,?,?)",username,email.trim(),Passwords.md5(password),name.trim(),role.name());
    }
    public List<User> users(User actor) { if(remote!=null)return remote.users();requireAdmin(actor); return dao.query("SELECT * FROM app_user ORDER BY id",Platform::user); }
    public void deleteUser(User actor,long id) {
        if(remote!=null){remote.call("delete-user",Wire.fields("id",id));return;}
        requireAdmin(actor); if(actor.id()==id) throw new IllegalArgumentException("不能删除当前登录账号");
        if(dao.update("DELETE FROM app_user WHERE id=?",id)!=1) throw new IllegalArgumentException("用户不存在");
    }
    public User updateProfile(User actor,String name,String oldPassword,String newPassword) {
        if(remote!=null)return Wire.user(remote.call("profile",Wire.fields("name",name,"oldPassword",oldPassword,"newPassword",newPassword)));
        User current=requireUser(actor); text(name,"姓名",50);
        if(newPassword!=null && !newPassword.isEmpty()) {
            validPassword(newPassword);
            int changed=dao.update("UPDATE app_user SET display_name=?,password_hash=? WHERE id=? AND password_hash=?",name.trim(),Passwords.md5(newPassword),current.id(),Passwords.md5(oldPassword==null?"":oldPassword));
            if(changed!=1) throw new IllegalArgumentException("原密码不正确");
        } else dao.update("UPDATE app_user SET display_name=? WHERE id=?",name.trim(),current.id());
        return new User(current.id(),current.username(),current.email(),name.trim(),current.role());
    }
    public List<Medicinal> medicinals(String keyword,String category) {
        if(remote!=null)return remote.medicinals(keyword,category);
        String key=clean(keyword).replace("!","!!").replace("%","!%").replace("_","!_");
        if(category==null || category.equals("全部")) return dao.query("SELECT * FROM medicinal WHERE name LIKE ? ESCAPE '!' ORDER BY id",Platform::medicinal,"%"+key+"%");
        return dao.query("SELECT * FROM medicinal WHERE name LIKE ? ESCAPE '!' AND category=? ORDER BY id",Platform::medicinal,"%"+key+"%",category);
    }
    public void saveMedicinal(User actor,Medicinal m) {
        if(remote!=null){remote.call("save-medicinal",Wire.medicinal(m));return;}
        requireEdit(actor); Objects.requireNonNull(m);
        text(m.code(),"药材编号",32); text(m.name(),"名称",50); text(m.category(),"分类",30); text(m.origin(),"产地",80);
        text(m.efficacy(),"功效",1000); text(m.usage(),"用法",1000); text(m.storage(),"储存条件",200);
        if(!Arrays.asList(CATEGORIES).contains(m.category())) throw new IllegalArgumentException("请选择有效分类");
        if(m.stock()<0 || m.stock()>1000000000 || m.threshold()<0 || m.threshold()>1000000000 || m.price()==null || m.price().signum()<0 || m.price().compareTo(new BigDecimal("9999999999.99"))>0 || m.price().scale()>2) throw new IllegalArgumentException("库存/阈值须为非负整数，单价须为非负金额且最多两位小数");
        writeMedicinal(m);
    }
    private void writeMedicinal(Medicinal m) {
        Object[] values={m.code().trim(),m.name().trim(),m.category(),m.origin().trim(),m.stock(),m.price(),m.efficacy().trim(),m.usage().trim(),m.storage().trim(),m.threshold()};
        if(m.id()==0) dao.update("INSERT INTO medicinal(code,name,category,origin,stock,price,efficacy,usage_text,storage,threshold_qty) VALUES(?,?,?,?,?,?,?,?,?,?)",values);
        else {
            // 库存只能通过入出库调整，编辑资料不会覆盖其他窗口的库存变化。
            if(dao.update("UPDATE medicinal SET code=?,name=?,category=?,origin=?,price=?,efficacy=?,usage_text=?,storage=?,threshold_qty=? WHERE id=?",m.code().trim(),m.name().trim(),m.category(),m.origin().trim(),m.price(),m.efficacy().trim(),m.usage().trim(),m.storage().trim(),m.threshold(),m.id())!=1) throw new IllegalArgumentException("药材已被删除，请刷新");
        }
    }
    public void deleteMedicinal(User actor,long id) { if(remote!=null){remote.call("delete-medicinal",Wire.fields("id",id));return;}requireEdit(actor); if(dao.update("DELETE FROM medicinal WHERE id=?",id)!=1) throw new IllegalArgumentException("药材不存在"); }
    public void adjustStock(User actor,long id,int delta,String note) {
        if(remote!=null){remote.call("adjust-stock",Wire.fields("id",id,"delta",delta,"note",note));return;}
        User current=requireEdit(actor);
        if(delta==0 || Math.abs((long)delta)>1000000000) throw new IllegalArgumentException("入出库数量应在 1～1000000000 之间");
        if(clean(note).length()>200) throw new IllegalArgumentException("备注不能超过 200 字");
        dao.adjustStock(id,delta,current.username(),clean(note));
    }
    public String ask(User actor,String question) {
        if(remote!=null)return remote.call("ask",Wire.fields("question",question)).get("answer");
        User current=requireUser(actor); text(question,"问题",500); question=question.trim();
        List<Medicinal> all=medicinals("","全部");
        String q=question;
        List<Medicinal> matches=all.stream().filter(m -> q.contains(m.name())).toList();
        if(matches.isEmpty()) matches=all.stream().filter(m -> Arrays.stream(m.efficacy().split("[，、。\\s]+" )).anyMatch(k -> k.length()>=2 && q.contains(k))).limit(3).toList();
        String answer;
        if(deepSeek!=null && deepSeek.enabled()) {
            try { answer=deepSeek.answer(question,matches.isEmpty()?all.stream().limit(12).toList():matches); }
            catch(RuntimeException failure) { answer=localAnswer(matches); }
        } else answer=localAnswer(matches);
        dao.update("INSERT INTO chat_history(user_id,question,answer) VALUES(?,?,?)",current.id(),question,answer);
        return answer;
    }
    private static String localAnswer(List<Medicinal> matches) {
        StringBuilder answer=new StringBuilder();
        if(matches.isEmpty()) answer.append("未匹配到相关药材。可以试试“黄芪的功效”“当归怎么保存”，或输入功效关键词，例如“清热解毒”。");
        else for(Medicinal m:matches) answer.append(m.name()).append("\n功效：").append(m.efficacy()).append("\n用法：").append(m.usage()).append("\n储存：").append(m.storage()).append("\n当前库存：").append(m.stock()).append(" 克（基准 ").append(m.threshold()).append(" 克）\n\n");
        answer.append("\n内容为课程知识库演示，不能替代专业诊疗建议。");
        return answer.toString();
    }
    public List<ChatRecord> history(User actor) { if(remote!=null)return remote.history();User current=requireUser(actor); return dao.query("SELECT * FROM chat_history WHERE user_id=? ORDER BY id DESC LIMIT 100",r -> new ChatRecord(r.getLong("id"),r.getString("question"),r.getString("answer"),r.getString("created_at")),current.id()); }
    public List<Object[]> loginLogs(User actor) { if(remote!=null)return remote.logs(true);requireAdmin(actor); return dao.query("SELECT * FROM login_log ORDER BY id DESC LIMIT 300",r -> new Object[]{r.getLong("id"),r.getString("username"),r.getString("created_at")}); }
    public List<Object[]> stockLogs(User actor) { if(remote!=null)return remote.logs(false);requireEdit(actor); return dao.query("SELECT * FROM stock_log ORDER BY id DESC LIMIT 300",r -> new Object[]{r.getString("medicinal_code"),r.getString("medicinal_name"),r.getInt("quantity_change"),r.getInt("remaining"),r.getString("operator_name"),r.getString("note"),r.getString("created_at")}); }
    private User requireUser(User actor) {
        if(actor==null) throw new SecurityException("请先登录");
        var records=dao.query("SELECT * FROM app_user WHERE id=?",Platform::user,actor.id());
        if(records.isEmpty() || !records.get(0).username().equals(actor.username())) throw new SecurityException("登录账号已失效，请重新登录");
        return records.get(0);
    }
    private void requireAdmin(User actor) { if(requireUser(actor).role()!=Role.ADMIN) throw new SecurityException("只有管理员可以执行此操作"); }
    private User requireEdit(User actor) { User current=requireUser(actor); if(!current.role().canEdit()) throw new SecurityException("访客仅有查看权限"); return current; }
    private static User user(ResultSet r) throws SQLException { return new User(r.getLong("id"),r.getString("username"),r.getString("email"),r.getString("display_name"),Role.valueOf(r.getString("role"))); }
    private static Medicinal medicinal(ResultSet r) throws SQLException { return new Medicinal(r.getLong("id"),r.getString("code"),r.getString("name"),r.getString("category"),r.getString("origin"),r.getInt("stock"),r.getBigDecimal("price"),r.getString("efficacy"),r.getString("usage_text"),r.getString("storage"),r.getInt("threshold_qty")); }
    private static String clean(String s) { return s==null?"":s.trim(); }
    private static void text(String value,String label,int max) { if(clean(value).isEmpty() || clean(value).length()>max) throw new IllegalArgumentException(label+"不能为空且不能超过 "+max+" 字"); }
    private static void validPassword(String p) { if(p==null || p.length()<6 || p.length()>64) throw new IllegalArgumentException("密码长度应为 6～64 位"); }
}
