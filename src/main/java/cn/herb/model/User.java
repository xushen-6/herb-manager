package cn.herb.model;

/** 会话对象不携带密码摘要。 */
public record User(long id, String username, String displayName, Role role) {}
