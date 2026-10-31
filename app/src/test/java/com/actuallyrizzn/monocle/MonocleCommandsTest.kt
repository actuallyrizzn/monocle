package com.actuallyrizzn.monocle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonocleCommandsTest {
    @Test
    fun showTextWrapsDisplayApi() {
        val cmd = MonocleCommands.showText("hello otto", subject = "Sanctum", sender = "Otto")
        assertTrue(cmd.contains("S='Sanctum'"))
        assertTrue(cmd.contains("W='Otto'"))
        assertTrue(cmd.contains("'hello otto'"))
        assertTrue(cmd.contains("def chrome():"))
        assertTrue(cmd.contains("battery_level"))
        assertTrue(cmd.contains("bx=640-len(b)*24"))
        assertTrue(cmd.contains("display.Text(S,0,0,"))
        assertTrue(cmd.contains("display.Text(W,0,50,"))
        assertTrue(cmd.contains("Y=100"))
        assertTrue(cmd.contains("display.brightness(0)"))
        assertFalse(cmd.contains("display.brightness(4)"))
        assertFalse(cmd.contains("display.Fill("))
        assertTrue(cmd.contains("if len(B)<=N:"))
        val longCmd = MonocleCommands.showText("alpha ".repeat(80), subject = "Long", sender = "Athena")
        assertTrue(longCmd.contains("time.sleep"))
        assertTrue(longCmd.contains("while s<w"))
        assertTrue(longCmd.contains("hh(True)"))
        assertTrue(LensText.wrap("alpha ".repeat(80)).size > LensText.BODY_ROWS)
        assertEquals(8767, MonocleCommands.HTTP_PORT)
        val blank = MonocleCommands.blankDisplay()
        assertTrue(blank.contains("h(True)"))
        assertTrue(blank.contains("getattr(device,'hibernate',None)"))
        assertTrue(blank.contains("print('HIB')"))
        assertFalse(blank.contains("fpga.run(False)"))
        assertFalse(blank.contains("force_sleep"))
        assertTrue(cmd.contains("getattr(device,'hibernate',None)"))
        assertTrue(cmd.contains("h(False)"))
        assertFalse(cmd.contains("fpga.run(False)"))
        assertFalse(cmd.contains("force_sleep"))
        val boot = MonocleCommands.bootSplash("0.5.23")
        assertTrue(boot.contains("S='Sanctum'"))
        assertTrue(boot.contains("W='Monocle Bridge'"))
        assertTrue(boot.contains("A='0.5.23'"))
        assertTrue(boot.contains("device.VERSION"))
        assertTrue(boot.contains("battery_level"))
        assertFalse(boot.contains("goes dark"))
        assertFalse(boot.contains("on this lens"))
        assertFalse(boot.contains("force_sleep"))
        assertTrue(LensText.PRODUCT_SUBJECT.length <= LensText.SUBJ_COLS)
        assertTrue(LensText.PRODUCT_FROM.length <= LensText.COLS)
    }

    @Test
    fun sensorsAndLedAndMic() {
        val s = MonocleCommands.sensors()
        assertTrue(s.contains("battery_level"))
        assertTrue(s.contains("touch.state"))
        val led = MonocleCommands.led("RED", true)
        assertTrue(led.contains("led.on(led.RED)"))
        val mic = MonocleCommands.micRecord(2.0, 8000, 8)
        assertTrue(mic.contains("microphone.record(seconds="))
        assertTrue(mic.contains("sample_rate="))
        assertTrue(mic.contains("bit_depth="))
        assertTrue(mic.contains("bluetooth.send"))
        assertFalse(mic.contains("\u0003\u0003"))
        val cam = MonocleCommands.cameraCapture()
        assertTrue(cam.contains("camera.capture()"))
        assertTrue(cam.contains("bluetooth.send"))
        assertTrue(cam.contains("except OSError"))
        assertTrue(cam.contains("b'ist:'"))
        assertTrue(cam.contains("b'idt:'"))
        assertTrue(cam.contains("b'ien:'"))
        assertTrue(cam.contains("camera.read(n)"))
        assertTrue(cam.contains("bluetooth.max_length()-4"))
        assertTrue(cam.contains("getattr(device,'hibernate',None)"))
        assertTrue(cam.contains("h(False)"))
        assertTrue(cam.indexOf("import camera") > cam.indexOf("h(False)"))
        assertTrue(cam.contains("hh(True)"))
        assertTrue(cam.contains("finally:"))
        assertFalse(cam.contains("force_sleep"))
    }

    @Test
    fun routesHaveNoSpeaker() {
        assertFalse(MonocleApi.ROUTES.contains("v1/tts"))
        assertTrue(MonocleApi.ROUTES.contains("v1/listen/start"))
        assertTrue(MonocleApi.ROUTES.contains("v1/mic"))
        assertTrue(MonocleApi.ROUTES.contains("v1/ask"))
        assertTrue(MonocleApi.ROUTES.contains("v1/record-limit"))
        assertTrue(MonocleApi.ROUTES.contains("v1/camera"))
        assertTrue(MonocleApi.ROUTES.contains("v1/raw"))
        assertTrue(MonocleApi.ROUTES.contains("v1/sensors"))
        assertTrue(MonocleApi.ROUTES.contains("v1/led"))
        assertTrue(MonocleApi.ROUTES.contains("v1/presence"))
        assertTrue(MonocleApi.ROUTES.contains("v1/presence/config"))
        assertTrue(MonocleApi.ROUTES.contains("v1/firmware"))
        assertTrue(MonocleApi.ROUTES.contains("v1/firmware/zip"))
        val dfu = MonocleCommands.enterDfu()
        assertTrue(dfu.contains("_update.nrf52()"))
        assertFalse(dfu.contains("update.micropython()"))
        assertFalse(dfu.contains("force_sleep"))
        assertEquals(
            "D0:8D:1F:1B:B4:EB",
            FirmwareFlasher.incrementedAddress("D0:8D:1F:1B:B4:EA"),
        )
        val zip = java.io.File.createTempFile("fwzip", ".zip")
        zip.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(1200))
        assertTrue(FirmwareFlasher.isAppZip(zip))
        zip.writeBytes(byteArrayOf(0, 1, 2, 3))
        assertFalse(FirmwareFlasher.isAppZip(zip))
        zip.delete()
    }
}
