package com.webchat.platformapi.webread;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.IDN;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebReadService with DNS-pinned connections.
 * <p>
 * Resolves DNS, validates all returned IPs are public, then connects
 * directly to a validated IP (setting Host header manually) so the
 * DNS resolution used for validation is the same one used for the
 * actual TCP connection — eliminating the TOCTOU / DNS-rebinding gap.
 */
@Service
public class WebReadService {

    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024; // 2 MB

    private final boolean enabled;
    private final int maxContentChars;
    private final int connectTimeoutMs;
    private final boolean allowHttp;
    private final boolean enforceHostAllowlist;
    private final String allowedHostsRaw;

    public WebReadService(
            @Value("${platform.web-read.enabled:false}") boolean enabled,
            @Value("${platform.web-read.max-content-chars:8000}") int maxContentChars,
            @Value("${platform.web-read.connect-timeout-ms:8000}") int connectTimeoutMs,
            @Value("${platform.web-read.allow-http:false}") boolean allowHttp,
            @Value("${platform.web-read.enforce-host-allowlist:true}") boolean enforceHostAllowlist,
            @Value("${platform.web-read.allowed-hosts:}") String allowedHostsRaw
    ) {
        this.enabled = enabled;
        this.maxContentChars = maxContentChars;
        this.connectTimeoutMs = connectTimeoutMs;
        this.allowHttp = allowHttp;
        this.enforceHostAllowlist = enforceHostAllowlist;
        this.allowedHostsRaw = allowedHostsRaw == null ? "" : allowedHostsRaw;
    }

