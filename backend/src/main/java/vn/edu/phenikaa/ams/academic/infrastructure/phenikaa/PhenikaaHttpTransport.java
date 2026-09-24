package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.*;

public final class PhenikaaHttpTransport implements AutoCloseable {
    static final URI PORTAL = URI.create("https://qldtbeta.phenikaa-uni.edu.vn");
    static final String SCHEDULE_PATH = "/sinhvienapi3/api/SV_ThongTin_MH/DSA4BRINKCIpAiAPKSAv";
    static final String PROFILE_PATH = "/sinhvienapi3/api/SV_Custom/DSA4FSkuLyYVKC8CKSgVKCQ1CS4SLgPP";
    static final String EXAM_PERIODS_PATH = "/sinhvienapi3/api/SV_ThongTin_MH/DSA4BRIVKS4oBiggLw0oIikVKSgP";
    static final String EXAMS_PATH = "/sinhvienapi3/api/SV_ThongTin_MH/DSA4BRINKCIpFSkoHgokCS4gIikVKSgP";
    private final HttpClient client;
    private final URI baseUri;
    private final Duration responseTimeout;
    private final int maxBytes;

    public PhenikaaHttpTransport(Duration connectTimeout, Duration responseTimeout, int maxBytes) {
        this(PORTAL, connectTimeout, responseTimeout, maxBytes);
    }

    // Package-private loopback endpoint is used only by offline HTTP tests.
    PhenikaaHttpTransport(URI baseUri, Duration connectTimeout, Duration responseTimeout, int maxBytes) {
        if (!System.getProperty("jdk.httpclient.HttpClient.log", "").isBlank())
            throw new IllegalStateException("HTTP wire logging must be disabled for the portal client");
        boolean localTest = "http".equals(baseUri.getScheme()) && "127.0.0.1".equals(baseUri.getHost())
                && (baseUri.getPath().isEmpty() || "/".equals(baseUri.getPath()));
        if ((!PORTAL.equals(baseUri) && !localTest) || baseUri.getUserInfo() != null
                || baseUri.getRawQuery() != null || baseUri.getFragment() != null)
            throw new IllegalArgumentException("Unsupported portal address");
        validateTimeout(connectTimeout);
        validateTimeout(responseTimeout);
        if (maxBytes < 1 || maxBytes > 16 * 1024 * 1024) throw new IllegalArgumentException("Invalid response limit");
        this.baseUri = baseUri;
        this.responseTimeout = responseTimeout;
        this.maxBytes = maxBytes;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private static void validateTimeout(Duration timeout) {
        if (timeout == null || timeout.toMillis() < 1 || timeout.compareTo(Duration.ofMinutes(2)) > 0)
            throw new IllegalArgumentException("Invalid HTTP timeout");
    }

    byte[] readSchedule(PhenikaaSession session, String encodedRequest) {
        return read(session, encodedRequest, SCHEDULE_PATH);
    }

    byte[] readProfile(PhenikaaSession session, String encodedRequest) {
        return read(session, encodedRequest, PROFILE_PATH);
    }

    byte[] readExamPeriods(PhenikaaSession session, String encodedRequest) {
        return read(session, encodedRequest, EXAM_PERIODS_PATH);
    }

    byte[] readExams(PhenikaaSession session, String encodedRequest) {
        return read(session, encodedRequest, EXAMS_PATH);
    }

    private byte[] read(PhenikaaSession session, String encodedRequest, String path) {
        if (encodedRequest == null || encodedRequest.length() > maxBytes) throw new PhenikaaClientException(RESPONSE_TOO_LARGE);
        var request = HttpRequest.newBuilder(baseUri.resolve(path)).timeout(responseTimeout)
                .header("Accept", "application/json").header("Accept-Encoding", "identity")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Origin", PORTAL.toString()).header("Referer", PORTAL + "/conggiangvien/index.aspx")
                .POST(HttpRequest.BodyPublishers.ofString("A=" + URLEncoder.encode(encodedRequest, StandardCharsets.UTF_8)));
        if (!session.authorization().isEmpty()) request.header("Authorization", session.authorization());
        if (!session.cookie().isEmpty()) request.header("Cookie", session.cookie());
        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request.build(), info -> new LimitedBody(maxBytes));
        try {
            var response = future.get(responseTimeout.toMillis(), TimeUnit.MILLISECONDS);
            int status = response.statusCode();
            if (status == 401 || (status >= 300 && status < 400 && isLoginRedirect(response)))
                throw new PhenikaaClientException(SESSION_EXPIRED);
            if (status < 200 || status >= 300) throw new PhenikaaClientException(HTTP_ERROR);
            String contentType = response.headers().firstValue("Content-Type").orElse("")
                    .split(";", 2)[0].trim();
            if (!contentType.equalsIgnoreCase("application/json")) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
            if (!response.headers().firstValue("Content-Encoding").orElse("identity").equalsIgnoreCase("identity"))
                throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
            return response.body();
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new PhenikaaClientException(TIMEOUT);
        } catch (InterruptedException ex) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new PhenikaaClientException(NETWORK_ERROR);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            while (cause != null) {
                if (cause instanceof PhenikaaClientException failure) throw new PhenikaaClientException(failure.code());
                if (cause instanceof HttpTimeoutException) throw new PhenikaaClientException(TIMEOUT);
                cause = cause.getCause();
            }
            throw new PhenikaaClientException(NETWORK_ERROR);
        }
    }

    private boolean isLoginRedirect(HttpResponse<?> response) {
        try {
            URI target = baseUri.resolve(response.headers().firstValue("Location").orElse(""));
            return baseUri.getScheme().equals(target.getScheme()) && baseUri.getHost().equals(target.getHost())
                    && baseUri.getPort() == target.getPort() && target.getUserInfo() == null
                    && "/conggiangvien/login.aspx".equals(target.getPath());
        } catch (IllegalArgumentException ex) { return false; }
    }

    @Override public void close() { client.shutdownNow(); }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedBody(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new PhenikaaClientException(RESPONSE_TOO_LARGE));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
                java.util.Arrays.fill(chunk, (byte) 0);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
