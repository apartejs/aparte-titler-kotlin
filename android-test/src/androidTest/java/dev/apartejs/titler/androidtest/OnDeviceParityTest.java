package dev.apartejs.titler.androidtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import dev.apartejs.titler.Titler;
import dev.apartejs.titler.latin.TitlerLatin;

/**
 * The parity test, on Android: the model loads from the library's jar as an app
 * would load it, and the 1,496 reference messages must give exactly the titles of
 * the JavaScript and Python runtimes. Written in Java on purpose: it also checks
 * that the API reads well from Java.
 */
@RunWith(AndroidJUnit4.class)
public class OnDeviceParityTest {
    @Test
    public void givesTheReferenceTitlesOnAndroid() throws Exception {
        Titler titler = TitlerLatin.load();
        assertEquals("latin", titler.getHeader().getModel());

        List<String> bad = new ArrayList<>();
        int messages = 0;
        int titles = 0;
        long t0 = System.nanoTime();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("latin-int3.jsonl"),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                JSONObject o = new JSONObject(line);
                String text = o.getString("text");
                String hybrid = titler.title(text);
                titles++;
                if (!hybrid.equals(o.getString("title"))) {
                    bad.add("hybrid expected «" + o.getString("title") + "» got «" + hybrid + "»");
                }
                if (o.has("title_budget6") && !o.isNull("title_budget6")) {
                    String budget6 = titler.title(text, 6);
                    titles++;
                    if (!budget6.equals(o.getString("title_budget6"))) {
                        bad.add("budget6 expected «" + o.getString("title_budget6") + "» got «" + budget6 + "»");
                    }
                }
                messages++;
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        Log.i("AparteTitler", messages + " messages, " + titles + " titles, "
                + String.format("%.1f", ms / titles) + " ms per title, " + bad.size() + " differ");

        assertEquals(1496, messages);
        assertTrue(bad.size() + " titles differ:\n" + bad.subList(0, Math.min(10, bad.size())), bad.isEmpty());
    }
}
