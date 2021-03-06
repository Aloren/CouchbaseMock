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
package com.couchbase.mock.control.handlers;

import com.couchbase.mock.Bucket;
import com.couchbase.mock.CouchbaseMock;
import com.couchbase.mock.control.CommandStatus;
import com.couchbase.mock.control.MockCommand;
import com.couchbase.mock.memcached.MemcachedServer;
import com.couchbase.mock.memcached.protocol.CommandCode;
import com.couchbase.mock.memcached.protocol.ErrorCode;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

public class OpfailCommandHandler extends MockCommand {

    @Override
    public CommandStatus execute(@NotNull CouchbaseMock mock,
            @NotNull Command command, @NotNull JsonObject payload) {

        ErrorCode eCode = ErrorCode.SUCCESS;
        boolean eCodeFound = false;

        JsonElement eServerList = null;
        if (payload.has("servers")) {
            eServerList = payload.get("servers");
        }

        List<Integer> serverList = new LinkedList<Integer>();
        if (eServerList != null) {
            for (JsonElement ix : eServerList.getAsJsonArray()) {
                serverList.add(ix.getAsInt());
            }
        }

        int count = payload.get("count").getAsInt();
        short iCode = payload.get("code").getAsShort();
        CommandCode operation = CommandCode.ILLEGAL; // default to all operations
        if (payload.has("operation")) {
            operation = CommandCode.valueOf(payload.get("operation").getAsInt());
        }
        for (ErrorCode rc : ErrorCode.values()) {
            if (iCode == rc.value()) {
                eCode = rc;
                eCodeFound = true;
                break;
            }
        }

        if (!eCodeFound) {
            return new CommandStatus().fail("Invalid error code");
        }

        for (Bucket bucket : mock.getBuckets().values()) {
            MemcachedServer[] servers = bucket.getServers();
            for (int ii = 0; ii < servers.length; ii++) {
                // In this case, size() is easier to read :)
                if (serverList.size() > 0 && !serverList.contains(ii)) {
                    continue;
                }

                servers[ii].updateFailMakerContext(eCode, count, operation);
            }
        }

        return new CommandStatus();
    }
}