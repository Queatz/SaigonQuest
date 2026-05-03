package services

import kotlinx.serialization.json.Json
import utils.json as appJson
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AlibabaAiClientTest {
    @Test
    fun testParseAsrTaskResponseWithResult() {
        val json = """
            {
              "request_id": "d0681413-3f9e-953d-8367-d2c929083813",
              "output": {
                "task_id": "4883038b-b758-45fe-85e2-70d9710f79dc",
                "task_status": "SUCCEEDED",
                "submit_time": "2026-03-28 14:27:51.171",
                "scheduled_time": "2026-03-28 14:27:51.187",
                "end_time": "2026-03-28 14:27:51.964",
                "result": {
                  "transcription_url": "http://example.com/result.json"
                }
              },
              "usage": {
                "seconds": 3
              }
            }
        """.trimIndent()

        val pollData = appJson.decodeFromString<AlibabaAiClient.AsrTaskResponse>(json)
        val output = pollData.output
        assertNotNull(output)
        assertEquals("SUCCEEDED", output!!.taskStatus)
        
        val res = output!!.result ?: output!!.results?.firstOrNull()
        assertNotNull(res)
        assertEquals("http://example.com/result.json", res!!.transcriptionUrl)
    }

    @Test
    fun testParseTranscriptionFileResult() {
        val json = """
            {
              "file_url": "https://example.com/audio.webm",
              "audio_info": {
                "format": "opus",
                "sample_rate": 48000
              },
              "transcripts": [
                {
                  "channel_id": 0,
                  "text": "What's up, brothers? Daughter called Tekken Annie.",
                  "sentences": [
                    {
                      "sentence_id": 0,
                      "begin_time": 108,
                      "end_time": 3469,
                      "language": "en",
                      "emotion": "neutral",
                      "text": "What's up, brothers? Daughter called Tekken Annie."
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val singleItem = appJson.decodeFromString<AlibabaAiClient.TranscriptionFileResult>(json)
        val transcript = singleItem.text ?: singleItem.transcription ?: singleItem.transcripts?.mapNotNull { it.text }?.joinToString(" ")
        assertEquals("What's up, brothers? Daughter called Tekken Annie.", transcript)
    }

    @Test
    fun testCombineImageAndMask() {
        val width = 100
        val height = 100
        
        // Create a red original image
        val original = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val og = original.createGraphics()
        og.color = Color.RED
        og.fillRect(0, 0, width, height)
        og.dispose()
        
        val originalBaos = ByteArrayOutputStream()
        ImageIO.write(original, "png", originalBaos)
        val originalBytes = originalBaos.toByteArray()
        
        // Create a mask (white circle on black background)
        val mask = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val mg = mask.createGraphics()
        mg.color = Color.BLACK
        mg.fillRect(0, 0, width, height)
        mg.color = Color.WHITE
        mg.fillOval(10, 10, 80, 80)
        mg.dispose()
        
        val maskBaos = ByteArrayOutputStream()
        ImageIO.write(mask, "png", maskBaos)
        val maskBytes = maskBaos.toByteArray()
        
        val combinedBytes = AlibabaAiClient.combineImageAndMask(originalBytes, maskBytes)
        assertNotNull(combinedBytes)
        
        // Verify combined image
        val combined = ImageIO.read(java.io.ByteArrayInputStream(combinedBytes))
        assertNotNull(combined)
        assertEquals(width, combined.width)
        assertEquals(height, combined.height)
        
        // Check a pixel inside the circle (should be red and opaque)
        val centerPixel = combined.getRGB(50, 50)
        val color = Color(centerPixel, true)
        assertEquals(255, color.red)
        assertEquals(0, color.green)
        assertEquals(0, color.blue)
        assertEquals(255, color.alpha)
        
        // Check a pixel outside the circle (should be transparent)
        val cornerPixel = combined.getRGB(0, 0)
        val cornerColor = Color(cornerPixel, true)
        assertEquals(0, cornerColor.alpha)
    }

    @Test
    fun testCombineImageAndMaskPixelArt() {
        val width = 8
        val height = 8
        
        // Create a 8x8 pixel art image (checkerboard)
        val original = BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((x + y) % 2 == 0) {
                    original.setRGB(x, y, Color.BLUE.rgb)
                } else {
                    original.setRGB(x, y, Color.GREEN.rgb)
                }
            }
        }
        
        val originalBaos = ByteArrayOutputStream()
        ImageIO.write(original, "png", originalBaos)
        val originalBytes = originalBaos.toByteArray()
        
        // Create a mask (top half white, bottom half black)
        val mask = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
        val mg = mask.createGraphics()
        mg.color = Color.WHITE
        mg.fillRect(0, 0, width, height / 2)
        mg.color = Color.BLACK
        mg.fillRect(0, height / 2, width, height / 2)
        mg.dispose()
        
        val maskBaos = ByteArrayOutputStream()
        ImageIO.write(mask, "png", maskBaos)
        val maskBytes = maskBaos.toByteArray()
        
        val combinedBytes = AlibabaAiClient.combineImageAndMask(originalBytes, maskBytes)
        assertNotNull(combinedBytes)
        
        val combined = ImageIO.read(java.io.ByteArrayInputStream(combinedBytes))
        assertNotNull(combined)
        
        // Top half should be opaque blue/green
        val topPixel = Color(combined.getRGB(0, 0), true)
        assertEquals(255, topPixel.alpha)
        assertEquals(Color.BLUE.red, topPixel.red)
        
        // Bottom half should be transparent
        val bottomPixel = Color(combined.getRGB(0, height - 1), true)
        assertEquals(0, bottomPixel.alpha)
    }

    @Test
    fun testCombineImageAndMaskResizing() {
        val ogWidth = 100
        val ogHeight = 100
        val maskWidth = 50
        val maskHeight = 50
        
        val original = BufferedImage(ogWidth, ogHeight, BufferedImage.TYPE_INT_RGB)
        val mask = BufferedImage(maskWidth, maskHeight, BufferedImage.TYPE_INT_RGB)
        
        val originalBaos = ByteArrayOutputStream()
        ImageIO.write(original, "png", originalBaos)
        val originalBytes = originalBaos.toByteArray()
        
        val maskBaos = ByteArrayOutputStream()
        ImageIO.write(mask, "png", maskBaos)
        val maskBytes = maskBaos.toByteArray()
        
        val combinedBytes = AlibabaAiClient.combineImageAndMask(originalBytes, maskBytes)
        assertNotNull(combinedBytes)
        
        val combined = ImageIO.read(java.io.ByteArrayInputStream(combinedBytes))
        assertEquals(ogWidth, combined.width)
        assertEquals(ogHeight, combined.height)
    }

    @Test
    fun testCombineImageAndMaskWithExistingAlpha() {
        val width = 10
        val height = 10
        
        // Semi-transparent red original
        val original = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                original.setRGB(x, y, Color(255, 0, 0, 128).rgb)
            }
        }
        
        val originalBaos = ByteArrayOutputStream()
        ImageIO.write(original, "png", originalBaos)
        val originalBytes = originalBaos.toByteArray()
        
        // White mask (should keep original alpha)
        val mask = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val mg = mask.createGraphics()
        mg.color = Color.WHITE
        mg.fillRect(0, 0, width, height)
        mg.dispose()
        
        val maskBaos = ByteArrayOutputStream()
        ImageIO.write(mask, "png", maskBaos)
        val maskBytes = maskBaos.toByteArray()
        
        val combinedBytes = AlibabaAiClient.combineImageAndMask(originalBytes, maskBytes)
        assertNotNull(combinedBytes)
        
        val combined = ImageIO.read(java.io.ByteArrayInputStream(combinedBytes))
        val pixel = Color(combined.getRGB(0, 0), true)
        // With improved logic, 128 (original alpha) * 255 (mask alpha) / 255 = 128
        assertEquals(128, pixel.alpha)
    }

    @Test
    fun testParseImageSynthesisResponse() {
        val json = """
            {
              "output": {
                "choices": [
                  {
                    "finish_reason": "stop",
                    "message": {
                      "content": [
                        {
                          "image": "https://example.com/result.png"
                        }
                      ],
                      "role": "assistant"
                    }
                  }
                ]
              },
              "usage": {
                "height": 832,
                "image_count": 1,
                "width": 1248
              },
              "request_id": "0346235e-3c1f-9957-8c59-305b4b70d4f4"
            }
        """.trimIndent()

        val data = appJson.decodeFromString<AlibabaAiClient.ImageSynthesisResponse>(json)
        val url = data.output?.choices?.firstOrNull()?.message?.content?.firstOrNull { it.image != null }?.image
        assertEquals("https://example.com/result.png", url)
    }
}
