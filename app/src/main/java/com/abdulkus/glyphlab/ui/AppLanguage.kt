package com.abdulkus.glyphlab.ui

enum class AppLanguage(
    val pickerCode: String,
    val storageCode: String,
    val nativeName: String
) {
    EN("EN", "en", "English"),
    RU("RU", "ru", "Русский"),
    DE("DE", "de", "Deutsch"),
    FR("FR", "fr", "Français"),
    PL("PL", "pl", "Polski"),
    UA("UA", "uk", "Українська"),
    HI("HI", "hi", "हिन्दी"),
    CH("CH", "zh", "中文"),
    JP("JP", "ja", "日本語"),
    KO("KO", "ko", "한국어");

    companion object {
        fun fromStorageCode(code: String?): AppLanguage? = entries.firstOrNull { it.storageCode == code }
    }
}

object UiStrings {
    private val en = mapOf(
        "choose_language" to "Choose language",
        "settings" to "SETTINGS",
        "shape" to "SHAPE",
        "bright_vertices" to "Bright vertices",
        "rotation_axes" to "AUTO-ROTATION AXES",
        "intensity" to "INTENSITY",
        "particles" to "PARTICLES",
        "flow" to "FLOW",
        "trail" to "TRAIL LENGTH",
        "speed" to "SPEED",
        "minute_update" to "TIME UPDATES ONCE A MINUTE",
        "auto_brightness" to "Auto brightness",
        "auto_source" to "AUTO BRIGHTNESS SOURCE",
        "ambient" to "Light sensor",
        "screen" to "Screen brightness",
        "min_brightness" to "MINIMUM BRIGHTNESS",
        "max_brightness" to "MAXIMUM BRIGHTNESS",
        "matrix_brightness" to "MATRIX BRIGHTNESS",
        "accelerometer" to "Accelerometer",
        "tilt_response" to "TILT RESPONSE",
        "frame_rate" to "FRAME RATE",
        "display" to "DISPLAY",
        "sleep_mode" to "SLEEP MODE",
        "lock_only" to "Lock screen only",
        "sleep_clock" to "Clock while sleeping",
        "start" to "▶  START ON MATRIX",
        "stop" to "■  STOP MATRIX",
        "add_toy" to "ADD TO ALWAYS-ON GLYPH TOYS",
        "toy_hint" to "Open: Glyph Interface → Flip to Glyph → Always-on Glyph Toy",
        "language" to "Language",
        "github" to "GitHub",
        "donate" to "Donate",
        "update_available" to "UPDATE AVAILABLE",
        "update_body" to "A newer Glyph Lab build is available on GitHub.",
        "download" to "DOWNLOAD",
        "downloading" to "DOWNLOADING",
        "install" to "INSTALL",
        "install_permission" to "Allow Glyph Lab to install apps, then the installer will open automatically.",
        "update_error" to "Could not check or download the update.",
        "effect_wireframe" to "3D SHAPES",
        "effect_fire" to "FIRE",
        "effect_gravity" to "SAND",
        "effect_plasma" to "PLASMA",
        "effect_starfield" to "STARS",
        "effect_clock" to "CLOCK",
        "solid_cube" to "Cube",
        "solid_tetra" to "Tetrahedron",
        "solid_octa" to "Octahedron",
        "solid_pyramid" to "Pyramid"
    )

