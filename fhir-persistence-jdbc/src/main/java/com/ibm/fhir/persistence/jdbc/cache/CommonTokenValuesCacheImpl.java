/*
 * (C) Copyright IBM Corp. 2020, 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.persistence.jdbc.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ibm.fhir.persistence.jdbc.dao.api.ICommonTokenValuesCache;
import com.ibm.fhir.persistence.jdbc.dao.impl.ResourceTokenValueRec;
import com.ibm.fhir.persistence.jdbc.dto.CommonTokenValue;


/**
 * Implementation of a cache used for lookups of entities related
 * to local and external resource references
 */
public class CommonTokenValuesCacheImpl implements ICommonTokenValuesCache {

    // We use LinkedHashMap for the local map because we also need to maintain order
    // of insertion to make sure we have correct LRU behavior when updating the shared cache
    private final ThreadLocal<LinkedHashMap<String, Integer>> codeSystems = new ThreadLocal<>();

    private final ThreadLocal<LinkedHashMap<CommonTokenValue, Long>> commonTokenValues = new ThreadLocal<>();

    // The lru cache shared at the server level
    private final LRUCache<String, Integer> codeSystemsCache;

    // The lru cache shared at the server level
    private final LRUCache<CommonTokenValue, Long> tokenValuesCache;


    /**
     * Public constructor
     * @param sharedExternalSystemNameCacheSize
     */
    public CommonTokenValuesCacheImpl(int codeSystemCacheSize, int tokenValueCacheSize) {

        // LRU cache for quick lookup of code-systems and token-values
        codeSystemsCache = new LRUCache<>(codeSystemCacheSize);
        tokenValuesCache = new LRUCache<>(tokenValueCacheSize);
    }

    /**
     * Called after a transaction commit() to transfer all the staged (thread-local) data
     * over to the shared LRU cache.
     */
    public void updateSharedMaps() {

        LinkedHashMap<String,Integer> sysMap = codeSystems.get();
        if (sysMap != null) {
            synchronized(this.codeSystemsCache) {
                codeSystemsCache.update(sysMap);
            }

            // clear the thread-local cache
            sysMap.clear();
        }

        LinkedHashMap<CommonTokenValue,Long> valMap = commonTokenValues.get();
        if (valMap != null) {
            synchronized(this.tokenValuesCache) {
                tokenValuesCache.update(valMap);
            }

            // clear the thread-local cache
            valMap.clear();
        }

    }

    @Override
    public Integer getCodeSystemId(String codeSystem) {
        // check the thread-local map first
        Integer result = null;

        if (codeSystems.get() != null) {
            result = codeSystems.get().get(codeSystem);

            if (result != null) {
                return result;
            }
        }

        // See if it's in the shared cache
        synchronized (this.codeSystemsCache) {
            result = codeSystemsCache.get(codeSystem);
        }

        if (result != null) {
            // We found it in the shared cache, so update our thread-local
            // cache.
            addCodeSystem(codeSystem, result);
        }

        return result;
    }

    @Override
    public void resolveCodeSystems(Collection<ResourceTokenValueRec> tokenValues,
        List<ResourceTokenValueRec> misses) {
        // Make one pass over the collection and resolve as much as we can in one go. Anything
        // we can't resolve gets put into the corresponding missing lists. Worst case is two passes, when
        // there's nothing in the local cache and we have to then look up everything in the shared cache

        // See what we have currently in our thread-local cache
        LinkedHashMap<String,Integer> sysMap = codeSystems.get();

        List<String> foundKeys = new ArrayList<>(tokenValues.size()); // for updating LRU
        List<ResourceTokenValueRec> needToFindSystems = new ArrayList<>(tokenValues.size()); // for the ref systems we haven't yet found
        for (ResourceTokenValueRec tv: tokenValues) {
            if (sysMap != null) {
                Integer id = sysMap.get(tv.getCodeSystemValue());
                if (id != null) {
                    foundKeys.add(tv.getCodeSystemValue());
                    tv.setCodeSystemValueId(id);
                } else {
                    // not found, so add to the cache miss list
                    needToFindSystems.add(tv);
                }
            } else {
                // no thread-local cache yet, so need to find them all
                needToFindSystems.add(tv);
            }
        }

        // If we still have keys to find, look them up in the shared cache (which we need to lock first)
        if (needToFindSystems.size() > 0) {
            synchronized (this.codeSystemsCache) {
                for (ResourceTokenValueRec xr: needToFindSystems) {
                    Integer id = codeSystemsCache.get(xr.getCodeSystemValue());
                    if (id != null) {
                        xr.setCodeSystemValueId(id);

                        // Update the local cache with this value
                        addCodeSystem(xr.getCodeSystemValue(), id);
                    } else {
                        // cache miss so add this record to the miss list for further processing
                        misses.add(xr);
                    }
                }
            }
        }
    }


