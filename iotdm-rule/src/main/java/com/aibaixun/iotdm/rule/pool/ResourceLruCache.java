package com.aibaixun.iotdm.rule.pool;

import com.aibaixun.iotdm.rule.send.HttpSendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 资源LRU 缓存
 * @author wangxiao@aibaixun.com
 * @date 2022/3/14
 */
public class ResourceLruCache<K,V extends PoolResource> {

    private final Logger log = LoggerFactory.getLogger(ResourceLruCache.class);

    private final LinkedHashMap<K, V> cache;

    public ResourceLruCache(final int maxSize) {
        this.cache = new LinkedHashMap<K, V>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                try {
                    eldest.getValue().releaseResource();
                }catch (Exception e){
                    log.error("releaseResource is error");
                }

                return this.size() > maxSize;
            }
        };
    }

    public V get(K key) {
        return this.cache.get(key);
    }

    public void put(K key, V value) {
        this.cache.put(key, value);
    }

    public boolean remove(K key) {
        return this.cache.remove(key) != null;
    }

    public long size() {
        return (long)this.cache.size();
    }
}