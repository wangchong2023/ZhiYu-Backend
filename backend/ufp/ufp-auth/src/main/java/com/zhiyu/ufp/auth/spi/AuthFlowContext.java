package com.zhiyu.ufp.auth.spi;

import com.zhiyu.ufp.auth.enums.AuthGrantType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证流程上下文。
 *
 * <p>用于在认证流程的各个 SPI 插件之间传递和共享会话级或请求级的属性参数。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
public final class AuthFlowContext {

    /** 认证授权类型 */
    private final AuthGrantType grantType;
    /** 上下文共享属性集 */
    private final Map<String, Object> attributes;

    private AuthFlowContext(final AuthGrantType grantType) {
        this.grantType = grantType;
        this.attributes = new HashMap<>();
    }

    /**
     * 根据授权类型创建流程上下文实例。
     *
     * @param grantType 授权类型
     * @return 认证流程上下文
     */
    public static AuthFlowContext of(final AuthGrantType grantType) {
        return new AuthFlowContext(grantType);
    }

    /**
     * 向上下文中存入属性参数。
     *
     * @param key 属性键
     * @param value 属性值
     * @return 流程上下文自身（支持链式调用）
     */
    public AuthFlowContext with(final String key, final Object value) {
        this.attributes.put(key, value);
        return this;
    }

    public AuthGrantType getGrantType() {
        return grantType;
    }

    /**
     * 根据属性键获取对应的属性值，并自动强转为期望的泛型类型。
     *
     * <p>由于属性存储在 {@code Map<String, Object>} 中，取出时需要强转为泛型 {@code T}。
     * 在泛型擦除机制下无法由编译器进行类型安全校验，
     * 故使用 {@code @SuppressWarnings("unchecked")} 抑制警告。
     * 调用方在使用时需自我保证类型一致性。</p>
     *
     * @param key 属性键
     * @param <T> 期望的泛型类型
     * @return 对应的属性值，若不存在则返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(final String key) {
        return (T) attributes.get(key);
    }

    /**
     * 获取只读属性映射表。
     *
     * @return 只读属性映射
     */
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }
}
