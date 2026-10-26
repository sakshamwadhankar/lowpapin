# Supabase setup

1. Create a Supabase project.
2. Run `migrations/001_social.sql` in the SQL editor.
3. Build with the project URL and publishable key supplied as Gradle properties or environment variables:

```powershell
./gradlew :app:assembleDebug -PsupabaseUrl=https://YOUR_PROJECT.supabase.co -PsupabasePublishableKey=YOUR_PUBLISHABLE_KEY
```

The publishable key is safe to ship in the client only when the database tables and functions are protected by the included RLS policies. Never put a `service_role` key in the APK.
