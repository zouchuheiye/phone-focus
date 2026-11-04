package com.phonefocus.guard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/** Explicit opt-in smoke test of the production client using synthetic pictures only. */
public class AgnesLiveProbeTest {
    @Test public void syntheticVisionUsingPrivateLocalConfiguration() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("phonefocus.agnes.live"));
        Path root=Path.of("..").toAbsolutePath().normalize();
        JSONObject config=new JSONObject(new String(Files.readAllBytes(root.resolve(".local/agnes-phone-config.json")),StandardCharsets.UTF_8));
        assertEquals("https://apihub.agnes-ai.com/v1",config.getString("base_url"));
        JSONArray checks=new JSONArray();AgnesClient client=new AgnesClient();
        try {
            for (String name:new String[]{"game","study","blank"}) {
                long started=System.nanoTime();
                String encoded=Base64.getEncoder().encodeToString(Files.readAllBytes(root.resolve("qa/agnes-synthetic-"+name+".jpg")));
                AgnesProtocol.Verdict verdict=client.classify(config.getString("api_key"),config.getString("model"),"synthetic.test",encoded,() -> true);
                JSONObject row=new JSONObject().put("scene",name).put("category",verdict.category).put("confidence",verdict.confidence)
                    .put("wouldBlock",verdict.shouldBlock()).put("milliseconds",(System.nanoTime()-started)/1_000_000);
                checks.put(row);
                JSONObject record=new JSONObject().put("model",config.getString("model")).put("endpoint",AgnesProtocol.ENDPOINT)
                    .put("syntheticOnly",true).put("checks",checks).put("scope","Production protocol/client smoke test only. Not real screenshot accuracy or Android runtime acceptance.");
                Files.write(root.resolve("qa/agnes-live-verification.json"),record.toString(2).getBytes(StandardCharsets.UTF_8));
                if (name.equals("game")) assertTrue("Synthetic game was not confidently classified",verdict.shouldBlock());
                else assertFalse("Non-entertainment synthetic scene must not lock",verdict.shouldBlock());
            }
        } finally { client.close(); }
    }
}