    @Override
    public void resolveTokenValues(Collection<ResourceTokenValueRec> tokenValues,
        List<ResourceTokenValueRec> misses) {
        // Make one pass over the collection and resolve as much as we can in one go. Anything
        // we can't resolve gets put into the corresponding missing lists. Worst case is two passes, when
        // there's nothing in the local cache and we have to then look up everything in the shared cache

        // See what we have currently in our thread-local cache
        LinkedHashMap<CommonTokenValue,Long> valMap = commonTokenValues.get();

        List<CommonTokenValue> foundKeys = new ArrayList<>(tokenValues.size()); // for updating LRU
        List<ResourceTokenValueRec> needToFindValues = new ArrayList<>(tokenValues.size()); // for the ref values we haven't yet found
        for (ResourceTokenValueRec tv: tokenValues) {
            if (valMap != null) {
                CommonTokenValue key = new CommonTokenValue(tv.getCodeSystemValueId(), tv.getTokenValue());
                Long id = valMap.get(key);
                if (id != null) {
                    foundKeys.add(key);
                    tv.setCommonTokenValueId(id);
                } else {
                    // not found, so add to the cache miss list
                    needToFindValues.add(tv);
                }
            } else {
                needToFindValues.add(tv);
            }
        }

        // If we still have keys to find, look them up in the shared cache (which we need to lock first)
        if (needToFindValues.size() > 0) {
            synchronized (this.tokenValuesCache) {
                for (ResourceTokenValueRec tv: needToFindValues) {
                    CommonTokenValue key = new CommonTokenValue(tv.getCodeSystemValueId(), tv.getTokenValue());
                    Long id = tokenValuesCache.get(key);
                    if (id != null) {
                        tv.setCommonTokenValueId(id);

                        // Update the local cache with this value
                        addTokenValue(key, id);
                    } else {
                        // cache miss so add this record to the miss list for further processing
                        misses.add(tv);
                    }
                }
            }
        }
    }


    @Override
    public void addCodeSystem(String codeSystem, int id) {
        LinkedHashMap<String,Integer> map = codeSystems.get();

        if (map == null) {
            map = new LinkedHashMap<>();
            codeSystems.set(map);
        }

        // add the id to the thread-local cache. The shared cache is updated
        // only if a call is made to #updateSharedMaps()
        map.put(codeSystem, id);
    }

    @Override
    public void addTokenValue(CommonTokenValue key, long id) {
        LinkedHashMap<CommonTokenValue,Long> map = commonTokenValues.get();

        if (map == null) {
            map = new LinkedHashMap<>();
            commonTokenValues.set(map);
        }

        // add the id to the thread-local cache. The shared cache is updated
        // only if a call is made to #updateSharedMaps()
        map.put(key, id);
    }

    @Override
    public void reset() {
        codeSystems.remove();
        commonTokenValues.remove();

        // clear the shared caches too
        synchronized (this.codeSystemsCache) {
            this.codeSystemsCache.clear();
        }

        synchronized (this.tokenValuesCache) {
            this.tokenValuesCache.clear();
        }
    }

    @Override
    public void clearLocalMaps() {
        // clear the maps, but keep the maps in place because they'll be used again
        // the next time this thread is picked from the pool
        LinkedHashMap<String,Integer> sysMap = codeSystems.get();

        if (sysMap != null) {
            sysMap.clear();
        }

        LinkedHashMap<CommonTokenValue,Long> valMap = commonTokenValues.get();

        if (valMap != null) {
            valMap.clear();
        }
    }

    @Override
    public void prefillCodeSystems(Map<String, Integer> codeSystems) {
        synchronized(codeSystemsCache) {
            codeSystemsCache.putAll(codeSystems);
        }
    }

    @Override
    public Long getCommonTokenValueId(String codeSystem, String tokenValue) {
        Long result;

        // Find the code-system first
        Integer codeSystemId = getCodeSystemId(codeSystem);

        if (codeSystemId != null) {
            CommonTokenValue key = new CommonTokenValue(codeSystemId, tokenValue);
            LinkedHashMap<CommonTokenValue,Long> valMap = commonTokenValues.get();
            result = valMap != null ? valMap.get(key) : null;
            if (result == null) {
                // not found in the local cache, try the shared cache
                synchronized (tokenValuesCache) {
                    result = tokenValuesCache.get(key);
                }

                if (result != null) {
                    // add to the local cache so we can find it again without locking
                    addTokenValue(key, result);
                }
            }
        } else {
            // The code-system isn't cached, so we don't know the id and therefore
            // can't look up the token value. This isn't a big deal, because we
            // always cache the code-system whenever we cache the token-value, so
            // if the code-system isn't found, it is unlikely the token value would
            // be available anyway (so a database read is inevitable).
            result = null;
        }

        return result;
    }
}