    private val ru = mapOf(
        "choose_language" to "Выберите язык", "settings" to "НАСТРОЙКИ", "shape" to "ФОРМА",
        "bright_vertices" to "Яркие вершины", "rotation_axes" to "ОСИ АВТОВРАЩЕНИЯ",
        "intensity" to "ИНТЕНСИВНОСТЬ", "particles" to "ЧАСТИЦЫ", "flow" to "СЫПУЧЕСТЬ",
        "trail" to "ДЛИНА ШЛЕЙФА", "speed" to "СКОРОСТЬ", "minute_update" to "ВРЕМЯ ОБНОВЛЯЕТСЯ РАЗ В МИНУТУ",
        "auto_brightness" to "Автояркость", "auto_source" to "ИСТОЧНИК АВТОЯРКОСТИ",
        "ambient" to "Датчик света", "screen" to "Яркость экрана", "min_brightness" to "МИНИМАЛЬНАЯ ЯРКОСТЬ",
        "max_brightness" to "МАКСИМАЛЬНАЯ ЯРКОСТЬ", "matrix_brightness" to "ЯРКОСТЬ МАТРИЦЫ",
        "accelerometer" to "Акселерометр", "tilt_response" to "РЕАКЦИЯ НА НАКЛОН", "frame_rate" to "ЧАСТОТА КАДРОВ",
        "display" to "ОТОБРАЖЕНИЕ", "sleep_mode" to "РЕЖИМ СНА", "lock_only" to "Только на блокировке",
        "sleep_clock" to "Часы во время сна", "start" to "▶  ЗАПУСТИТЬ НА МАТРИЦЕ", "stop" to "■  ОСТАНОВИТЬ МАТРИЦУ",
        "add_toy" to "ДОБАВИТЬ В ALWAYS-ON GLYPH TOYS", "toy_hint" to "Откройте: Glyph Interface → Flip to Glyph → Always-on Glyph Toy",
        "language" to "Язык", "github" to "GitHub", "donate" to "Donate", "update_available" to "ДОСТУПНО ОБНОВЛЕНИЕ",
        "update_body" to "На GitHub доступна более новая сборка Glyph Lab.", "download" to "СКАЧАТЬ", "downloading" to "СКАЧИВАНИЕ",
        "install" to "УСТАНОВИТЬ", "install_permission" to "Разрешите Glyph Lab установку приложений — после этого установщик откроется автоматически.",
        "update_error" to "Не удалось проверить или скачать обновление.", "effect_wireframe" to "3D-ФИГУРЫ", "effect_fire" to "ОГОНЬ",
        "effect_gravity" to "ПЕСОК", "effect_plasma" to "ПЛАЗМА", "effect_starfield" to "ЗВЁЗДЫ", "effect_clock" to "ЧАСЫ",
        "solid_cube" to "Куб", "solid_tetra" to "Тетраэдр", "solid_octa" to "Октаэдр", "solid_pyramid" to "Пирамида"
    )

    private val de = mapOf(
        "choose_language" to "Sprache wählen", "settings" to "EINSTELLUNGEN", "shape" to "FORM",
        "bright_vertices" to "Helle Eckpunkte", "rotation_axes" to "AUTO-ROTATIONSACHSEN", "intensity" to "INTENSITÄT",
        "particles" to "PARTIKEL", "flow" to "FLIESSVERHALTEN", "trail" to "SCHWEIFLÄNGE", "speed" to "GESCHWINDIGKEIT",
        "minute_update" to "ZEIT WIRD EINMAL PRO MINUTE AKTUALISIERT", "auto_brightness" to "Auto-Helligkeit",
        "auto_source" to "QUELLE DER AUTO-HELLIGKEIT", "ambient" to "Lichtsensor", "screen" to "Displayhelligkeit",
        "min_brightness" to "MINIMALE HELLIGKEIT", "max_brightness" to "MAXIMALE HELLIGKEIT", "matrix_brightness" to "MATRIX-HELLIGKEIT",
        "accelerometer" to "Beschleunigungssensor", "tilt_response" to "NEIGUNGSREAKTION", "frame_rate" to "BILDRATE",
        "display" to "ANZEIGE", "sleep_mode" to "SCHLAFMODUS", "lock_only" to "Nur auf Sperrbildschirm", "sleep_clock" to "Uhr im Schlafmodus",
        "start" to "▶  AUF MATRIX STARTEN", "stop" to "■  MATRIX STOPPEN", "add_toy" to "ZU ALWAYS-ON GLYPH TOYS HINZUFÜGEN",
        "toy_hint" to "Öffne: Glyph Interface → Flip to Glyph → Always-on Glyph Toy", "language" to "Sprache", "github" to "GitHub", "donate" to "Spenden",
        "update_available" to "UPDATE VERFÜGBAR", "update_body" to "Auf GitHub ist eine neuere Glyph-Lab-Version verfügbar.", "download" to "HERUNTERLADEN",
        "downloading" to "WIRD GELADEN", "install" to "INSTALLIEREN", "install_permission" to "Erlaube Glyph Lab die Installation von Apps; danach öffnet sich der Installer automatisch.",
        "update_error" to "Update konnte nicht geprüft oder geladen werden.", "effect_wireframe" to "3D-FORMEN", "effect_fire" to "FEUER",
        "effect_gravity" to "SAND", "effect_plasma" to "PLASMA", "effect_starfield" to "STERNE", "effect_clock" to "UHR",
        "solid_cube" to "Würfel", "solid_tetra" to "Tetraeder", "solid_octa" to "Oktaeder", "solid_pyramid" to "Pyramide"
    )

