package cn.herb;

import cn.herb.dao.Database;
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
        admin = p.login("admin", "Admin123!", "ABCD", "abcd");
        pharmacist = p.login("pharmacist", "Pharm123!", "ABCD", "ABCD");
        guest = p.login("guest", "Guest123!", "ABCD", "ABCD");
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
        p.updateProfile(guest,"新昵称","Guest123!","Newpass123!");
        assertEquals(guest.id(), p.login("guest","Newpass123!","X","X").id());
    }
    @Test void initializationIsIdempotent() {
        int n = p.medicinals("","全部").size(); p.initialize();
        assertEquals(n,p.medicinals("","全部").size());
        assertEquals(3,p.users(admin).size());
    }
}
