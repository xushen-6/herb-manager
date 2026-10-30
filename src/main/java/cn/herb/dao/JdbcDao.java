package cn.herb.dao;

import java.sql.*;
import java.util.*;

/** PreparedStatement 参数化查询；所有持久化操作集中在 DAO 层。 */
public final class JdbcDao {
    private final Database database;
    public JdbcDao(Database database) { this.database = database; }
    @FunctionalInterface public interface Mapper<T> { T map(ResultSet rs) throws SQLException; }
    public <T> List<T> query(String sql, Mapper<T> mapper, Object... args) {
        try (Connection c=database.connect(); PreparedStatement ps=prepare(c,sql,args); ResultSet rs=ps.executeQuery()) {
            List<T> result=new ArrayList<>(); while(rs.next()) result.add(mapper.map(rs)); return result;
        } catch(SQLException e) { throw failure(e); }
    }
    public int update(String sql,Object... args) {
        try(Connection c=database.connect(); PreparedStatement ps=prepare(c,sql,args)) { return ps.executeUpdate(); }
        catch(SQLException e) { throw failure(e); }
    }
    public static PreparedStatement prepare(Connection c,String sql,Object... args) throws SQLException {
        PreparedStatement ps=c.prepareStatement(sql);
        for(int i=0;i<args.length;i++) ps.setObject(i+1,args[i]);
        return ps;
    }
    /** 条件更新和流水写入在同一事务，库存不足时不留下流水。 */
    public void adjustStock(long id,int delta,String operator,String note) {
        try(Connection c=database.connect()) {
            c.setAutoCommit(false);
            try {
                try(PreparedStatement ps=prepare(c,"UPDATE medicinal SET stock=stock+? WHERE id=? AND stock+? >= 0 AND stock+? <= 1000000000",delta,id,delta,delta)) {
                    if(ps.executeUpdate()!=1) throw new IllegalArgumentException("库存不足、数量超限或药材已删除");
                }
                try(PreparedStatement ps=prepare(c,"INSERT INTO stock_log(medicinal_code,medicinal_name,quantity_change,remaining,operator_name,note) SELECT code,name,?,stock,?,? FROM medicinal WHERE id=?",delta,operator,note,id)) { ps.executeUpdate(); }
                c.commit();
            } catch(Exception e) { c.rollback(); throw e; }
        } catch(SQLException e) { throw failure(e); }
    }
    public static RuntimeException failure(SQLException e) {
        if(e.getSQLState()!=null && e.getSQLState().startsWith("23")) return new IllegalArgumentException("账号或药材编号已存在，或记录仍被使用",e);
        return new IllegalStateException("数据库操作失败（"+e.getSQLState()+"），请检查数据库连接与配置",e);
    }
}
