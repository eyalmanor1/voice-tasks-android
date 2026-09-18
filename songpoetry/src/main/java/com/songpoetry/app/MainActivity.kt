package com.songpoetry.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

class MainActivity : AppCompatActivity(), RecognitionListener {
  private lateinit var url: EditText
  private lateinit var web: WebView
  private lateinit var output: LinearLayout
  private lateinit var analyze: Button
  private var recognizer: SpeechRecognizer? = null
  private var running = false
  private val translator by lazy {
    Translation.getClient(TranslatorOptions.Builder()
      .setSourceLanguage(TranslateLanguage.PERSIAN)
      .setTargetLanguage(TranslateLanguage.HEBREW).build())
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    buildUi()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun buildUi() {
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(24,24,24,24)
      setBackgroundColor(Color.rgb(16,16,20))
    }
    fun text(s:String, size:Float)=TextView(this).apply { text=s; textSize=size; setTextColor(Color.WHITE) }
    root.addView(text("שירה בשפות",28f))
    root.addView(text("YouTube → מקור → תעתיק → עברית",15f))

    val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
    url=EditText(this).apply {
      hint="הדבק קישור YouTube"
      setHintTextColor(Color.GRAY); setTextColor(Color.WHITE); singleLine=true
      layoutParams=LinearLayout.LayoutParams(0,WRAP_CONTENT,1f)
    }
    val paste=Button(this).apply { text="הדבק"; setOnClickListener {
      val cm=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
      url.setText(cm.primaryClip?.getItemAt(0)?.coerceToText(this@MainActivity)?.toString().orEmpty())
    }}
    row.addView(url); row.addView(paste); root.addView(row)

    val open=Button(this).apply { text="פתח שיר"; setOnClickListener {
      val id=parseId(url.text.toString())
      if(id==null) Toast.makeText(this@MainActivity,"קישור YouTube לא תקין",Toast.LENGTH_SHORT).show()
      else web.evaluateJavascript("loadVideo('"+id+"')",null)
    }}
    root.addView(open)

    val loader=WebViewAssetLoader.Builder().addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(this)).build()
    web=WebView(this).apply {
      layoutParams=LinearLayout.LayoutParams(MATCH_PARENT,(resources.displayMetrics.widthPixels*9/16))
      settings.javaScriptEnabled=true; settings.domStorageEnabled=true; settings.mediaPlaybackRequiresUserGesture=true
      webViewClient=object:WebViewClient(){
        override fun shouldInterceptRequest(v:WebView?,r:android.webkit.WebResourceRequest?)=r?.url?.let(loader::shouldInterceptRequest)
      }
      addJavascriptInterface(object {
        @JavascriptInterface fun ready(){ runOnUiThread { analyze.isEnabled=true } }
        @JavascriptInterface fun state(s:Int){}
      },"AndroidBridge")
      loadUrl("https://appassets.androidplatform.net/assets/youtube_player.html")
    }
    root.addView(web)

    analyze=Button(this).apply {
      text="נתח שיר"; isEnabled=false
      setOnClickListener { if(running) stopRecognition() else ensureMicAndStart() }
    }
    root.addView(analyze)

    val scroll=ScrollView(this)
    output=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    scroll.addView(output)
    root.addView(scroll,LinearLayout.LayoutParams(MATCH_PARENT,0,1f))
    setContentView(root)
  }

  private fun ensureMicAndStart(){
    if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) startRecognition()
    else ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),7)
  }
  override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){
    super.onRequestPermissionsResult(r,p,g)
    if(r==7 && g.firstOrNull()==PackageManager.PERMISSION_GRANTED) startRecognition()
  }

  private fun startRecognition(){
    if(!SpeechRecognizer.isRecognitionAvailable(this)){ Toast.makeText(this,"זיהוי דיבור אינו זמין",Toast.LENGTH_LONG).show(); return }
    recognizer=SpeechRecognizer.createSpeechRecognizer(this).also{it.setRecognitionListener(this)}
    running=true; analyze.text="עצור ניתוח"; web.evaluateJavascript("playVideo()",null); listen()
  }
  private fun listen(){
    if(!running)return
    recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_LANGUAGE,"fa-IR")
      putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
    })
  }
  private fun stopRecognition(){ running=false; analyze.text="נתח שיר"; recognizer?.destroy(); recognizer=null }

  private fun addLine(source:String){
    val latin=transliterate(source,false)
    val hebrewSound=transliterate(source,true)
    val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(18,18,18,18); setBackgroundColor(Color.rgb(38,38,46)) }
    fun line(s:String,size:Float)=TextView(this).apply { text=s; textSize=size; setTextColor(Color.WHITE); textDirection=android.view.View.TEXT_DIRECTION_RTL }
    card.addView(line(source,24f)); card.addView(line(latin,17f)); card.addView(line(hebrewSound,21f))
    val translated=line("מתרגם…",18f); card.addView(translated)
    output.addView(card,LinearLayout.LayoutParams(MATCH_PARENT,WRAP_CONTENT).apply{setMargins(0,8,0,8)})
    translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
      .continueWithTask { translator.translate(source) }
      .addOnSuccessListener { translated.text=it }
      .addOnFailureListener { translated.text="התרגום אינו זמין כרגע" }
  }

  private fun transliterate(s:String,he:Boolean):String {
    val words=mapOf("عاشق" to ("âshegh" to "אָשֶׁג"),"شو" to ("sho" to "שׁוֹ"),"برای" to ("barâye" to "בָּרָאיֶה"),"یک" to ("yek" to "יֶק"),"شب" to ("shab" to "שַׁבּ"),"دل" to ("del" to "דֶל"),"من" to ("man" to "מַן"),"تو" to ("to" to "תוֹ"))
    return s.replace('ي','ی').replace('ك','ک').split(Regex("\\s+")).joinToString(" "){w->
      words[w]?.let{if(he)it.second else it.first} ?: w
    }
  }
  private fun parseId(x:String):String?{
    val t=x.trim()
    if(Regex("^[A-Za-z0-9_-]{11}$").matches(t))return t
    val u=runCatching{android.net.Uri.parse(t)}.getOrNull()?:return null
    val h=u.host?.lowercase()?.removePrefix("www.")?:return null
    val id=when(h){"youtu.be"->u.pathSegments.firstOrNull();"youtube.com","m.youtube.com","music.youtube.com"->if(u.path=="/watch")u.getQueryParameter("v") else u.pathSegments.getOrNull(1);else->null}
    return id?.takeIf{Regex("^[A-Za-z0-9_-]{11}$").matches(it)}
  }

  override fun onResults(b:Bundle?){ b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf{it.isNotBlank()}?.let(::addLine); if(running)listen() }
  override fun onError(e:Int){ if(running && (e==SpeechRecognizer.ERROR_NO_MATCH||e==SpeechRecognizer.ERROR_SPEECH_TIMEOUT))listen() else if(running)stopRecognition() }
  override fun onPartialResults(b:Bundle?){}
  override fun onReadyForSpeech(b:Bundle?){}
  override fun onBeginningOfSpeech(){}
  override fun onRmsChanged(v:Float){}
  override fun onBufferReceived(b:ByteArray?){}
  override fun onEndOfSpeech(){}
  override fun onEvent(t:Int,b:Bundle?){}
  override fun onDestroy(){ stopRecognition(); translator.close(); web.destroy(); super.onDestroy() }
}
