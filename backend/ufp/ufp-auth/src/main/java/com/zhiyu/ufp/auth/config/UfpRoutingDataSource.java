package com.zhiyu.ufp.auth.config;

import com.zhiyu.ufp.common.datasource.UfpDSContextHolder;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Routing {@link DataSource} that uses {@link UfpDSContextHolder#peek()}
 * to determine the current datasource key.
 *
 * <p>When no {@code @UfpDS} annotation is active, {@code peek()} returns
 * {@code null} and the default target datasource is used.
 * When {@code @UfpDS("ufp_auth")} is active, the "ufp_auth" target is selected.
 */
public class UfpRoutingDataSource extends AbstractRoutingDataSource {

    public UfpRoutingDataSource(final DataSource defaultTarget,
                                 final Map<Object, Object> targetDataSources) {
        setDefaultTargetDataSource(defaultTarget);
        setTargetDataSources(targetDataSources);
        afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return UfpDSContextHolder.peek();
    }
}
