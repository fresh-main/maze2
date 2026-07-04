package com.otbor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otbor.OtborSounds;
import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Финальные титры — «детективная доска».
 *
 * Каждый чанк прилетает на доску как отдельная записка В СЛУЧАЙНОМ МЕСТЕ
 * (с проверкой непересечения с уже висящими). Записки НЕ стираются и НЕ
 * фейдятся — они накапливаются на доске. Связь между ними рисуется красной
 * ниткой от pin к pin.
 *
 * Цикл per chunk: ARRIVAL → PIN → TAPE → WRITING → STAMP → STRING → HOLD → next.
 * Erase отсутствует. Финальный чанк — крупный, с диагональным «ДЕЛО ЗАКРЫТО».
 * После финала всё стоит на доске + общий fade-out.
 *
 * Нельзя закрыть: ESC, клавиши, клики — всё глотается.
 */
public class OtborCreditsScreen extends Screen {

    // ====== Контент ======
    //
    // Финальный массив чанков собирается динамически в конструкторе:
    //   chunks = INTRO_CHUNK + POEM_CHUNKS + FINAL_CHUNK
    //
    // Количество чанков, индекс финала, hold-таймы — всё высчитывается из
    // chunks.length. Чтобы поменять стих — правишь только POEM_CHUNKS.

    /** Стартовый чанк — заголовок дела. */
    private static final String[] INTRO_CHUNK = {
            "О.Т.Б.О.Р", "БЕГУЩИЙ В ЛАБИРИНТЕ", "ДЕЛО №047", "ЗАКРЫТО"
    };

    /** Финальный чанк — закрытие. */
    private static final String[] FINAL_CHUNK = {
            "◆",
            "К  О  Н  Е  Ц",
            "спасибо, что прошёл",
            "лабиринт"
    };

    /** Кредитный чанк — конкретный участник: 2 строки (nickname, role). */
    private static final String[][] CREDIT_CHUNKS = {
            {"MoSber",       "Прораб, главный строитель, тот кто вёл всех к стенам"},
            {"R_DuRAcK",     "Строитель, чертил чертежи, выставлял нулевой сектор"},
            {"Fresh__main",  "Мододел, создатель гриверов"},
            {"Yan4ik",       "Строитель, обустраивал локации"},
            {"Teljoran",     "Строил лианы"},
            {"Older",        "Строил лианы и кусты"},
            {"Relik",        "Строитель"},
            {"Ness",         "Начальник, сценарист, планировщик"},
            {"Ruguar",       "Украшал лабиринт и больницу"},
            {"Тери",         "Сценарист, автор идеи, изготовитель моделей, лоровед"},
            {"мирфа",        "Начальство, главный строитель"},
            {"AriyNex",      "Билдер, тестировщик"},
            {"Yonkof",       "Делал лабиринт, лианы, декорил"},
            {"DENISPLAY36",  "Строитель"},
            {"ТУРИСТИШЬ!",   "Физик-кодер, сделал интерфейс"}
    };

    /** Имена файлов фото в textures/credits_photos/ — параллельно CREDIT_CHUNKS. */
    private static final String[] CREDIT_PHOTOS = {
            "mosber.png", "r_durack.png", "fresh__main.png", "yan4ik.png",
            "teljoran.png", "older.png", "relik.png", "ness.png",
            "ruguar.png", "teri.png", "mirfa.png", "ariynex.png",
            "yonkof.png", "denisplay36.png", "turistish.png"
    };

    /** Тело: стих. Можно добавлять/убирать строфы без правки логики. */
    private static final String[][] POEM_CHUNKS = {
            {"Лабиринт стоит веками.",
                    "Каждый день — другой проход.",
                    "В каждом сердце — свой страх.",
                    "В каждом крике — свой исход."},
            {"Кто-то стал просто стеной,",
                    "Кто-то — именем в реестре.",
                    "А кто-то прошёл насквозь",
                    "и услышал: «Ты — последний»."},
            {"Бегущий знает: всё проходит.",
                    "Стены — лишь черта на мел.",
                    "Двери — это просто двери.",
                    "Главное — кто их посмел."},
            {"Помни тех, кто не вернулся.",
                    "Помни лица. Помни даты.",
                    "В этом доме без углов",
                    "ты единственная карта."},
            {"Когда закончится дорога",
                    "и стена пойдёт к стене —",
                    "вспомни: ты был здесь не зря.",
                    "Ты дошёл. И этого достаточно."}
    };

    /** Реальный список чанков — собирается в конструкторе. */
    private final String[][] chunks;
    private final int introIndex;
    private final int finalIndex;

    private static final String[] STAMP_TEXTS = {
            "СЕКРЕТНО", "ИЗЪЯТО", "ПРОВЕРЕНО", "АРХИВ", "СЕКТОР C-3", "ДОПУСК"
    };
    /**
     * Short variants для маленьких credit-карточек — full text не помещается
     * в правый нижний угол card-rect 22×10 даже на scale 1.0.
     */
    private static final String[] STAMP_TEXTS_SHORT = {
            "СЕКРЕТ", "ИЗЪЯТО", "ПРОВЕР.", "АРХИВ", "С C-3", "ДОПУСК"
    };
    /** Safe-area margin от края карточки, в пикселях card-local space. */
    private static final int STAMP_MARGIN_PX = 4;

    // ====== Тайминги ======
    //
    // Per-chunk timeline:
    //   CENTER_APPEAR_MS  → карточка крупно появляется в центре экрана
    //   WRITING           → текст пишется маркером ПО ЦЕНТРУ ЭКРАНА (не на доске)
    //   POST_WRITE_HOLD_MS → пауза с заполненной карточкой по центру
    //   FLY_TO_BOARD_MS   → карточка летит из центра к своему месту на доске
    //                       (scale 1.55 → 1.0, rot 0 → final, pos screenC → boardC)
    //   PIN_POP_MS        → pin pop-in (после посадки)
    //   TAPE_FADE_MS      → tape fade-in (после посадки)
    //   STAMP_POP_MS      → stamp pop-in
    //   STRING_DRAW_MS    → красная нитка от прошлого pin к нашему,
    //                       с автоматическим обходом всех других карточек
    //   HOLD_DEFAULT_MS   → пауза → next chunk

    private static final long CENTER_APPEAR_MS = 260;
    private static final long POST_WRITE_HOLD_MS = 450;
    private static final long FLY_TO_BOARD_MS = 700;
    private static final long PIN_POP_MS = 220;
    private static final long TAPE_FADE_MS = 240;
    private static final long DRAW_CHAR_MS = 38;
    private static final long CHAR_POP_MS = 130;
    private static final long STAMP_POP_MS = 240;
    private static final long STRING_DELAY_AFTER_PIN = 90;
    private static final long STRING_DRAW_MS = 520;

    private static final long HOLD_FIRST_MS = 2200;
    private static final long HOLD_DEFAULT_MS = 4200;
    private static final long HOLD_FINAL_MS = 11000;

    /** Scale, до которого «надувается» карточка по центру экрана во время writing. */
    private static final float CENTER_SCALE = 1.55f;

    private static final long FADE_OUT_MS = 1500;
    private static final long POST_FADE_LINGER_MS = 600;
    /**
     * Интро-«затемнение»: первые 750 мс мир ТЕМНЕЕТ (0→255 alpha чёрного),
     * следующие 750 мс мир СВЕТЛЕЕТ (255→0). Доска и карточки не рисуются
     * во время всей этой фазы. После — мгновенно начинается chunk 0.
     */
    private static final long INTRO_BLACKOUT_MS = 1500;
    private static final long BLACKOUT_DARKEN_MS = 750;
    private static final long BLACKOUT_LIGHTEN_MS = 750;

    // ====== Стиль письма ======

    private static final float TEXT_SCALE = 0.95f;
    private static final int LINE_SPACING_PX = 14;
    private static final float CHAR_JITTER_Y_PX = 1.0f;
    private static final float CHAR_JITTER_ROT_DEG = 2.0f;
    private static final float CHAR_JITTER_SCALE = 0.05f;

    /** Минимальный зазор между карточками при random-placement. */
    private static final int CARD_MIN_GAP = 16;
    /** Зазор для маленьких credit-карточек (между собой) — теснее, доска переполнена. */
    private static final int CREDIT_CARD_MIN_GAP = 6;
    /** Сколько попыток случайного размещения перед сдачей. */
    private static final int PLACEMENT_ATTEMPTS = 160;

    // ====== Доска (пробковая, в центре экрана) ======
    /** Доля экрана которую занимает доска. */
    private static final float BOARD_WIDTH_FRAC = 0.82f;
    private static final float BOARD_HEIGHT_FRAC = 0.78f;
    /** Толщина деревянной рамы доски. */
    private static final int BOARD_FRAME_PX = 12;
    /** Внутренний отступ между рамой и областью карточек. */
    private static final int BOARD_INNER_MARGIN = 12;

    // Координаты доски — кэшируются в init().
    private int boardX, boardY, boardW, boardH;

    // Предвычисленный декор доски (cork dots + wood fibers) — упаковано как
    // int (x<<16)|y и int[] цветов параллельным массивом. Cтроим РАЗ в init(),
    // в render() просто проход по массиву → дешёвые fill-ы без Random/прочих
    // вычислений. Это решает FPS-просадку (раньше каждый кадр генерировался
    // ~19000 случайных точек).
    private int[] cachedDotX, cachedDotY, cachedDotW, cachedDotH;
    private int[] cachedDotColor;
    private int[] cachedFibX, cachedFibY, cachedFibLen;
    private int[] cachedFibColor;

    /** Кастомный sound instance для фоновой темы — поддерживает плавный fade-out. */
    @org.jetbrains.annotations.Nullable
    private CreditsMusicInstance musicInstance;
    /** Когда триггерить начало fade-out фоновой музыки. */
    private long musicFadeAtMs = Long.MAX_VALUE;

