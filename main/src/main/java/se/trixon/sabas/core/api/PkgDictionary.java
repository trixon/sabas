/*
 * Copyright 2026 Patrik Karlström <patrik@trixon.se>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.trixon.sabas.core.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class PkgDictionary {

    private final Map<DictionarySection, AtomicInteger> mCounters = new EnumMap<>(DictionarySection.class);
    private final Map<DictionarySection, Map<Integer, String>> mIdToStringMap = new EnumMap<>(DictionarySection.class);
    private final Map<DictionarySection, Map<String, Integer>> mStringToIdMap = new EnumMap<>(DictionarySection.class);

    public static PkgDictionary getInstance() {
        return Holder.INSTANCE;
    }

    private PkgDictionary() {
        for (var section : DictionarySection.values()) {
            mStringToIdMap.put(section, new ConcurrentHashMap<>());
            mIdToStringMap.put(section, new ConcurrentHashMap<>());
            mCounters.put(section, new AtomicInteger(0));
        }
    }

    public synchronized void clear() {
        for (var section : DictionarySection.values()) {
            mStringToIdMap.get(section).clear();
            mIdToStringMap.get(section).clear();
            mCounters.get(section).set(0);
        }
    }

    public void debugPrint() {
        for (var section : DictionarySection.values()) {
            System.out.println();
            System.out.println(section.name());
            System.out.println(String.join(", ", getAllValuesSorted(section)));
        }
    }

    public synchronized List<String> getAllValuesSorted(DictionarySection section) {
        var values = new ArrayList<>(mIdToStringMap.get(section).values());
        Collections.sort(values, String.CASE_INSENSITIVE_ORDER);

        return values;
    }

    public synchronized int getId(DictionarySection section, String value) {
        if (value == null || value.strip().isEmpty()) {
            return -1;
        }

        return mStringToIdMap.get(section).getOrDefault(value, -1);
    }

    public int getOrCreateId(DictionarySection section, String value) {
        if (value == null || value.strip().isEmpty()) {
            return -1;
        }

        var stringToId = mStringToIdMap.get(section);
        var idToString = mIdToStringMap.get(section);

        return stringToId.computeIfAbsent(value, key -> {
            int currentId = mCounters.get(section).getAndIncrement();
            idToString.put(currentId, key);

            return currentId;
        });
    }

    public synchronized String getString(DictionarySection section, int id) {
        return mIdToStringMap.get(section).getOrDefault(id, "");
    }

    private static class Holder {

        private static final PkgDictionary INSTANCE = new PkgDictionary();
    }
}
