# Voice Tasks (Android Native) — Debug APK לשיתוף

מה יש:
- הקלטה מקומית (MediaRecorder) + רשימת משימות
- ניגון הקלטות
- תמלול "אופליין אם זמין" בזמן ההקלטה (SpeechRecognizer + EXTRA_PREFER_OFFLINE)
  * תלוי במכשיר/חבילות שפה — אם אין, עדיין הכל עובד.

## איך מוציאים APK בלי להתקין כלום במחשב
1) העלה את הריפו הזה ל-GitHub (main)
2) Actions → Build Debug APK → Run workflow
3) הורד Artifact בשם `voice-tasks-debug-apk` (זה ה-APK)

## איך עושים לינק שאפשר לשלוח לכל אחד
Releases → Draft a new release → העלה את ה-APK → Publish  
ואז יש לינק קבוע להורדה.

## התקנה על טלפון
להוריד APK → לאשר Install unknown apps → להתקין
