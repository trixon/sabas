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
package se.trixon.sabas.core;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import se.trixon.almond.util.swing.DelayedResetRunner;
import se.trixon.sabas.api.Pkg;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class FilterManager {

    private final DelayedResetRunner mDelayedResetRunner;
    private boolean mFilterDescription;
    private boolean mFilterSummary;
    private final Set<Filter> mFilters = new HashSet<>();
    private final PkgManager mPkgManager = PkgManager.getInstance();
    private String mText;

    public static FilterManager getInstance() {
        return FilterManagerHolder.INSTANCE;
    }

    private FilterManager() {
        mDelayedResetRunner = new DelayedResetRunner(300, () -> {
            filter();
        });
    }

    public void add(Filter filter) {
        mFilters.add(filter);
    }

    public boolean filter(Pkg pkg) {
        return mFilters.stream().allMatch(filter -> filter.filter(pkg));
    }

    public void requestFiltering() {
        mDelayedResetRunner.reset();
    }

    public void reset() {
        mFilters.forEach(f -> f.reset());
    }

    public void setFilterDescription(boolean filterDescription) {
        mFilterDescription = filterDescription;
    }

    public void setFilterSummary(boolean filterSummary) {
        mFilterSummary = filterSummary;
    }

    public void setText(String text) {
        mText = text;
    }

    private void filter() {
        var filterStream = mPkgManager.getAllItems().stream()
                .filter(p -> filter(p));

        if (StringUtils.isNotBlank(mText)) {
            var cleanInput = mText.trim().toLowerCase();
            var tokens = StringUtils.split(cleanInput);

            filterStream = filterStream
                    .filter(p -> FilterHelper.matchSplitFuzzy(
                            p.getNameLower(),
                            p.getSummaryLower(),
                            p.getDescriptionLower(),
                            tokens,
                            mFilterSummary,
                            mFilterDescription))
                    .map(p -> {
                        int score = FilterHelper.calculateRelevance(p.getNameLower(), cleanInput, tokens);
                        if (score == 4 && tokens.length > 0 && !p.getNameLower().contains(tokens[0])) {
                            score = 5;
                        }
                        return new WeightedPkg(p, score);
                    })
                    .sorted(Comparator
                            .comparingInt(WeightedPkg::relevance)
                            .thenComparing((w1, w2) -> w1.pkg().getName().compareToIgnoreCase(w2.pkg().getName()))
                    )
                    .map(WeightedPkg::pkg);
        }
        mPkgManager.setFilteredItems(filterStream.toList());

    }

    private static class FilterManagerHolder {

        private static final FilterManager INSTANCE = new FilterManager();
    }

    private record WeightedPkg(Pkg pkg, int relevance) {

    }
}
