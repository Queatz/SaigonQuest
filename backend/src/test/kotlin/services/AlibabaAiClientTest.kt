package services

import kotlinx.serialization.json.Json
import utils.json as appJson
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
}
