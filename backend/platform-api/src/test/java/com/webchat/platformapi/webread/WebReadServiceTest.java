package com.webchat.platformapi.webread;

import org.junit.jupiter.api.Test;

import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WebReadServiceTest {

    @Test
    void blocksLoopbackHostBeforeFetch() {
        WebReadService service = new WebReadService(
                true,
                8000,
                8000,
                false,
                false,
                ""
        );

        WebReadService.WebReadResult result = service.read("https://127.0.0.1/internal");

        assertFalse(result.success());
        assertEquals("host is not allowed", result.error());
    }

    @Test
    void blocksPrivateIpBeforeFetch() {
        WebReadService service = new WebReadService(
                true,
                8000,
                8000,
                false,
                false,
                ""
        );

        WebReadService.WebReadResult result = service.read("https://10.0.0.1/internal");

        assertFalse(result.success());
        assertEquals("host is not allowed", result.error());
    }

    @Test
    void blocksHostWhenAnyResolvedAddressIsUnsafe() throws Exception {
        WebReadService service = new WebReadService(
                true,
                8000,
                8000,
                false,
                false,
                ""
        ) {
            @Override
            protected InetAddress[] resolveAddresses(String host) throws Exception {
                return new InetAddress[] {
                        InetAddress.getByName("93.184.216.34"),
                        InetAddress.getByName("127.0.0.1")
                };
            }
        };

        WebReadService.WebReadResult result = service.read("https://example.com/article");

        assertFalse(result.success());
        assertEquals("host is not allowed", result.error());
    }

    @Test
    void blocksCarrierGradeNatResolvedAddress() throws Exception {
        WebReadService service = new WebReadService(
                true,
                8000,
                8000,
                false,
                false,
                ""
        ) {
            @Override
            protected InetAddress[] resolveAddresses(String host) throws Exception {
                return new InetAddress[] { InetAddress.getByName("100.64.0.10") };
            }
        };

        WebReadService.WebReadResult result = service.read("https://example.com/article");

        assertFalse(result.success());
        assertEquals("host is not allowed", result.error());
    }

    @Test
    void blocksIpv6UniqueLocalResolvedAddress() throws Exception {
        WebReadService service = new WebReadService(
                true,
                8000,
                8000,
                false,
                false,
                ""
        ) {
            @Override
            protected InetAddress[] resolveAddresses(String host) throws Exception {
                return new InetAddress[] { InetAddress.getByName("fc00::1") };
            }
        };

        WebReadService.WebReadResult result = service.read("https://example.com/article");

        assertFalse(result.success());
        assertEquals("host is not allowed", result.error());
    }

    @Test
    void resolvesOncePerFetchAttemptWhenUnsafeAddressDetected() throws Exception {
        AtomicInteger resolveCount = new AtomicInteger();
        WebReadService service = new WebReadService(
                true,
                8000,
                8000,
                false,
                false,
                ""
        ) {
            @Override
            protected InetAddress[] resolveAddresses(String host) throws Exception {
                resolveCount.incrementAndGet();
                return new InetAddress[] { InetAddress.getByName("127.0.0.1") };
            }
        };

        WebReadService.WebReadResult result = service.read("https://example.com/article");

        assertFalse(result.success());
        assertEquals("host is not allowed", result.error());
        assertEquals(1, resolveCount.get());
    }

    @Test
    void pinnedSslFactorySetsSniAndHttpsEndpointIdentification() throws Exception {
        Class<?> factoryClass = Class.forName("com.webchat.platformapi.webread.WebReadService$HostPinnedSSLSocketFactory");
        Constructor<?> constructor = factoryClass.getDeclaredConstructor(SSLSocketFactory.class, String.class, InetAddress.class, int.class);
        constructor.setAccessible(true);

        RecordingSSLSocketFactory delegate = new RecordingSSLSocketFactory();
        Object factory = constructor.newInstance(delegate, "example.com", InetAddress.getByName("93.184.216.34"), 443);
        Method createSocket = factoryClass.getMethod("createSocket", String.class, int.class);

        SSLSocket socket = (SSLSocket) createSocket.invoke(factory, "93.184.216.34", 443);
        SSLParameters params = socket.getSSLParameters();

        assertEquals("HTTPS", params.getEndpointIdentificationAlgorithm());
        assertNotNull(params.getServerNames());
        SNIServerName serverName = params.getServerNames().get(0);
        assertInstanceOf(SNIHostName.class, serverName);
        assertEquals("example.com", ((SNIHostName) serverName).getAsciiName());
    }

    @Test
    void usesPinnedIpUrlAndOriginalHostHeader() throws Exception {
        RecordingConnection connection = new RecordingConnection(new URL("http://93.184.216.34/article"));
        WebReadService service = new WebReadService(
                true,
                8000,
                8000,
                true,
                false,
                ""
        ) {
            @Override
            protected InetAddress[] resolveAddresses(String host) throws Exception {
                return new InetAddress[] { InetAddress.getByName("93.184.216.34") };
            }

            @Override
            protected HttpURLConnection openPinnedConnection(URL url) {
                connection.requestUrl = url;
                return connection;
            }
        };

        WebReadService.WebReadResult result = service.read("http://example.com/article");

        assertEquals("93.184.216.34", connection.requestUrl.getHost());
        assertEquals("example.com", connection.getRequestProperty("Host"));
        assertEquals("Mozilla/5.0 (compatible; WebReadBot/1.0)", connection.getRequestProperty("User-Agent"));
        assertEquals("Example", result.title());
        assertEquals("hello world", result.content());
    }

    @Test
    void blocksRedirectWhenTargetHostResolvesUnsafeAddress() throws Exception {
        AtomicInteger openCount = new AtomicInteger();
        WebReadService service = new WebReadService(
                true,
                8000,
                8000,
                true,
                false,
                ""
        ) {
            @Override
            protected InetAddress[] resolveAddresses(String host) throws Exception {
                if ("example.com".equals(host)) {
                    return new InetAddress[] { InetAddress.getByName("93.184.216.34") };
                }
                if ("redirected.example.com".equals(host)) {
                    return new InetAddress[] { InetAddress.getByName("127.0.0.1") };
                }
                return super.resolveAddresses(host);
            }

            @Override
            protected HttpURLConnection openPinnedConnection(URL url) {
                openCount.incrementAndGet();
                RecordingConnection connection = new RecordingConnection(url);
                connection.statusCode = 302;
                connection.headers.put("Location", "http://redirected.example.com/internal");
                return connection;
            }
        };

        WebReadService.WebReadResult result = service.read("http://example.com/article");

        assertFalse(result.success());
        assertEquals("host is not allowed", result.error());
        assertEquals(1, openCount.get());
    }

    private static final class RecordingSSLSocketFactory extends SSLSocketFactory {
        @Override
        public String[] getDefaultCipherSuites() {
            return new String[0];
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return new String[0];
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) {
            return new RecordingSSLSocket();
        }

        @Override
        public Socket createSocket(String host, int port) {
            return new RecordingSSLSocket();
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) {
            return new RecordingSSLSocket();
        }

        @Override
        public Socket createSocket(InetAddress host, int port) {
            return new RecordingSSLSocket();
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) {
            return new RecordingSSLSocket();
        }
    }

    private static final class RecordingSSLSocket extends SSLSocket {
        private SSLParameters parameters = new SSLParameters();

        @Override
        public SSLParameters getSSLParameters() {
            return parameters;
        }

        @Override
        public void setSSLParameters(SSLParameters params) {
            this.parameters = params;
        }

        @Override public String[] getSupportedCipherSuites() { return new String[0]; }
        @Override public String[] getEnabledCipherSuites() { return new String[0]; }
        @Override public void setEnabledCipherSuites(String[] suites) {}
        @Override public String[] getSupportedProtocols() { return new String[0]; }
        @Override public String[] getEnabledProtocols() { return new String[0]; }
        @Override public void setEnabledProtocols(String[] protocols) {}
        @Override public SSLSession getSession() { return null; }
        @Override public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {}
        @Override public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {}
        @Override public void startHandshake() {}
        @Override public void setUseClientMode(boolean mode) {}
        @Override public boolean getUseClientMode() { return true; }
        @Override public void setNeedClientAuth(boolean need) {}
        @Override public boolean getNeedClientAuth() { return false; }
        @Override public void setWantClientAuth(boolean want) {}
        @Override public boolean getWantClientAuth() { return false; }
        @Override public void setEnableSessionCreation(boolean flag) {}
        @Override public boolean getEnableSessionCreation() { return true; }
        @Override public void close() throws IOException {}
    }

    private static final class RecordingConnection extends HttpURLConnection {
        private final Map<String, String> headers = new LinkedHashMap<>();
        private byte[] body = "<html><head><title>Example</title></head><body>hello world</body></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        private URL requestUrl;
        private int statusCode = 200;

        private RecordingConnection(URL url) {
            super(url);
            this.requestUrl = url;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }

        @Override
        public void setRequestMethod(String method) throws ProtocolException {
            this.method = method;
        }

        @Override
        public void setRequestProperty(String key, String value) {
            headers.put(key, value);
        }

        @Override
        public String getRequestProperty(String key) {
            return headers.get(key);
        }

        @Override
        public int getResponseCode() {
            return statusCode;
        }

        @Override
        public String getContentType() {
            return "text/html; charset=UTF-8";
        }

        @Override
        public String getHeaderField(String name) {
            return headers.get(name);
        }

        @Override
        public ByteArrayInputStream getInputStream() {
            return new ByteArrayInputStream(body);
        }
    }
}