    private val fr = mapOf(
        "choose_language" to "Choisir la langue", "settings" to "RÉGLAGES", "shape" to "FORME", "bright_vertices" to "Sommets lumineux",
        "rotation_axes" to "AXES DE ROTATION AUTO", "intensity" to "INTENSITÉ", "particles" to "PARTICULES", "flow" to "FLUIDITÉ",
        "trail" to "LONGUEUR DE TRAÎNÉE", "speed" to "VITESSE", "minute_update" to "L’HEURE EST ACTUALISÉE CHAQUE MINUTE",
        "auto_brightness" to "Luminosité auto", "auto_source" to "SOURCE DE LUMINOSITÉ AUTO", "ambient" to "Capteur de lumière",
        "screen" to "Luminosité écran", "min_brightness" to "LUMINOSITÉ MINIMALE", "max_brightness" to "LUMINOSITÉ MAXIMALE",
        "matrix_brightness" to "LUMINOSITÉ DE LA MATRICE", "accelerometer" to "Accéléromètre", "tilt_response" to "RÉACTION À L’INCLINAISON",
        "frame_rate" to "IMAGES/S", "display" to "AFFICHAGE", "sleep_mode" to "MODE SOMMEIL", "lock_only" to "Écran verrouillé uniquement",
        "sleep_clock" to "Horloge pendant le sommeil", "start" to "▶  LANCER SUR LA MATRICE", "stop" to "■  ARRÊTER LA MATRICE",
        "add_toy" to "AJOUTER AUX ALWAYS-ON GLYPH TOYS", "toy_hint" to "Ouvrez : Glyph Interface → Flip to Glyph → Always-on Glyph Toy",
        "language" to "Langue", "github" to "GitHub", "donate" to "Don", "update_available" to "MISE À JOUR DISPONIBLE",
        "update_body" to "Une version plus récente de Glyph Lab est disponible sur GitHub.", "download" to "TÉLÉCHARGER", "downloading" to "TÉLÉCHARGEMENT",
        "install" to "INSTALLER", "install_permission" to "Autorisez Glyph Lab à installer des applications ; l’installateur s’ouvrira ensuite automatiquement.",
        "update_error" to "Impossible de vérifier ou télécharger la mise à jour.", "effect_wireframe" to "FORMES 3D", "effect_fire" to "FEU",
        "effect_gravity" to "SABLE", "effect_plasma" to "PLASMA", "effect_starfield" to "ÉTOILES", "effect_clock" to "HORLOGE",
        "solid_cube" to "Cube", "solid_tetra" to "Tétraèdre", "solid_octa" to "Octaèdre", "solid_pyramid" to "Pyramide"
    )

    private val pl = mapOf(
        "choose_language" to "Wybierz język", "settings" to "USTAWIENIA", "shape" to "KSZTAŁT", "bright_vertices" to "Jasne wierzchołki",
        "rotation_axes" to "OSIE AUTOOBROTU", "intensity" to "INTENSYWNOŚĆ", "particles" to "CZĄSTECZKI", "flow" to "SYPKOŚĆ",
        "trail" to "DŁUGOŚĆ SMUGI", "speed" to "PRĘDKOŚĆ", "minute_update" to "CZAS ODSWIEŻA SIĘ RAZ NA MINUTĘ",
        "auto_brightness" to "Autojasność", "auto_source" to "ŹRÓDŁO AUTOJASNOŚCI", "ambient" to "Czujnik światła", "screen" to "Jasność ekranu",
        "min_brightness" to "MINIMALNA JASNOŚĆ", "max_brightness" to "MAKSYMALNA JASNOŚĆ", "matrix_brightness" to "JASNOŚĆ MATRYCY",
        "accelerometer" to "Akcelerometr", "tilt_response" to "REAKCJA NA PRZECHYŁ", "frame_rate" to "LICZBA KLATEK", "display" to "WYŚWIETLANIE",
        "sleep_mode" to "TRYB UŚPIENIA", "lock_only" to "Tylko na ekranie blokady", "sleep_clock" to "Zegar podczas uśpienia",
        "start" to "▶  URUCHOM NA MATRYCY", "stop" to "■  ZATRZYMAJ MATRYCĘ", "add_toy" to "DODAJ DO ALWAYS-ON GLYPH TOYS",
        "toy_hint" to "Otwórz: Glyph Interface → Flip to Glyph → Always-on Glyph Toy", "language" to "Język", "github" to "GitHub", "donate" to "Wesprzyj",
        "update_available" to "DOSTĘPNA AKTUALIZACJA", "update_body" to "Na GitHubie jest dostępna nowsza wersja Glyph Lab.", "download" to "POBIERZ",
        "downloading" to "POBIERANIE", "install" to "ZAINSTALUJ", "install_permission" to "Zezwól Glyph Lab na instalowanie aplikacji; instalator otworzy się automatycznie.",
        "update_error" to "Nie udało się sprawdzić lub pobrać aktualizacji.", "effect_wireframe" to "FIGURY 3D", "effect_fire" to "OGIEŃ",
        "effect_gravity" to "PIASEK", "effect_plasma" to "PLAZMA", "effect_starfield" to "GWIAZDY", "effect_clock" to "ZEGAR",
        "solid_cube" to "Sześcian", "solid_tetra" to "Czworościan", "solid_octa" to "Ośmiościan", "solid_pyramid" to "Piramida"
    )

