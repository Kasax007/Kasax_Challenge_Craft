package net.kasax.challengecraft.autopilot;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * The Bob-Trainer's dashboard: a small web page on 127.0.0.1 (nobody else can reach it) that shows
 * what the trainer does and takes the buttons - pause, pause now, go on, end, export, power - and
 * the Minecraft EULA, which the person accepts himself before the first game.
 *
 * <p>GET {@code /} the page, {@code /api/status} the state as JSON (the supervisor's snapshot, made
 * by its loop each second, and the app's own fields). POST {@code /api/<action>} with the header
 * {@code X-Bob: 1} (a page from elsewhere cannot send it without the browser asking first).
 */
final class Dashboard {
    private final HttpServer http;
    private final Path root, home;
    final String url;
    /** The supervisor once the games may start (after the EULA), else null. */
    volatile Supervisor sup;
    /** Asked to end before the supervisor ran (the EULA not accepted yet). */
    volatile boolean quit;
    /** What the app is doing before the supervisor runs (shown on the page). */
    volatile String phase = "startet";

    private Dashboard(HttpServer http, Path root, Path home, int port) {
        this.http = http;
        this.root = root;
        this.home = home;
        this.url = "http://127.0.0.1:" + port + "/";
    }

    /** On the given port, or the next free one of the following nineteen. */
    static Dashboard start(Path root, Path home, int port) throws IOException {
        IOException last = null;
        for (int p = port; p < port + 20; p++) {
            try {
                HttpServer s = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), p), 16);
                Dashboard d = new Dashboard(s, root, home, p);
                s.createContext("/", d::handle);
                s.setExecutor(Executors.newFixedThreadPool(2, r -> {
                    Thread t = new Thread(r, "Bob-Trainer dashboard");
                    t.setDaemon(true);
                    return t;
                }));
                s.start();
                Files.writeString(home.resolve("dashboard.url"), d.url, StandardCharsets.UTF_8);
                return d;
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    void stop() {
        http.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (ex.getRequestMethod().equals("GET") && (path.equals("/") || path.equals("/index.html"))) {
                send(ex, 200, "text/html; charset=utf-8", page());
            } else if (ex.getRequestMethod().equals("GET") && path.equals("/api/status")) {
                send(ex, 200, "application/json; charset=utf-8", status());
            } else if (ex.getRequestMethod().equals("POST") && path.startsWith("/api/")) {
                if (!"1".equals(ex.getRequestHeaders().getFirst("X-Bob"))) {
                    send(ex, 403, "text/plain; charset=utf-8", "missing X-Bob header");
                    return;
                }
                try (InputStream in = ex.getRequestBody()) {
                    in.readAllBytes();
                }
                send(ex, 200, "application/json; charset=utf-8", Json.write(action(path.substring(5), ex.getRequestURI().getQuery())));
            } else {
                send(ex, 404, "text/plain; charset=utf-8", "not found");
            }
        } catch (RuntimeException e) {
            send(ex, 500, "text/plain; charset=utf-8", e.toString());
        }
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(b);
        }
    }

    /** A button: what it did, in a word. */
    private Map<String, Object> action(String what, String query) {
        Map<String, Object> o = new LinkedHashMap<>();
        Supervisor s = sup;
        switch (what) {
            case "eula" -> {
                try {
                    Files.writeString(root.resolve("eula.txt"), "# Accepted by the user in the Bob-Trainer dashboard, " + LocalDateTime.now()
                            + " (https://aka.ms/MinecraftEULA)\neula=true\n", StandardCharsets.UTF_8);
                    o.put("ok", true);
                } catch (IOException e) {
                    o.put("ok", false);
                    o.put("error", e.getMessage());
                }
                return o;
            }
            case "stop" -> {
                quit = true;
                if (s != null) s.commands.add("stop");
            }
            case "pause" -> cmd(s, "pause");
            case "pausenow" -> cmd(s, "pausenow");
            case "resume" -> cmd(s, "resume");
            case "export" -> cmd(s, "export");
            case "power" -> {
                String level = query == null ? "" : query.replace("level=", "");
                if (!java.util.List.of("niedrig", "mittel", "hoch").contains(level)) {
                    o.put("ok", false);
                    return o;
                }
                if (s != null) cmd(s, "power " + level);
                else {
                    // (Before the games start - the EULA page: kept for the start.)
                    try {
                        Files.writeString(home.resolve("power"), level, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        o.put("ok", false);
                        return o;
                    }
                }
            }
            default -> {
                o.put("ok", false);
                o.put("error", "unknown action " + what);
                return o;
            }
        }
        o.put("ok", true);
        return o;
    }

    private static void cmd(Supervisor s, String c) {
        if (s != null) s.commands.add(c);
    }

    /** The supervisor's snapshot with the app's fields (EULA, phase, the folders). */
    private String status() {
        Supervisor s = sup;
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("eula", Autopilot.eulaAccepted(root));
        app.put("phase", s == null ? phase : s.stopRequested ? "beendet sich" : s.paused ? "pausiert" : "laeuft");
        app.put("export_dir", home.resolve("export").toAbsolutePath().toString());
        // (No supervisor - the start failed, or the EULA is pending: nothing is being downloaded.)
        app.put("minecraft_ready", s == null || s.minecraftReady());
        if (s != null && s.relocated) {
            app.put("work_dir", s.work.getParent().toAbsolutePath().toString());
            app.put("work_synced", Disk.cloudSynced(s.work));
        }
        String snap = s == null ? "{}" : s.snapshot;
        // (The snapshot is a JSON object: the app's fields go in front of it.)
        String head = Json.write(app);
        return "{\"app\":" + head + (snap.length() > 2 ? "," + snap.substring(1) : "}");
    }

    private static String page() {
        return PAGE;
    }

    private static final String PAGE = """
            <!doctype html>
            <html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Bob-Trainer</title>
            <style>
            :root{--bg:#f6f7f9;--card:#fff;--ink:#1d2330;--mute:#6b7280;--line:#e3e6ea;--acc:#2f6fdb;--good:#1e8a4c;--bad:#c0392b;--warn:#b7791f}
            @media (prefers-color-scheme: dark){:root{--bg:#14171c;--card:#1d2128;--ink:#e6e8eb;--mute:#9aa3ad;--line:#2c323b;--acc:#6aa0ff;--good:#4cc282;--bad:#ff7a6b;--warn:#e0b050}}
            body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.45 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
            header{padding:14px 18px;border-bottom:1px solid var(--line);display:flex;gap:12px;align-items:center;flex-wrap:wrap}
            h1{font-size:20px;margin:0 12px 0 0} h2{font-size:15px;margin:0 0 8px}
            main{padding:16px;display:grid;gap:14px;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));max-width:1300px}
            .card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 14px}
            .state{font-weight:600;padding:3px 10px;border-radius:99px;background:var(--line)}
            button{font:inherit;padding:6px 12px;border-radius:7px;border:1px solid var(--line);background:var(--card);color:var(--ink);cursor:pointer}
            button.main{background:var(--acc);border-color:var(--acc);color:#fff} button.on{outline:2px solid var(--acc)}
            table{border-collapse:collapse;width:100%;font-size:13.5px} td,th{padding:3px 6px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}
            .mute{color:var(--mute)} .good{color:var(--good)} .bad{color:var(--bad)} .num{text-align:right;font-variant-numeric:tabular-nums}
            .bar{height:8px;background:var(--line);border-radius:4px;overflow:hidden}.bar>div{height:100%;background:var(--acc)}
            #eula{display:none;border-color:var(--warn)} code{font-size:12.5px;word-break:break-all}
            </style></head><body>
            <header><h1>Bob-Trainer</h1><span id="state" class="state">...</span>
            <button onclick="act('pause')">Pause</button><button onclick="act('pausenow')">Sofort pausieren</button>
            <button class="main" onclick="act('resume')">Weiter</button><button onclick="if(confirm('Bob-Trainer beenden?'))act('stop')">Beenden</button>
            <button onclick="act('export')">Export jetzt</button>
            <span class="mute">Leistung:</span><button id="p-niedrig" onclick="power('niedrig')">niedrig</button><button id="p-mittel" onclick="power('mittel')">mittel</button><button id="p-hoch" onclick="power('hoch')">hoch</button>
            </header>
            <main>
            <div class="card" id="eula"><h2>Erster Start: die Minecraft-EULA</h2>
            <p>Bob spielt auf kleinen Minecraft-Servern auf diesem Rechner. Dafuer musst du die Lizenz von Minecraft (EULA) selbst lesen und akzeptieren:
            <a href="https://aka.ms/MinecraftEULA" target="_blank" rel="noopener">https://aka.ms/MinecraftEULA</a></p>
            <label><input type="checkbox" id="eulabox"> Ich habe die Minecraft-EULA gelesen und akzeptiere sie.</label><br><br>
            <button class="main" onclick="eula()">Akzeptieren und starten</button></div>
            <div class="card"><h2>Stand</h2><div id="overview" class="mute">laedt...</div></div>
            <div class="card"><h2>Laufende Spiele</h2><div id="servers"></div></div>
            <div class="card"><h2>Das Duell</h2><div id="duel"></div></div>
            <div class="card"><h2>Knoepfe: geschaetzte Wirkung (Felder je Spiel)</h2><div id="arms" style="max-height:340px;overflow:auto"></div></div>
            <div class="card"><h2>Letzte Spiele</h2><div id="games"></div></div>
            <div class="card"><h2>Ereignisse</h2><div id="events" class="mute" style="font-size:13px"></div></div>
            </main>
            <script>
            const $=id=>document.getElementById(id);
            const esc=s=>String(s??'').replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
            async function act(a,q){try{const r=await fetch('/api/'+a+(q?'?'+q:''),{method:'POST',headers:{'X-Bob':'1'}});await r.json();}catch(e){} setTimeout(load,300);}
            function power(l){act('power','level='+l);}
            function eula(){if(!$('eulabox').checked){alert('Bitte zuerst das Haekchen setzen.');return;} act('eula');}
            function pct(p){return Math.round(p*100)+' %';}
            async function load(){
              let s; try{s=await (await fetch('/api/status')).json();}catch(e){$('state').textContent='nicht erreichbar (beendet?)';return;}
              const a=s.app||{}, L=s.league||{}, d=L.duel||{};
              $('eula').style.display=a.eula?'none':'block';
              let st=a.eula?a.phase:'wartet auf die EULA'; if(s.stopping) st='beendet sich';
              $('state').textContent=st;
              ['niedrig','mittel','hoch'].forEach(l=>$('p-'+l).classList.toggle('on',s.power===l));
              const gm=s.game_minutes?`${s.game_minutes} Spielminuten, jedes ${s.long_game_every}. Spiel ${s.long_game_minutes}`:'';
              $('overview').innerHTML=`<table>
               <tr><td>Spiele fertig</td><td class="num">${L.games??0}</td><td class="mute">abgebrochen ${L.failed??0}</td></tr>
               <tr><td>Gleichzeitig</td><td class="num">${s.active_games??'-'}</td><td class="mute">${esc(gm)}</td></tr>
               <tr><td>Champion</td><td colspan="2"><code>${esc(L.champion||'-')}</code></td></tr>
               <tr><td>Platz</td><td class="num">${s.disk_mb!=null?s.disk_mb+' MB':'-'}</td><td class="mute">${s.host?s.host.cores+' Kerne, '+Math.round(s.host.ram_mb/1024)+' GB':''}</td></tr>
               <tr><td>Letzter Export</td><td colspan="2">${s.exporting?'wird gepackt...':s.last_export?`<code>${esc(s.last_export)}</code> (${esc(s.last_export_at)})<br><span class="mute">Diese Datei im Chat hochladen.</span>`:'<span class="mute">noch keiner</span>'}</td></tr>
               <tr><td>Exportordner</td><td colspan="2"><code>${esc(a.export_dir)}</code></td></tr>
               ${a.work_dir?`<tr><td>Spielwelten</td><td colspan="2"><code>${esc(a.work_dir)}</code><br><span class="mute">${a.work_synced?'liegt selbst in einem Sync-Ordner: bitte work_dir aendern':'ausserhalb von OneDrive: dort sperrt nichts die Dateien der Spiele'}</span></td></tr>`:''}
               ${a.eula&&!a.minecraft_ready?'<tr><td colspan="3" class="mute">Erster Start: Minecraft wird heruntergeladen (einmalig, einige Minuten).</td></tr>':''}
              </table>`;
              const sv=(s.servers||[]).filter(x=>x.state!=='idle');
              $('servers').innerHTML=sv.length?`<table><tr><th>Spiel</th><th>Welt</th><th class="num">Minute</th><th class="num">Tempo</th><th>Felder</th></tr>`+sv.map(x=>{
                const m=x.game_secs!=null?Math.floor(x.game_secs/60)+' / '+Math.round(x.of_secs/60):x.state;
                const t=x.tiles?Object.entries(x.tiles).map(([k,v])=>k+' '+v).join(', '):'';
                return `<tr><td>${esc(x.game??x.name)}</td><td><code>${esc(x.seed??'')}</code></td><td class="num">${esc(m)}</td><td class="num">${x.speed!=null?x.speed+'x':''}</td><td>${esc(t)}</td></tr>`;}).join('')+'</table>'
                :'<span class="mute">'+(s.paused?'pausiert':'keins')+'</span>';
              const p=d.p_better??0.5;
              $('duel').innerHTML=L.challenger?`Herausforderer: <code>${esc(L.challenger)}</code><br>
                Welten gespielt: <b>${d.games??0}</b> &nbsp; Felder Vorsprung je Welt: <b>${d.mean_diff??0}</b> (Siege ${d.wins??0}, Niederlagen ${d.losses??0})<br>
                Wahrscheinlichkeit, dass er besser ist: <b>${pct(p)}</b><div class="bar"><div style="width:${pct(p)}"></div></div>
                <span class="mute">Er wird Champion ab 95 % (fruehestens nach 8 Welten), faellt unter 5 % oder nach 24 Welten raus.</span>`:'<span class="mute">noch keins</span>';
              const arms=(s.arms||[]).slice().sort((x,y)=>(y.duels>0)-(x.duels>0)||y.edge-x.edge);
              $('arms').innerHTML=`<table><tr><th>Knopf</th><th class="num">Duelle</th><th class="num">Wirkung</th><th class="num">+/-</th></tr>`+arms.map(x=>
                `<tr><td>${esc(x.knob)}=${esc(x.value)}</td><td class="num">${x.duels}</td><td class="num ${x.duels?(x.edge>0?'good':'bad'):'mute'}">${x.duels?x.edge:'ungetestet'}</td><td class="num mute">${x.sd}</td></tr>`).join('')+'</table>';
              $('games').innerHTML=(s.last_games||[]).map(g=>`<div>${esc(g.text)} <span class="mute">(Welt ${esc(g.seed)})</span></div>`).join('')||'<span class="mute">noch keine</span>';
              $('events').innerHTML=(s.events||[]).slice(0,14).map(e=>`<div>${esc(e)}</div>`).join('');
            }
            load(); setInterval(load,2000);
            </script></body></html>
            """;
}
