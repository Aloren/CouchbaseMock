/*
 * Copyright 2017 Couchbase, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.couchbase.mock.memcached.errormap;

import com.google.gson.annotations.SerializedName;

/**
 * Created by mnunberg on 4/12/17.
 */
public class RetrySpec {
    public static final String CONSTANT = "constant";
    public static final String LINEAR = "linear";
    public static final String EXPONENTIAL = "exponential";

    private String strategy = null;
    private int interval = 0;
    private int after;

    @SerializedName("max-duration")
    private int maxDuration;
    private int ceil;

    public String getStrategy() {
        return strategy;
    }

    public int getInterval() {
        return interval;
    }

    public int getAfter() {
        return after;
    }

    public int getMaxDuration() {
        return maxDuration;
    }

    public int getCeil() {
        return ceil;
    }
}