    public WebReadResult read(String rawUrl) {
        if (!enabled) return WebReadResult.error("web read is disabled");
        if (rawUrl == null || rawUrl.isBlank()) return WebReadResult.error("url is required");
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (Exception e) {
            return WebReadResult.error("invalid url");
        }
        String validationError = validateUri(uri);
        if (validationError != null) {
            return WebReadResult.error(validationError);
        }

        try {
            URI current = uri;
            for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
                PinnedResponse pinnedResponse = pinnedFetch(current);

                if (isRedirect(pinnedResponse.statusCode)) {
                    String location = pinnedResponse.getHeader("Location");
                    if (location == null || location.isBlank()) {
                        return WebReadResult.error("redirect location is missing");
                    }
                    current = current.resolve(location);
                    String redirectValidationError = validateUri(current);
                    if (redirectValidationError != null) {
                        return WebReadResult.error(redirectValidationError);
                    }
                    continue;
                }

                if (pinnedResponse.statusCode < 200 || pinnedResponse.statusCode >= 300) {
                    return WebReadResult.error("web read failed: status=" + pinnedResponse.statusCode);
                }

                String html = pinnedResponse.bodyAsString();
                Document doc = Jsoup.parse(html, current.toString());
                doc.select("script,style,noscript,iframe,svg").remove();
                String title = doc.title() == null ? "" : doc.title().trim();
                String text = doc.body() == null ? "" : doc.body().text().replaceAll("\\s+", " ").trim();
                if (text.isEmpty()) return WebReadResult.error("page text is empty");
                int originalLength = text.length();
                boolean truncated = false;
                if (text.length() > maxContentChars) {
                    text = text.substring(0, maxContentChars);
                    truncated = true;
                }
                return WebReadResult.ok(current.toString(), title, text, truncated, originalLength);
            }
            return WebReadResult.error("too many redirects");
        } catch (SecurityException e) {
            return WebReadResult.error("host is not allowed");
        } catch (Exception e) {
            return WebReadResult.error("web read failed");
        }
    }

    /**
     * Fetch a URL with DNS-pinned connection:
     * 1. Resolve DNS for the host
     * 2. Validate ALL resolved IPs are public
     * 3. Connect directly to the validated IP, setting Host header
     *
     * This eliminates the TOCTOU gap where DNS could return a different
     * IP between validation and actual connection.
     */
    private PinnedResponse pinnedFetch(URI uri) throws Exception {
        String host = normalizeHost(uri.getHost());
        if (host == null) throw new IllegalArgumentException("invalid host");

        // Step 1: Resolve DNS
        InetAddress[] addresses = resolveAddresses(host);
        if (addresses == null || addresses.length == 0) {
            throw new IllegalArgumentException("DNS resolution failed");
        }

        // Step 2: Validate ALL resolved IPs
        for (InetAddress addr : addresses) {
            if (isUnsafeAddress(addr)) {
                throw new SecurityException("host resolves to unsafe address");
            }
        }

        // Step 3: Pick the first safe address and connect directly to it
        InetAddress pinnedAddress = addresses[0];
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port <= 0) {
            port = "https".equals(scheme) ? 443 : 80;
        }

        // Build URL with IP address instead of hostname
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        String query = uri.getRawQuery();
        String urlWithIp = scheme + "://" + pinnedAddress.getHostAddress() + ":" + port + path
                + (query != null ? "?" + query : "");

        URL url = URI.create(urlWithIp).toURL();
        HttpURLConnection conn;
        if ("https".equals(scheme)) {
            HttpsURLConnection httpsConn = (HttpsURLConnection) openPinnedConnection(url);
            // Set SNI hostname for TLS
            SSLContext sslContext = createSslContext();
            SSLSocketFactory factory = sslContext.getSocketFactory();
            httpsConn.setSSLSocketFactory(new HostPinnedSSLSocketFactory(factory, host, pinnedAddress, port));
            httpsConn.setHostnameVerifier((hostname, session) ->
                    HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session)
            );
            conn = httpsConn;
        } else {
            conn = openPinnedConnection(url);
        }

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Host", host);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; WebReadBot/1.0)");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*");
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(connectTimeoutMs);
        conn.setInstanceFollowRedirects(false);

        int statusCode = conn.getResponseCode();
        String contentType = conn.getContentType();
        String locationHeader = conn.getHeaderField("Location");

        byte[] body = null;
        if (!isRedirect(statusCode)) {
            try (InputStream is = statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                if (is != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int totalRead = 0;
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        totalRead += n;
                        if (totalRead > MAX_BODY_BYTES) break;
                        baos.write(buf, 0, n);
                    }
                    body = baos.toByteArray();
                }
            }
        }

        conn.disconnect();

        return new PinnedResponse(statusCode, contentType, locationHeader, body);
    }

    protected HttpURLConnection openPinnedConnection(URL url) throws Exception {
        return (HttpURLConnection) url.openConnection();
    }

    protected SSLContext createSslContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        return sslContext;
    }

    // ===================== Inner classes =====================

    /**
     * SSL socket factory that connects to a pinned IP address with proper SNI hostname.
     */
    private static class HostPinnedSSLSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final String sniHost;
        private final InetAddress pinnedAddress;
        private final int port;

        HostPinnedSSLSocketFactory(SSLSocketFactory delegate, String sniHost, InetAddress pinnedAddress, int port) {
            this.delegate = delegate;
            this.sniHost = sniHost;
            this.pinnedAddress = pinnedAddress;
            this.port = port;
        }

        private Socket configureSocket(Socket socket) {
            if (socket instanceof javax.net.ssl.SSLSocket sslSocket) {
                javax.net.ssl.SSLParameters params = sslSocket.getSSLParameters();
                params.setServerNames(java.util.List.of(new javax.net.ssl.SNIHostName(sniHost)));
                params.setEndpointIdentificationAlgorithm("HTTPS");
                sslSocket.setSSLParameters(params);
                return sslSocket;
            }
            return socket;
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws java.io.IOException {
            return configureSocket(delegate.createSocket(s, sniHost, port, autoClose));
        }

        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public Socket createSocket(String h, int p) throws java.io.IOException { return configureSocket(delegate.createSocket(pinnedAddress, port)); }
        @Override public Socket createSocket(String h, int p, InetAddress la, int lp) throws java.io.IOException { return configureSocket(delegate.createSocket(pinnedAddress, port, la, lp)); }
        @Override public Socket createSocket(InetAddress a, int p) throws java.io.IOException { return configureSocket(delegate.createSocket(pinnedAddress, port)); }
        @Override public Socket createSocket(InetAddress a, int p, InetAddress la, int lp) throws java.io.IOException { return configureSocket(delegate.createSocket(pinnedAddress, port, la, lp)); }
    }

    private static class PinnedResponse {
        final int statusCode;
        final String contentType;
        final String locationHeader;
        final byte[] body;

        PinnedResponse(int statusCode, String contentType, String locationHeader, byte[] body) {
            this.statusCode = statusCode;
            this.contentType = contentType;
            this.locationHeader = locationHeader;
            this.body = body;
        }

        String getHeader(String name) {
            if ("Location".equalsIgnoreCase(name)) return locationHeader;
            return null;
        }

        String bodyAsString() {
            if (body == null) return "";
            Charset charset = parseCharset(contentType);
            return new String(body, charset);
        }

        private static Charset parseCharset(String contentType) {
            if (contentType == null) return StandardCharsets.UTF_8;
            Matcher m = Pattern.compile("charset\\s*=\\s*([\\w-]+)", Pattern.CASE_INSENSITIVE).matcher(contentType);
            if (m.find()) {
                try {
                    return Charset.forName(m.group(1));
                } catch (IllegalArgumentException e) {
                    return StandardCharsets.UTF_8;
                }
            }
            return StandardCharsets.UTF_8;
        }
    }

    // ===================== Validation =====================

    public boolean isEnabled() { return enabled; }
    public int getMaxContentChars() { return maxContentChars; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public boolean isAllowHttp() { return allowHttp; }
    public boolean isEnforceHostAllowlist() { return enforceHostAllowlist; }
    public String getAllowedHostsRaw() { return allowedHostsRaw; }

    private String validateUri(URI uri) {
        if (uri == null) return "invalid url";
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !(allowHttp && "http".equals(scheme))) {
            return "only https urls are allowed";
        }
        String host = normalizeHost(uri.getHost());
        if (host == null || host.isBlank()) {
            return "invalid host";
        }
        if (enforceHostAllowlist && !matchesAllowedHost(host)) {
            return "host is not in allowlist";
        }
        return null;
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    private boolean isPublicHost(String host) {
        try {
            InetAddress[] addresses = resolveAddresses(host);
            if (addresses == null || addresses.length == 0) {
                return false;
            }
            return Arrays.stream(addresses).noneMatch(WebReadService::isUnsafeAddress);
        } catch (Exception e) {
            return false;
        }
    }

    protected InetAddress[] resolveAddresses(String host) throws Exception {
        return InetAddress.getAllByName(host);
    }

    private static boolean isUnsafeAddress(InetAddress address) {
        return address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isUnsafeIpv4(address)
                || isUnsafeIpv6(address);
    }

    private static boolean isUnsafeIpv4(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] raw = address.getAddress();
        int b0 = raw[0] & 0xff;
        int b1 = raw[1] & 0xff;
        int b2 = raw[2] & 0xff;

        if (b0 == 0 || b0 >= 224) {
            return true;
        }
        if (b0 == 100 && b1 >= 64 && b1 <= 127) {
            return true;
        }
        if (b0 == 198 && (b1 == 18 || b1 == 19)) {
            return true;
        }
        if (b0 == 192 && b1 == 0 && b2 == 2) {
            return true;
        }
        if (b0 == 198 && b1 == 51 && b2 == 100) {
            return true;
        }
        return b0 == 203 && b1 == 0 && b2 == 113;
    }

    private static boolean isUnsafeIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        byte[] raw = address.getAddress();
        if (raw.length != 16) {
            return true;
        }
        if (isIpv4MappedAddress(raw)) {
            return isUnsafeMappedIpv4(raw);
        }
        if ((raw[0] & 0xfe) == 0xfc) {
            return true;
        }
        return (raw[0] & 0xff) == 0x20
                && (raw[1] & 0xff) == 0x01
                && (raw[2] & 0xff) == 0x0d
                && (raw[3] & 0xff) == 0xb8;
    }

    private static boolean isIpv4MappedAddress(byte[] raw) {
        for (int i = 0; i < 10; i++) {
            if (raw[i] != 0) {
                return false;
            }
        }
        return raw[10] == (byte) 0xff && raw[11] == (byte) 0xff;
    }

    private static boolean isUnsafeMappedIpv4(byte[] raw) {
        int b0 = raw[12] & 0xff;
        int b1 = raw[13] & 0xff;
        int b2 = raw[14] & 0xff;

        if (b0 == 0 || b0 >= 224) {
            return true;
        }
        if (b0 == 10 || b0 == 127) {
            return true;
        }
        if (b0 == 169 && b1 == 254) {
            return true;
        }
        if (b0 == 172 && b1 >= 16 && b1 <= 31) {
            return true;
        }
        if (b0 == 192 && b1 == 168) {
            return true;
        }
        if (b0 == 100 && b1 >= 64 && b1 <= 127) {
            return true;
        }
        if (b0 == 198 && (b1 == 18 || b1 == 19)) {
            return true;
        }
        if (b0 == 192 && b1 == 0 && b2 == 2) {
            return true;
        }
        if (b0 == 198 && b1 == 51 && b2 == 100) {
            return true;
        }
        return b0 == 203 && b1 == 0 && b2 == 113;
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        try {
            return IDN.toASCII(host.trim()).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean matchesAllowedHost(String host) {
        if (host == null || host.isBlank()) return false;
        String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
        String raw = allowedHostsRaw == null ? "" : allowedHostsRaw.trim();
        if (raw.isEmpty()) return false;
        for (String entry : raw.split(",")) {
            String allowed = entry.trim().toLowerCase(Locale.ROOT);
            if (allowed.isEmpty()) continue;
            if (normalizedHost.equals(allowed)) {
                return true;
            }
            if (allowed.startsWith("*.")) {
                String suffix = allowed.substring(1);
                if (normalizedHost.endsWith(suffix) && normalizedHost.length() > suffix.length()) {
                    return true;
                }
            } else if (allowed.startsWith(".")) {
                if (normalizedHost.endsWith(allowed) && normalizedHost.length() > allowed.length()) {
                    return true;
                }
            }
        }
        return false;
    }

    public record WebReadResult(boolean success, String error, String url, String title, String content, boolean truncated, int contentLength) {
        public static WebReadResult ok(String url, String title, String content, boolean truncated, int contentLength) {
            return new WebReadResult(true, null, url, title, content, truncated, contentLength);
        }

        public static WebReadResult error(String error) {
            return new WebReadResult(false, error, null, null, null, false, 0);
        }
    }
}