    /**
     * SoundEvent создаётся напрямую через ResourceLocation, БЕЗ обращения к
     * DeferredRegister/RegistryObject. Причина: при подключении к серверу,
     * который не имеет этого мода, Forge синхронизирует registries и СТИРАЕТ
     * client-side sound-entries, не присутствующие на сервере →
     * {@code RegistryObject.get()} начинает кидать {@code NullPointerException:
     * Registry Object not present: otbor:credits_theme}. Sound manager же сам
     * по себе резолвит .ogg по ResourceLocation из sounds.json, регистрация
     * SoundEvent в registry для воспроизведения через SoundManager НЕ нужна.
     */
    private static final SoundEvent CREDITS_THEME_EVENT =
            SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath("otbor", "credits_theme"));

    // ====== State ======

    private CardLayout[] layouts;
    /** Предвычисленные пути красной нити i-1 → i, c обходом всех других карточек. */
    private java.util.List<java.util.List<int[]>> stringPaths;
    private int chunkIndex = 0;
    private long phaseStartMs = 0L;
    /** Момент открытия экрана — относительно него считаем интро-«затемнение». */
    private long introStartMs = 0L;
    /** Стали ли уже титры идти (chunk 0 начат). Используется чтобы phaseStartMs выставился ровно один раз. */
    private boolean introTitlesStarted = false;
    private int drawSoundCounter = 0;
    private boolean playedPinSound = false;
    private boolean playedStampSound = false;
    private long allDoneAtMs = 0L;

    /** ResourceLocation фото для каждого чанка (null если не credit-чанк). Параллельно chunks. */
    private final ResourceLocation[] chunkPhotos;
    /** Является ли чанк "credit-карточкой" (для рендера: с фото, мельче). */
    private final boolean[] isCreditChunk;
    private final int firstCreditIdx;
    private final int lastCreditIdx;

    public OtborCreditsScreen() {
        super(Component.literal("ТИТРЫ"));
        // Динамическая сборка финального списка чанков:
        //   0 → INTRO
        //   1..len(POEM) → стих
        //   далее → CREDIT_CHUNKS (15 шт.)
        //   последний → FINAL
        List<String[]> all = new ArrayList<>();
        List<ResourceLocation> photos = new ArrayList<>();
        List<Boolean> isCredit = new ArrayList<>();

        all.add(INTRO_CHUNK);
        photos.add(null);
        isCredit.add(false);

        // POEM_CHUNKS оставлены как dead-data (компилируются, но не используются)
        // — пользователь убрал стихотворные карточки, в финальной сборке только
        // интро + кредитные карточки + финал. Восстановить можно одной строкой.

        int firstCredit = all.size();
        for (int i = 0; i < CREDIT_CHUNKS.length; i++) {
            all.add(CREDIT_CHUNKS[i]);
            photos.add(ResourceLocation.fromNamespaceAndPath(
                    "otbor", "textures/credits_photos/" + CREDIT_PHOTOS[i]));
            isCredit.add(true);
        }
        int lastCredit = all.size() - 1;

        all.add(FINAL_CHUNK);
        photos.add(null);
        isCredit.add(false);

        chunks = all.toArray(new String[0][]);
        chunkPhotos = photos.toArray(new ResourceLocation[0]);
        isCreditChunk = new boolean[isCredit.size()];
        for (int i = 0; i < isCredit.size(); i++) isCreditChunk[i] = isCredit.get(i);
        introIndex = 0;
        finalIndex = chunks.length - 1;
        firstCreditIdx = firstCredit;
        lastCreditIdx = lastCredit;
    }

    @Override
    protected void init() {
        super.init();
        // Геометрия доски: центр экрана, BOARD_WIDTH_FRAC × BOARD_HEIGHT_FRAC.
        boardW = (int) (this.width * BOARD_WIDTH_FRAC);
        boardH = (int) (this.height * BOARD_HEIGHT_FRAC);
        boardX = (this.width - boardW) / 2;
        boardY = (this.height - boardH) / 2;

        precomputeBoardDecor();

        // Каждая сессия — свой random.
        Random rnd = new Random();
        layouts = placeCards(rnd);
        // Пути красных ниток предвычислены ОДИН раз — потом дешёво анимируются прогрессом.
        // Для финальной карточки нити нет (она парит в центре экрана, не крепится к доске).
        stringPaths = new ArrayList<>();
        stringPaths.add(null);
        for (int i = 1; i < chunks.length; i++) {
            if (i == finalIndex) { stringPaths.add(null); continue; }
            stringPaths.add(routePath(i - 1, i));
        }

        long nowInit = System.currentTimeMillis();
        introStartMs = nowInit;
        introTitlesStarted = true;  // chunk-таймер запускаем сразу с фазы B (под фейдом)
        // Chunk 0 стартует НА СЕРЕДИНЕ блекаута — в момент когда мир начинает разсветляться.
        // Так пока чёрный fade-out идёт, доска уже рендерится сзади, и к моменту полного
        // разсветления intro-карточка уже на ~750мс пути writing'а → нет ощущения паузы.
        phaseStartMs = nowInit + BLACKOUT_DARKEN_MS;
        chunkIndex = 0;
        drawSoundCounter = 0;
        playedPinSound = false;
        playedStampSound = false;
        allDoneAtMs = 0L;
        musicFadeAtMs = Long.MAX_VALUE;
        playSound(OtborSounds.PAGE_FLIP.get(), 1.0f, 0.6f);

        // Фоновая тема — играем громкую stream-музыку. Если файл повреждён
        // или sound engine не доступен — игнорируем (try/catch внутри).
        startCreditsMusic();
    }

    private void startCreditsMusic() {
        try {
            // CREDITS_THEME_EVENT — статический SoundEvent без registry.
            // Sound manager сам резолвит .ogg через sounds.json по location.
            CreditsMusicInstance custom = new CreditsMusicInstance(CREDITS_THEME_EVENT);
            Minecraft.getInstance().getSoundManager().play(custom);
            musicInstance = custom;
            com.labyrinthmod.LabyrinthMod.LOGGER.info("[otbor] credits theme started: {} (custom instance)",
                    CREDITS_THEME_EVENT.getLocation());
        } catch (Throwable t) {
            com.labyrinthmod.LabyrinthMod.LOGGER.warn(
                    "[otbor] custom music instance failed, falling back to SimpleSoundInstance", t);
            musicInstance = null;
            try {
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(CREDITS_THEME_EVENT, 1.0f, 1.0f));
                com.labyrinthmod.LabyrinthMod.LOGGER.info("[otbor] credits theme started: {} (fallback simple)",
                        CREDITS_THEME_EVENT.getLocation());
            } catch (Throwable t2) {
                com.labyrinthmod.LabyrinthMod.LOGGER.warn("[otbor] fallback simple instance also failed", t2);
            }
        }
    }

    /**
     * Кэшируем точки пробки и полосы дерева — генерируем один раз и потом
     * рисуем без Random в render-кадре. Раньше Random в render тратил
     * заметные миллисекунды и просаживал FPS.
     */
    private void precomputeBoardDecor() {
        int frame = BOARD_FRAME_PX;
        int ix = boardX + frame;
        int iy = boardY + frame;
        int iw = boardW - frame * 2;
        int ih = boardH - frame * 2;

        // Cork dots: density /600 (≈ 2250 точек, было /70 ≈ 19000). Визуально
        // разница минимальна (точки и так псевдо-шум), нагрузка ×8 меньше.
        int spotCount = Math.max(150, (iw * ih) / 600);
        cachedDotX = new int[spotCount];
        cachedDotY = new int[spotCount];
        cachedDotW = new int[spotCount];
        cachedDotH = new int[spotCount];
        cachedDotColor = new int[spotCount];

        int corkDark = 0xFF8A6440;
        int corkLight = 0xFFC9A07A;
        Random rnd = new Random(0xC0BB1234L);
        for (int i = 0; i < spotCount; i++) {
            cachedDotX[i] = ix + rnd.nextInt(iw);
            cachedDotY[i] = iy + rnd.nextInt(ih);
            int sz = rnd.nextInt(4) == 0 ? 2 : 1;
            cachedDotW[i] = sz;
            cachedDotH[i] = sz;
            int v = rnd.nextInt(5);
            int col;
            if (v == 0) col = (corkDark & 0x00FFFFFF) | 0x80000000;
            else if (v == 1) col = (corkLight & 0x00FFFFFF) | 0x60000000;
            else if (v == 2) col = 0x40000000;
            else col = (corkDark & 0x00FFFFFF) | 0x30000000;
            cachedDotColor[i] = col;
        }

        // Wood fibers вдоль рамы: уменьшено с 60 до 20.
        int fiberCount = 20;
        cachedFibX = new int[fiberCount];
        cachedFibY = new int[fiberCount];
        cachedFibLen = new int[fiberCount];
        cachedFibColor = new int[fiberCount];
        int frameDark = 0xFF2A1A0E;
        int frameLight = 0xFF6B4226;
        Random frnd = new Random(0xD00DBE3FL);
        for (int i = 0; i < fiberCount; i++) {
            int fx, fy;
            int attempts = 0;
            do {
                fx = boardX + 2 + frnd.nextInt(boardW - 4);
                fy = boardY + 2 + frnd.nextInt(boardH - 4);
                attempts++;
            } while (attempts < 8
                    && fx > boardX + frame && fx < boardX + boardW - frame
                    && fy > boardY + frame && fy < boardY + boardH - frame);
            cachedFibX[i] = fx;
            cachedFibY[i] = fy;
            cachedFibLen[i] = 4 + frnd.nextInt(8);
            int col = frnd.nextBoolean() ? frameDark : frameLight;
            cachedFibColor[i] = (col & 0x00FFFFFF) | 0x80000000;
        }
    }

    /**
     * Размещает карточки на доске:
     *  • intro (idx=0) — ВСЕГДА в правом верхнем углу доски, как «название дела»
     *  • final          — в центре доски, доминирует визуально
     *  • остальные      — случайно, без пересечений и без наезда на intro/final
     */
    private CardLayout[] placeCards(Random rnd) {
        CardLayout[] result = new CardLayout[chunks.length];
        List<int[]> placed = new ArrayList<>();
        int inner = BOARD_FRAME_PX + BOARD_INNER_MARGIN;
        int areaXMin = boardX + inner;
        int areaYMin = boardY + inner;
        int areaXMax = boardX + boardW - inner;
        int areaYMax = boardY + boardH - inner;

        // ===== INTRO в правом верхнем углу =====
        int introW = 200;
        int introH = 108;
        int introX = areaXMax - introW - 2;
        int introY = areaYMin + 2;
        placed.add(new int[]{introX, introY, introW, introH});
        result[introIndex] = buildLayout(introIndex, introX, introY, introW, introH,
                rnd, false, true);

        // ===== FINAL — НЕ КРЕПИТСЯ К ДОСКЕ.
        // Layout-координаты неважны: рендер игнорирует L.x/L.y и всегда рисует
        // её по центру ЭКРАНА. Ставим её как «незанятый прямоугольник» в углу
        // чтобы overlapsAny её не учитывал — но любое placement подойдёт.
        int finalW = 220;
        int finalH = 130;
        int finalX = areaXMin;  // dummy, render override-ит
        int finalY = areaYMin;
        result[finalIndex] = buildLayout(finalIndex, finalX, finalY, finalW, finalH,
                rnd, true, false);
        // НЕ добавляем в placed — final не претендует на место на доске.

        // ===== Остальные — случайные НЕПЕРЕСЕКАЮЩИЕСЯ места =====
        // v1.5.2: Strict non-overlap. Размер credit-карточек начинается с
        // дефолтных значений и УМЕНЬШАЕТСЯ итеративно если placement не
        // сходится за PLACEMENT_ATTEMPTS. Шаг шринка — 5%, нижний предел —
        // 130×60. Intro и final не шринкуются. POEM_CHUNKS сейчас пустой
        // (стихи отключены) — поэтому шринк по сути касается только credit-карт.
        int poemW = 205, poemH = 110;
        int creditWNoPhotoStart = 170, creditHNoPhotoStart = 75;
        int creditWPhotoStart = 180, creditHPhotoStart = 85;
        int minCreditW = 130;
        int minCreditH = 60;

        // Iterative shrink: stop когда ВСЕ карточки помещаются без overlap.
        // Каждая итерация полностью пере-генерирует positions (placed list).
        int iterations = 0;
        int maxIterations = 30;
        int[][] placedRectsFinal = null;
        int creditWNoPhoto = creditWNoPhotoStart;
        int creditHNoPhoto = creditHNoPhotoStart;
        int creditWPhoto = creditWPhotoStart;
        int creditHPhoto = creditHPhotoStart;
        outer:
        while (iterations++ < maxIterations) {
            // Re-init: оставляем intro placed, сбрасываем все остальные.
            java.util.List<int[]> tmpPlaced = new ArrayList<>();
            tmpPlaced.add(placed.get(0));  // intro stays
            int[][] rects = new int[chunks.length][];
            boolean allOk = true;

            for (int idx = 0; idx < chunks.length; idx++) {
                if (idx == introIndex || idx == finalIndex) continue;
                boolean credit = isCreditChunk[idx];
                int cardW, cardH;
                if (credit) {
                    boolean hasPhoto = chunkPhotos[idx] != null;
                    cardW = hasPhoto ? creditWPhoto : creditWNoPhoto;
                    cardH = hasPhoto ? creditHPhoto : creditHNoPhoto;
                } else {
                    cardW = poemW;
                    cardH = poemH;
                }
                int gap = credit ? CREDIT_CARD_MIN_GAP : CARD_MIN_GAP;
                int bestX = 0, bestY = 0;
                boolean fit = false;
                int attempts = 0;
                while (attempts++ <= PLACEMENT_ATTEMPTS) {
                    int xMax = areaXMax - cardW;
                    int yMax = areaYMax - cardH;
                    if (xMax <= areaXMin) xMax = areaXMin + 1;
                    if (yMax <= areaYMin) yMax = areaYMin + 1;
                    int x = areaXMin + rnd.nextInt(xMax - areaXMin);
                    int y = areaYMin + rnd.nextInt(yMax - areaYMin);
                    if (!overlapsAnyWithGap(x, y, cardW, cardH, tmpPlaced, gap)) {
                        bestX = x; bestY = y; fit = true;
                        break;
                    }
                }
                if (!fit) {
                    allOk = false;
                    break;  // эта итерация провалена → шринкаем и retry
                }
                tmpPlaced.add(new int[]{bestX, bestY, cardW, cardH});
                rects[idx] = new int[]{bestX, bestY, cardW, cardH};
            }

            if (allOk) {
                // Success! зафиксируем результаты этой итерации.
                placedRectsFinal = rects;
                placed = tmpPlaced;
                com.labyrinthmod.LabyrinthMod.LOGGER.info(
                        "[otbor] credits placement OK after {} shrink iterations, " +
                        "creditCard photo={}×{} no-photo={}×{}",
                        iterations, creditWPhoto, creditHPhoto,
                        creditWNoPhoto, creditHNoPhoto);
                break outer;
            }

            // Шринк credit-карт на 5%. Intro/final/poem не трогаем.
            int newCWP = (int) Math.max(minCreditW, Math.floor(creditWPhoto * 0.95f));
            int newCHP = (int) Math.max(minCreditH, Math.floor(creditHPhoto * 0.95f));
            int newCWN = (int) Math.max(minCreditW, Math.floor(creditWNoPhoto * 0.95f));
            int newCHN = (int) Math.max(minCreditH, Math.floor(creditHNoPhoto * 0.95f));
            // Если уже на лимите И не помогло — выходим (примем легкий overlap).
            if (newCWP == creditWPhoto && newCHP == creditHPhoto
                    && newCWN == creditWNoPhoto && newCHN == creditHNoPhoto) {
                com.labyrinthmod.LabyrinthMod.LOGGER.warn(
                        "[otbor] credits placement FAILED to fit at minimum " +
                        "{}×{} after {} iterations — accepting last attempt with possible overlap",
                        creditWPhoto, creditHPhoto, iterations);
                placedRectsFinal = rects;
                placed = tmpPlaced;
                break;
            }
            creditWPhoto = newCWP;
            creditHPhoto = newCHP;
            creditWNoPhoto = newCWN;
            creditHNoPhoto = newCHN;
        }

        // Если cycle закончился без success И без rects — fallback (заполняем
        // dummy rects по сетке). Не должно случаться при разумных размерах доски.
        if (placedRectsFinal == null) {
            placedRectsFinal = new int[chunks.length][];
            int gridX = areaXMin, gridY = areaYMin;
            for (int idx = 0; idx < chunks.length; idx++) {
                if (idx == introIndex || idx == finalIndex) continue;
                placedRectsFinal[idx] = new int[]{gridX, gridY, creditWPhoto, creditHPhoto};
                gridX += creditWPhoto + 4;
                if (gridX + creditWPhoto > areaXMax) {
                    gridX = areaXMin;
                    gridY += creditHPhoto + 4;
                }
            }
        }

        // Build layouts из финальных rects. Если для какого-то idx rect=null
        // (failed iteration не успел дойти до этой карточки) — кладём в верхний
        // левый угол с возможным overlap. Лучше overlap чем NPE.
        int fallbackGridX = areaXMin;
        int fallbackGridY = areaYMin;
        for (int idx = 0; idx < chunks.length; idx++) {
            if (idx == introIndex || idx == finalIndex) continue;
            int[] r = placedRectsFinal[idx];
            if (r == null) {
                r = new int[]{fallbackGridX, fallbackGridY, creditWPhoto, creditHPhoto};
                fallbackGridX += creditWPhoto + 4;
                if (fallbackGridX + creditWPhoto > areaXMax) {
                    fallbackGridX = areaXMin;
                    fallbackGridY += creditHPhoto + 4;
                }
            }
            result[idx] = buildLayout(idx, r[0], r[1], r[2], r[3], rnd, false, false);
        }
        return result;
    }

    private static boolean overlapsAny(int x, int y, int w, int h, List<int[]> placed) {
        return overlapsAnyWithGap(x, y, w, h, placed, CARD_MIN_GAP);
    }

    private static boolean overlapsAnyWithGap(int x, int y, int w, int h, List<int[]> placed, int gap) {
        for (int[] r : placed) {
            if (x < r[0] + r[2] + gap
                    && x + w + gap > r[0]
                    && y < r[1] + r[3] + gap
                    && y + h + gap > r[1]) {
                return true;
            }
        }
        return false;
    }

    /** Возвращает максимальную долю перекрытия с любой уже размещённой картой (0..1). */
    private static double maxOverlapFrac(int x, int y, int w, int h, List<int[]> placed) {
        double area = (double) w * h;
        double maxFrac = 0;
        for (int[] r : placed) {
            int ix0 = Math.max(x, r[0]);
            int iy0 = Math.max(y, r[1]);
            int ix1 = Math.min(x + w, r[0] + r[2]);
            int iy1 = Math.min(y + h, r[1] + r[3]);
            if (ix1 > ix0 && iy1 > iy0) {
                double overlap = (ix1 - ix0) * (iy1 - iy0);
                double frac = overlap / area;
                if (frac > maxFrac) maxFrac = frac;
            }
        }
        return maxFrac;
    }

    private CardLayout buildLayout(int idx, int x, int y, int w, int h, Random rnd,
                                    boolean isFinal, boolean isIntro) {
        // Intro слегка повернут (-3°), final ровный, остальные ±6°.
        float rot;
        if (isFinal)      rot = 0f;
        else if (isIntro) rot = -3f;
        else              rot = (-6f + rnd.nextFloat() * 12f);

        // Pin = красная кнопка В правом верхнем углу карточки (так нитка
        // визуально цепляется ВСЕГДА за угол, не «втыкается в пустоту»).
        // Для финальной — pin сверху-слева ради разнообразия.
        int pinX = isFinal ? x + 12 : x + w - 12;
        int pinY = y + 12;
        boolean pinIsRed = true;  // все pin'ы красные — это «кнопка», к которой идёт нить

        // Текст — в ВЕРХНЕЙ 65% карточки, штамп — в нижних 35% (НЕ перекрывает).
        int textH = chunks[idx].length * LINE_SPACING_PX;
        int textAreaH = (int) (h * 0.65f);
        int textCx = x + w / 2;
        int textTop = y + textAreaH / 2 - textH / 2 + 2;
        int textBottom = textTop + textH;

        String stampText;
        float stampRot;
        boolean stampRound;
        int stampX, stampY;
        // Базовая дополнительная шкала ВНЕ pop-in анимации — то что в renderStamp
        // умножалось на «idx == finalIndex ? 1.8 : 1.0». Теперь высчитывается из
        // requirement-фита: bbox после rotation+scale должен быть в (0..w, 0..h)
        // карточки с MARGIN = STAMP_MARGIN_PX. Финальный штамп начинается с 1.5
        // (вместо 1.8) и шринкуется ниже если ещё мала.
        float stampScaleBase;

        boolean isCredit = idx >= 0 && idx < isCreditChunk.length && isCreditChunk[idx];
        if (isFinal) {
            // Final — крупный диагональный штамп под текстом.
            stampText = "ДЕЛО ЗАКРЫТО";
            stampRot = -12f;
            stampRound = false;
            stampX = textCx;
            stampY = y + textAreaH + (h - textAreaH) / 2;
            stampScaleBase = 1.5f;
        } else if (isCredit) {
            // Credit-карточка — крошечный угловой штамп, чтобы не толкать фото/имя.
            // Используем abbreviated text — full text не помещается в right-bottom
            // corner на маленькой credit-карте, даже без rotation.
            int sIdx = rnd.nextInt(STAMP_TEXTS_SHORT.length);
            stampText = STAMP_TEXTS_SHORT[sIdx];
            stampRot = -8f + rnd.nextFloat() * 16f;
            stampRound = false;
            // В правом нижнем углу карточки.
            stampX = x + w - 22;
            stampY = y + h - 10;
            stampScaleBase = 1f;
        } else {
            stampText = STAMP_TEXTS[rnd.nextInt(STAMP_TEXTS.length)];
            stampRot = -10f + rnd.nextFloat() * 20f;
            // Round-стampы заменены rect'ами — round не помещался в нижнюю полосу
            // карточки без overflow текста-надписи за пределы круга → визуально
            // он залезал на текст записки. Rect-stamp ALWAYS укладывается ровно.
            stampRound = false;
            int bandTop = y + textAreaH + 2;
            int bandBottom = y + h - 3;
            stampY = (bandTop + bandBottom) / 2;
            boolean stampLeft = rnd.nextBoolean();
            stampX = stampLeft ? (x + 38) : (x + w - 38);
            stampScaleBase = 1f;
        }

        // ===== Расчёт фит-шкалы штампа: bbox после rotation+scale должен =====
        // ===== целиком сидеть в (x+MARGIN..x+w-MARGIN, y+MARGIN..y+h-MARGIN) =====
        // Stamp rect (без rotation/scale) — это PaperRender.drawRectStamp:
        //   half-width  = (font.width(text) + 14) / 2
        //   half-height = 8 (h=16)
        // Центр в (stampX, stampY) в card-coords.
        // После scale s и rotation θ корнер (±hw, ±hh) проецируется в:
        //   px = s*( hw*cosθ - hh*sinθ )
        //   py = s*( hw*sinθ + hh*cosθ )
        // Bbox в card-local: stampX±|px|max, stampY±|py|max.
        float stampScale = stampScaleBase;
        if (this.font != null && stampText != null && !stampText.isEmpty()) {
            float hw = (this.font.width(stampText) + 14) * 0.5f;
            float hh = 8f;
            double rad = Math.toRadians(stampRot);
            double cos = Math.abs(Math.cos(rad));
            double sin = Math.abs(Math.sin(rad));
            float halfBboxW = (float) (hw * cos + hh * sin);
            float halfBboxH = (float) (hw * sin + hh * cos);
            // Доступные расстояния от центра штампа до краёв карточки.
            float leftRoom   = (stampX - x) - STAMP_MARGIN_PX;
            float rightRoom  = (x + w - stampX) - STAMP_MARGIN_PX;
            float topRoom    = (stampY - y) - STAMP_MARGIN_PX;
            float bottomRoom = (y + h - stampY) - STAMP_MARGIN_PX;
            float roomX = Math.max(1f, Math.min(leftRoom, rightRoom));
            float roomY = Math.max(1f, Math.min(topRoom, bottomRoom));
            float fitX = roomX / halfBboxW;
            float fitY = roomY / halfBboxH;
            float fit = Math.min(fitX, fitY);
            if (fit < stampScale) stampScale = fit;
            if (stampScale < 0.5f) stampScale = 0.5f;  // нижний предел читаемости
        }

        return new CardLayout(x, y, w, h, rot, 0,
                pinX, pinY, pinIsRed, stampText, stampX, stampY, stampRot, stampRound,
                stampScale);
    }

    // ====== Блокировка закрытия ======
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean keyPressed(int k, int s, int m) { return true; }
    @Override public boolean charTyped(char c, int m) { return true; }
    @Override public boolean mouseClicked(double a, double b, int c) { return true; }
    @Override public boolean mouseReleased(double a, double b, int c) { return true; }
    @Override public boolean mouseDragged(double a, double b, int c, double d, double e) { return true; }
    @Override public boolean mouseScrolled(double a, double b, double c) { return true; }
    @Override public boolean isPauseScreen() { return false; }

    // ====== Render ======

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        // ===== Интро-«затемнение» =====
        //   фаза A (0..750ms): alpha 0 → 255 (мир темнеет)
        //   фаза B (750..1500ms): alpha 255 → 0 (мир светлеет — игрок видит FPV-мир)
        //   фаза C (>=1500ms): мгновенно начинаем chunk 0
        // Во время A и B НИЧЕГО кроме чёрной заливки не рисуется (board, карточки и т.п. — нет).
        long introElapsed = now - introStartMs;
        // Phase A (0..750ms): только чёрный overlay alpha 0→255, доска НЕ рисуется (мир затемняется).
        // Phase B (750..1500ms): доска и chunk 0 УЖЕ рендерятся под фейдящим overlay alpha 255→0
        //   (chunk 0 стартовал в начале phase B т.к. phaseStartMs = introStartMs + BLACKOUT_DARKEN_MS).
        //   Так пользователь видит "разсветление мира НА доску с уже идущими титрами", без паузы.
        // Phase C (>=1500ms): normal render.
        if (introElapsed < BLACKOUT_DARKEN_MS) {
            int alpha = (int) (introElapsed * 255L / BLACKOUT_DARKEN_MS);
            gfx.fill(0, 0, this.width, this.height, (Math.max(0, Math.min(255, alpha)) & 0xFF) << 24);
            return;  // фаза A: ничего кроме чёрной заливки над FPV
        }
        // Тёмная «стена» вокруг доски.
        PaperRender.drawBoardBackground(gfx, this.width, this.height);
        // Сама доска — рама + пробка + винты.
        renderBoard(gfx);
        renderCornerDecor(gfx, now);

        if (allDoneAtMs == 0L) {
            // Прошлые карточки — финальное состояние на доске.
            for (int i = 0; i < chunkIndex; i++) {
                renderCard(gfx, layouts[i], i, Long.MAX_VALUE, now, true);
                if (i > 0) renderStringPath(gfx, stringPaths.get(i), 1f, 0.85f);
            }
            // Текущая карточка в активной фазе.
            long elapsed = now - phaseStartMs;
            renderCard(gfx, layouts[chunkIndex], chunkIndex, elapsed, now, false);
            // Нитка от прошлой карточки к текущей рисуется ТОЛЬКО когда текущая
            // уже села на доску (после fly + pin start) — иначе она бы тянулась
            // к карточке висящей в центре экрана.
            if (chunkIndex > 0) {
                long flyEnd = flyEndT(chunkIndex);
                long stringStartT = flyEnd + STRING_DELAY_AFTER_PIN;
                if (elapsed >= stringStartT) {
                    float stringP = Mth.clamp((elapsed - stringStartT) / (float) STRING_DRAW_MS, 0f, 1f);
                    renderStringPath(gfx, stringPaths.get(chunkIndex), stringP, 0.85f);
                }
            }
            advancePhase(elapsed, now);
        } else {
            // Финальный fade-out — все карточки на доске.
            for (int i = 0; i < chunks.length; i++) {
                renderCard(gfx, layouts[i], i, Long.MAX_VALUE, now, true);
                if (i > 0) renderStringPath(gfx, stringPaths.get(i), 1f, 0.85f);
            }
            long fadeElapsed = now - allDoneAtMs;
            int alpha = (int) Math.min(255, fadeElapsed * 255L / FADE_OUT_MS);
            gfx.fill(0, 0, this.width, this.height, alpha << 24);
            if (fadeElapsed >= FADE_OUT_MS + POST_FADE_LINGER_MS) {
                this.minecraft.setScreen(null);
                return;
            }
        }
        // Phase B (750..1500ms): доска уже отрисована выше, теперь поверх неё фейдим
        // overlay alpha 255→0. К концу phase B overlay = 0, доска полностью видна, intro-card
        // уже идёт writing с момента phaseStartMs = introStartMs+BLACKOUT_DARKEN_MS.
        if (introElapsed < INTRO_BLACKOUT_MS) {
            long t2 = introElapsed - BLACKOUT_DARKEN_MS;
            int alpha = (int) Math.max(0L, 255L - t2 * 255L / BLACKOUT_LIGHTEN_MS);
            alpha = Math.max(0, Math.min(255, alpha));
            if (alpha > 0) gfx.fill(0, 0, this.width, this.height, (alpha & 0xFF) << 24);
        }
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    /**
     * 1.5.3: для интро-чанка writing стартует МГНОВЕННО при elapsed=0 (без 260ms
     * пустого CENTER_APPEAR pop-in), чтобы сразу после un-darken пользователь видел
     * как карточка пишется. Для остальных — стандартный pop-in.
     */
    private long writingStartT(int idx) {
        return (idx == introIndex) ? 0L : CENTER_APPEAR_MS;
    }

    /**
     * elapsed-время, когда карточка ПОЛНОСТЬЮ села на свою позицию.
     * Для finalIndex — без FLY-фазы (final остаётся в центре экрана,
     * не крепится к доске).
     */
    private long flyEndT(int idx) {
        int totalChars = totalCharsIn(chunks[idx]);
        long base = writingStartT(idx) + (long) totalChars * DRAW_CHAR_MS + POST_WRITE_HOLD_MS;
        return (idx == finalIndex) ? base : base + FLY_TO_BOARD_MS;
    }

    private void advancePhase(long elapsed, long now) {
        int totalChars = totalCharsIn(chunks[chunkIndex]);
        boolean isFinal = chunkIndex == finalIndex;
        boolean hasString = chunkIndex > 0 && !isFinal;

        long writingStartT = writingStartT(chunkIndex);
        long writingEndT = writingStartT + (long) totalChars * DRAW_CHAR_MS;
        long flyEnd = flyEndT(chunkIndex);
        long pinEnd = flyEnd + PIN_POP_MS;
        long stampStartT = flyEnd + 60;
        long stampEnd = stampStartT + STAMP_POP_MS;
        long stringEnd = flyEnd + STRING_DELAY_AFTER_PIN + STRING_DRAW_MS;
        long allDoneT;
        if (isFinal)         allDoneT = stampEnd;
        else if (hasString)  allDoneT = Math.max(stampEnd, stringEnd);
        else                 allDoneT = Math.max(stampEnd, pinEnd);
        long holdMs = isFinal ? HOLD_FINAL_MS
                : (chunkIndex == introIndex ? HOLD_FIRST_MS : HOLD_DEFAULT_MS);
        long chunkEndT = allDoneT + holdMs;

        // One-shot звуки — pin sound только для не-финальных.
        if (!isFinal && !playedPinSound && elapsed >= flyEnd) {
            playedPinSound = true;
            playSound(SoundEvents.WOOD_PLACE, 1.6f, 0.55f);
            playSound(SoundEvents.WOOD_PLACE, 1.95f, 0.4f);
        }
        if (!playedStampSound && elapsed >= stampStartT) {
            playedStampSound = true;
            playSound(SoundEvents.WOOD_HIT, 0.7f, 0.75f);
        }

        // Звук pencil во время writing (в центре экрана).
        if (elapsed >= writingStartT && elapsed <= writingEndT) {
            int charsRevealed = (int) Math.min(totalChars, (elapsed - writingStartT) / DRAW_CHAR_MS);
            while (drawSoundCounter < charsRevealed) {
                if (drawSoundCounter % 4 == 0) {
                    float pitch = 0.95f + ((drawSoundCounter * 137) % 13) * 0.025f;
                    playSound(OtborSounds.PENCIL.get(), pitch, 0.45f);
                }
                drawSoundCounter++;
            }
        }

        // Fade музыки за ~4.5 сек до конца финального чанка.
        if (isFinal && musicFadeAtMs == Long.MAX_VALUE) {
            musicFadeAtMs = phaseStartMs + chunkEndT - 4500;
        }
        if (musicInstance != null && now >= musicFadeAtMs) {
            musicInstance.triggerFade(now);
        }

        // Переход к следующему чанку.
        if (elapsed >= chunkEndT) {
            chunkIndex++;
            if (chunkIndex >= chunks.length) {
                allDoneAtMs = now;
                if (musicInstance != null) musicInstance.triggerFade(now);
                return;
            }
            phaseStartMs = now;
            drawSoundCounter = 0;
            playedPinSound = false;
            playedStampSound = false;
            playSound(OtborSounds.PAGE_FLIP.get(), 0.95f, 0.55f);
        }
    }

    @Override
    public void removed() {
        // При закрытии экрана глушим музыку (если ещё играет).
        if (musicInstance != null) {
            musicInstance.stopMe();
            musicInstance = null;
        }
        super.removed();
    }

    /**
     * Render одной карточки. elapsed=Long.MAX_VALUE → прошлая карточка
     * (полное состояние на доске, fast-path).
     *
     * Активная карточка проходит фазы:
     *   t < CENTER_APPEAR_MS                                         — pop-in в центре экрана
     *   CENTER_APPEAR..writeEnd                                      — пишется маркером, по центру
     *   writeEnd..writeEnd+POST_WRITE_HOLD                            — пауза в центре
     *   writeEnd+POST_WRITE_HOLD..flyEnd                              — летит на доску
     *   flyEnd..                                                      — на доске, pin+tape+stamp
     */
    private void renderCard(GuiGraphics gfx, CardLayout L, int idx, long elapsed, long now,
                            boolean isPrevious) {
        int totalChars = totalCharsIn(chunks[idx]);
        // 1.5.3: intro чанк скипает CENTER_APPEAR pop-in (writingStart=0) — это
        // убирает ~260ms «пустой растущей карточки» сразу после un-darken.
        long writingStart = writingStartT(idx);
        long writingEnd = writingStart + (long) totalChars * DRAW_CHAR_MS;
        long centerHoldEnd = writingEnd + POST_WRITE_HOLD_MS;
        long flyEnd = flyEndT(idx);
        boolean isFinal = (idx == finalIndex);
        // Если для этого чанка приoriт нет CENTER_APPEAR (intro), карточка стартует
        // сразу в полном размере по центру — writing идёт уже с первого кадра.
        boolean skipAppearPop = (writingStart == 0L);

        // ---- Геометрия: центр экрана vs центр карточки на доске ----
        int boardCx = L.x + L.w / 2;
        int boardCy = L.y + L.h / 2;
        int screenCx = this.width / 2;
        int screenCy = this.height / 2;

        float renderCx, renderCy, renderScale, renderRot;

        if (isFinal) {
            // FINAL — всегда в центре экрана, без fly, без поворота.
            if (!isPrevious && elapsed < CENTER_APPEAR_MS) {
                float p = elapsed / (float) CENTER_APPEAR_MS;
                if (p < 0.75f) {
                    renderScale = Mth.lerp(PaperRender.easeOut(p / 0.75f), 0.2f, CENTER_SCALE * 1.08f);
                } else {
                    renderScale = Mth.lerp((p - 0.75f) / 0.25f, CENTER_SCALE * 1.08f, CENTER_SCALE);
                }
            } else {
                renderScale = CENTER_SCALE;
            }
            renderCx = screenCx;
            renderCy = screenCy;
            renderRot = 0f;
        } else if (isPrevious || elapsed >= flyEnd) {
            renderCx = boardCx;
            renderCy = boardCy;
            renderScale = 1f;
            renderRot = L.rotationDeg;
        } else if (!skipAppearPop && elapsed < CENTER_APPEAR_MS) {
            float p = elapsed / (float) CENTER_APPEAR_MS;
            renderCx = screenCx;
            renderCy = screenCy;
            if (p < 0.75f) {
                renderScale = Mth.lerp(PaperRender.easeOut(p / 0.75f), 0.2f, CENTER_SCALE * 1.08f);
            } else {
                renderScale = Mth.lerp((p - 0.75f) / 0.25f, CENTER_SCALE * 1.08f, CENTER_SCALE);
            }
            renderRot = 0f;
        } else if (elapsed < centerHoldEnd) {
            renderCx = screenCx;
            renderCy = screenCy;
            renderScale = CENTER_SCALE;
            renderRot = 0f;
        } else {
            float p = (elapsed - centerHoldEnd) / (float) FLY_TO_BOARD_MS;
            float pe = easeInOut(Mth.clamp(p, 0f, 1f));
            renderCx = Mth.lerp(pe, screenCx, boardCx);
            renderCy = Mth.lerp(pe, screenCy, boardCy);
            renderScale = Mth.lerp(pe, CENTER_SCALE, 1f);
            renderRot = Mth.lerp(pe, 0f, L.rotationDeg);
        }

        PoseStack pose = gfx.pose();
        pose.pushPose();
        pose.translate(renderCx, renderCy, 0);
        pose.scale(renderScale, renderScale, 1f);
        pose.mulPose(Axis.ZP.rotationDegrees(renderRot));
        pose.translate(-boardCx, -boardCy, 0);

        // Бумажная карточка.
        PaperRender.drawPaperCard(gfx, L.x, L.y, L.w, L.h, 1f, PaperRender.PAPER_LIGHT);

        boolean isCredit = idx >= 0 && idx < isCreditChunk.length && isCreditChunk[idx];
        ResourceLocation photo = idx >= 0 && idx < chunkPhotos.length ? chunkPhotos[idx] : null;

        // Лента и pin — НЕ для финальной карточки (она не крепится к доске).
        boolean onBoard = !isFinal && (isPrevious || elapsed >= flyEnd);
        if (onBoard) {
            float tapeP = isPrevious ? 1f
                    : Mth.clamp((elapsed - flyEnd) / (float) TAPE_FADE_MS, 0f, 1f);
            if (tapeP > 0f) {
                int tapeAlpha = (int) (0xC0 * tapeP);
                int tapeW = isCredit ? 22 : 32;
                PaperRender.drawTape(gfx, L.x + 8, L.y - 5, tapeW, 10, tapeAlpha);
                PaperRender.drawTape(gfx, L.x + L.w - 8 - tapeW, L.y - 5, tapeW, 10, tapeAlpha);
            }
        }

        // Фото — на credit-карточках, слева 32×32 после посадки/появления.
        if (isCredit && photo != null) {
            // Тёмная рамка-«поляроид» вокруг фото.
            gfx.fill(L.x + 5, L.y + 5, L.x + 5 + 34, L.y + 5 + 34, 0xFF1A0F08);
            gfx.fill(L.x + 6, L.y + 6, L.x + 6 + 32, L.y + 6 + 32, 0xFF000000);
            try {
                // 11-arg overload (x,y, destW,destH, u,v, srcW,srcH, texW,texH)
                // Тут texW=texH=96 потому что мы конвертировали все фото с
                // max-side=96. Минорные расхождения в реальных размерах
                // (например 53×96) приведут к небольшому кропу — это
                // принимаемо для thumbnail'ов 32×32.
                gfx.blit(photo, L.x + 6, L.y + 6, 32, 32, 0f, 0f, 96, 96, 96, 96);
            } catch (Throwable ignored) {
                // Если текстура не загрузилась — оставляем чёрный квадрат-плейсхолдер.
            }
        }

        // Текст — writing с прогрессом.
        int charsRevealed;
        if (isPrevious) {
            charsRevealed = totalChars;
        } else {
            charsRevealed = elapsed < writingStart ? 0
                    : (int) Math.min(totalChars, (elapsed - writingStart) / DRAW_CHAR_MS);
        }
        if (charsRevealed > 0) {
            if (isCredit) {
                // Credit-карточка — собственный рендер: nickname крупно/жирно, роль помельче, фото слева.
                renderCreditText(gfx, L, chunks[idx], charsRevealed, isPrevious, idx, now);
            } else if (isPrevious) {
                renderTextFast(gfx, L, chunks[idx]);
            } else {
                long writingStartGlobal = phaseStartMs + writingStart;
                renderHandwrittenChunk(gfx, L, chunks[idx], charsRevealed, writingStartGlobal, now, idx);
            }
        }
        if (!isPrevious && !isCredit && charsRevealed < totalChars && elapsed >= writingStart) {
            renderPenCursor(gfx, L, chunks[idx], charsRevealed, now);
        }

        // Pin — только для не-финальных, после посадки.
        if (onBoard) {
            float pinP = isPrevious ? 1f
                    : Mth.clamp((elapsed - flyEnd) / (float) PIN_POP_MS, 0f, 1f);
            if (pinP > 0f) {
                float pinScale = 1.5f - 0.5f * PaperRender.easeOut(pinP);
                pose.pushPose();
                pose.translate(L.pinX, L.pinY, 0);
                pose.scale(pinScale, pinScale, 1f);
                PaperRender.drawPin(gfx, 0, 0, false);
                pose.popPose();
            }
        }

        // Stamp — для всех (включая final), после посадки/после writing-фазы.
        long stampStart = flyEnd + 60;
        if (isPrevious || elapsed >= stampStart) {
            float stampP = isPrevious ? 1f
                    : Mth.clamp((elapsed - stampStart) / (float) STAMP_POP_MS, 0f, 1f);
            if (stampP > 0f) renderStamp(gfx, L, idx, stampP);
        }

        pose.popPose();
    }

    private static float easeInOut(float t) {
        return t < 0.5f ? 2f * t * t : 1f - (1f - t) * (1f - t) * 2f;
    }

    /**
     * Дешёвый рендер «уже написанной» карточки: одна drawString на строку,
     * без per-char pose-manipulation. Жертвуем wobble-style для прошлых
     * карточек ради FPS — на них всё равно никто не пялится подробно.
     */
    private void renderTextFast(GuiGraphics gfx, CardLayout L, String[] lines) {
        int totalH = lines.length * LINE_SPACING_PX;
        int textAreaH = (int) (L.h * 0.65f);
        int startY = L.y + textAreaH / 2 - totalH / 2 + 2;
        for (int li = 0; li < lines.length; li++) {
            String full = lines[li];
            int fullWidth = (int) (font.width(full) * TEXT_SCALE);
            int startX = L.x + L.w / 2 - fullWidth / 2;
            PoseStack pose = gfx.pose();
            pose.pushPose();
            pose.translate(startX, startY + li * LINE_SPACING_PX, 0);
            pose.scale(TEXT_SCALE, TEXT_SCALE, 1f);
            gfx.drawString(font, full, 0, 0, PaperRender.INK, false);
            pose.popPose();
        }
    }

    private void renderHandwrittenChunk(GuiGraphics gfx, CardLayout L,
                                         String[] lines, int charsRevealed,
                                         long writingStartGlobalMs, long now, int idx) {
        int totalH = lines.length * LINE_SPACING_PX;
        int textAreaH = (int) (L.h * 0.65f);
        int startY = L.y + textAreaH / 2 - totalH / 2 + 2;
        int charIdxGlobal = 0;
        for (int li = 0; li < lines.length; li++) {
            String full = lines[li];
            int charsThisLine = Math.min(full.length(),
                    Math.max(0, charsRevealed - sumLines(lines, li)));
            String visible = full.substring(0, charsThisLine);
            drawHandwrittenLine(gfx, visible, full,
                    L.x + L.w / 2, startY + li * LINE_SPACING_PX,
                    idx * 1000 + li * 100, charIdxGlobal,
                    writingStartGlobalMs, now);
            charIdxGlobal += full.length();
        }
    }

    private static int sumLines(String[] lines, int upTo) {
        int n = 0;
        for (int i = 0; i < upTo; i++) n += lines[i].length();
        return n;
    }

    private void drawHandwrittenLine(GuiGraphics gfx, String visible, String full,
                                      int centerX, int y, int seed,
                                      int globalCharStart, long writingStartGlobalMs, long now) {
        Font font = this.font;
        int fullWidth = (int) (font.width(full) * TEXT_SCALE);
        int startX = centerX - fullWidth / 2;

        float curX = 0;
        for (int i = 0; i < visible.length(); i++) {
            String c = String.valueOf(visible.charAt(i));
            int h = hash(seed + i);
            float dy = ((h & 0x07) - 3) / 7f * CHAR_JITTER_Y_PX;
            float rot = (((h >> 3) & 0x07) - 3) / 7f * CHAR_JITTER_ROT_DEG * 2f;
            float baseScale = TEXT_SCALE + (((h >> 6) & 0x07) - 3) / 7f * CHAR_JITTER_SCALE * 2f;

            float scale = baseScale;
            int alpha = 0xFF;
            if (writingStartGlobalMs > 0) {  // активная запись → pop-in
                long bornMs = writingStartGlobalMs + (long) (globalCharStart + i) * DRAW_CHAR_MS;
                long age = now - bornMs;
                if (age >= 0 && age < CHAR_POP_MS) {
                    float popT = age / (float) CHAR_POP_MS;
                    float pop = 1f - Mth.clamp(popT, 0f, 1f);
                    scale = baseScale * (1f + 0.30f * pop);
                    alpha = (int) Mth.lerp(popT, 0.4f * 255, 1f * 255);
                }
            }
            int color = (alpha << 24) | (PaperRender.INK & 0x00FFFFFF);

            PoseStack pose = gfx.pose();
            pose.pushPose();
            pose.translate(startX + curX, y, 0);
            pose.translate(0, dy, 0);
            pose.scale(scale, scale, 1f);
            pose.mulPose(Axis.ZP.rotationDegrees(rot));
            gfx.drawString(font, c, 0, 0, color, false);
            pose.popPose();
            curX += font.width(c) * baseScale;
        }
    }

    private void renderPenCursor(GuiGraphics gfx, CardLayout L, String[] lines, int charsRevealed, long now) {
        int sum = 0;
        for (int li = 0; li < lines.length; li++) {
            int len = lines[li].length();
            if (charsRevealed <= sum + len) {
                String full = lines[li];
                int charsThisLine = charsRevealed - sum;
                int fullWidth = (int) (font.width(full) * TEXT_SCALE);
                int startX = L.x + L.w / 2 - fullWidth / 2;
                String drawnSoFar = full.substring(0, charsThisLine);
                int curX = (int) (font.width(drawnSoFar) * TEXT_SCALE);

                int totalH = lines.length * LINE_SPACING_PX;
                int textAreaH = (int) (L.h * 0.65f);
                int sy = L.y + textAreaH / 2 - totalH / 2 + 2;
                int px = startX + curX + 1;
                int py = sy + li * LINE_SPACING_PX + (int) (9 * TEXT_SCALE);

                float blink = 0.5f + 0.5f * Mth.sin(now / 90f);
                int a = (int) Mth.lerp(blink, 0.4f * 255, 0.9f * 255);
                int penCol = (a << 24) | (PaperRender.INK & 0x00FFFFFF);
                gfx.fill(px, py - 3, px + 3, py - 1, penCol);
                gfx.fill(px + 1, py - 1, px + 2, py, penCol);
                return;
            }
            sum += len;
        }
    }

    /**
     * Рендер credit-карточки: фото слева (рисуется в renderCard), справа текст:
     *   строка 0 (nickname) — крупнее, акцент чёрнилами (повтор для bold-эффекта)
     *   строки 1+ (role) — обычный размер, может переноситься по словам
     * Постепенное появление по char-count работает: nickname → потом role.
     */
    private void renderCreditText(GuiGraphics gfx, CardLayout L, String[] lines,
                                   int charsRevealed, boolean isPrevious, int idx, long now) {
        boolean hasPhoto = idx >= 0 && idx < chunkPhotos.length && chunkPhotos[idx] != null;
        // Текстовая область — правее фото, отступ от рамки.
        int textXStart = L.x + (hasPhoto ? 44 : 8);
        int textXEnd   = L.x + L.w - 6;
        int textW = Math.max(20, textXEnd - textXStart);

        // ВЕРТИКАЛЬНОЕ расположение:
        // nickname занимает строку покрупнее (scale 1.30) красным акцентом —
        // раньше был nick scale 1.05 + INK = сливался с role text;
        // role — wrap по textW, шрифт scale 0.85 + INK_SOFT.
        float nickScale = 1.30f;
        float roleScale = 0.85f;
        int nickH = (int) (10 * nickScale) + 5;
        int roleLineH = (int) (10 * roleScale) + 3;

        String nick = lines.length > 0 ? lines[0] : "";
        String role = lines.length > 1 ? lines[1] : "";

        // Wrap role по словам.
        List<String> roleLines = wrapByWords(this.font, role, (int) (textW / roleScale));

        int totalBlockH = nickH + roleLines.size() * roleLineH;
        int topY = L.y + (L.h - totalBlockH) / 2 + 1;

        // ----- Nickname -----
        int nickRevealed;
        if (isPrevious) {
            nickRevealed = nick.length();
        } else {
            nickRevealed = Math.min(nick.length(), charsRevealed);
        }
        if (nickRevealed > 0) {
            String shown = nick.substring(0, nickRevealed);
            // Используем minecraft:uniform — настоящий другой typeface, не
            // тот же default только покрупнее. Чтобы ник визуально оторвался
            // от мелкого role-текста, который рендерится дефолтным шрифтом.
            net.minecraft.network.chat.Style nickStyle = net.minecraft.network.chat.Style.EMPTY
                    .withFont(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "uniform"))
                    .withBold(true)
                    .withItalic(true);
            net.minecraft.network.chat.Component shownC =
                    net.minecraft.network.chat.Component.literal(shown).withStyle(nickStyle);
            PoseStack pose = gfx.pose();
            pose.pushPose();
            pose.translate(textXStart, topY, 0);
            pose.scale(nickScale, nickScale, 1f);
            // Ink shadow + 2-pass красный = жирный курсив на uniform-шрифте.
            gfx.drawString(font, shownC, 1, 1, PaperRender.withAlpha(PaperRender.INK, 0.35f), false);
            gfx.drawString(font, shownC, 0, 0, PaperRender.INK_RED, false);
            // Тонкая красная подчерк-линия снизу — на ширину текста (uniform width).
            int underlineW = font.width(shownC);
            gfx.fill(0, (int) (10), underlineW, (int) (10) + 1,
                     PaperRender.withAlpha(PaperRender.INK_RED, 0.55f));
            pose.popPose();
        }

        // ----- Role (после того как nickname написан) -----
        int roleStartCounter = nick.length();
        int roleRevealedTotal;
        if (isPrevious) {
            roleRevealedTotal = role.length();
        } else {
            roleRevealedTotal = Math.max(0, charsRevealed - roleStartCounter);
        }
        if (roleRevealedTotal > 0) {
            int charsLeft = roleRevealedTotal;
            int yPos = topY + nickH;
            for (String rl : roleLines) {
                if (charsLeft <= 0) break;
                String shown = rl;
                if (charsLeft < rl.length()) {
                    shown = rl.substring(0, charsLeft);
                }
                charsLeft -= rl.length();
                PoseStack pose = gfx.pose();
                pose.pushPose();
                pose.translate(textXStart, yPos, 0);
                pose.scale(roleScale, roleScale, 1f);
                gfx.drawString(font, shown, 0, 0, PaperRender.INK_SOFT, false);
                pose.popPose();
                yPos += roleLineH;
            }
        }
    }

    /** Простой word-wrap: жадно набивает слова в строку шириной maxW (в font-px). */
    private static List<String> wrapByWords(Font font, String text, int maxW) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            String candidate = cur.length() == 0 ? w : cur + " " + w;
            if (font.width(candidate) <= maxW) {
                cur.setLength(0);
                cur.append(candidate);
            } else {
                if (cur.length() > 0) out.add(cur.toString());
                cur.setLength(0);
                cur.append(w);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private void renderStamp(GuiGraphics gfx, CardLayout L, int idx, float popP) {
        // Pop-анимация — overshoot 1.5 → 1.0 в первые 60% popP.
        float popMul;
        if (popP < 0.6f) {
            popMul = Mth.lerp(popP / 0.6f, 1.5f, 1.0f);
        } else {
            popMul = 1.0f;
        }
        // Финальная шкала: pop-overshoot × stampScale (computed in buildLayout
        // to fit rotated stamp bbox inside card with STAMP_MARGIN_PX safe area).
        float scale = popMul * L.stampScale;

        int color = PaperRender.withAlpha(PaperRender.INK_RED, 0.9f);

        PoseStack pose = gfx.pose();
        pose.pushPose();
        pose.translate(L.stampX, L.stampY, 0);
        pose.scale(scale, scale, 1f);
        pose.mulPose(Axis.ZP.rotationDegrees(L.stampRotationDeg));

        if (L.stampRound) {
            PaperRender.drawRoundStamp(gfx, font, 0, 0, 24, L.stampText, "о.т.б.о.р", color);
        } else {
            PaperRender.drawRectStamp(gfx, font, L.stampText, 0, 0, color);
        }
        pose.popPose();
    }

    /**
     * Рисует красную нитку как полилинию по предвычисленным точкам, прогрессивно
     * (от 0 до 1). Каждый сегмент рисуется полностью, частичный — обрезается.
     *
     * На промежуточных waypoint'ах (1..n-2) рисует МАЛЕНЬКИЙ красный pin
     * чтобы визуально «поглотить» ~90° углы — без этого пин-ушки на изгибах
     * нити заметно ломали иллюзию плавности.
     */
    private void renderStringPath(GuiGraphics gfx, java.util.List<int[]> path,
                                   float progress, float alpha) {
        if (path == null || path.size() < 2 || progress <= 0f) return;
        int color = PaperRender.withAlpha(PaperRender.INK_RED, alpha);
        int n = path.size();
        double total = 0;
        double[] segs = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            int[] a = path.get(i), b = path.get(i + 1);
            segs[i] = Math.hypot(b[0] - a[0], b[1] - a[1]);
            total += segs[i];
        }
        if (total < 0.5) return;
        double target = total * Mth.clamp(progress, 0f, 1f);
        double covered = 0;
        int lastFullPoint = 0;  // индекс последней точки, до которой нить уже дошла полностью
        boolean truncated = false;
        for (int i = 0; i < n - 1; i++) {
            int[] a = path.get(i), b = path.get(i + 1);
            if (covered + segs[i] <= target) {
                drawThickLine(gfx, a[0], a[1], b[0], b[1], color);
                covered += segs[i];
                lastFullPoint = i + 1;
            } else {
                double remain = target - covered;
                double t = remain / segs[i];
                int mx = (int) (a[0] + (b[0] - a[0]) * t);
                int my = (int) (a[1] + (b[1] - a[1]) * t);
                drawThickLine(gfx, a[0], a[1], mx, my, color);
                truncated = true;
                break;
            }
        }
        // Pin'ы на промежуточных waypoint'ах — индекс 1..n-2 (endpoints держат
        // свои pin'ы карточек). Рисуем только до lastFullPoint (т.е. где нитка
        // уже доросла), чтобы прогрессивное появление выглядело синхронно.
        int upTo = truncated ? lastFullPoint : (n - 1);
        for (int i = 1; i < n - 1 && i <= upTo; i++) {
            int[] p = path.get(i);
            drawTinyRedPin(gfx, p[0], p[1], alpha);
        }
    }

    /**
     * Маленький 4×4 красный пин — закрывает изгибы нити на waypoint'ах.
     * v1.5.2: уменьшено с 5×5 до 4×4 пропорционально уменьшению толщины нити (3→2).
     * Тёмная окантовка + ярко-красный центр + 1px highlight.
     */
    private static void drawTinyRedPin(GuiGraphics gfx, int cx, int cy, float alpha) {
        int outline = PaperRender.withAlpha(0xFF400000, alpha);
        int red     = PaperRender.withAlpha(0xFFB42020, alpha);
        int hi      = PaperRender.withAlpha(0xFFE84A4A, alpha);
        // 3×3 outline
        gfx.fill(cx - 1, cy - 1, cx + 2, cy + 2, outline);
        // 1×1 red core
        gfx.fill(cx, cy, cx + 1, cy + 1, red);
        // 1×1 highlight (top-left of core)
        gfx.fill(cx - 1, cy - 1, cx, cy, hi);
    }

    /**
     * Реальные ЭКРАННЫЕ координаты pin'а с учётом поворота карточки.
     * Без этого нитка кончалась в "нерайованных" pin-координатах, а сама
     * кнопка визуально была сдвинута поворотом ±6° (≈12px смещение в углу)
     * → нитка казалась прицепленной к пустому месту рядом с pin'ом.
     */
    private static int[] visualPinPos(CardLayout L) {
        double cx = L.x + L.w / 2.0;
        double cy = L.y + L.h / 2.0;
        double rad = Math.toRadians(L.rotationDeg);
        double dx = L.pinX - cx;
        double dy = L.pinY - cy;
        double rx = cx + dx * Math.cos(rad) - dy * Math.sin(rad);
        double ry = cy + dx * Math.sin(rad) + dy * Math.cos(rad);
        return new int[]{(int) Math.round(rx), (int) Math.round(ry)};
    }

    /**
     * Строит ломаную (pin_from → waypoints → pin_to), которая ОБХОДИТ все
     * остальные карточки. Endpoints берутся в ЭКРАННЫХ координатах pin'а
     * с учётом поворота — иначе нитка цепляется не за кнопку.
     */
    private java.util.List<int[]> routePath(int fromIdx, int toIdx) {
        java.util.List<int[]> path = new ArrayList<>();
        int[] from = visualPinPos(layouts[fromIdx]);
        int[] to = visualPinPos(layouts[toIdx]);
        java.util.Set<Integer> skip = new java.util.HashSet<>();
        skip.add(fromIdx);
        skip.add(toIdx);
        path.add(from);

        // ITERATIVE routing: пока сегмент current→to пересекает какую-то карточку,
        // добавляем ОДИН waypoint в её ближайший угол и продолжаем. БЕЗ рекурсии:
        // никакого subdivision на половинки — это раньше создавало лишние изломы.
        int curX = from[0], curY = from[1];
        int safety = 0;
        while (safety++ < 6) {
            int hit = findFirstObstacle(curX, curY, to[0], to[1], skip);
            if (hit < 0) break;
            int[] corner = pickBestCorner(layouts[hit], curX, curY, to[0], to[1]);
            path.add(corner);
            skip.add(hit);
            curX = corner[0];
            curY = corner[1];
        }
        path.add(to);

        // POST-PASS: убрать waypoints которые стали не нужны — если прямая
        // path[i-1]→path[i+1] свободна, удаляем path[i]. Это убивает «лишние»
        // изломы (когда мы случайно вставили waypoint вокруг карточки которая
        // на самом деле не мешала прямой линии между соседними точками).
        java.util.Set<Integer> skipForSimplify = new java.util.HashSet<>(skip);
        skipForSimplify.add(fromIdx);
        skipForSimplify.add(toIdx);
        boolean changed = true;
        while (changed && path.size() > 2) {
            changed = false;
            for (int i = 1; i < path.size() - 1; i++) {
                int[] a = path.get(i - 1);
                int[] b = path.get(i + 1);
                if (findFirstObstacle(a[0], a[1], b[0], b[1], skipForSimplify) < 0) {
                    path.remove(i);
                    changed = true;
                    break;
                }
            }
        }
        return path;
    }

    private int findFirstObstacle(int x0, int y0, int x1, int y1, java.util.Set<Integer> skip) {
        int hitIdx = -1;
        double bestT = 1.0;
        int margin = 8;
        for (int i = 0; i < chunks.length; i++) {
            if (skip.contains(i)) continue;
            CardLayout L = layouts[i];
            if (L == null) continue;
            double t = segmentRectFirstHitT(x0, y0, x1, y1,
                    L.x - margin, L.y - margin,
                    L.x + L.w + margin, L.y + L.h + margin);
            if (t > 0.001 && t < bestT) {
                bestT = t;
                hitIdx = i;
            }
        }
        return hitIdx;
    }

    private int[] pickBestCorner(CardLayout O, int x0, int y0, int x1, int y1) {
        int m = 10;
        int bMinX = boardX + BOARD_FRAME_PX + 2;
        int bMaxX = boardX + boardW - BOARD_FRAME_PX - 2;
        int bMinY = boardY + BOARD_FRAME_PX + 2;
        int bMaxY = boardY + boardH - BOARD_FRAME_PX - 2;
        int[][] corners = {
                {O.x - m, O.y - m},
                {O.x + O.w + m, O.y - m},
                {O.x - m, O.y + O.h + m},
                {O.x + O.w + m, O.y + O.h + m}
        };
        int[] best = corners[0];
        double bestDist = Double.MAX_VALUE;
        for (int[] c : corners) {
            c[0] = Math.max(bMinX, Math.min(bMaxX, c[0]));
            c[1] = Math.max(bMinY, Math.min(bMaxY, c[1]));
            double d = Math.hypot(c[0] - x0, c[1] - y0) + Math.hypot(c[0] - x1, c[1] - y1);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    /**
     * Liang–Barsky: возвращает t∈(0,1) первой точки входа отрезка (x0,y0)→(x1,y1)
     * внутрь AABB (rxMin..rxMax, ryMin..ryMax). -1 если не пересекаются. Если
     * сегмент НАЧИНАЕТСЯ внутри AABB — возвращаем t выхода (значит мы внутри
     * obstacle bbox с начала, что в нашей задаче не должно случаться т.к.
     * мы скипаем from/to rects).
     */
    private static double segmentRectFirstHitT(int x0, int y0, int x1, int y1,
                                                 double rxMin, double ryMin,
                                                 double rxMax, double ryMax) {
        double dx = x1 - x0, dy = y1 - y0;
        double tEnter = 0, tLeave = 1;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x0 - rxMin, rxMax - x0, y0 - ryMin, ryMax - y0};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) {
                if (q[i] < 0) return -1;
            } else {
                double t = q[i] / p[i];
                if (p[i] < 0) {
                    if (t > tLeave) return -1;
                    if (t > tEnter) tEnter = t;
                } else {
                    if (t < tEnter) return -1;
                    if (t < tLeave) tLeave = t;
                }
            }
        }
        if (tEnter > tLeave) return -1;
        return tEnter > 0 ? tEnter : -1;  // start-inside случай — игнорим (skip from/to)
    }

    /**
     * Тонкая (1px) красная нить через повёрнутый rect — ОДИН gfx.fill + push/pop
     * pose вместо ~300 fill'ов Bresenham-ом. v1.5.2: 3px→2px, v1.5.4: 2px→1px
     * — user complained про слишком толстую нить.
     */
    private static void drawThickLine(GuiGraphics gfx, int x0, int y0, int x1, int y1, int color) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1) return;
        float angle = (float) (Math.atan2(dy, dx) * 180.0 / Math.PI);
        gfx.pose().pushPose();
        gfx.pose().translate(x0, y0, 0);
        gfx.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        gfx.fill(0, 0, (int) Math.ceil(length), 1, color);
        gfx.pose().popPose();
    }

    /**
     * Доска с деревянной рамой, пробковой поверхностью и винтами в углах.
     * Карточки крепятся именно на эту область (не на голую «стену»).
     */
    private void renderBoard(GuiGraphics gfx) {
        int bx = boardX, by = boardY, bw = boardW, bh = boardH;
        int frame = BOARD_FRAME_PX;

        // Тень от доски.
        gfx.fill(bx + 6, by + 10, bx + bw + 6, by + bh + 10, 0x90000000);
        gfx.fill(bx + 12, by + 18, bx + bw + 12, by + bh + 18, 0x40000000);

        // Внешняя рама — тёмное дерево с текстурой.
        int frameDark = 0xFF2A1A0E;
        int frameMid = 0xFF4A2D18;
        int frameLight = 0xFF6B4226;
        gfx.fill(bx, by, bx + bw, by + bh, frameDark);
        gfx.fill(bx + 2, by + 2, bx + bw - 2, by + bh - 2, frameMid);
        // Светлая полоса по верху рамы — блик
        gfx.fill(bx + 2, by + 2, bx + bw - 2, by + 4, frameLight);
        gfx.fill(bx + 2, by + 2, bx + 4, by + bh - 2, frameLight);

        // Кэшированные «волокна» дерева — без Random в render-кадре.
        if (cachedFibX != null) {
            for (int i = 0; i < cachedFibX.length; i++) {
                int fx = cachedFibX[i];
                int fy = cachedFibY[i];
                gfx.fill(fx, fy, fx + 1, fy + cachedFibLen[i], cachedFibColor[i]);
            }
        }

        // Пробковая поверхность — внутри рамы.
        int corkBase = 0xFFB08864;
        int ix = bx + frame;
        int iy = by + frame;
        int iw = bw - frame * 2;
        int ih = bh - frame * 2;
        gfx.fill(ix, iy, ix + iw, iy + ih, corkBase);

        // Кэшированные точки пробки.
        if (cachedDotX != null) {
            for (int i = 0; i < cachedDotX.length; i++) {
                int sx = cachedDotX[i];
                int sy = cachedDotY[i];
                gfx.fill(sx, sy,
                        Math.min(ix + iw, sx + cachedDotW[i]),
                        Math.min(iy + ih, sy + cachedDotH[i]),
                        cachedDotColor[i]);
            }
        }

        // Внутренняя тень от рамы по краям пробки.
        int sh = 5;
        for (int i = 0; i < sh; i++) {
            int a = (int) (110 * (1 - i / (float) sh));
            int aCol = a << 24;
            gfx.fill(ix + i, iy, ix + i + 1, iy + ih, aCol);
            gfx.fill(ix + iw - i - 1, iy, ix + iw - i, iy + ih, aCol);
            gfx.fill(ix, iy + i, ix + iw, iy + i + 1, aCol);
            gfx.fill(ix, iy + ih - i - 1, ix + iw, iy + ih - i, aCol);
        }

        // Винты в 4 углах рамы.
        int boltOff = frame / 2;
        drawScrewHead(gfx, bx + boltOff, by + boltOff);
        drawScrewHead(gfx, bx + bw - boltOff - 1, by + boltOff);
        drawScrewHead(gfx, bx + boltOff, by + bh - boltOff - 1);
        drawScrewHead(gfx, bx + bw - boltOff - 1, by + bh - boltOff - 1);
    }

    private static void drawScrewHead(GuiGraphics gfx, int cx, int cy) {
        // Тёмный обод
        gfx.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF1A0F08);
        // Металлический круг
        gfx.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF7B7064);
        gfx.fill(cx - 2, cy - 2, cx, cy, 0xFFB5A89A);
        // Прорезь
        gfx.fill(cx - 2, cy, cx + 3, cy + 1, 0xFF1A0F08);
    }

    private void renderCornerDecor(GuiGraphics gfx, long now) {
        Font font = this.font;
        gfx.pose().pushPose();
        gfx.pose().translate(this.width - 200, 22, 0);
        gfx.pose().mulPose(Axis.ZP.rotationDegrees(6f));
        PaperRender.drawScribble(gfx, font, "финальное досье", 0, 0,
                PaperRender.withAlpha(PaperRender.INK_RED, 0.75f));
        PaperRender.drawScribble(gfx, font, "дело №047", 0, 12,
                PaperRender.withAlpha(PaperRender.INK_FADED, 0.85f));
        gfx.pose().popPose();

        gfx.pose().pushPose();
        gfx.pose().translate(20, this.height - 60, 0);
        gfx.pose().mulPose(Axis.ZP.rotationDegrees(-4f));
        PaperRender.drawScribble(gfx, font, "печать дела.", 0, 0,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.85f));
        PaperRender.drawScribble(gfx, font, "2026", 0, 12,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.7f));
        gfx.pose().popPose();

        long gt = PaperRender.gameTime();
        if (((gt / 20L) % 5L) != 0L) {
            gfx.pose().pushPose();
            gfx.pose().translate(80, this.height - 30, 0);
            gfx.pose().mulPose(Axis.ZP.rotationDegrees(-8f));
            PaperRender.drawRectStamp(gfx, font, "СЕКРЕТНО", 0, 0,
                    PaperRender.withAlpha(PaperRender.INK_RED, 0.85f));
            gfx.pose().popPose();
        }
    }

    // ====== Helpers ======

    private static int totalCharsIn(String[] lines) {
        int n = 0;
        for (String s : lines) n += s.length();
        return n;
    }

    private static int hash(int x) {
        x = (x ^ 61) ^ (x >>> 16);
        x = x + (x << 3);
        x = x ^ (x >>> 4);
        x = x * 0x27d4eb2d;
        x = x ^ (x >>> 15);
        return x & 0x7FFFFFFF;
    }

    private static void playSound(SoundEvent event, float pitch, float volume) {
        if (event == null) return;
        try {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(event, pitch, volume));
        } catch (Throwable ignored) {}
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new OtborCreditsScreen());
    }

    // ====== Music instance с поддержкой fade-out ======

    /**
     * Кастомный sound instance: позволяет в любой момент стартовать плавное
     * затухание (3.5 секунды), после чего звук останавливается. Громкость
     * читается каждый tick через field {@code this.volume}.
     */
    public static class CreditsMusicInstance extends AbstractTickableSoundInstance {
        private static final long FADE_DURATION_MS = 3500;
        private long fadeStartMs = Long.MAX_VALUE;

        public CreditsMusicInstance(SoundEvent event) {
            // SoundSource.MASTER — играет независимо от настройки music-канала
            // в опциях игрока (на многих сборках music slider=0 по умолчанию).
            super(event, SoundSource.MASTER, SoundInstance.createUnseededRandom());
            this.looping = false;
            this.delay = 0;
            this.volume = 1.0f;
            this.pitch = 1.0f;
            this.relative = true;  // не зависит от позиции игрока
            this.attenuation = Attenuation.NONE;
        }

        /** Запустить fade — игнорирует повторные вызовы (срабатывает только первый раз). */
        public void triggerFade(long now) {
            if (fadeStartMs == Long.MAX_VALUE) fadeStartMs = now;
        }

        /** Принудительно остановить (при закрытии экрана). */
        public void stopMe() {
            this.stop();
        }

        @Override
        public void tick() {
            if (fadeStartMs == Long.MAX_VALUE) return;
            long now = System.currentTimeMillis();
            float p = Mth.clamp((now - fadeStartMs) / (float) FADE_DURATION_MS, 0f, 1f);
            this.volume = 1.0f - p;
            if (p >= 1f) this.stop();
        }
    }

    // ====== Layout record ======

    private record CardLayout(
            int x, int y, int w, int h,
            float rotationDeg,
            int arrivalSide,
            int pinX, int pinY,
            boolean pinIsRed,
            String stampText,
            int stampX, int stampY,
            float stampRotationDeg,
            boolean stampRound,
            float stampScale
    ) {}
}
