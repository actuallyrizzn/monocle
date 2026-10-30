package com.actuallyrizzn.monocle

import android.content.Context
import android.util.Base64
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject

class MonocleHttpServer(
    private val ble: MonocleBleClient,
    private val appContext: Context,
    private val listen: ListenLoop,
) : NanoHTTPD("127.0.0.1", MonocleCommands.HTTP_PORT) {

    private val phoneStt = PhoneStt(appContext)
    private val hud = HudSession(ble)
    private val idle = IdleBlank { blankWaveguide() }
    private val cardLock = Any()
    private var lastSubject = LensText.DEFAULT_SUBJECT
    private var lastFrom = LensText.DEFAULT_FROM
    private var lastBody = " "
    var presenceLoop: PresenceLoop? = null
    var presenceClient: PresenceClient? = null
    var presencePrefs: PresencePrefs? = null

    init {
        listen.recorder = { seconds -> recordMic(seconds) }
    }

    override fun serve(session: IHTTPSession): Response {
        val path = session.uri.trimStart('/').substringBefore('?')
        return try {
            when {
                path == "health" || path.isEmpty() -> health()
                path == "v1/status" -> health()
                path == "v1/connect" && session.method == Method.POST -> connect()
                path == "v1/display" && session.method == Method.POST -> display(session)
                path == "v1/display/clear" && session.method == Method.POST -> clearCard()
                path == "v1/display/brightness" && session.method == Method.POST -> brightness(session)
                path == "v1/repl" && session.method == Method.POST -> repl(session)
                path == "v1/sensors" -> sensors()
                path == "v1/led" && session.method == Method.POST -> led(session)
                path == "v1/touch" -> touch()
                path == "v1/mic" && session.method == Method.POST -> mic(session)
                path == "v1/camera" && session.method == Method.POST -> camera()
                path == "v1/raw" -> drainRaw()
                path == "v1/stt/phone" && session.method == Method.POST -> sttPhone()
                path == "v1/listen/start" && session.method == Method.POST -> listenStart(session)
                path == "v1/listen/stop" && session.method == Method.POST -> listenStop()
                path == "v1/listen" -> listenStatus()
                path == "v1/listen/utterance" -> utteranceJson()
                path == "v1/listen/utterance.wav" -> utteranceWav()
                path == "v1/demo" && session.method == Method.POST -> demo()
                path == "v1/operator" && session.method == Method.POST -> operatorInstall()
                path == "v1/operator" -> operatorStatus()
                path == "v1/presence" -> presenceStatus()
                path == "v1/presence/config" && session.method == Method.POST -> presenceConfig(session)
                path == "v1/firmware/zip" &&
                    (session.method == Method.POST || session.method == Method.PUT) ->
                    firmwareStage(session)
                path == "v1/firmware" && session.method == Method.POST -> firmwareFlash()
                path == "v1/firmware" -> firmwareStatus()
                else -> json(Response.Status.NOT_FOUND, """{"error":"not_found"}""")
            }
        } catch (e: Exception) {
            json(Response.Status.INTERNAL_ERROR, JSONObject().put("ok", false).put("error", e.message ?: "fail").toString())
        }
    }

    private fun health(): Response {
        val routes = JSONArray()
        for (r in MonocleApi.ROUTES) routes.put(r)
        val u = listen.lastUtterance()
        val body = JSONObject()
            .put("ok", true)
            .put("service", "monocle-bridge")
            .put("version", BuildConfig.VERSION_NAME)
            .put("ble", ble.isConnected())
            .put("raw_data", ble.rawSubscribed)
            .put("status", ble.displayStatus())
            .put("device", ble.deviceName ?: JSONObject.NULL)
            .put("listen", listen.isRunning())
            .put("last_speaking", u?.speaking ?: false)
            .put("presence_on", presenceLoop?.lastOn ?: false)
            .put("presence_ok", presenceLoop?.lastHeartbeatOk ?: false)
            .put("presence_error", presenceLoop?.lastError ?: JSONObject.NULL)
            .put("presence_key", presenceClient?.keySet() == true)
            .put("hud", hud.armed && ble.isConnected())
            .put("operator", hud.armed && ble.isConnected())
            .put("routes", routes)
            .toString()
        return json(Response.Status.OK, body)
    }

    private fun connect(): Response {
        val ok = ble.connectBlocking(20_000)
        if (ok) onLensUp()
        val body = JSONObject()
            .put("ok", ok)
            .put("status", ble.displayStatus())
            .put("raw_data", ble.rawSubscribed)
            .put("hud", hud.armed && ble.isConnected())
            .put("operator", hud.armed && ble.isConnected())
            .put("device", ble.deviceName ?: JSONObject.NULL)
            .put("error", ble.lastError() ?: JSONObject.NULL)
            .toString()
        return json(if (ok) Response.Status.OK else Response.Status.BAD_REQUEST, body)
    }

    fun ensureHud(): Boolean = onLensUp()

    /** Connected chrome on the waveguide. Does not install op.py. */
    fun onLensUp(): Boolean {
        if (!ble.isConnected()) return false
        hud.ensure()
        val app = BuildConfig.VERSION_NAME
        synchronized(cardLock) {
            lastSubject = LensText.PRODUCT_SUBJECT
            lastFrom = LensText.PRODUCT_FROM
            lastBody = app
        }
        if (hud.show(LensText.PRODUCT_SUBJECT, LensText.PRODUCT_FROM, app)) {
            idle.bump()
            return true
        }
        val out = ble.exec(MonocleCommands.bootSplash(app), timeoutMs = 12_000)
        hud.rearmAfterExec()
        idle.bump()
        return !out.startsWith("ERROR:")
    }

    private fun blankWaveguide() {
        if (!ble.isConnected()) return
        if (hud.clear()) return
        ble.exec(MonocleCommands.blankDisplay(), timeoutMs = 8_000)
    }

    private fun display(session: IHTTPSession): Response {
        val json = readJson(session)
        val text = json.optString("text").trim()
        val subject = json.optString("subject").ifBlank { json.optString("title") }.trim()
        val from = json.optString("from").ifBlank { json.optString("sender") }.trim()
        val out = paint(text = text.ifBlank { null }, subject = subject.ifBlank { null }, sender = from.ifBlank { null })
        val body = JSONObject()
            .put("ok", !out.startsWith("ERROR:"))
            .put("text", synchronized(cardLock) { lastBody })
            .put("subject", synchronized(cardLock) { lastSubject })
            .put("from", synchronized(cardLock) { lastFrom })
            .put("repl", out)
            .put("status", ble.status)
            .toString()
        return json(Response.Status.OK, body)
    }

    fun paint(text: String? = null, subject: String? = null, sender: String? = null): String {
        val subj: String
        val who: String
        val body: String
        synchronized(cardLock) {
            if (!subject.isNullOrBlank()) lastSubject = subject.trim()
            if (!sender.isNullOrBlank()) lastFrom = sender.trim()
            if (!text.isNullOrBlank()) lastBody = text.trim()
            subj = lastSubject
            who = lastFrom
            body = lastBody
        }
        if (hud.show(subj, who, body)) {
            idle.bump()
            return "HUD"
        }
        val cmd = MonocleCommands.showText(body, subj, who)
        val out = ble.exec(cmd, timeoutMs = LensText.execTimeoutMs(LensText.wrap(body).size))
        hud.rearmAfterExec()
        idle.bump()
        return out
    }

    private fun operatorStatus(): Response {
        val ver = ble.exec(HudScript.CHECK, timeoutMs = 8_000)
        val present = ver.contains(HudScript.VERSION)
        val body = JSONObject()
            .put("ok", !ver.startsWith("ERROR:"))
            .put("present", present)
            .put("armed", hud.armed && ble.isConnected())
            .put("operator", hud.armed && ble.isConnected())
            .put("version", HudScript.VERSION)
            .put("repl", ver)
            .put("status", ble.status)
            .toString()
        return json(Response.Status.OK, body)
    }

    private fun operatorInstall(): Response {
        val out = hud.install()
        val ok = hud.armed && ble.isConnected() && !out.startsWith("ERROR:")
        if (ok) {
            paint(
                text = "Operator is on this lens.",
                subject = "Monocle",
                sender = "Otto",
            )
        }
        val body = JSONObject()
            .put("ok", ok)
            .put("armed", hud.armed && ble.isConnected())
            .put("operator", hud.armed && ble.isConnected())
            .put("bytes", HudScript.sourceBytes().size)
            .put("repl", out)
            .put("status", ble.status)
            .toString()
        return json(if (ok) Response.Status.OK else Response.Status.BAD_REQUEST, body)
    }

    private fun presenceStatus(): Response {
        val loop = presenceLoop
        val client = presenceClient
        val body = JSONObject()
            .put("ok", true)
            .put("running", loop?.running ?: false)
            .put("on", loop?.lastOn ?: false)
            .put("heartbeat_ok", loop?.lastHeartbeatOk ?: false)
            .put("error", loop?.lastError ?: JSONObject.NULL)
            .put("shown", loop?.lastShown ?: 0)
            .put("url", client?.baseUrl ?: JSONObject.NULL)
            .put("key_set", client?.keySet() == true)
            .put("ble", ble.isConnected())
            .toString()
        return json(Response.Status.OK, body)
    }

    private fun firmwareStatus(): Response {
        val present = try {
            appContext.assets.open(FirmwareFlasher.ASSET).use { it.available() > 0 }
        } catch (_: Exception) {
            false
        }
        val staged = FirmwareFlasher.stagedFile(appContext)
        val stagedOk = FirmwareFlasher.isAppZip(staged)
        val body = JSONObject()
            .put("ok", true)
            .put("bundled", FirmwareFlasher.ASSET)
            .put("present", present)
            .put("staged", stagedOk)
            .put("staged_bytes", if (stagedOk) staged.length() else 0)
            .put("source", FirmwareFlasher.lastSource)
            .put("address", ble.lastAddress() ?: JSONObject.NULL)
            .put("incremented", ble.lastAddress()?.let { FirmwareFlasher.incrementedAddress(it) } ?: JSONObject.NULL)
            .put("scan", JSONArray(FirmwareFlasher.lastScan))
            .put("tried", JSONArray(FirmwareFlasher.lastTried))
            .put("ble", ble.isConnected())
            .put("status", ble.displayStatus())
            .toString()
        return json(Response.Status.OK, body)
    }

    private fun firmwareStage(session: IHTTPSession): Response {
        val len = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (len < 1024 || len > FirmwareFlasher.MAX_ZIP_BYTES) {
            return json(
                Response.Status.BAD_REQUEST,
                JSONObject().put("ok", false).put("error", "zip size $len").toString(),
            )
        }
        val bytes = ByteArray(len)
        val input = session.inputStream
        var n = 0
        while (n < len) {
            val r = input.read(bytes, n, len - n)
            if (r < 0) break
            n += r
        }
        if (n != len) {
            return json(
                Response.Status.BAD_REQUEST,
                JSONObject().put("ok", false).put("error", "short zip $n/$len").toString(),
            )
        }
        return try {
            val dest = FirmwareFlasher(appContext, ble).stageZip(bytes)
            json(
                Response.Status.OK,
                JSONObject()
                    .put("ok", true)
                    .put("staged", true)
                    .put("bytes", dest.length())
                    .put("name", FirmwareFlasher.STAGED)
                    .toString(),
            )
        } catch (e: Exception) {
            json(
                Response.Status.BAD_REQUEST,
                JSONObject().put("ok", false).put("error", e.message ?: "stage").toString(),
            )
        }
    }

    private fun firmwareFlash(): Response {
        idle.cancel()
        val result = FirmwareFlasher(appContext, ble).flashZip(null)
        val ok = result == "OK"
        if (ok) {
            ble.connectBlocking(25_000)
            onLensUp()
        }
        val body = JSONObject()
            .put("ok", ok)
            .put("result", result)
            .put("ble", ble.isConnected())
            .put("status", ble.displayStatus())
            .put("device", ble.deviceName ?: JSONObject.NULL)
            .put("scan", JSONArray(FirmwareFlasher.lastScan))
            .put("tried", JSONArray(FirmwareFlasher.lastTried))
            .toString()
        return json(if (ok) Response.Status.OK else Response.Status.INTERNAL_ERROR, body)
    }

    private fun presenceConfig(session: IHTTPSession): Response {
        val json = readJson(session)
        val url = json.optString("url").ifBlank { json.optString("base") }.trim()
        val key = json.optString("key").ifBlank { json.optString("api_key") }.trim()
        val prefs = presencePrefs
        val client = presenceClient
        if (prefs == null || client == null) {
            return json(Response.Status.INTERNAL_ERROR, """{"ok":false,"error":"presence_not_wired"}""")
        }
        if (url.isNotEmpty()) client.baseUrl = url.trimEnd('/')
        if (key.isNotEmpty()) client.apiKey = key
        prefs.save(url.ifBlank { null }, key.ifBlank { null })
        val body = JSONObject()
            .put("ok", true)
            .put("url", client.baseUrl)
            .put("key_set", client.keySet())
            .toString()
        return json(Response.Status.OK, body)
    }

    private fun brightness(session: IHTTPSession): Response {
        val level = readJson(session).optInt("level", 0)
        if (hud.brightness(level)) {
            val body = JSONObject()
                .put("ok", true)
                .put("level", level)
                .put("via", "hud")
                .toString()
            return json(Response.Status.OK, body)
        }
        return runCmd(MonocleCommands.brightness(level))
    }

    private fun clearCard(): Response {
        idle.cancel()
        if (hud.clear()) {
            val body = JSONObject().put("ok", true).put("via", "hud").toString()
            return json(Response.Status.OK, body)
        }
        return runCmd(MonocleCommands.blankDisplay())
    }

    private fun repl(session: IHTTPSession): Response {
        val code = readJson(session).optString("code").trim()
        if (code.isEmpty()) {
            return json(Response.Status.BAD_REQUEST, """{"error":"missing_code"}""")
        }
        val out = ble.exec(code)
        hud.rearmAfterExec()
        val body = JSONObject()
            .put("ok", !out.startsWith("ERROR:"))
            .put("repl", out)
            .put("status", ble.status)
            .toString()
        return json(Response.Status.OK, body)
    }

    private fun sensors(): Response {
        val sns = hud.sensorsText()
        if (sns != null) {
            return json(Response.Status.OK, sensorObject(ReplParse.labeled(sns), sns, "op").toString())
        }
        val out = ble.exec(MonocleCommands.sensors())
        hud.rearmAfterExec()
        return json(
            Response.Status.OK,
            sensorObject(ReplParse.labeled(out), out, "repl").toString(),
        )
    }

    private fun sensorObject(labels: Map<String, String>, repl: String, via: String): JSONObject {
        return JSONObject()
            .put("ok", !repl.startsWith("ERROR:"))
            .put("battery", labels["BAT"]?.toIntOrNull() ?: JSONObject.NULL)
            .put("charging", labels["CHG"])
            .put("version", labels["VER"])
            .put("mac", labels["MAC"])
            .put("touch", labels["TOUCH"])
            .put("fpga", labels["FPGA"])
            .put("reset_cause", labels["RST"])
            .put("via", via)
            .put("repl", repl)
            .put("status", ble.status)
    }

    private fun led(session: IHTTPSession): Response {
        val req = readJson(session)
        val color = req.optString("color", "GREEN")
        val on = req.optBoolean("on", true)
        if (hud.led(color, on)) {
            val body = JSONObject()
                .put("ok", true)
                .put("via", "op")
                .put("color", color)
                .put("on", on)
                .toString()
            return json(Response.Status.OK, body)
        }
        return runCmd(MonocleCommands.led(color, on))
    }

    private fun touch(): Response {
        val t = hud.touchText()
        if (t != null) {
            val body = JSONObject()
                .put("ok", true)
                .put("touch", t)
                .put("via", "op")
                .toString()
            return json(Response.Status.OK, body)
        }
        return runCmd(MonocleCommands.touch(), extra = { body, repl ->
            body.put("touch", ReplParse.labeled(repl)["TOUCH"] ?: JSONObject.NULL)
        })
    }

    private fun recordMic(seconds: Double): Utterance {
        val collected = hud.mic(seconds)
        if (collected != null) {
            val u = MicCapture.fromPackets(collected.packets, collected.raw, 8000, 8, "OP")
            if (u.pcm.isNotEmpty()) return u
        }
        val u = MicCapture.record(ble, seconds, 8000, 8)
        hud.rearmAfterExec()
        return u
    }

    private fun mic(session: IHTTPSession): Response {
        val seconds = readJson(session).optDouble("seconds", 2.0)
        return json(Response.Status.OK, utteranceObject(recordMic(seconds)).toString())
    }

    private fun drainRaw(): Response {
        val raw = ble.takeRaw()
        val jpeg = NoaPhoto.assembleBlob(raw)
        val soi = jpeg.size >= 2 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte()
        val body = JSONObject()
            .put("ok", raw.isNotEmpty())
            .put("bytes", jpeg.size)
            .put("jpeg", soi)
            .put("jpeg_b64", if (jpeg.isNotEmpty()) Base64.encodeToString(jpeg, Base64.NO_WRAP) else JSONObject.NULL)
            .toString()
        return json(Response.Status.OK, body)
    }

    private fun camera(): Response {
        val op = hud.camera()
        var packets = op?.packets ?: emptyList()
        var raw = op?.raw ?: ByteArray(0)
        var repl = op?.repl ?: ""
        var via = "op"
        var jpeg = NoaPhoto.assemble(packets)
        if (jpeg.size < 8) {
            val collected = ble.execCollectRaw(
                MonocleCommands.cameraCapture(),
                timeoutMs = 25_000,
                rawQuietMs = 800,
                rawDeadlineMs = 12_000,
            )
            packets = collected.packets
            raw = collected.raw
            repl = collected.repl
            via = "repl"
            jpeg = NoaPhoto.assemble(packets)
            hud.rearmAfterExec()
        }
        idle.bump()
        if (jpeg.size < 8 && raw.size >= 8) {
            jpeg = NoaPhoto.assembleBlob(raw)
        }
        if (jpeg.isEmpty()) {
            jpeg = ReplParse.hexPayload(repl) ?: ByteArray(0)
        }
        val soi = jpeg.size >= 2 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte()
        val ok = soi && jpeg.size > 8_000 && !repl.contains("Traceback")
        val body = JSONObject()
            .put("ok", ok)
            .put("bytes", jpeg.size)
            .put("via", via)
            .put("jpeg_b64", if (jpeg.isNotEmpty()) Base64.encodeToString(jpeg, Base64.NO_WRAP) else JSONObject.NULL)
            .put("repl", repl)
            .toString()
        return json(Response.Status.OK, body)
    }

    private fun sttPhone(): Response {
        val text = phoneStt.listenBlocking()
        val ok = !text.startsWith("ERROR:")
        if (ok) {
            paint(text = text.take(80), subject = "Phone", sender = "You")
        }
        val body = JSONObject()
            .put("ok", ok)
            .put("text", text)
            .put("source", "phone_mic")
            .toString()
        return json(Response.Status.OK, body)
    }

    private fun listenStart(session: IHTTPSession): Response {
        val seconds = readJson(session).optDouble("seconds", 2.0)
        listen.start(seconds)
        paint(text = "Listening", subject = "Listen", sender = "Monocle")
        return listenStatus()
    }

    private fun listenStop(): Response {
        listen.stop()
        paint(text = "Listen off", subject = "Listen", sender = "Monocle")
        return listenStatus()
    }

    private fun listenStatus(): Response {
        val u = listen.lastUtterance()
        val body = JSONObject()
            .put("ok", true)
            .put("running", listen.isRunning())
            .put("window_seconds", listen.windowSeconds)
            .put("error", listen.lastError ?: JSONObject.NULL)
            .put("last_speaking", u?.speaking ?: false)
            .put("last_energy", u?.energy ?: JSONObject.NULL)
            .put("last_bytes", u?.pcm?.size ?: 0)
            .put("last_at_ms", u?.atMs ?: JSONObject.NULL)
            .toString()
        return json(Response.Status.OK, body)
    }

    private fun utteranceJson(): Response {
        val u = listen.lastUtterance()
            ?: return json(Response.Status.NOT_FOUND, """{"error":"no_utterance"}""")
        return json(Response.Status.OK, utteranceObject(u).toString())
    }

    private fun utteranceWav(): Response {
        val u = listen.lastUtterance()
            ?: return json(Response.Status.NOT_FOUND, """{"error":"no_utterance"}""")
        val wav = u.wav()
        return newFixedLengthResponse(
            Response.Status.OK,
            "audio/wav",
            java.io.ByteArrayInputStream(wav),
            wav.size.toLong(),
        )
    }

    private fun demo(): Response {
        val sensorsOut = ble.exec(MonocleCommands.sensors())
        ble.exec(MonocleCommands.led("GREEN", true))
        paint(text = "Sensor hooks are live.", subject = "Demo", sender = "Otto")
        val touchOut = ble.exec(MonocleCommands.touch())
        ble.exec(MonocleCommands.led("GREEN", false))
        hud.rearmAfterExec()
        val labels = ReplParse.labeled(sensorsOut)
        val body = JSONObject()
            .put("ok", true)
            .put("battery", labels["BAT"]?.toIntOrNull() ?: JSONObject.NULL)
            .put("charging", labels["CHG"])
            .put("version", labels["VER"])
            .put("touch", ReplParse.labeled(touchOut)["TOUCH"])
            .put("note", "Speak after POST /v1/listen/start. Replies go on the lens, not a speaker.")
            .put("repl", sensorsOut)
            .toString()
        return json(Response.Status.OK, body)
    }

    private fun utteranceObject(u: Utterance): JSONObject {
        val wav = u.wav()
        return JSONObject()
            .put("ok", true)
            .put("speaking", u.speaking)
            .put("energy", u.energy)
            .put("sample_rate", u.sampleRate)
            .put("bit_depth", u.bitDepth)
            .put("bytes", u.pcm.size)
            .put("at_ms", u.atMs)
            .put("wav_b64", Base64.encodeToString(wav, Base64.NO_WRAP))
            .put("repl", u.repl)
    }

    private fun runCmd(
        code: String,
        extra: (JSONObject, String) -> Unit = { _, _ -> },
    ): Response {
        val out = ble.exec(code)
        hud.rearmAfterExec()
        val body = JSONObject()
            .put("ok", !out.startsWith("ERROR:"))
            .put("repl", out)
            .put("status", ble.status)
        extra(body, out)
        return json(Response.Status.OK, body.toString())
    }

    private fun readJson(session: IHTTPSession): JSONObject {
        val files = HashMap<String, String>()
        return try {
            session.parseBody(files)
            val raw = files["postData"] ?: ""
            if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun json(status: Response.Status, body: String): Response {
        return newFixedLengthResponse(status, "application/json; charset=utf-8", body)
    }
}
