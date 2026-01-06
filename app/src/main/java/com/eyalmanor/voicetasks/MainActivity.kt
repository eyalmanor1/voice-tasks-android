package com.eyalmanor.voicetasks

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

  private lateinit var recordBtn: Button
  private lateinit var status: TextView
  private lateinit var recycler: RecyclerView

  private val taskDao by lazy { AppDb.get(this).taskDao() }
  private val adapter by lazy { TaskAdapter(::onToggleDone, ::onPlay, ::onShare, ::onDelete) }

  private var recorder: MediaRecorder? = null
  private var mediaPlayer: MediaPlayer? = null

  private var speech: SpeechRecognizer? = null
  private var latestTranscript: String = ""

  private var isRecording = false
  private var currentOutputFile: File? = null
  private var startedAt = 0L

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    recordBtn = findViewById(R.id.recordBtn)
    status = findViewById(R.id.status)
    recycler = findViewById(R.id.recycler)

    recycler.layoutManager = LinearLayoutManager(this)
    recycler.adapter = adapter

    recordBtn.setOnClickListener { if (!isRecording) startCapture() else stopCaptureAndSave() }

    refresh()
  }

  private fun refresh() {
    lifecycleScope.launch {
      val items = withContext(Dispatchers.IO) { taskDao.getAllNewest() }
      adapter.submit(items)
    }
  }

  private fun ensureMicPermission(): Boolean {
    val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    if (!ok) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 2001)
    return ok
  }

  private fun startCapture() {
    if (!ensureMicPermission()) return

    latestTranscript = ""
    startedAt = System.currentTimeMillis()

    val outDir = getExternalFilesDir(null) ?: filesDir
    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val outFile = File(outDir, "voice_task_$ts.m4a")
    currentOutputFile = outFile

    recorder = MediaRecorder(this).apply {
      setAudioSource(MediaRecorder.AudioSource.MIC)
      setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
      setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
      setAudioEncodingBitRate(128000)
      setAudioSamplingRate(44100)
      setOutputFile(outFile.absolutePath)
      prepare()
      start()
    }

    if (SpeechRecognizer.isRecognitionAvailable(this)) {
      speech = SpeechRecognizer.createSpeechRecognizer(this).apply {
        setRecognitionListener(object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) {}
          override fun onBeginningOfSpeech() {}
          override fun onRmsChanged(rmsdB: Float) {}
          override fun onBufferReceived(buffer: ByteArray?) {}
          override fun onEndOfSpeech() {}
          override fun onError(error: Int) {}
          override fun onResults(results: Bundle?) {
            val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (best.isNotBlank()) latestTranscript = best
          }
          override fun onPartialResults(partialResults: Bundle?) {
            val best = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (best.isNotBlank()) latestTranscript = best
          }
          override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
          putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        startListening(intent)
      }
    }

    isRecording = true
    status.text = "מקליט… (תמלול אופליין אם זמין)"
    recordBtn.text = "■ עצור"
  }

  private fun stopCaptureAndSave() {
    if (!isRecording) return
    isRecording = false

    try { speech?.stopListening() } catch (_: Exception) {}
    try { speech?.destroy() } catch (_: Exception) {}
    speech = null

    try { recorder?.stop() } catch (_: Exception) {}
    try { recorder?.release() } catch (_: Exception) {}
    recorder = null

    val file = currentOutputFile
    if (file == null || !file.exists()) {
      status.text = "שגיאה בשמירה"
      recordBtn.text = "● הקלט"
      return
    }

    val now = System.currentTimeMillis()
    val title = "הקלטה " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("he","IL")).format(Date(now))
    val durationMs = (now - startedAt).coerceAtLeast(0L)

    lifecycleScope.launch {
      withContext(Dispatchers.IO) {
        taskDao.insert(TaskEntity(
          title = title,
          createdAt = now,
          isDone = false,
          audioPath = file.absolutePath,
          durationMs = durationMs,
          transcript = latestTranscript
        ))
      }
      status.text = "מוכן"
      recordBtn.text = "● הקלט"
      refresh()
    }
  }

  private fun onToggleDone(item: TaskEntity) {
    lifecycleScope.launch(Dispatchers.IO) {
      taskDao.update(item.copy(isDone = !item.isDone))
      withContext(Dispatchers.Main) { refresh() }
    }
  }

  private fun onPlay(item: TaskEntity) {
    val path = item.audioPath ?: return
    try {
      mediaPlayer?.stop()
      mediaPlayer?.release()
      mediaPlayer = MediaPlayer().apply {
        setDataSource(path)
        prepare()
        start()
      }
    } catch (_: Exception) {}
  }

  private fun onShare(item: TaskEntity) {
    val path = item.audioPath ?: return
    val file = File(path)
    if (!file.exists()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "audio/*"
      putExtra(Intent.EXTRA_STREAM, android.net.Uri.fromFile(file))
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, "שתף הקלטה"))
  }

  private fun onDelete(item: TaskEntity) {
    lifecycleScope.launch(Dispatchers.IO) {
      item.audioPath?.let { try { File(it).delete() } catch (_: Exception) {} }
      taskDao.delete(item)
      withContext(Dispatchers.Main) { refresh() }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    try { recorder?.release() } catch (_: Exception) {}
    try { mediaPlayer?.release() } catch (_: Exception) {}
    try { speech?.destroy() } catch (_: Exception) {}
  }
}