    private val ua = mapOf(
        "choose_language" to "Оберіть мову", "settings" to "НАЛАШТУВАННЯ", "shape" to "ФОРМА", "bright_vertices" to "Яскраві вершини",
        "rotation_axes" to "ОСІ АВТООБЕРТАННЯ", "intensity" to "ІНТЕНСИВНІСТЬ", "particles" to "ЧАСТИНКИ", "flow" to "СИПКІСТЬ",
        "trail" to "ДОВЖИНА ШЛЕЙФУ", "speed" to "ШВИДКІСТЬ", "minute_update" to "ЧАС ОНОВЛЮЄТЬСЯ РАЗ НА ХВИЛИНУ",
        "auto_brightness" to "Автояскравість", "auto_source" to "ДЖЕРЕЛО АВТОЯСКРАВОСТІ", "ambient" to "Датчик світла", "screen" to "Яскравість екрана",
        "min_brightness" to "МІНІМАЛЬНА ЯСКРАВІСТЬ", "max_brightness" to "МАКСИМАЛЬНА ЯСКРАВІСТЬ", "matrix_brightness" to "ЯСКРАВІСТЬ МАТРИЦІ",
        "accelerometer" to "Акселерометр", "tilt_response" to "РЕАКЦІЯ НА НАХИЛ", "frame_rate" to "ЧАСТОТА КАДРІВ", "display" to "ВІДОБРАЖЕННЯ",
        "sleep_mode" to "РЕЖИМ СНУ", "lock_only" to "Лише на екрані блокування", "sleep_clock" to "Годинник під час сну",
        "start" to "▶  ЗАПУСТИТИ НА МАТРИЦІ", "stop" to "■  ЗУПИНИТИ МАТРИЦЮ", "add_toy" to "ДОДАТИ ДО ALWAYS-ON GLYPH TOYS",
        "toy_hint" to "Відкрийте: Glyph Interface → Flip to Glyph → Always-on Glyph Toy", "language" to "Мова", "github" to "GitHub", "donate" to "Підтримати",
        "update_available" to "ДОСТУПНЕ ОНОВЛЕННЯ", "update_body" to "На GitHub доступна новіша збірка Glyph Lab.", "download" to "ЗАВАНТАЖИТИ",
        "downloading" to "ЗАВАНТАЖЕННЯ", "install" to "ВСТАНОВИТИ", "install_permission" to "Дозвольте Glyph Lab встановлювати програми — після цього інсталятор відкриється автоматично.",
        "update_error" to "Не вдалося перевірити або завантажити оновлення.", "effect_wireframe" to "3D-ФІГУРИ", "effect_fire" to "ВОГОНЬ",
        "effect_gravity" to "ПІСОК", "effect_plasma" to "ПЛАЗМА", "effect_starfield" to "ЗІРКИ", "effect_clock" to "ГОДИННИК",
        "solid_cube" to "Куб", "solid_tetra" to "Тетраедр", "solid_octa" to "Октаедр", "solid_pyramid" to "Піраміда"
    )

