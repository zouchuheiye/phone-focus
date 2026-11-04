package com.phonefocus.guard;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class AgnesClientTest {
    private String envelope(String verdict) throws Exception {
        return new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("finish_reason","stop")
            .put("message",new JSONObject().put("content",verdict)))).toString();
    }
    private String verdict(String category,Object confidence) throws Exception {
        return new JSONObject().put("category",category).put("confidence",confidence).put("reason","synthetic test").toString();
    }
    @Test public void onlyHighConfidenceEntertainmentLocks() throws Exception {
        assertTrue(AgnesProtocol.parse(envelope(verdict("entertainment",.90))).shouldLock());
        assertTrue(AgnesProtocol.parse(envelope(verdict("entertainment",1))).shouldLock());
        assertFalse(AgnesProtocol.parse(envelope(verdict("entertainment",.8999))).shouldLock());
        for (String category:new String[]{"study","work","essential","uncertain"})
            assertFalse(AgnesProtocol.parse(envelope(verdict(category,1))).shouldLock());
    }
    @Test public void invalidSchemaNeverBecomesLockDecision() throws Exception {
        String[] bad={"{}","[]","null","hello",verdict("entertainment","0.99"),verdict("entertainment",true),
            verdict("entertainment",-1),verdict("entertainment",1.01),verdict("lock",1),
            verdict("entertainment",1)+verdict("study",1),"Result: "+verdict("entertainment",1)};
        for (String value:bad) {
            try { AgnesProtocol.parse(envelope(value));fail("Accepted invalid schema"); } catch (JSONException expected) { }
        }
        try { AgnesProtocol.parse("not json");fail(); } catch (JSONException expected) { }
    }
    @Test public void truncatedRefusedAndToolCallingResponsesAreRejected() throws Exception {
        JSONObject value=new JSONObject(envelope(verdict("entertainment",1)));
        value.getJSONArray("choices").getJSONObject(0).put("finish_reason","length");
        try { AgnesProtocol.parse(value.toString());fail(); } catch (JSONException expected) { }
        value.getJSONArray("choices").getJSONObject(0).put("finish_reason","stop");
        value.getJSONArray("choices").getJSONObject(0).getJSONObject("message").put("tool_calls",new JSONArray());
        try { AgnesProtocol.parse(value.toString());fail(); } catch (JSONException expected) { }
        value=new JSONObject(envelope(verdict("entertainment",1)));
        value.getJSONArray("choices").getJSONObject(0).getJSONObject("message").put("refusal","refused");
        try { AgnesProtocol.parse(value.toString());fail(); } catch (JSONException expected) { }
    }
    @Test public void requestsCarryImageAndUntrustedContextWithoutTools() throws Exception {
        JSONObject value=AgnesProtocol.request(AgnesProtocol.DEFAULT_MODEL,"sample.app","AAAA");
        assertFalse(value.getBoolean("stream"));assertFalse(value.has("tools"));
        assertEquals(AgnesProtocol.PROMPT,value.getJSONArray("messages").getJSONObject(0).getString("content"));
        JSONArray content=value.getJSONArray("messages").getJSONObject(1).getJSONArray("content");
        assertEquals("data:image/jpeg;base64,AAAA",content.getJSONObject(1).getJSONObject("image_url").getString("url"));
        assertEquals("sample.app",new JSONObject(content.getJSONObject(0).getString("text")).getString("foreground_package"));
    }
    @Test public void successfulHttpUsesBearerAndReturnsStructuredVerdict() throws Exception {
        try (MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(envelope(verdict("entertainment",.95))));server.start();
            AgnesClient client=new AgnesClient();
            try {
                assertTrue(client.perform(server.url("/v1/chat/completions").toString(),"test-placeholder",AgnesProtocol.DEFAULT_MODEL,"test.app","AAAA").shouldLock());
                RecordedRequest request=server.takeRequest(2,TimeUnit.SECONDS);assertNotNull(request);
                assertEquals("Bearer test-placeholder",request.getHeader("Authorization"));
                assertEquals(AgnesProtocol.DEFAULT_MODEL,new JSONObject(request.getBody().readUtf8()).getString("model"));
            } finally { client.close(); }
        }
    }
    @Test public void errorsAndRedirectsAreNotFollowedOrRetried() throws Exception {
        try (MockWebServer server=new MockWebServer()) {
            server.start();AgnesClient client=new AgnesClient();
            try {
                for (int code:new int[]{401,429,500,302}) {
                    server.enqueue(new MockResponse().setResponseCode(code).setHeader("Location",server.url("/other")).setBody("server error"));
                    try { client.perform(server.url("/vision").toString(),"test-placeholder",AgnesProtocol.DEFAULT_MODEL,"test.app","AAAA");fail(); }
                    catch (IOException expected) { assertEquals("Agnes HTTP "+code,expected.getMessage()); }
                }
                assertEquals(4,server.getRequestCount());
            } finally { client.close(); }
        }
    }
    @Test public void oversizedResponseIsRejected() throws Exception {
        try (MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("x".repeat(65537)));server.start();AgnesClient client=new AgnesClient();
            try { client.perform(server.url("/vision").toString(),"test-placeholder",AgnesProtocol.DEFAULT_MODEL,"test.app","AAAA");fail(); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("large") || expected.getMessage().contains("Invalid")); }
            finally { client.close(); }
        }
    }
    @Test public void callTimeoutAndExplicitCancellationAreBounded() throws Exception {
        try (MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(envelope(verdict("entertainment",1))).setHeadersDelay(2,TimeUnit.SECONDS));
            server.start();AgnesClient client=new AgnesClient(new OkHttpClient.Builder().callTimeout(100,TimeUnit.MILLISECONDS).build());
            try { client.perform(server.url("/vision").toString(),"test-placeholder",AgnesProtocol.DEFAULT_MODEL,"test.app","AAAA");fail(); }
            catch (IOException expected) { }
            finally { client.close(); }
        }
        try (MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody(envelope(verdict("entertainment",1))).setHeadersDelay(2,TimeUnit.SECONDS));server.start();
            AgnesClient client=new AgnesClient();java.util.concurrent.ExecutorService executor=Executors.newSingleThreadExecutor();
            try {
                java.util.concurrent.Future<Boolean> result=executor.submit(() -> {
                    try { client.perform(server.url("/vision").toString(),"test-placeholder",AgnesProtocol.DEFAULT_MODEL,"test.app","AAAA");return false; }
                    catch (IOException expected) { return true; }
                });
                assertNotNull(server.takeRequest(2,TimeUnit.SECONDS));client.cancel();assertTrue(result.get(2,TimeUnit.SECONDS));
            } finally { client.close();executor.shutdownNow(); }
        }
    }
}
