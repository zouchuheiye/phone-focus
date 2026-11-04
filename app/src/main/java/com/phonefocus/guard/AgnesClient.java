package com.phonefocus.guard;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONException;

/** A single bounded HTTPS call. No redirects, retries, image hosting or response logging. */
final class AgnesClient {
    private final OkHttpClient client;
    private volatile Call active;
    AgnesClient() { this(new OkHttpClient.Builder().callTimeout(30,TimeUnit.SECONDS).connectTimeout(8,TimeUnit.SECONDS)
        .readTimeout(25,TimeUnit.SECONDS).writeTimeout(10,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()); }
    AgnesClient(OkHttpClient client) { this.client=client; }
    AgnesProtocol.Verdict classify(String key,String model,String pkg,String encoded,BooleanSupplier allowed) throws IOException,JSONException {
        return perform(AgnesProtocol.ENDPOINT,key,model,pkg,encoded,allowed);
    }
    // Package-private seam permits a local mock server in JVM tests; production endpoint is fixed above.
    AgnesProtocol.Verdict perform(String endpoint,String key,String model,String pkg,String encoded) throws IOException,JSONException {
        return perform(endpoint,key,model,pkg,encoded,() -> true);
    }
    private AgnesProtocol.Verdict perform(String endpoint,String key,String model,String pkg,String encoded,BooleanSupplier allowed) throws IOException,JSONException {
        if (!allowed.getAsBoolean()) throw new IOException("Check cancelled");
        RequestBody body=RequestBody.create(AgnesProtocol.request(model,pkg,encoded).toString(),MediaType.get("application/json; charset=utf-8"));
        Request request=new Request.Builder().url(endpoint).header("Authorization","Bearer "+key).post(body).build();
        Call call=client.newCall(request); active=call;
        if (!allowed.getAsBoolean()) call.cancel();
        try (Response response=call.execute()) {
            if (!response.isSuccessful()) throw new IOException("Agnes HTTP "+response.code());
            ResponseBody payload=response.body();
            if (payload==null || payload.contentLength()>65536) throw new IOException("Invalid Agnes response");
            payload.source().request(65537);
            if (payload.source().buffer().size()>65536) throw new IOException("Agnes response too large");
            return AgnesProtocol.parse(payload.string());
        } finally { if (active==call) active=null; }
    }
    void cancel() { Call call=active; if (call!=null) call.cancel(); }
    void close() { cancel(); client.dispatcher().executorService().shutdown(); client.connectionPool().evictAll(); }
}