    private val hindi = mapOf(
        "choose_language" to "भाषा चुनें", "settings" to "सेटिंग्स", "shape" to "आकार", "bright_vertices" to "चमकीले कोने",
        "rotation_axes" to "ऑटो-रोटेशन अक्ष", "intensity" to "तीव्रता", "particles" to "कण", "flow" to "प्रवाह",
        "trail" to "ट्रेल लंबाई", "speed" to "गति", "minute_update" to "समय हर मिनट अपडेट होता है",
        "auto_brightness" to "ऑटो ब्राइटनेस", "auto_source" to "ऑटो ब्राइटनेस स्रोत", "ambient" to "लाइट सेंसर", "screen" to "स्क्रीन ब्राइटनेस",
        "min_brightness" to "न्यूनतम ब्राइटनेस", "max_brightness" to "अधिकतम ब्राइटनेस", "matrix_brightness" to "मैट्रिक्स ब्राइटनेस",
        "accelerometer" to "एक्सेलेरोमीटर", "tilt_response" to "झुकाव प्रतिक्रिया", "frame_rate" to "फ्रेम रेट", "display" to "डिस्प्ले",
        "sleep_mode" to "स्लीप मोड", "lock_only" to "केवल लॉक स्क्रीन", "sleep_clock" to "स्लीप में घड़ी",
        "start" to "▶  मैट्रिक्स पर शुरू करें", "stop" to "■  मैट्रिक्स रोकें", "add_toy" to "ALWAYS-ON GLYPH TOYS में जोड़ें",
        "toy_hint" to "खोलें: Glyph Interface → Flip to Glyph → Always-on Glyph Toy", "language" to "भाषा", "github" to "GitHub", "donate" to "दान",
        "update_available" to "अपडेट उपलब्ध है", "update_body" to "GitHub पर Glyph Lab की नई बिल्ड उपलब्ध है।", "download" to "डाउनलोड",
        "downloading" to "डाउनलोड हो रहा है", "install" to "इंस्टॉल", "install_permission" to "Glyph Lab को ऐप इंस्टॉल करने की अनुमति दें; इसके बाद इंस्टॉलर अपने आप खुल जाएगा।",
        "update_error" to "अपडेट की जाँच या डाउनलोड नहीं हो सका।", "effect_wireframe" to "3D आकार", "effect_fire" to "आग",
        "effect_gravity" to "रेत", "effect_plasma" to "प्लाज़्मा", "effect_starfield" to "तारे", "effect_clock" to "घड़ी",
        "solid_cube" to "घन", "solid_tetra" to "चतुष्फलक", "solid_octa" to "अष्टफलक", "solid_pyramid" to "पिरामिड"
    )

    private val zh = mapOf(
        "choose_language" to "选择语言", "settings" to "设置", "shape" to "形状", "bright_vertices" to "高亮顶点",
        "rotation_axes" to "自动旋转轴", "intensity" to "强度", "particles" to "粒子", "flow" to "流动性", "trail" to "拖尾长度",
        "speed" to "速度", "minute_update" to "时间每分钟更新一次", "auto_brightness" to "自动亮度", "auto_source" to "自动亮度来源",
        "ambient" to "光线传感器", "screen" to "屏幕亮度", "min_brightness" to "最低亮度", "max_brightness" to "最高亮度",
        "matrix_brightness" to "矩阵亮度", "accelerometer" to "加速度计", "tilt_response" to "倾斜响应", "frame_rate" to "帧率",
        "display" to "显示", "sleep_mode" to "睡眠模式", "lock_only" to "仅锁屏显示", "sleep_clock" to "睡眠时显示时钟",
        "start" to "▶  在矩阵上启动", "stop" to "■  停止矩阵", "add_toy" to "添加到 ALWAYS-ON GLYPH TOYS",
        "toy_hint" to "打开：Glyph Interface → Flip to Glyph → Always-on Glyph Toy", "language" to "语言", "github" to "GitHub", "donate" to "赞助",
        "update_available" to "有可用更新", "update_body" to "GitHub 上有更新的 Glyph Lab 版本。", "download" to "下载", "downloading" to "正在下载",
        "install" to "安装", "install_permission" to "请允许 Glyph Lab 安装应用，随后会自动打开安装程序。", "update_error" to "无法检查或下载更新。",
        "effect_wireframe" to "3D 形状", "effect_fire" to "火焰", "effect_gravity" to "沙粒", "effect_plasma" to "等离子",
        "effect_starfield" to "星空", "effect_clock" to "时钟", "solid_cube" to "立方体", "solid_tetra" to "四面体", "solid_octa" to "八面体", "solid_pyramid" to "金字塔"
    )

