package cn.herb;

import cn.herb.dao.Database;
import cn.herb.model.*;
import cn.herb.service.*;
import cn.herb.server.BackendServer;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BackendServerTest {
    @Test void remoteCrudStockHistoryAndPermissionsUseServerSession() throws Exception {
        Platform backend=new Platform(new Database("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        backend.initialize();
        try(BackendServer server=new BackendServer(backend,0)) {
            server.start();String url="http://127.0.0.1:"+server.port();
            Platform admin=new Platform(new RemotePlatform(url)),guest=new Platform(new RemotePlatform(url));
            admin.initialize();guest.initialize();
            User a=admin.login("admin","123456"),g=guest.login("123456","123456");
            assertThrows(IllegalArgumentException.class,()->guest.users(a));
            Medicinal m=new Medicinal(0,"REMOTE001","远程测试甲药","清热","四川",10,BigDecimal.ONE,"长功效".repeat(333),"长用法".repeat(333),"阴凉",5);
            admin.saveMedicinal(a,m);
            m=admin.medicinals("远程测试","清热").get(0);
            final Medicinal selected=m;
            assertThrows(IllegalArgumentException.class,()->guest.saveMedicinal(a,selected));
            admin.adjustStock(a,m.id(),5,"远程入库");
            assertEquals(15,admin.medicinals("远程测试","清热").get(0).stock());
            assertEquals(1,admin.stockLogs(a).size());
            String answer=guest.ask(g,"远程测试甲药的功效");
            assertEquals(answer,guest.history(g).get(0).answer());
            assertTrue(admin.history(a).isEmpty());
            admin.createUser(a,"remote_user","123456","远程用户",Role.GUEST);
            User created=admin.users(a).stream().filter(u->u.username().equals("remote_user")).findFirst().orElseThrow();
            admin.deleteUser(a,created.id());admin.deleteMedicinal(a,m.id());
            assertTrue(admin.medicinals("远程测试","清热").isEmpty());
            guest.logout();assertThrows(IllegalArgumentException.class,()->guest.history(g));
        }
    }
    @Test void missingSessionsAndInvalidRegistrationAreRejected() throws Exception {
        Platform backend=new Platform(new Database("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));backend.initialize();
        try(BackendServer server=new BackendServer(backend,0)) {
            server.start();RemotePlatform remote=new RemotePlatform("http://127.0.0.1:"+server.port());
            assertThrows(IllegalArgumentException.class,()->remote.call("users",Map.of()));
            assertThrows(IllegalArgumentException.class,()->remote.call("login",Wire.fields("username","admin","password","wrong")));
            assertThrows(IllegalArgumentException.class,()->remote.call("register",Wire.fields("username","invalid_user","email","test@example.com","password","123456","name","用户","code","123456")));
            assertThrows(IllegalArgumentException.class,()->new RemotePlatform("http://example.com"));
            remote.login("admin","123456");
            remote.call("logout",Map.of()); // 模拟服务端会话已失效。
            assertDoesNotThrow(remote::logout);
        }
    }
    @Test void wirePreservesLongChineseAnswersAndSpecialCharacters() {
        var fields=Wire.fields("answer","药材🌿\n&=+%".repeat(20000),"empty","");
        assertEquals(fields,Wire.decode(Wire.encode(fields)));
    }
}
