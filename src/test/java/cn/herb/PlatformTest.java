package cn.herb;

import cn.herb.dao.Database;
import cn.herb.dao.JdbcDao;
import cn.herb.model.*;
import cn.herb.service.Platform;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PlatformTest {
    Platform p;
    User admin, pharmacist, guest;
    @BeforeEach void setup() {
        p = new Platform(new Database("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        p.initialize();
        admin = p.login("admin", "123456", "ABCD", "abcd");
        pharmacist = p.login("yaoshi", "123456", "ABCD", "ABCD");
        guest = p.login("123456", "123456", "ABCD", "ABCD");
    }
    @Test void loginValidatesPasswordAndRecordsLog() {
        assertThrows(IllegalArgumentException.class, () -> p.login("admin", "wrong", "ABCD", "ABCD"));
        assertEquals(Role.ADMIN, admin.role());
        assertEquals(3, p.loginLogs(admin).size());
        assertThrows(SecurityException.class, () -> p.loginLogs(guest));
    }
    @Test void registrationNeverEscalatesAndRejectsDuplicates() {
        p.register("student", "Student123!", "同学");
        assertEquals(Role.GUEST, p.login("student", "Student123!", "X", "X").role());
        assertThrows(IllegalArgumentException.class, () -> p.register("student", "Student123!", "同学"));
        assertThrows(IllegalArgumentException.class, () -> p.register("ab", "123", ""));
    }
    @Test void guestCannotModifyAndPharmacistCannotManageUsers() {
        Medicinal m = p.medicinals("", "全部").get(0);
        assertThrows(SecurityException.class, () -> p.saveMedicinal(guest, m));
        assertThrows(SecurityException.class, () -> p.deleteMedicinal(guest, m.id()));
        assertThrows(SecurityException.class, () -> p.adjustStock(guest, m.id(), 1, "入库"));
        assertThrows(SecurityException.class, () -> p.users(pharmacist));
        assertThrows(SecurityException.class, () -> p.createUser(pharmacist, "another", "12345678", "用户", Role.ADMIN));
        assertThrows(IllegalArgumentException.class, () -> p.deleteUser(admin, admin.id()));
    }
    Medicinal herb(String code) {
        return new Medicinal(0,code,"测试药材","清热","四川",10,new BigDecimal("12.50"),"教学功效","教学用法","阴凉干燥",5);
    }
    @Test void crudFilteringAndValidation() {
        p.saveMedicinal(pharmacist, herb("TEST001"));
        Medicinal m = p.medicinals("测试", "清热").get(0);
        assertEquals("TEST001", m.code());
        assertEquals(0, p.medicinals("测试", "补血").size());
        assertThrows(IllegalArgumentException.class, () -> p.saveMedicinal(admin, herb("TEST001")));
        p.saveMedicinal(admin,new Medicinal(m.id(),m.code(),"更新药材",m.category(),m.origin(),m.stock(),m.price(),m.efficacy(),m.usage(),m.storage(),m.threshold()));
        assertEquals(1,p.medicinals("更新", "全部").size());
        assertThrows(IllegalArgumentException.class, () -> p.saveMedicinal(admin,new Medicinal(0,"BAD","坏数据","清热","四川",-1,BigDecimal.ONE,"功效","用法","保存",5)));
        p.deleteMedicinal(admin,m.id());
        assertTrue(p.medicinals("更新", "全部").isEmpty());
    }
    @Test void stockIsAtomicAndCannotBecomeNegative() throws Exception {
        p.saveMedicinal(admin,herb("STOCK001"));
        Medicinal m = p.medicinals("测试","全部").get(0);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> jobs = List.of(() -> withdraw(m.id()), () -> withdraw(m.id()));
            long successes = pool.invokeAll(jobs).stream().filter(f -> { try {return f.get();} catch(Exception e){throw new RuntimeException(e);} }).count();
            assertEquals(1, successes);
            assertEquals(3,p.medicinals("测试","全部").get(0).stock());
            assertEquals(1,p.stockLogs(admin).size());
            assertThrows(IllegalArgumentException.class,() -> p.adjustStock(admin,m.id(),0,""));
        } finally { pool.shutdownNow(); }
    }
    boolean withdraw(long id) { try {p.adjustStock(admin,id,-7,"领用"); return true;} catch(IllegalArgumentException e){return false;} }
    @Test void historyIsPersistentAndPrivate() {
        String answer = p.ask(guest,"黄芪的功效是什么");
        assertTrue(answer.contains("黄芪"));
        assertEquals(1,p.history(guest).size());
        assertEquals(0,p.history(admin).size());
        assertTrue(p.ask(guest,"不存在的药材").contains("未匹配"));
        assertThrows(IllegalArgumentException.class,() -> p.ask(guest," "));
    }
    @Test void profileAndPasswordChanges() {
        User changed = p.updateProfile(guest,"新昵称","","");
        assertEquals("新昵称",changed.displayName());
        assertThrows(IllegalArgumentException.class,() -> p.updateProfile(guest,"新昵称","wrong","Newpass123!"));
        p.updateProfile(guest,"新昵称","123456","Newpass123!");
        assertEquals(guest.id(), p.login("123456","Newpass123!","X","X").id());
    }
    @Test void longKnowledgeAnswersAreSavedWithoutTruncation() {
        String efficacy = "教学功效".repeat(250), usage = "教学用法".repeat(250);
        for (String name : List.of("长文本甲药", "长文本乙药")) {
            p.saveMedicinal(admin, new Medicinal(0, name, name, "清热", "四川", 10,
                    BigDecimal.ONE, efficacy, usage, "阴凉干燥", 5));
        }
        String answer = p.ask(guest, "长文本甲药和长文本乙药的功效与用法");
        assertTrue(answer.length() > 4000);
        assertTrue(answer.contains("长文本甲药"));
        assertTrue(answer.contains("长文本乙药"));
        assertEquals(answer, p.history(guest).get(0).answer());
        assertEquals(2, answer.split(efficacy, -1).length - 1);
        assertEquals(2, answer.split(usage, -1).length - 1);
    }
    @Test void legacyAnswerColumnIsUpgradedWithoutLosingHistory() {
        Database db = new Database("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        Platform legacy = new Platform(db);
        legacy.initialize();
        User user = legacy.login("123456", "123456");
        JdbcDao dao = new JdbcDao(db);
        dao.update("ALTER TABLE chat_history ALTER COLUMN answer VARCHAR(4000) NOT NULL");
        String oldAnswer = legacy.ask(user, "黄芪的功效");

        db.initialize();
        db.initialize(); // 重复启动也应安全，已有历史不能丢失。
        String longAnswer = "中药知识🌿".repeat(20000);
        dao.update("INSERT INTO chat_history(user_id,question,answer) VALUES(?,?,?)",
                user.id(), "长回答", longAnswer);
        var history = legacy.history(user);
        assertEquals(2, history.size());
        assertEquals(longAnswer, history.get(0).answer());
        assertEquals(oldAnswer, history.get(1).answer());
        assertThrows(IllegalArgumentException.class, () -> dao.update(
                "INSERT INTO chat_history(user_id,question,answer) VALUES(?,?,?)", user.id(), "无效回答", null));
    }
    @Test void initializationIsIdempotent() {
        int n = p.medicinals("","全部").size(); p.initialize();
        assertEquals(n,p.medicinals("","全部").size());
        assertEquals(3,p.users(admin).size());
    }
    @Test void initializationMigratesLegacyDemoAccountsWhenTargetAlreadyExists() {
        Database db = new Database("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        db.initialize();
        JdbcDao dao = new JdbcDao(db);
        String sql = "INSERT INTO app_user(username,email,password_hash,display_name,role) VALUES(?,?,?,?,?)";
        dao.update(sql,"admin","admin@example.com","old","管理员","ADMIN");
        dao.update(sql,"pharmacist","pharmacist@example.com","old","药师","PHARMACIST");
        dao.update(sql,"guest","guest@example.com","old","访客","GUEST");
        dao.update(sql,"123456","user@example.com","old","已注册用户","GUEST");
        Platform migrated = new Platform(db);
        migrated.initialize();
        assertEquals(Role.ADMIN,migrated.login("admin","123456").role());
        assertEquals(Role.PHARMACIST,migrated.login("yaoshi","123456").role());
        assertEquals(Role.GUEST,migrated.login("123456","123456").role());
        assertThrows(IllegalArgumentException.class,() -> migrated.login("guest","123456"));
        assertEquals(3,migrated.users(migrated.login("admin","123456")).size());
    }
}