    private val ja = mapOf(
        "choose_language" to "言語を選択", "settings" to "設定", "shape" to "形状", "bright_vertices" to "明るい頂点",
        "rotation_axes" to "自動回転軸", "intensity" to "強度", "particles" to "パーティクル", "flow" to "流動性", "trail" to "軌跡の長さ",
        "speed" to "速度", "minute_update" to "時刻は1分ごとに更新", "auto_brightness" to "自動輝度", "auto_source" to "自動輝度のソース",
        "ambient" to "光センサー", "screen" to "画面の明るさ", "min_brightness" to "最小輝度", "max_brightness" to "最大輝度", "matrix_brightness" to "マトリクス輝度",
        "accelerometer" to "加速度センサー", "tilt_response" to "傾きの反応", "frame_rate" to "フレームレート", "display" to "表示",
        "sleep_mode" to "スリープモード", "lock_only" to "ロック画面のみ", "sleep_clock" to "スリープ中の時計",
        "start" to "▶  マトリクスで開始", "stop" to "■  マトリクスを停止", "add_toy" to "ALWAYS-ON GLYPH TOYS に追加",
        "toy_hint" to "開く: Glyph Interface → Flip to Glyph → Always-on Glyph Toy", "language" to "言語", "github" to "GitHub", "donate" to "寄付",
        "update_available" to "アップデートがあります", "update_body" to "GitHub に新しい Glyph Lab ビルドがあります。", "download" to "ダウンロード",
        "downloading" to "ダウンロード中", "install" to "インストール", "install_permission" to "Glyph Lab にアプリのインストールを許可すると、インストーラーが自動で開きます。",
        "update_error" to "アップデートを確認またはダウンロードできませんでした。", "effect_wireframe" to "3D形状", "effect_fire" to "炎",
        "effect_gravity" to "砂", "effect_plasma" to "プラズマ", "effect_starfield" to "星", "effect_clock" to "時計",
        "solid_cube" to "立方体", "solid_tetra" to "四面体", "solid_octa" to "八面体", "solid_pyramid" to "ピラミッド"
    )

    private val ko = mapOf(
        "choose_language" to "언어 선택", "settings" to "설정", "shape" to "모양", "bright_vertices" to "밝은 꼭짓점",
        "rotation_axes" to "자동 회전 축", "intensity" to "강도", "particles" to "입자", "flow" to "흐름", "trail" to "잔상 길이",
        "speed" to "속도", "minute_update" to "시간은 1분마다 갱신", "auto_brightness" to "자동 밝기", "auto_source" to "자동 밝기 소스",
        "ambient" to "조도 센서", "screen" to "화면 밝기", "min_brightness" to "최소 밝기", "max_brightness" to "최대 밝기", "matrix_brightness" to "매트릭스 밝기",
        "accelerometer" to "가속도계", "tilt_response" to "기울기 반응", "frame_rate" to "프레임 속도", "display" to "표시",
        "sleep_mode" to "절전 모드", "lock_only" to "잠금 화면에서만", "sleep_clock" to "절전 중 시계",
        "start" to "▶  매트릭스에서 시작", "stop" to "■  매트릭스 중지", "add_toy" to "ALWAYS-ON GLYPH TOYS에 추가",
        "toy_hint" to "열기: Glyph Interface → Flip to Glyph → Always-on Glyph Toy", "language" to "언어", "github" to "GitHub", "donate" to "후원",
        "update_available" to "업데이트 사용 가능", "update_body" to "GitHub에 더 최신 Glyph Lab 빌드가 있습니다.", "download" to "다운로드",
        "downloading" to "다운로드 중", "install" to "설치", "install_permission" to "Glyph Lab의 앱 설치를 허용하면 설치 프로그램이 자동으로 열립니다.",
        "update_error" to "업데이트를 확인하거나 다운로드할 수 없습니다.", "effect_wireframe" to "3D 도형", "effect_fire" to "불꽃",
        "effect_gravity" to "모래", "effect_plasma" to "플라즈마", "effect_starfield" to "별", "effect_clock" to "시계",
        "solid_cube" to "정육면체", "solid_tetra" to "사면체", "solid_octa" to "팔면체", "solid_pyramid" to "피라미드"
    )

    private val all = mapOf(
        AppLanguage.EN to en, AppLanguage.RU to ru, AppLanguage.DE to de, AppLanguage.FR to fr,
        AppLanguage.PL to pl, AppLanguage.UA to ua, AppLanguage.HI to hindi, AppLanguage.CH to zh,
        AppLanguage.JP to ja, AppLanguage.KO to ko
    )

    fun text(language: AppLanguage, key: String): String = all[language]?.get(key) ?: en[key] ?: key
}

fun AppLanguage.t(key: String): String = UiStrings.text(this, key)