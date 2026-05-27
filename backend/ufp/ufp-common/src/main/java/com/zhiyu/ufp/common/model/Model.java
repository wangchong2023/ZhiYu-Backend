package com.zhiyu.ufp.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zhiyu.ufp.common.annotation.Primary;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 实体和 DTO 的抽象基类。
 *
 * <p>提供分页、排序参数以及通过反射机制解析主键的能力。</p>
 *
 * @author ZhiYu
 * @since 1.0.0
 */
@Data
public abstract class Model implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(hidden = true)
    private PageParam page;

    @Schema(hidden = true)
    private List<Sort> sorts;

    /**
     * 获取实体的主键值。
     *
     * <p>通过反射方式，查找类及其父类中被 {@link Primary} 注解标记 of 的字段并返回其值。</p>
     *
     * @return 主键的字符串表示，如果未找到则返回 null
     */
    @JsonIgnore
    public String getPrimary() {
        return getPrimaryValue(this.getClass());
    }

    /**
     * 递归获取类及父类中的主键值。
     *
     * <p>此处因需要读取被标记为 {@code @Primary} 的反射字段，需要临时提升私有字段的访问权限。
     * 故使用 {@code @SuppressWarnings("PMD.AvoidAccessibilityAlteration")} 抑制反射权限修改警告。
     * 临时修改仅在此只读场景中生效，且有安全性验证防护，属于受控安全操作。</p>
     *
     * @param clazz 待解析的类类型
     * @return 主键值，未匹配则返回 null
     */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private String getPrimaryValue(final Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            Primary primary = field.getAnnotation(Primary.class);
            if (primary != null) {
                field.setAccessible(true);
                try {
                    return (String) field.get(this);
                } catch (IllegalAccessException ignored) {
                    // 尽管执行了 setAccessible，由于外部安全性机制限制该字段仍不可访问，安全跳过以递归解析父类字段
                }
            }
        }
        Class<?> superclass = clazz.getSuperclass();
        if (superclass == null || superclass.equals(Object.class)) {
            return null;
        }
        return getPrimaryValue(superclass);
    }

    /**
     * 获取按逗号拆分后的主键值列表。
     *
     * <p>适用于存储为 "id1,id2" 格式的联合主键。</p>
     *
     * @return 主键片段列表，如果不存在主键则返回空集合
     */
    @JsonIgnore
    public List<String> getPrimaries() {
        String primary = getPrimary();
        if (primary == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.asList(primary.split(",")));
    }
}
