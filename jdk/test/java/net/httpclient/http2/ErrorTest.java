/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8157105
 * @library /lib/testlibrary server
 * @key intermittent
 * @build jdk.testlibrary.SimpleSSLContext
 * @modules jdk.incubator.httpclient/jdk.incubator.http.internal.common
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.frame
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.hpack
 *          java.security.jgss
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=ssl,errors ErrorTest
 * @summary check exception thrown when bad TLS parameters selected
 */

import java.io.IOException;
import java.net.URI;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import jdk.testlibrary.SimpleSSLContext;
import static jdk.incubator.http.HttpClient.Version.HTTP_2;
import static jdk.incubator.http.HttpRequest.BodyProcessor.fromString;
import static jdk.incubator.http.HttpResponse.BodyHandler.discard;

import org.testng.annotations.Test;

/**
 * When selecting an unacceptable cipher suite the TLS handshake will fail.
 * But, the exception that was thrown was not being returned up to application
 * causing hang problems
 */
public class ErrorTest {

    static final String[] CIPHER_SUITES = new String[]{ "TLS_KRB5_WITH_3DES_EDE_CBC_SHA" };

    static final String SIMPLE_STRING = "Hello world Goodbye world";

    //@Test(timeOut=5000)
    @Test
    public void test() throws Exception {
        SSLContext sslContext = (new SimpleSSLContext()).get();
        ExecutorService exec = Executors.newCachedThreadPool();
        HttpClient client = HttpClient.newBuilder()
                                      .executor(exec)
                                      .sslContext(sslContext)
                                      .sslParameters(new SSLParameters(CIPHER_SUITES))
                                      .version(HTTP_2)
                                      .build();

        Http2TestServer httpsServer = null;
        try {
            httpsServer = new Http2TestServer(true,
                                              0,
                                              exec,
                                              sslContext);
            httpsServer.addHandler(new EchoHandler(), "/");
            int httpsPort = httpsServer.getAddress().getPort();
            String httpsURIString = "https://127.0.0.1:" + httpsPort + "/bar/";

            httpsServer.start();
            URI uri = URI.create(httpsURIString);
            System.err.println("Request to " + uri);

            HttpRequest req = HttpRequest.newBuilder(uri)
                                    .POST(fromString(SIMPLE_STRING))
                                    .build();
            HttpResponse response;
            try {
                response = client.send(req, discard(null));
                throw new RuntimeException("Unexpected response: " + response);
            } catch (IOException e) {
                System.err.println("Caught Expected IOException: " + e);
            }
            System.err.println("DONE");
        } finally {
            if (httpsServer != null )  { httpsServer.stop(); }
            exec.shutdownNow();
        }
    }
}