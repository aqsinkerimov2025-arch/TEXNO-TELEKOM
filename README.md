# TEXNO-TELEKOM

## Vahid funksiya uyğunluğu

Bu layihədə sayt (`index.html`) və Android tətbiqi (`app/`) eyni Firebase layihəsini və eyni məlumat kolleksiyalarını istifadə edir. Yeni funksiyalar hazırlanarkən hər iki platformada uyğun istifadəçi axını yaradılmalıdır.

### Ortaq funksiyalar
- Firebase Authentication ilə admin girişi; yazma icazəsi yalnız təsdiqlənmiş admin UID üçün.
- `programs`: proqram kataloqu, ikon, təsvir, versiya, fayl ölçüsü, yükləmə linki; əlavə etmə, redaktə, silmə.
- `programs` daxilində `content_type: "channel_post"`: kanal paylaşımı, VIP/Premium, versiya, xüsusiyyətlər, APK linki, Android tələbi, Telegram linki.
- `phone_information`: TEXNO-MƏLUMAT başlığı, mətn, şəkil URL-i; canlı siyahı və admin silməsi.
- `site_settings/content`: sayt başlığı və məlumat yazıları, canlı dizayn rəngləri və qızılı kart seçimi.
- Cloudinary: proqram şəkilləri, TEXNO-MƏLUMAT şəkilləri və APK faylları.
- Axtarış və Firebase snapshot yenilənmələri.

### Platformaya məxsus imkanlar
- Android APK yeniləməsinin yoxlanması və Android quraşdırıcısının açılması yalnız Android tətbiqində mümkündür.
- Android cihaz ID-si və cihaz məlumatlarının göstərilməsi Android-ə məxsusdur.
- Veb qalereya seçimi brauzerin fayl seçicisindən, Android qalereya seçimi isə Android picker-dən istifadə edir.

### Yeni funksiya əlavə etmə qaydası
1. Əvvəlcə ortaq məlumat modeli və Firebase kolleksiyası müəyyənləşdirilir.
2. Admin əməliyyatları həm vebdə, həm Android-də hazırlanır.
3. İstifadəçi tərəfdə göstərilmə və axtarış hər iki platformada yoxlanılır.
4. Firestore qaydaları və giriş icazələri yoxlanılır.
5. Android kodu dəyişibsə, imzalanmış APK build edilir və nəticəsi yoxlanılır.
6. Sonda sayt və tətbiq üçün funksional uyğunluq yoxlama siyahısı yenilənir.

**Vacib:** Vebdə dəyişikliklər dərhal yayımlana bilər. Android-in native kodu dəyişdikdə yeni APK build edib quraşdırmaq lazımdır. Firebase-dən gələn mətn, şəkil, link və dəstəklənən dizayn parametrləri isə APK yeniləmədən hər iki platformada yenilənə bilər.
