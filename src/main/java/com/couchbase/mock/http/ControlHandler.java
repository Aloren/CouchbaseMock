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
package com.couchbase.mock.http;

import com.couchbase.mock.control.CommandNotFoundException;
import com.couchbase.mock.control.MockCommandDispatcher;
import com.couchbase.mock.control.handlers.MockHelpCommandHandler;
import com.couchbase.mock.httpio.HandlerUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLDecoder;

/**
 *
 * @author Mark Nunberg
 */
public class ControlHandler implements HttpRequestHandler {
    private final MockCommandDispatcher dispatcher;
    private class WantHelpException extends Exception { }

    public ControlHandler(MockCommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;

    }
    /**
     * Sends a help text with the provided code
     * @param response The response
     * @param code The HTTP status code to be used in the response
     */
    private static void sendHelpText(HttpResponse response, int code) throws IOException {
        HandlerUtil.makeStringResponse(response, MockHelpCommandHandler.getIndentedHelp());
        response.setStatusCode(code);
    }

    private JsonObject getQueryPayload(URL url, HttpRequest request) throws HttpException, IOException {
        // First check the URL itself to see if there's a payload
        JsonObject payload;
        payload = HandlerUtil.getJsonQuery(url);
        if (payload != null) {
            return payload;
        }

        // Next, check the actual request body..
        if (!(request instanceof HttpEntityEnclosingRequest)) {
            return null;
        }

        String encoded = EntityUtils.toString(((HttpEntityEnclosingRequest)request).getEntity());
        // Determine how to parse this..
        Header header = request.getLastHeader(HttpHeaders.CONTENT_TYPE);
        if (header == null || header.getValue().equals(ContentType.APPLICATION_FORM_URLENCODED.getMimeType())) {
            // Parse as URL:
            url = new URL("http://foo.com?" + encoded);
            return HandlerUtil.getJsonQuery(url);
        } else if (header.getValue().equals(ContentType.APPLICATION_JSON.getMimeType())) {
            JsonParser parser = new JsonParser();
            JsonElement elem = parser.parse(encoded);
            if (elem != null) {
                return elem.getAsJsonObject();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        URL url = HandlerUtil.getUrl(request);
        String path = url.getPath();
        path = path.replaceFirst("^/+", "");
        String[] components = path.split("/");

        try {

            if (components.length != 2) {
                throw new CommandNotFoundException("/mock/<COMMAND>", null);
            }

            if (components[1].equals("help")) {
                throw new WantHelpException();
            }

            JsonObject payload = getQueryPayload(url, request);
            String cmdStr = URLDecoder.decode(components[1], "UTF-8");
            String rStr = dispatcher.dispatch(cmdStr, payload).toString();
            HandlerUtil.makeJsonResponse(response, rStr);

        } catch (WantHelpException e) {
            sendHelpText(response, 200);

        } catch (CommandNotFoundException e) {
            sendHelpText(response, 404);

        } catch (RuntimeException e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(new PrintWriter(pw));
            HandlerUtil.makeStringResponse(response, sw.toString());
            HandlerUtil.bailResponse(context, response);
            throw e;

        }
    }
}
