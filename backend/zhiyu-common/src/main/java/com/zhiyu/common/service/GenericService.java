package com.zhiyu.common.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shared pagination and batch-fetch helpers for MyBatis-Plus services.
 *
 * <p>All methods are static so services can use them without changing
 * their inheritance hierarchy. New services may extend
 * {@link com.baomidou.mybatisplus.extension.service.impl.ServiceImpl}
 * directly if they prefer the full IService API.</p>
 */
public final class GenericService {

    private GenericService() { /* utility class */ }

    /**
     * Convert a MyBatis-Plus entity {@link Page} into a DTO {@link Page}
     * using the given converter function.
     *
     * <p>Replaces the repetitive three-step pattern:
     * <pre>{@code
     * Page<E> entityPage = mapper.selectPage(new Page<>(page, size), wrapper);
     * Page<D> dtoPage = new Page<>(page, size, entityPage.getTotal());
     * dtoPage.setRecords(entityPage.getRecords().stream().map(converter).toList());
     * }</pre>
     * with a single call: {@code GenericService.pageDto(entityPage, converter)}.
     */
    public static <T, D> Page<D> pageDto(final IPage<T> entityPage,
                                          final Function<T, D> converter) {
        Page<D> dtoPage = new Page<>(entityPage.getCurrent(),
                entityPage.getSize(), entityPage.getTotal());
        List<D> records = entityPage.getRecords().stream()
                .map(converter)
                .collect(Collectors.toList());
        dtoPage.setRecords(records);
        return dtoPage;
    }

    /**
     * Batch-fetch entities by a set of IDs in a single query, returning
     * a lookup map keyed by the entity's natural key.
     *
     * <p>Eliminates the N+1 pattern where a loop calls
     * {@code mapper.selectById(id)} for every row:
     * <pre>{@code
     * Map<Long, User> users = GenericService.batchFetchOne(userIds, batch -> {
     *     return userMapper.selectBatchIds(batch);
     * }, User::getId);
     * }</pre>
     *
     * @param ids          the IDs to fetch
     * @param batchFetcher function that fetches entities for a batch of IDs
     * @param keyExtractor function to extract the key from each entity
     * @return unmodifiable map from key to entity
     */
    public static <K, V> Map<K, V> batchFetchOne(
            final java.util.Collection<K> ids,
            final Function<java.util.Collection<K>, List<V>> batchFetcher,
            final Function<V, K> keyExtractor) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<V> results = batchFetcher.apply(ids);
        if (results == null || results.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(
                results.stream()
                        .collect(Collectors.toMap(keyExtractor, Function.identity(), (a, b) -> a)));
    }
}
