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
package com.couchbase.mock.memcached;

import com.couchbase.mock.Bucket;
import com.couchbase.mock.memcached.protocol.BinaryCommand;
import com.couchbase.mock.memcached.protocol.BinaryResponse;
import com.couchbase.mock.memcached.protocol.BinarySaslResponse;
import com.couchbase.mock.memcached.protocol.CommandCode;
import com.couchbase.mock.memcached.protocol.ErrorCode;
import com.couchbase.mock.security.sasl.Sasl;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.net.ProtocolException;

/**
 * @author Sergey Avseyev
 */
public class SaslCommandExecutor implements CommandExecutor {
    // http://www.ietf.org/rfc/rfc4616.txt
    // http://www.ietf.org/rfc/rfc5802.txt

    private SaslServer saslServer;

    @Override
    public BinaryResponse execute(BinaryCommand cmd, MemcachedServer server, MemcachedConnection client) throws ProtocolException {
        CommandCode cc = cmd.getComCode();

        BinaryResponse response;
        String mechanism = cmd.getKey();
        switch (cc) {
            case SASL_LIST_MECHS:
                StringBuilder sb = new StringBuilder();
                for (String m : server.getSaslMechanisms()) {
                    sb.append(m + " ");
                }
                response = new BinarySaslResponse(cmd, sb.toString());
                break;
            case SASL_AUTH:
                if (server.supportsSaslMechanism(mechanism)) {
                    if ("PLAIN".equals(mechanism)) {
                        response = plainAuth(cmd, server, client);
                    } else {
                        createSaslServer(cmd, server);
                        response = saslAuth(cmd, client);
                    }
                } else {
                    response = new BinarySaslResponse(cmd);
                }
                break;
            case SASL_STEP:
                response = saslAuth(cmd, client);
                break;
            default:
                response = new BinarySaslResponse(cmd);
                break;
        }
        return response;

    }

    private BinarySaslResponse plainAuth(BinaryCommand cmd, MemcachedServer server, MemcachedConnection client) {
        byte[] raw = cmd.getValue();
        String[] strs = new String[3];

        int offset = 0;
        int oix = 0;

        for (int ii = 0; ii < raw.length; ii++) {
            if (raw[ii] == 0x0) {
                strs[oix++] = new String(raw, offset, ii - offset);
                offset = ii + 1;
            }
        }
        strs[oix] = new String(raw, offset, raw.length - offset);

        String user = strs[1];
        String pass = strs[2];

        BinarySaslResponse response;

        Bucket bucket = server.getBucket();
        String bPass = bucket.getPassword();

        if (!bucket.getName().equals(user) || !bPass.equals(pass)) {
            response = new BinarySaslResponse(cmd);
        } else {
            client.setAuthenticated();
            response = new BinarySaslResponse(cmd, "Authenticated");
        }
        return response;
    }

    private void createSaslServer(BinaryCommand cmd, MemcachedServer server)
            throws ProtocolException {
        try {
            Bucket bucket = server.getBucket();
            saslServer = Sasl.createSaslServer(cmd.getKey(), server.getHostname(), null,
                    new SaslCallbackHandler(bucket.getName(), bucket.getPassword()));
        } catch (SaslException e) {
            throw new ProtocolException(e.getMessage());
        }
    }

    private BinaryResponse saslAuth(BinaryCommand cmd, MemcachedConnection client) throws ProtocolException {
        if (saslServer == null) {
            return new BinarySaslResponse(cmd);
        }
        byte[] raw = cmd.getValue();
        try {
            BinaryResponse response;
            final byte[] challenge = saslServer.evaluateResponse(raw);
            if (saslServer.isComplete()) {
                client.setAuthenticated();
                response = new BinarySaslResponse(cmd, new String(challenge));
            } else {
                response = new BinarySaslResponse(cmd, new String(challenge), ErrorCode.AUTH_CONTINUE);
            }
            return response;
        } catch (SaslException e) {
            throw new ProtocolException(e.getMessage());
        }
    }
}
