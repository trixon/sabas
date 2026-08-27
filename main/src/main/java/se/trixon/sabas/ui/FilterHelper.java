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
package se.trixon.sabas.ui;

/**
 *
 * @author Patrik Karlström <patrik@trixon.se>
 */
public class FilterHelper {

    public static boolean matchSplitFuzzy(String pkgName, String pkgSummary, String pkgDesc,
            String[] searchTokens, boolean searchSummary, boolean searchDesc) {

        if (searchTokens == null || searchTokens.length == 0) {
            return true;
        }

        for (var token : searchTokens) {
            if (token.isEmpty()) {
                continue;
            }

            boolean tokenMatched = false;

            if (pkgName.contains(token)) {
                tokenMatched = true;
            } else if (token.length() >= 2 && containsSubsequence(pkgName, token)) {
                tokenMatched = true;
            }

            if (!tokenMatched && searchSummary && pkgSummary != null && pkgSummary.contains(token)) {
                tokenMatched = true;
            }
            if (!tokenMatched && searchDesc && pkgDesc != null && pkgDesc.contains(token)) {
                tokenMatched = true;
            }

            if (!tokenMatched) {
                return false;
            }
        }

        return true;
    }

    private static boolean containsSubsequence(String source, String token) {
        int sourceLen = source.length();
        int tokenLen = token.length();

        if (tokenLen > sourceLen) {
            return false;
        }

        int srcIdx = 0;
        int tokIdx = 0;

        while (srcIdx < sourceLen && tokIdx < tokenLen) {
            if (source.charAt(srcIdx) == token.charAt(tokIdx)) {
                tokIdx++;
            }
            srcIdx++;
        }

        return tokIdx == tokenLen;
    }

    static int calculateRelevance(String pkgName, String fullSearchText, String[] searchTokens) {
        if (fullSearchText.isEmpty()) {
            return 100;
        }

        if (pkgName.equals(fullSearchText)) {
            return 0;
        }

        if (pkgName.startsWith(fullSearchText)) {
            return 1;
        }

        if (pkgName.contains(fullSearchText)) {
            return 2;
        }

        if (searchTokens.length > 0 && pkgName.contains(searchTokens[0])) {
            return 3;
        }

        return 4;
    }

}
