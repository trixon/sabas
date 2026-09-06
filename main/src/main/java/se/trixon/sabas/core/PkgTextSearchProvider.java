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

import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;

public class PkgTextSearchProvider implements SearchProvider {

    private final FilterManager mFilterManager = FilterManager.getInstance();
    private final PkgManager mPkgManager = PkgManager.getInstance();

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        for (var pkg : mFilterManager.filterNow(request.getText())) {
            if (!response.addResult(() -> {
                //TODO set selected item in list
                mPkgManager.setSelectedPkg(pkg);
                System.out.println(pkg.getName());
            }, pkg.getName())) {
                break;
            }
        }
    }
}
