package com.artman.navisplit;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Scenario persistence + widget-instance -> scenario mapping (SharedPreferences/JSON). */
public class Store {
    static final String PREFS = "scenarios";
    static final String KEY = "list";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, 0);
    }

    static List<Scenario> load(Context c) {
        List<Scenario> out = new ArrayList<>();
        String j = prefs(c).getString(KEY, null);
        if (j == null) {
            // seed a sensible default on first run
            out.add(new Scenario(System.currentTimeMillis(), "Навигатор + Музыка",
                    "ru.yandex.yandexnavi", "ua.itaysonlab.vkx", 65));
            save(c, out);
            return out;
        }
        try {
            JSONArray arr = new JSONArray(j);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Scenario(o.getLong("id"), o.getString("name"),
                        o.getString("left"), o.getString("right"), o.getInt("lp")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    static void save(Context c, List<Scenario> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Scenario s : list) {
                JSONObject o = new JSONObject();
                o.put("id", s.id); o.put("name", s.name); o.put("left", s.left);
                o.put("right", s.right); o.put("lp", s.leftPercent);
                arr.put(o);
            }
        } catch (Exception ignored) { }
        prefs(c).edit().putString(KEY, arr.toString()).apply();
    }

    static Scenario byId(Context c, long id) {
        for (Scenario s : load(c)) if (s.id == id) return s;
        return null;
    }

    // widget instance mapping
    static void mapWidget(Context c, int widgetId, long scenarioId) {
        prefs(c).edit().putLong("w_" + widgetId, scenarioId).apply();
    }

    static long widgetScenario(Context c, int widgetId) {
        return prefs(c).getLong("w_" + widgetId, -1);
    }

    static void unmapWidget(Context c, int widgetId) {
        prefs(c).edit().remove("w_" + widgetId).apply();
    }
}
